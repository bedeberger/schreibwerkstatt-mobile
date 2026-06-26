package ch.schreibwerkstatt.mobile.ui.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.data.net.dto.ChapterNodeDto
import ch.schreibwerkstatt.mobile.data.net.dto.TreeDto
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository
import ch.schreibwerkstatt.mobile.data.repo.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
)

class TreeViewModel(
    private val repo: ContentRepository,
    private val coordinator: SyncCoordinator,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState(bookId = bookId))
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    init {
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
                .onSuccess { _state.value = _state.value.copy(rows = flatten(it), loading = false) }
                .onFailure { _state.value = _state.value.copy(error = it.message, loading = false) }
        }
    }

    private fun flatten(tree: TreeDto): List<TreeRow> {
        val rows = mutableListOf<TreeRow>()
        tree.topPages.forEach { rows += TreeRow.Page(it.id, it.name, depth = 0) }
        tree.chapters.forEach { walk(it, depth = 0, into = rows) }
        return rows
    }

    private fun walk(chapter: ChapterNodeDto, depth: Int, into: MutableList<TreeRow>) {
        into += TreeRow.Chapter(chapter.name, depth)
        chapter.pages.forEach { into += TreeRow.Page(it.id, it.name, depth + 1) }
        chapter.children.forEach { walk(it, depth + 1, into) }
    }

    companion object {
        fun factory(locator: ServiceLocator, bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { TreeViewModel(locator.repository, locator.syncCoordinator, bookId) }
        }
    }
}
