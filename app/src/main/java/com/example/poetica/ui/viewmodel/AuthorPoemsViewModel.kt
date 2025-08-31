package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.repository.PoemRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthorPoemsUiState(
    val authorName: String = "",
    val poems: List<Poem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false
)

class AuthorPoemsViewModel(
    private val authorName: String,
    private val repository: PoemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _poems = MutableStateFlow<List<Poem>>(emptyList())
    
    val uiState: StateFlow<AuthorPoemsUiState> = combine(
        _poems,
        _isLoading,
        _isRefreshing,
        _error
    ) { poems, isLoading, isRefreshing, error ->
        AuthorPoemsUiState(
            authorName = authorName,
            poems = poems.sortedBy { it.title },
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthorPoemsUiState(
            authorName = authorName,
            isLoading = true
        )
    )
    
    init {
        loadAuthorPoems()
    }
    
    fun refresh() {
        loadAuthorPoems(isRefresh = true)
    }
    
    private fun loadAuthorPoems(isRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                if (isRefresh) {
                    _isRefreshing.value = true
                } else {
                    _isLoading.value = true
                }
                _error.value = null
                
                repository.getPoemsByAuthorMetadata(authorName).collect { poems ->
                    _poems.value = poems
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to load poems: ${e.message}"
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }
}