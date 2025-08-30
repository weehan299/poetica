package com.example.poetica

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.poetica.data.api.ApiConfig
import com.example.poetica.data.config.PoeticaConfig
import com.example.poetica.data.database.PoeticaDatabase
import com.example.poetica.data.repository.PoemRepository
import com.example.poetica.navigation.PoeticaNavigation
import com.example.poetica.ui.theme.PoeticaTheme
import com.example.poetica.ui.viewmodel.HomeViewModel
import com.example.poetica.ui.viewmodel.DiscoverViewModel
import com.example.poetica.ui.viewmodel.PoemReaderViewModel

class MainActivity : ComponentActivity() {
    
    private lateinit var repository: PoemRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize repository with API integration
        val database = PoeticaDatabase.getDatabase(this)
        val config = PoeticaConfig.getInstance(this)

        // Configure API URL - now using actual machine IP for reliable connectivity
        Log.d("MainActivity", "🔧 Configuring API URL for current environment...")

        config.useProductionApi()  // Force production API change this if you want to use local host api
        config.logCurrentConfig()
        
        val apiService = ApiConfig.createApiService(this)
        
        repository = PoemRepository(
            poemDao = database.poemDao(),
            context = this,
            apiService = apiService,
            config = config
        )
        
        // Initialize data and check API health
        lifecycleScope.launch {
            repository.initializeWithBundledPoems()
            
            if (config.useRemoteData) {
                Log.d("MainActivity", "🏥 Performing API health check...")
                val isHealthy = repository.checkApiHealth()
                if (!isHealthy) {
                    Log.w("MainActivity", "⚠️ API health check failed - API will retry on each search attempt")
                    Log.w("MainActivity", "💡 Make sure your API server is running: uvicorn main:app --reload --host 0.0.0.0 --port 8000")
                    Log.w("MainActivity", "💡 API URL configured as: ${config.apiBaseUrl}")
                    // Don't permanently disable API - let individual search attempts retry
                    // config.isApiEnabled = false  // <-- REMOVED: This was preventing retries
                } else {
                    Log.d("MainActivity", "✅ API is healthy, remote data enabled")
                }
            } else {
                Log.d("MainActivity", "📱 Using local data only (remote data disabled in config)")
            }
        }
        
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
    val discoverViewModel = remember { DiscoverViewModel(repository) }
    
    val poemReaderViewModelFactory: (String) -> PoemReaderViewModel = { poemId ->
        PoemReaderViewModel(repository, poemId)
    }
    
    PoeticaNavigation(
        navController = navController,
        homeViewModel = homeViewModel,
        discoverViewModel = discoverViewModel,
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