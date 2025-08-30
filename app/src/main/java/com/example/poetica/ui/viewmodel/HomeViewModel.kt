package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.Author
import com.example.poetica.data.repository.PoemRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val poemOfTheDay: Poem? = null,
    val authors: List<Author> = emptyList(),
    val isLoading: Boolean = false,
    val authorsLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val repository: PoemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    private val _authorsLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _poemOfTheDay = MutableStateFlow<Poem?>(null)
    private val _authors = MutableStateFlow<List<Author>>(emptyList())
    
    val uiState: StateFlow<HomeUiState> = combine(
        _poemOfTheDay,
        _authors,
        _isLoading,
        _authorsLoading,
        _error
    ) { poemOfTheDay, authors, isLoading, authorsLoading, error ->
        HomeUiState(
            poemOfTheDay = poemOfTheDay,
            authors = authors,
            isLoading = isLoading,
            authorsLoading = authorsLoading,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )
    
    init {
        initializeData()
    }
    
    fun refreshPoemOfTheDay() {
        loadPoemOfTheDay()
    }
    
    private fun initializeData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.initializeWithBundledPoems()
                loadPoemOfTheDay()
                loadTopAuthors()
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Failed to load poems: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    private fun loadPoemOfTheDay() {
        viewModelScope.launch {
            try {
                val poem = repository.getPoemOfTheDay()
                _poemOfTheDay.value = poem
            } catch (e: Exception) {
                _error.value = "Failed to load poem of the day: ${e.message}"
            }
        }
    }
    
    private fun loadTopAuthors() {
        viewModelScope.launch {
            try {
                _authorsLoading.value = true
                val authors = repository.getTopAuthors(limit = 20)
                _authors.value = authors
                _authorsLoading.value = false
            } catch (e: Exception) {
                _authorsLoading.value = false
            }
        }
    }
}