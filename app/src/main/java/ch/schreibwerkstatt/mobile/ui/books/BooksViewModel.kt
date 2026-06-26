package ch.schreibwerkstatt.mobile.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.data.db.BookEntity
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BooksViewModel(private val repo: ContentRepository) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repo.refreshBooks().onFailure { _error.value = it.message }
            _refreshing.value = false
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        fun factory(locator: ServiceLocator): ViewModelProvider.Factory = viewModelFactory {
            initializer { BooksViewModel(locator.repository) }
        }
    }
}
