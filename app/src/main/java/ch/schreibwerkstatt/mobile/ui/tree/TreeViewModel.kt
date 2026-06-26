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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TreeRow {
    val depth: Int
    data class Chapter(val name: String, override val depth: Int) : TreeRow
    data class Page(val id: Long, val name: String, override val depth: Int) : TreeRow
}

data class TreeUiState(
    val bookId: Long,
    val rows: List<TreeRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class TreeViewModel(
    private val repo: ContentRepository,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState(bookId = bookId))
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    init {
        load()
        // Im Hintergrund Offline-Cache der Seiten auffrischen (Delta-Pull).
        viewModelScope.launch { repo.syncBook(bookId) }
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
            initializer { TreeViewModel(locator.repository, bookId) }
        }
    }
}
