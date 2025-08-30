package com.example.poetica.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Author
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SearchResult
import com.example.poetica.data.model.SearchResponse
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
    val searchAuthors: List<Author> = emptyList(),
    val searchPoems: List<SearchResult> = emptyList(),
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
                flowOf(SearchResponse())
            } else {
                Log.d(TAG, "🔎 Starting search for: '$query'")
                repository.searchPoems(query.trim())
                    .onEach { searchResponse ->
                        Log.d(TAG, "🔎 Search results received: ${searchResponse.authors.size} authors, ${searchResponse.poems.size} poems for query '$query'")
                        if (searchResponse.authors.isNotEmpty()) {
                            Log.d(TAG, "🔎 First few authors: ${searchResponse.authors.take(3).map { "'${it.name}' (${it.poemCount} poems)" }}")
                        }
                        if (searchResponse.poems.isNotEmpty()) {
                            Log.d(TAG, "🔎 First few poems: ${searchResponse.poems.take(3).map { "'${it.poem.title}' by ${it.poem.author}" }}")
                        }
                    }
                    .catch { throwable ->
                        // Log error but don't break the UI
                        Log.w(TAG, "❌ Search failed for query: '$query'", throwable)
                        Log.w(TAG, "❌ Error type: ${throwable.javaClass.simpleName}, message: ${throwable.message}")
                        emit(SearchResponse()) 
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchResponse()
        )
    
    val uiState: StateFlow<DiscoverUiState> = combine(
        poems,
        _searchQuery,
        searchResults,
        _isLoading,
        _error
    ) { values ->
        val poems = values[0] as List<Poem>
        val searchQuery = values[1] as String
        val searchResponse = values[2] as SearchResponse
        val isLoading = values[3] as Boolean
        val error = values[4] as String?
        
        DiscoverUiState(
            poems = poems,
            searchQuery = searchQuery,
            searchResults = searchResponse.poems, // Keep for backward compatibility if needed
            searchAuthors = searchResponse.authors,
            searchPoems = searchResponse.poems,
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