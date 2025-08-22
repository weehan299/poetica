package com.example.poetica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.poetica.data.database.PoeticaDatabase
import com.example.poetica.data.repository.PoemRepository
import com.example.poetica.navigation.PoeticaNavigation
import com.example.poetica.ui.theme.PoeticaTheme
import com.example.poetica.ui.viewmodel.HomeViewModel
import com.example.poetica.ui.viewmodel.PoemReaderViewModel

class MainActivity : ComponentActivity() {
    
    private lateinit var repository: PoemRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize repository
        val database = PoeticaDatabase.getDatabase(this)
        repository = PoemRepository(database.poemDao(), this)
        
        setContent {
            PoeticaTheme {
                PoeticaApp(repository = repository)
            }
        }
    }
}

@Composable
fun PoeticaApp(
    repository: PoemRepository,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    
    val homeViewModel = remember { HomeViewModel(repository) }
    
    val poemReaderViewModelFactory: (String) -> PoemReaderViewModel = { poemId ->
        PoemReaderViewModel(repository, poemId)
    }
    
    PoeticaNavigation(
        navController = navController,
        homeViewModel = homeViewModel,
        poemReaderViewModelFactory = poemReaderViewModelFactory,
        modifier = modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun PoeticaAppPreview() {
    PoeticaTheme {
        // Preview would need a mock repository
    }
}