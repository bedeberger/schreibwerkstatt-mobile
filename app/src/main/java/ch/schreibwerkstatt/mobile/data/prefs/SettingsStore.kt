package ch.schreibwerkstatt.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "sw_settings")

/**
 * Nicht-geheime App-Konfiguration (DataStore/Preferences):
 *  - Server-Basis-URL (beim ersten Start abgefragt)
 *  - device_id (stabile Installations-UUID, getrennt vom Auth-Token; für
 *    Konflikt-/Presence-Zuordnung, siehe PUT /content/pages/:id { device_id }).
 *
 * Das geheime Device-Token liegt NICHT hier, sondern verschlüsselt im
 * [TokenStore].
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_base_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val serverBaseUrl: Flow<String?> =
        context.dataStore.data.map { it[Keys.SERVER_URL] }

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
    }
}
