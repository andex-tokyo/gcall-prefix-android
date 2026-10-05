package jp.example.gcallprefix

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gcallDataStore by preferencesDataStore(name = "gcall_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.gcallDataStore
    val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }
    suspend fun setEnabled(enabled: Boolean) { store.edit { it[ENABLED] = enabled } }
    private companion object { val ENABLED = booleanPreferencesKey("enabled") }
}
