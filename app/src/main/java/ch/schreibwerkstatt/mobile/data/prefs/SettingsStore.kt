package ch.schreibwerkstatt.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "sw_settings")

/** Nutzergewähltes Farbschema. SYSTEM folgt der System-Einstellung. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Nicht-geheime App-Konfiguration (DataStore/Preferences):
 *  - Server-Basis-URL (beim ersten Start abgefragt)
 *  - device_id (stabile Installations-UUID, getrennt vom Auth-Token; für
 *    Konflikt-/Presence-Zuordnung, siehe PUT /content/pages/:id { device_id }).
 *  - theme_mode (Hell/Dunkel/System; rein kosmetisch).
 *
 * Das geheime Device-Token liegt NICHT hier, sondern verschlüsselt im
 * [TokenStore].
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_base_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
    }

    val serverBaseUrl: Flow<String?> =
        context.dataStore.data.map { it[Keys.SERVER_URL] }

    /** Gewähltes Farbschema; Default [ThemeMode.SYSTEM] bei unbekanntem/leerem Wert. */
    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { parseThemeMode(it[Keys.THEME_MODE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** Periodischer Background-Sync (WorkManager, stündlich). Default: aktiviert. */
    val backgroundSync: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BACKGROUND_SYNC] ?: true }

    suspend fun backgroundSyncOnce(): Boolean =
        context.dataStore.data.map { it[Keys.BACKGROUND_SYNC] ?: true }.first()

    suspend fun setBackgroundSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_SYNC] = enabled }
    }

    suspend fun serverBaseUrlOnce(): String? =
        context.dataStore.data.map { it[Keys.SERVER_URL] }.first()

    suspend fun setServerBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = normalizeBaseUrl(url) }
    }

    /** Stabile Installations-UUID; lazy erzeugt und persistiert. */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.map { it[Keys.DEVICE_ID] }.first()
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    companion object {
        /** Trailing-Slash entfernen; Retrofit-baseUrl bekommt ihn separat. */
        fun normalizeBaseUrl(raw: String): String =
            raw.trim().trimEnd('/')

        /** Robust gegen alte/kaputte Werte: fällt auf [ThemeMode.SYSTEM] zurück. */
        fun parseThemeMode(raw: String?): ThemeMode =
            raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
}
