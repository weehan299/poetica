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
    val error: String? = null,
    val isRandomMode: Boolean = false
)

class HomeViewModel(
    private val repository: PoemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    private val _authorsLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _poemOfTheDay = MutableStateFlow<Poem?>(null)
    private val _authors = MutableStateFlow<List<Author>>(emptyList())
    private val _isRandomMode = MutableStateFlow(false)
    
    val uiState: StateFlow<HomeUiState> = combine(
        _poemOfTheDay,
        _authors,
        _isLoading,
        _authorsLoading,
        _error,
        _isRandomMode
    ) { flows ->
        HomeUiState(
            poemOfTheDay = flows[0] as Poem?,
            authors = flows[1] as List<Author>,
            isLoading = flows[2] as Boolean,
            authorsLoading = flows[3] as Boolean,
            error = flows[4] as String?,
            isRandomMode = flows[5] as Boolean
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
    
    fun showRandomPoem() {
        viewModelScope.launch {
            try {
                val randomPoem = repository.getRandomLocalPoem()
                if (randomPoem != null) {
                    _poemOfTheDay.value = randomPoem
                    _isRandomMode.value = true
                } else {
                    _error.value = "No random poems available"
                }
            } catch (e: Exception) {
                _error.value = "Failed to load random poem: ${e.message}"
            }
        }
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
                _isRandomMode.value = false // Reset to poem of the day mode
            } catch (e: Exception) {
                _error.value = "Failed to load poem of the day: ${e.message}"
            }
        }
    }
    
    private fun loadTopAuthors() {
        viewModelScope.launch {
            try {
                _authorsLoading.value = true
                val authors = repository.getRandomAuthors(limit = 20)
                _authors.value = authors
                _authorsLoading.value = false
            } catch (e: Exception) {
                _authorsLoading.value = false
            }
        }
    }
}