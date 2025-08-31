package com.example.poetica.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SearchResult
import com.example.poetica.data.model.SearchResultItem
import com.example.poetica.data.repository.PoemRepository
// SearchEngine import removed - now using repository.searchPoems() for API integration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val poems: List<Poem> = emptyList(),
    val searchQuery: String = "",
    val mixedSearchResults: List<SearchResultItem> = emptyList(),
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
    
    
    private val mixedSearchResults = _searchQuery
        .debounce(300) // Wait 300ms after user stops typing
        .distinctUntilChanged()
        .onEach { query ->
            Log.d(TAG, "🔎 Mixed search query changed: '$query'")
        }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                Log.d(TAG, "🔎 Empty query, returning empty mixed results")
                flowOf(emptyList())
            } else {
                Log.d(TAG, "🔎 Starting remote-first mixed search for: '$query'")
                repository.searchMixedResults(query.trim())
                    .onEach { results ->
                        val authorCount = results.count { it is SearchResultItem.AuthorResult }
                        val poemCount = results.count { it is SearchResultItem.PoemResult }
                        Log.d(TAG, "🔎 ✅ Mixed search completed: ${results.size} total items ($authorCount authors, $poemCount poems) for query '$query'")
                        
                        if (results.isNotEmpty()) {
                            val summary = results.take(3).map { item ->
                                when (item) {
                                    is SearchResultItem.AuthorResult -> "📝 Author: ${item.authorSearchResult.author.name} (${item.authorSearchResult.author.poemCount} poems)"
                                    is SearchResultItem.PoemResult -> "📖 Poem: '${item.searchResult.poem.title}' by ${item.searchResult.poem.author}"
                                }
                            }
                            Log.d(TAG, "🔎 Sample results: $summary")
                        } else {
                            Log.w(TAG, "🔎 ⚠️ No results found for query: '$query'")
                        }
                    }
                    .catch { throwable ->
                        // Enhanced error handling with more details
                        Log.e(TAG, "❌ Mixed search failed for query: '$query'", throwable)
                        when (throwable) {
                            is java.net.SocketTimeoutException -> {
                                Log.w(TAG, "❌ Network timeout occurred during search")
                            }
                            is java.net.UnknownHostException -> {
                                Log.w(TAG, "❌ Network connection issue (DNS/connectivity)")
                            }
                            is kotlinx.coroutines.TimeoutCancellationException -> {
                                Log.w(TAG, "❌ Search operation timed out after 60 seconds")
                            }
                            else -> {
                                Log.w(TAG, "❌ Unexpected error: ${throwable.javaClass.simpleName} - ${throwable.message}")
                            }
                        }
                        // Always return empty list to prevent UI crashes
                        emit(emptyList<SearchResultItem>()) 
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
        mixedSearchResults,
        _isLoading,
        _error
    ) { poems, searchQuery, mixedSearchResults, isLoading, error ->
        DiscoverUiState(
            poems = poems,
            searchQuery = searchQuery,
            mixedSearchResults = mixedSearchResults,
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