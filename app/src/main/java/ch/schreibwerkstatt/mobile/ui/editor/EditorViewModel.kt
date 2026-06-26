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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Läuft, solange ein Diktat-Segment auf eine Sprechpause überwacht wird. */
    private var monitorJob: Job? = null

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

        // Presence-Heartbeat: solange der Editor offen ist, regelmässig pingen,
        // damit die Server-Präsenz nicht abläuft. Endet mit dem viewModelScope.
        viewModelScope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                repo.devicePing(bookId, pageId)
            }
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

    /** Zeigt eine Hinweis-Snackbar (z.B. fehlende Mikrofon-Berechtigung). */
    fun notify(message: String) { _state.value = _state.value.copy(snackbar = message) }

    // ── Diktat ────────────────────────────────────────────────────────────────

    /**
     * Startet/stoppt die Aufnahme. Beim Start überwacht ein Monitor-Job die
     * Amplitude und beendet das Segment automatisch nach einer Sprechpause
     * (oder an der Maximaldauer). Manuelles Antippen stoppt sofort. In beiden
     * Fällen wird transkribiert und [onText] mit dem erkannten Text aufgerufen.
     */
    fun toggleDictation(onText: (String) -> Unit) {
        if (!dictation.isRecording) {
            dictation.startRecording()
                .onSuccess {
                    _state.value = _state.value.copy(recording = true)
                    monitorJob = viewModelScope.launch { monitorSilence(onText) }
                }
                .onFailure { _state.value = _state.value.copy(snackbar = "Aufnahme fehlgeschlagen: ${it.message}") }
        } else {
            monitorJob?.cancel()
            monitorJob = null
            transcribeCurrentSegment(onText)
        }
    }

    /**
     * Pollt die Amplitude und beendet das Segment, sobald nach erkannter Sprache
     * eine Pause von [SILENCE_HANGOVER_MS] vorliegt oder [MAX_SEGMENT_MS]
     * erreicht ist. Wird der Job (manueller Stopp) gecancelt, bricht [delay] ab.
     */
    private suspend fun monitorSilence(onText: (String) -> Unit) {
        var spoke = false
        var silentMs = 0L
        var elapsedMs = 0L
        while (dictation.isRecording) {
            delay(POLL_MS)
            elapsedMs += POLL_MS
            if (dictation.currentAmplitude() >= SPEECH_AMPLITUDE) {
                spoke = true
                silentMs = 0L
            } else {
                silentMs += POLL_MS
            }
            val pauseAfterSpeech = spoke && silentMs >= SILENCE_HANGOVER_MS
            if (pauseAfterSpeech || elapsedMs >= MAX_SEGMENT_MS) {
                monitorJob = null
                transcribeCurrentSegment(onText)
                return
            }
        }
    }

    private fun transcribeCurrentSegment(onText: (String) -> Unit) {
        if (!dictation.isRecording) return
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

    fun resolveConflictWithServer(onApplied: (html: String) -> Unit) {
        viewModelScope.launch {
            repo.resolveWithServerVersion(pageId, bookId)
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        conflict = null,
                        snackbar = "Server-Version geladen – lokale Änderung verworfen.",
                    )
                    onApplied(page.html ?: "<p><br></p>")
                }
                .onFailure { _state.value = _state.value.copy(snackbar = "Laden fehlgeschlagen: ${it.message}") }
        }
    }

    fun dismissConflict() { _state.value = _state.value.copy(conflict = null) }

    override fun onCleared() {
        monitorJob?.cancel()
        dictation.cancel()
        super.onCleared()
    }

    companion object {
        /** Presence-Heartbeat-Intervall. */
        private const val PING_INTERVAL_MS = 30_000L

        // ── VAD-Parameter (Stille-Erkennung) ──
        /** Poll-Intervall der Amplitude. */
        private const val POLL_MS = 150L
        /** Ab dieser Spitzenamplitude (0..32767) gilt das Segment als „Sprache". */
        private const val SPEECH_AMPLITUDE = 1_500
        /** Stille-Dauer nach Sprache, die ein Segment automatisch beendet. */
        private const val SILENCE_HANGOVER_MS = 2_000L
        /** Harte Obergrenze pro Segment (Schutz vor dem 5-MB-Server-Limit). */
        private const val MAX_SEGMENT_MS = 120_000L

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
