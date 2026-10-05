package jp.example.gcallprefix

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }
    private val repository by lazy { SettingsRepository(applicationContext) }
    private val historyRepository by lazy { HistoryRepository(applicationContext) }
    private var history by mutableStateOf<List<CallHistoryEntry>>(emptyList())
    private var historyLoaded by mutableStateOf(false)
    private var historyBusy by mutableStateOf(false)
    private var historyMessage by mutableStateOf<String?>(null)
    private var roleHeld by mutableStateOf(false)
    private var roleAvailable by mutableStateOf(false)
    private var enabledState by mutableStateOf<Boolean?>(null)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshRole()
        lifecycleScope.launch {
            try { repository.enabled.collect { enabledState = it } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { enabledState = null; message = "設定を読み込めません。アプリを開き直してください。" }
        }
        lifecycleScope.launch {
            try {
                historyRepository.entries.collect { history = it; historyLoaded = true }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { historyMessage = "履歴を読み込めません。アプリを開き直してください。" }
        }
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("G-Call 自動付与", style = MaterialTheme.typography.headlineMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("G-Call 自動付与")
                                Text(if (enabledState == null) "設定読込中" else if (enabledState == true) "ON" else "OFF")
                            }
                            Switch(checked = enabledState == true, enabled = enabledState != null && !busy,
                                onCheckedChange = { saveEnabled(it) })
                        }
                        Text("使用プレフィックス: ${PrefixRule.PREFIX}", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        Text("Call Redirection Service", style = MaterialTheme.typography.titleMedium)
                        Text(when { roleHeld -> "有効"; !roleAvailable -> "この端末では利用できません"; else -> "無効" })
                        if (!roleHeld) {
                            Button(onClick = { openRoleSettings() }) { Text("Androidの設定を開く") }
                        }
                        Text(if (roleHeld && enabledState == true) "通常の電話アプリからの発信に0063を付与します。"
                            else "自動付与にはスイッチONとサービスの有効化が必要です。")
                        HorizontalDivider()
                        Text("除外対象", style = MaterialTheme.typography.titleMedium)
                        Text("• 110 / 118 / 119\n• Androidが緊急通報と判定する番号\n• 0120 / 020 / 060 / 0570 / 0800 / 0990 で始まる番号\n• 0170 / 0180 / 0190 などの特殊サービス番号\n• # / * で始まる番号\n• すでに0063で始まる番号")
                        Text("国内固定電話10桁、050・070・080・090の11桁が対象です。国際電話は+国番号と数字のみのE.164形式に対応し、+を0063に置き換えます。+81は国内形式で判定します。184／186は0063の前に維持します。010・001・0033等の国際発信形式、短縮番号・ポーズ付き番号は変更しません。海外では手動でOFFにしてください。")
                        HorizontalDivider()
                        Text("発信処理履歴（直近20件）", style = MaterialTheme.typography.titleMedium)
                        Text("番号は省略せず端末内に保存します。接続成功・料金適用を示す履歴ではありません。緊急通報など、Androidから渡されない発信は記録されません。")
                        OutlinedButton(onClick = { clearHistory() },
                            enabled = historyLoaded && history.isNotEmpty() && !historyBusy) {
                            Text(if (historyBusy) "削除中…" else "履歴を削除")
                        }
                        if (!historyLoaded && historyMessage == null) Text("履歴読込中…")
                        else if (historyLoaded && history.isEmpty()) Text("処理履歴はありません")
                        history.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                                        .format(Date(entry.timestampMillis)), style = MaterialTheme.typography.labelLarge)
                                    Text("発信番号: ${entry.originalNumber}")
                                    entry.redirectedNumber?.let { Text("変更後番号: $it") }
                                    Text(entry.result.label)
                                }
                            }
                        }
                        historyMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); refreshRole() }

    private fun refreshRole() {
        roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION) == true
        roleHeld = roleAvailable && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) == true
    }

    private fun saveEnabled(value: Boolean) {
        busy = true
        message = null
        lifecycleScope.launch {
            try { repository.setEnabled(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "設定を保存できませんでした。再度お試しください。" }
            finally { busy = false }
        }
    }

    private fun clearHistory() {
        historyBusy = true
        historyMessage = null
        lifecycleScope.launch {
            try { historyRepository.clear() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { historyMessage = "履歴を削除できませんでした。再度お試しください。" }
            finally { historyBusy = false }
        }
    }

    private fun openRoleSettings() {
        try {
            if (roleAvailable) roleRequest.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION))
            else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: ActivityNotFoundException) { message = "設定アプリを開けません。端末の設定から既定のアプリを確認してください。" }
        }
    }
}
