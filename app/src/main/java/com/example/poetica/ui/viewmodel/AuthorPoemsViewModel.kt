package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.poetica.data.model.Poem
import com.example.poetica.data.repository.PoemRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthorPoemsUiState(
    val authorName: String = "",
    val error: String? = null,
    val isRefreshing: Boolean = false
)

class AuthorPoemsViewModel(
    private val authorName: String,
    private val repository: PoemRepository
) : ViewModel() {
    
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<AuthorPoemsUiState> = combine(
        _isRefreshing,
        _error
    ) { isRefreshing, error ->
        AuthorPoemsUiState(
            authorName = authorName,
            isRefreshing = isRefreshing,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthorPoemsUiState(authorName = authorName)
    )
    
    // Paged poems flow with caching
    val pagedPoems: Flow<PagingData<Poem>> = repository
        .getAuthorPoemsPagedFlow(authorName)
        .cachedIn(viewModelScope)
    
    fun refresh() {
        _isRefreshing.value = true
        // Note: PagingData refresh is handled automatically by Paging 3
        // when the user performs pull-to-refresh action
        _isRefreshing.value = false
        _error.value = null
    }
    
    fun clearError() {
        _error.value = null
    }
}