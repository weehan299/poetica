package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SearchResult
import com.example.poetica.data.repository.PoemRepository
import com.example.poetica.ui.search.SearchEngine
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
    
    private val searchEngine = SearchEngine()
    
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
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                flow {
                    emit(searchEngine.search(query.trim()))
                }.catch { emit(emptyList()) }
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
        initializeData()
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    private fun initializeData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.initializeWithBundledPoems()
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Failed to load poems: ${e.message}"
                _isLoading.value = false
            }
        }
        
        // Update search index when poems change - separate coroutine
        viewModelScope.launch {
            poems.collect { poemList ->
                if (poemList.isNotEmpty()) {
                    searchEngine.buildIndex(poemList)
                }
            }
        }
    }
}