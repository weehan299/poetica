package com.example.poetica.ui.viewmodel

import android.util.Log
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
    
    companion object {
        private const val TAG = "PoemReader"
    }
    
    private val _uiState = MutableStateFlow(PoemReaderUiState(isLoading = true))
    val uiState: StateFlow<PoemReaderUiState> = _uiState.asStateFlow()
    
    init {
        loadPoem()
    }
    
    private fun loadPoem() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔍 Loading poem with ID: '$poemId'")
                _uiState.value = PoemReaderUiState(isLoading = true)
                val poem = repository.getPoemById(poemId)
                if (poem != null) {
                    // Log detailed content information
                    val contentLength = poem.content.length
                    val lineCount = poem.content.count { it == '\n' } + 1
                    val paragraphCount = poem.content.split("\n\n").size
                    val preview = poem.content.take(100).replace("\n", "\\n")
                    
                    Log.d(TAG, "📖 ✅ Loaded poem: '${poem.title}' by ${poem.author}")
                    Log.d(TAG, "📊 Content stats - Length: $contentLength chars, Lines: $lineCount, Paragraphs: $paragraphCount")
                    Log.d(TAG, "📝 Content preview (first 100 chars): \"$preview${if (contentLength > 100) "..." else ""}\"")
                    
                    _uiState.value = PoemReaderUiState(poem = poem)
                } else {
                    Log.w(TAG, "❌ Poem with ID '$poemId' not found")
                    _uiState.value = PoemReaderUiState(error = "Poem not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load poem with ID '$poemId': ${e.message}", e)
                _uiState.value = PoemReaderUiState(error = "Failed to load poem: ${e.message}")
            }
        }
    }
}