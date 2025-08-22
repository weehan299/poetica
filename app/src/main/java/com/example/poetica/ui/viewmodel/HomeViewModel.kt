package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.repository.PoemRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val poemOfTheDay: Poem? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val repository: PoemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _poemOfTheDay = MutableStateFlow<Poem?>(null)
    
    val uiState: StateFlow<HomeUiState> = combine(
        _poemOfTheDay,
        _isLoading,
        _error
    ) { poemOfTheDay, isLoading, error ->
        HomeUiState(
            poemOfTheDay = poemOfTheDay,
            isLoading = isLoading,
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
}