package ch.schreibwerkstatt.mobile.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.audio.DictationController
import ch.schreibwerkstatt.mobile.bundle.BundleManager
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository
import ch.schreibwerkstatt.mobile.editor.EditorBridge
import ch.schreibwerkstatt.mobile.editor.EditorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface BundleState {
    data object Loading : BundleState
    data class Ready(val dir: File) : BundleState
    data class Error(val message: String) : BundleState
}

data class EditorUiState(
    val bundle: BundleState = BundleState.Loading,
    val sttEnabled: Boolean = false,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val snackbar: String? = null,
    val conflict: EditorEvent.Conflict? = null,
)

class EditorViewModel(
    private val repo: ContentRepository,
    private val bundleManager: BundleManager,
    private val network: NetworkClient,
    private val settings: SettingsStore,
    private val dictation: DictationController,
    val bookId: Long,
    val pageId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Scope für die Bridge (Lade-/Speicher-Coroutinen). */
    val bridgeScope: CoroutineScope get() = viewModelScope

    init {
        viewModelScope.launch {
            val base = settings.serverBaseUrlOnce()
            if (base == null) {
                _state.value = _state.value.copy(bundle = BundleState.Error("Keine Server-URL"))
                return@launch
            }
            // Bundle OTA-sicherstellen.
            bundleManager.ensureBundle(base)
                .onSuccess { ok ->
                    _state.value = if (ok) _state.value.copy(bundle = BundleState.Ready(bundleManager.bundleDir))
                    else _state.value.copy(bundle = BundleState.Error("Bundle nicht verfügbar"))
                }
                .onFailure { _state.value = _state.value.copy(bundle = BundleState.Error(it.message ?: "Bundle-Fehler")) }

            // STT-Verfügbarkeit prüfen (Mic-Button nur bei stt.enabled).
            runCatching { network.config(base).config().stt?.enabled == true }
                .onSuccess { _state.value = _state.value.copy(sttEnabled = it) }

            // Schreibendes Gerät als Buch-Präsenz registrieren (best effort).
            repo.devicePing(bookId, pageId)
        }
    }

    fun newBridge(evalJs: (String) -> Unit, darkTheme: Boolean): EditorBridge =
        EditorBridge(
            repo = repo,
            scope = bridgeScope,
            pageId = pageId,
            bookId = bookId,
            evalJs = evalJs,
            onEvent = ::onEditorEvent,
            darkTheme = darkTheme,
        )

    private fun onEditorEvent(event: EditorEvent) {
        _state.value = when (event) {
            is EditorEvent.Ready -> _state.value
            is EditorEvent.SavedOffline -> _state.value.copy(snackbar = "Offline gespeichert – wird später synchronisiert.")
            is EditorEvent.Conflict -> _state.value.copy(
                conflict = event,
                snackbar = "Konflikt: ${event.serverEditorName ?: "jemand"} hat die Seite geändert.",
            )
            is EditorEvent.Locked -> _state.value.copy(
                snackbar = "Seite gesperrt durch ${event.lockedByEmail ?: "Lektorat"}.",
            )
            is EditorEvent.Error -> _state.value.copy(snackbar = "Editor-Fehler: ${event.message}")
        }
    }

    fun consumeSnackbar() { _state.value = _state.value.copy(snackbar = null) }

    // ── Diktat ────────────────────────────────────────────────────────────────

    /** Startet/stoppt die Aufnahme. Bei Stopp wird transkribiert und [onText] aufgerufen. */
    fun toggleDictation(onText: (String) -> Unit) {
        if (!dictation.isRecording) {
            dictation.startRecording()
                .onSuccess { _state.value = _state.value.copy(recording = true) }
                .onFailure { _state.value = _state.value.copy(snackbar = "Aufnahme fehlgeschlagen: ${it.message}") }
        } else {
            _state.value = _state.value.copy(recording = false, transcribing = true)
            viewModelScope.launch {
                dictation.stopAndTranscribe(bookId, pageId)
                    .onSuccess { text ->
                        _state.value = _state.value.copy(transcribing = false)
                        if (text.isNotBlank()) onText(text)
                    }
                    .onFailure {
                        _state.value = _state.value.copy(transcribing = false, snackbar = it.message)
                    }
            }
        }
    }

    fun resolveConflictWithServer(onApplied: (html: String) -> Unit) {
        viewModelScope.launch {
            repo.resolveWithServerVersion(pageId, bookId)
                .onSuccess { page ->
                    _state.value = _state.value.copy(conflict = null)
                    onApplied(page.html ?: "<p><br></p>")
                }
                .onFailure { _state.value = _state.value.copy(snackbar = "Laden fehlgeschlagen: ${it.message}") }
        }
    }

    fun dismissConflict() { _state.value = _state.value.copy(conflict = null) }

    override fun onCleared() {
        dictation.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(locator: ServiceLocator, bookId: Long, pageId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EditorViewModel(
                        repo = locator.repository,
                        bundleManager = locator.bundleManager,
                        network = locator.network,
                        settings = locator.settings,
                        dictation = locator.dictationController(),
                        bookId = bookId,
                        pageId = pageId,
                    )
                }
            }
    }
}
