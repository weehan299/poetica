package com.example.poetica.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SearchResult
import com.example.poetica.data.repository.PoemRepository
// SearchEngine import removed - now using repository.searchPoems() for API integration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val poems: List<Poem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val repository: PoemRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "DiscoverViewModel"
    }
    
    // SearchEngine removed - now using repository.searchPoems() for API integration
    
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    
    private val poems = repository.getAllPoems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val searchResults = _searchQuery
        .debounce(300) // Wait 300ms after user stops typing
        .distinctUntilChanged()
        .onEach { query ->
            Log.d(TAG, "🔎 Search query changed: '$query'")
        }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                Log.d(TAG, "🔎 Empty query, returning empty results")
                flowOf(emptyList())
            } else {
                Log.d(TAG, "🔎 Starting search for: '$query'")
                repository.searchPoems(query.trim())
                    .onEach { results ->
                        Log.d(TAG, "🔎 Search results received: ${results.size} items for query '$query'")
                        if (results.isNotEmpty()) {
                            Log.d(TAG, "🔎 First few results: ${results.take(3).map { "'${it.poem.title}' by ${it.poem.author}" }}")
                        }
                    }
                    .catch { throwable ->
                        // Log error but don't break the UI
                        Log.w(TAG, "❌ Search failed for query: '$query'", throwable)
                        Log.w(TAG, "❌ Error type: ${throwable.javaClass.simpleName}, message: ${throwable.message}")
                        emit(emptyList()) 
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val uiState: StateFlow<DiscoverUiState> = combine(
        poems,
        _searchQuery,
        searchResults,
        _isLoading,
        _error
    ) { poems, searchQuery, searchResults, isLoading, error ->
        DiscoverUiState(
            poems = poems,
            searchQuery = searchQuery,
            searchResults = searchResults,
            isLoading = isLoading,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiscoverUiState(isLoading = true)
    )
    
    init {
        Log.d(TAG, "🏁 DiscoverViewModel initialized")
        initializeData()
    }
    
    fun updateSearchQuery(query: String) {
        Log.d(TAG, "🔎 updateSearchQuery() called: '$query' (previous: '${_searchQuery.value}')")
        _searchQuery.value = query
    }
    
    fun clearSearch() {
        Log.d(TAG, "🔎 clearSearch() called (previous query: '${_searchQuery.value}')")
        _searchQuery.value = ""
    }
    
    private fun initializeData() {
        Log.d(TAG, "🚀 initializeData() called")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "🚀 Loading bundled poems...")
                repository.initializeWithBundledPoems()
                Log.d(TAG, "✅ Bundled poems loaded successfully")
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Failed to load poems: ${e.message}"
                Log.e(TAG, "❌ Failed to initialize repository", e)
                Log.e(TAG, "❌ Error type: ${e.javaClass.simpleName}, message: ${e.message}")
                _isLoading.value = false
            }
        }
    }
}