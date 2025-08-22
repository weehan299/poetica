package com.example.poetica.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.poetica.data.repository.PoemRepository

class HomeViewModelFactory(
    private val repository: PoemRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PoemReaderViewModelFactory(
    private val repository: PoemRepository,
    private val poemId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PoemReaderViewModel::class.java)) {
            return PoemReaderViewModel(repository, poemId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}