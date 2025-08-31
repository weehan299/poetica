package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Author
import com.example.poetica.data.repository.PoemRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthorsUiState(
    val authors: List<Author> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true
)

class AuthorsViewModel(
    private val repository: PoemRepository
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _authors = MutableStateFlow<List<Author>>(emptyList())
    private val _hasMore = MutableStateFlow(true)
    
    private var currentOffset = 0
    private val pageSize = 50
    
    val uiState: StateFlow<AuthorsUiState> = combine(
        _authors,
        _searchQuery,
        _isLoading,
        _error,
        _hasMore
    ) { authors, searchQuery, isLoading, error, hasMore ->
        AuthorsUiState(
            authors = authors,
            searchQuery = searchQuery,
            isLoading = isLoading,
            error = error,
            hasMore = hasMore
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthorsUiState(isLoading = true)
    )
    
    init {
        loadAuthors()
        
        // Set up search functionality
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _searchQuery
                .debounce(300) // Debounce search queries
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        resetAndLoadAuthors()
                    } else {
                        searchAuthors(query)
                    }
                }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun loadMore() {
        if (!_isLoading.value && _hasMore.value && _searchQuery.value.isBlank()) {
            loadAuthors(append = true)
        }
    }
    
    fun refresh() {
        if (_searchQuery.value.isBlank()) {
            resetAndLoadAuthors()
        } else {
            searchAuthors(_searchQuery.value)
        }
    }
    
    private fun resetAndLoadAuthors() {
        currentOffset = 0
        _authors.value = emptyList()
        _hasMore.value = true
        loadAuthors()
    }
    
    private fun loadAuthors(append: Boolean = false) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val offset = if (append) currentOffset else 0
                val authors = repository.getAuthorsPage(pageSize, offset)
                
                if (append) {
                    _authors.value = _authors.value + authors
                } else {
                    _authors.value = authors
                    currentOffset = 0
                }
                
                currentOffset += authors.size
                _hasMore.value = authors.size == pageSize
                _isLoading.value = false
                
            } catch (e: Exception) {
                _error.value = "Failed to load authors: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    private fun searchAuthors(query: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val authors = repository.searchAuthors(query, limit = 100)
                _authors.value = authors
                _hasMore.value = false // No pagination for search results
                _isLoading.value = false
                
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
                _isLoading.value = false
            }
        }
    }
}