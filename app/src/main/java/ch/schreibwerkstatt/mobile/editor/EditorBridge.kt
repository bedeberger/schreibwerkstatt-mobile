package ch.schreibwerkstatt.mobile.editor

import android.util.Log
import android.webkit.JavascriptInterface
import ch.schreibwerkstatt.mobile.BuildConfig
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository
import ch.schreibwerkstatt.mobile.data.repo.SaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JS↔native Brücke für den eingebetteten Focus-Editor. Wird unter dem Namen
 * `SWHost` in die WebView injiziert (siehe host.html). Die Methoden laufen auf
 * einem Binder-Thread; Ergebnisse werden asynchron über
 * `window.__sw.resolve/reject` (per [evalJs], auf dem UI-Thread) zurückgereicht.
 *
 * Lade-/Speicher-Calls gehen über das [ContentRepository] (geteilter Room-Cache),
 * NICHT direkt gegen den Server.
 */
class EditorBridge(
    private val repo: ContentRepository,
    private val scope: CoroutineScope,
    private val pageId: Long,
    private val bookId: Long,
    /** Führt JS auf dem UI-Thread aus (WebView.evaluateJavascript). */
    private val evalJs: (String) -> Unit,
    /** Native UI-Events (Snackbar etc.). */
    private val onEvent: (EditorEvent) -> Unit,
    /** Dark-Mode-Wunsch (folgt isSystemInDarkTheme); steuert `data-theme` in der WebView. */
    private val darkTheme: Boolean,
) {
    private val json = Json { explicitNulls = false }

    private companion object {
        const val TAG = "EditorBridge"
    }

    @Serializable
    private data class BridgePage(val id: Long, val name: String?, val html: String?)

    @Serializable
    private data class SavePayload(val id: Long? = null, val name: String? = null, val html: String = "")

    @JavascriptInterface
    fun loadPage(reqId: String) {
        scope.launch {
            repo.loadPage(pageId, bookId)
                .onSuccess { p ->
                    val payload = json.encodeToString(
                        BridgePage.serializer(),
                        BridgePage(p.id, p.name, p.html ?: "<p><br></p>")
                    )
                    evalJs("window.__sw.resolve('${reqId.esc()}', ${jsString(payload)});")
                }
                .onFailure { e ->
                    evalJs("window.__sw.reject('${reqId.esc()}', ${jsString(e.message ?: "load failed")});")
                }
        }
    }

    @JavascriptInterface
    fun savePage(reqId: String, payloadJson: String) {
        scope.launch {
            val payload = runCatching { json.decodeFromString(SavePayload.serializer(), payloadJson) }.getOrNull()
            if (payload == null) {
                evalJs("window.__sw.reject('${reqId.esc()}', 'bad payload');")
                return@launch
            }
            when (val res = repo.savePage(pageId, bookId, payload.html)) {
                is SaveResult.Saved -> {
                    val out = json.encodeToString(
                        BridgePage.serializer(),
                        BridgePage(res.page.id, res.page.name, res.page.html)
                    )
                    evalJs("window.__sw.resolve('${reqId.esc()}', ${jsString(out)});")
                }
                is SaveResult.Queued -> {
                    // Lokal gespeichert (offline) — Editor darf weitermachen.
                    evalJs("window.__sw.resolve('${reqId.esc()}', null);")
                    onEvent(EditorEvent.SavedOffline)
                }
                is SaveResult.Conflict -> {
                    evalJs("window.__sw.resolve('${reqId.esc()}', null);")
                    onEvent(EditorEvent.Conflict(res.serverEditorName, res.serverUpdatedAt))
                }
                is SaveResult.Locked -> {
                    evalJs("window.__sw.resolve('${reqId.esc()}', null);")
                    onEvent(EditorEvent.Locked(res.lockedByEmail))
                }
            }
        }
    }

    /** Synchroner Boot-Call aus host.html: setzt `data-theme` ohne Hell-Flash. */
    @JavascriptInterface
    fun prefersDark(): Boolean = darkTheme

    @JavascriptInterface
    fun onReady() = onEvent(EditorEvent.Ready)

    @JavascriptInterface
    fun notifyError(msg: String) = onEvent(EditorEvent.Error(msg))

    @JavascriptInterface
    fun log(msg: String) {
        // Editor-interne JS-Logs nur in Debug-Builds nach Logcat spiegeln —
        // in Release bleibt die WebView still (keine PII/Inhalte ins Log).
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    /** JS-/JSON-String-Literal (Quoting/Escaping) für die evaluateJavascript-Einbettung. */
    private fun jsString(value: String): String = json.encodeToString(String.serializer(), value)

    private fun String.esc(): String = replace("\\", "\\\\").replace("'", "\\'")
}

sealed interface EditorEvent {
    data object Ready : EditorEvent
    data object SavedOffline : EditorEvent
    data class Conflict(val serverEditorName: String?, val serverUpdatedAt: String?) : EditorEvent
    data class Locked(val lockedByEmail: String?) : EditorEvent
    data class Error(val message: String) : EditorEvent
}
