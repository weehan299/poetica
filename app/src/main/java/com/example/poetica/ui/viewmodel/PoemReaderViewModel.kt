package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poetica.data.model.Poem
import com.example.poetica.data.repository.PoemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PoemReaderUiState(
    val poem: Poem? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class PoemReaderViewModel(
    private val repository: PoemRepository,
    private val poemId: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PoemReaderUiState(isLoading = true))
    val uiState: StateFlow<PoemReaderUiState> = _uiState.asStateFlow()
    
    init {
        loadPoem()
    }
    
    private fun loadPoem() {
        viewModelScope.launch {
            try {
                _uiState.value = PoemReaderUiState(isLoading = true)
                val poem = repository.getPoemById(poemId)
                if (poem != null) {
                    _uiState.value = PoemReaderUiState(poem = poem)
                } else {
                    _uiState.value = PoemReaderUiState(error = "Poem not found")
                }
            } catch (e: Exception) {
                _uiState.value = PoemReaderUiState(error = "Failed to load poem: ${e.message}")
            }
        }
    }
}