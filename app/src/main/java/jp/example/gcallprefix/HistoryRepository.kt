package jp.example.gcallprefix

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.historyDataStore by preferencesDataStore(name = "gcall_history")

class HistoryRepository(context: Context) {
    private val store = context.applicationContext.historyDataStore
    val entries = store.data.map { decode(it[ENTRIES]) }

    suspend fun append(entry: CallHistoryEntry) {
        store.edit { preferences ->
            preferences[ENTRIES] = encode(CallHistory.prepend(decode(preferences[ENTRIES]), entry))
        }
    }

    suspend fun clear() { store.edit { it.remove(ENTRIES) } }

    private fun decode(encoded: String?): List<CallHistoryEntry> {
        if (encoded == null) return emptyList()
        return try {
            val array = JSONArray(encoded)
            (0 until minOf(array.length(), CallHistory.MAX_ENTRIES)).map { index ->
                val row = array.getJSONObject(index)
                CallHistoryEntry(row.getLong("time"), row.getString("original"),
                    if (row.isNull("redirected")) null else row.getString("redirected"),
                    CallResult.valueOf(row.getString("result")))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun encode(entries: List<CallHistoryEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().apply {
                put("time", entry.timestampMillis)
                put("original", entry.originalNumber)
                put("redirected", entry.redirectedNumber ?: JSONObject.NULL)
                put("result", entry.result.name)
            })
        }
    }.toString()

    companion object {
        private val ENTRIES = stringPreferencesKey("entries")
        // Process scope: a normal service destruction must not cancel a pending write.
        // OS process termination can still lose records; this is diagnostic history.
        private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun recordAfterResponse(context: Context, entry: CallHistoryEntry) {
            val application = context.applicationContext
            writerScope.launch {
                try { HistoryRepository(application).append(entry) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Do not log phone numbers or interrupt the call. */ }
            }
        }
    }
}
