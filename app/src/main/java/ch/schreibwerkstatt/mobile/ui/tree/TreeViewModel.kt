package ch.schreibwerkstatt.mobile.ui.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.data.net.dto.ChapterNodeDto
import ch.schreibwerkstatt.mobile.data.net.dto.TreeDto
import ch.schreibwerkstatt.mobile.data.repo.BUCHTYP_TAGEBUCH
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository
import ch.schreibwerkstatt.mobile.data.repo.SyncCoordinator
import ch.schreibwerkstatt.mobile.data.repo.diaryDateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

sealed interface TreeRow {
    val depth: Int
    data class Chapter(val name: String, override val depth: Int) : TreeRow
    data class Page(val id: Long, val name: String, override val depth: Int) : TreeRow
}

data class TreeUiState(
    val bookId: Long,
    val rows: List<TreeRow> = emptyList(),
    /** Server-`updated_at` (ISO-8601) je Seite aus dem lokalen Cache; Quelle für „zuletzt geändert". */
    val pageUpdatedAt: Map<Long, String?> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    /** true = Buch ist ein Tagebuch (`buchtyp='tagebuch'`) → Kalender-Modus verfügbar. */
    val isDiary: Boolean = false,
    /** true = Kalender statt Liste anzeigen (nur für Tagebücher relevant). */
    val calendarMode: Boolean = false,
    /** ISO-Tag `YYYY-MM-DD` → Page-ID für vorhandene Tagebuch-Einträge. */
    val diaryEntries: Map<String, Long> = emptyMap(),
    /** Aktuell im Kalender angezeigter Monat. */
    val calendarMonth: YearMonth = YearMonth.now(),
    /** true, während ein Eintrag serverseitig angelegt wird. */
    val creatingEntry: Boolean = false,
    /** Einmalige Fehlermeldung (Snackbar); nach Anzeige via [TreeViewModel.consumeMessage] löschen. */
    val message: String? = null,
)

class TreeViewModel(
    private val repo: ContentRepository,
    private val coordinator: SyncCoordinator,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState(bookId = bookId))
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    init {
        // Buchtyp aus dem Cache → entscheidet, ob der Kalender-Modus angeboten wird.
        viewModelScope.launch {
            val diary = repo.bookById(bookId)?.buchtyp == BUCHTYP_TAGEBUCH
            _state.update { it.copy(isDiary = diary, calendarMode = diary) }
        }
        load()
        // Im Hintergrund Offline-Cache der Seiten auffrischen (Delta-Pull).
        viewModelScope.launch { repo.syncBook(bookId) }
        // „Zuletzt geändert" je Seite reaktiv aus dem Room-Cache spiegeln
        // (wird vom Delta-Pull aktualisiert).
        viewModelScope.launch {
            repo.observePages(bookId).collect { pages ->
                _state.value = _state.value.copy(
                    pageUpdatedAt = pages.associate { it.id to it.updatedAt }
                )
            }
        }
        // Bei (wiederhergestellter) Verbindung erneut pullen + Struktur neu laden.
        // drop(1): den Startwert überspringen (init macht das schon oben).
        viewModelScope.launch {
            coordinator.online.drop(1).collect { isOnline ->
                if (isOnline) {
                    repo.syncBook(bookId)
                    load()
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.tree(bookId)
                .onSuccess { tree ->
                    val rows = flatten(tree)
                    _state.update {
                        it.copy(
                            rows = rows,
                            diaryEntries = diaryEntriesOf(rows),
                            loading = false,
                        )
                    }
                }
                .onFailure { _state.value = _state.value.copy(error = it.message, loading = false) }
        }
    }

    fun setCalendarMode(enabled: Boolean) = _state.update { it.copy(calendarMode = enabled) }

    fun stepMonth(delta: Long) = _state.update { it.copy(calendarMonth = it.calendarMonth.plusMonths(delta)) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Eintrag für `dateIso` (`YYYY-MM-DD`) öffnen: existiert er, direkt; sonst
     * serverseitig anlegen. Nach Erfolg wird der Baum neu geladen und [onOpen]
     * mit der Page-ID aufgerufen (für die Editor-Navigation).
     */
    fun openOrCreateEntry(dateIso: String, onOpen: (pageId: Long, pageName: String) -> Unit) {
        _state.value.diaryEntries[dateIso]?.let { onOpen(it, dateIso); return }
        if (_state.value.creatingEntry) return
        viewModelScope.launch {
            _state.update { it.copy(creatingEntry = true) }
            repo.createDiaryEntry(bookId, dateIso)
                .onSuccess { pageId ->
                    repo.syncBook(bookId)
                    load()
                    _state.update { it.copy(creatingEntry = false) }
                    onOpen(pageId, dateIso)
                }
                .onFailure { e ->
                    _state.update { it.copy(creatingEntry = false, message = e.error()) }
                }
        }
    }

    private fun Throwable.error(): String = message ?: this::class.simpleName ?: "Fehler"

    private fun diaryEntriesOf(rows: List<TreeRow>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        rows.filterIsInstance<TreeRow.Page>().forEach { page ->
            diaryDateOf(page.name)?.let { date -> out.putIfAbsent(date, page.id) }
        }
        return out
    }

    private fun flatten(tree: TreeDto): List<TreeRow> {
        val rows = mutableListOf<TreeRow>()
        tree.topPages.forEach { rows += TreeRow.Page(it.id, it.name, depth = 0) }
        // Der Server liefert Kapitel je nach Pfad in zwei Formen: verschachtelt
        // (Unterkapitel in `subchapters`) ODER flach (alle Kapitel in `chapters`,
        // Hierarchie nur über `parent_chapter_id`). Beide hier vereinheitlichen:
        // flache Kinder über parent_chapter_id den Wurzelkapiteln zuordnen, damit
        // die `walk`-Rekursion in jedem Fall die volle Tiefe ausgibt.
        val ids = tree.chapters.map { it.id }.toSet()
        val childrenOf = tree.chapters
            .filter { it.parent_chapter_id != null && it.parent_chapter_id in ids }
            .groupBy { it.parent_chapter_id!! }
        tree.chapters
            .filter { it.parent_chapter_id == null || it.parent_chapter_id !in ids }
            .forEach { walk(it, depth = 0, into = rows, childrenOf = childrenOf) }
        return rows
    }

    private fun walk(
        chapter: ChapterNodeDto,
        depth: Int,
        into: MutableList<TreeRow>,
        childrenOf: Map<Long, List<ChapterNodeDto>>,
    ) {
        into += TreeRow.Chapter(chapter.name, depth)
        chapter.pages.forEach { into += TreeRow.Page(it.id, it.name, depth + 1) }
        // Verschachtelte Form: Unterkapitel hängen direkt am Knoten.
        chapter.subchapters.forEach { walk(it, depth + 1, into, childrenOf) }
        // Flache Form: Unterkapitel referenzieren diesen Knoten per parent_chapter_id.
        childrenOf[chapter.id]?.forEach { walk(it, depth + 1, into, childrenOf) }
    }

    companion object {
        fun factory(locator: ServiceLocator, bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { TreeViewModel(locator.repository, locator.syncCoordinator, bookId) }
        }
    }
}
