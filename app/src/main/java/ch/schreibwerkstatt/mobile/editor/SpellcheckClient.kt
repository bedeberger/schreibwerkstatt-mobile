package ch.schreibwerkstatt.mobile.editor

import androidx.annotation.VisibleForTesting
import ch.schreibwerkstatt.mobile.data.net.DictionaryAddRequest
import ch.schreibwerkstatt.mobile.data.net.LtCheckRequest
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rechtschreib-/Grammatikprüfung für den eingebetteten Focus-Editor.
 *
 * Der Spellcheck-Controller des OTA-Bundles würde per Default selbst
 * `fetch('/languagetool/check')` aufrufen — im WebView liefe das gegen den
 * Asset-Origin (`appassets.androidplatform.net`) und ohne `Authorization`.
 * Darum reicht [EditorBridge] die Prüfung hierher durch: OkHttp mit
 * `AuthInterceptor` gegen die echte Server-Basis-URL.
 *
 * Die Prüf-Antwort bleibt ein roher JSON-String (LanguageTool-`matches[]`) und
 * wird unverändert an den Controller zurückgegeben — kein DTO, keine Drift.
 */
class SpellcheckClient(
    private val net: NetworkClient,
    private val settings: SettingsStore,
) {

    /**
     * Prüft [text] und liefert den JSON-Body der Server-Antwort. Ist LanguageTool
     * serverseitig aus (404), kommt [DISABLED_JSON] zurück — der Controller
     * behandelt das als „Feature aus" (kein Retry, Badge grau).
     */
    suspend fun check(text: String, language: String?, bookId: Long, pageId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(!textTooLarge(text.length)) { "lt_text_too_large" }
                val baseUrl = settings.serverBaseUrlOnce() ?: error("Keine Server-URL")
                val resp = net.languagetool(baseUrl).check(
                    LtCheckRequest(
                        text = text,
                        // Nur Fallback: mit gesetztem bookId gewinnt die Buch-Locale
                        // des Servers. Die App kennt sie nicht und sendet meist null.
                        language = language?.takeIf { it.isNotBlank() && it != "auto" },
                        bookId = bookId,
                        pageId = pageId,
                    )
                )
                when {
                    resp.code() == 404 -> DISABLED_JSON
                    resp.isSuccessful -> resp.body()?.string()?.takeIf { it.isNotBlank() } ?: EMPTY_JSON
                    else -> error("lt_http_${resp.code()}")
                }
            }
        }

    /**
     * Wort ins persönliche Wörterbuch aufnehmen (buchübergreifend, `bookId=0` wie
     * im Web-Popover). Liefert true bei Erfolg — der Controller entfernt dann die
     * Markierung und prüft erneut.
     */
    suspend fun addWord(word: String, lang: String?): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = settings.serverBaseUrlOnce() ?: error("Keine Server-URL")
                val normalized = lang?.trim()?.takeIf { it.isNotEmpty() && it != "auto" } ?: "*"
                net.languagetool(baseUrl)
                    .addWord(DictionaryAddRequest(word = word, bookId = 0, lang = normalized))
                    .isSuccessful
            }
        }

    companion object {
        /** Server-Limit pro Prüf-Request (`routes/languagetool.js`, `TEXT_MAX`). */
        const val MAX_TEXT_CHARS: Int = 500_000

        /** Antwort-Marker für „LanguageTool serverseitig deaktiviert" (HTTP 404). */
        const val DISABLED_JSON: String = """{"disabled":true}"""

        private const val EMPTY_JSON: String = """{"matches":[]}"""

        /**
         * true, wenn der Text das Server-Limit reisst und gar nicht erst gesendet
         * werden darf (sonst 413). Genau am Limit ist noch erlaubt.
         */
        @VisibleForTesting
        fun textTooLarge(chars: Int): Boolean = chars > MAX_TEXT_CHARS
    }
}
