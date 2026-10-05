package jp.example.gcallprefix

enum class CallResult(val label: String) {
    REDIRECTED("0063付与（リダイレクト応答済み）"),
    DISABLED("OFFのため変更なし"),
    EXCLUDED_NUMBER("除外番号のため変更なし"),
    UNSUPPORTED_NUMBER("対象外の番号形式のため変更なし"),
    EMERGENCY_NUMBER("緊急番号のため変更なし"),
    EMERGENCY_CHECK_FAILED("緊急番号を確認できないため変更なし"),
    SETTINGS_FAILED("設定読込失敗のため変更なし"),
    TIMEOUT("処理タイムアウトのため変更なし"),
    FRAMEWORK_TIMEOUT("Android応答期限超過（発信中止の可能性）"),
    RESPONSE_FAILED("Androidへの応答に失敗（発信結果不明）")
}

data class CallDecision(val result: CallResult, val redirectedNumber: String? = null)

data class CallHistoryEntry(
    val timestampMillis: Long,
    val originalNumber: String,
    val redirectedNumber: String?,
    val result: CallResult
)

object CallHistory {
    const val MAX_ENTRIES = 20
    /** Arrival order is retained even if the system clock is adjusted. */
    fun prepend(existing: List<CallHistoryEntry>, entry: CallHistoryEntry): List<CallHistoryEntry> =
        (listOf(entry) + existing).take(MAX_ENTRIES)
}
