package com.example.poetica.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.poetica.ui.screens.HomeScreen
import com.example.poetica.ui.screens.PoemReaderScreen
import com.example.poetica.ui.viewmodel.HomeViewModel
import com.example.poetica.ui.viewmodel.PoemReaderViewModel

object PoeticaDestinations {
    const val HOME_ROUTE = "home"
    const val POEM_READER_ROUTE = "poem_reader"
    const val POEM_ID_KEY = "poem_id"
}

@Composable
fun PoeticaNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    homeViewModel: HomeViewModel,
    poemReaderViewModelFactory: (String) -> PoemReaderViewModel
) {
    NavHost(
        navController = navController,
        startDestination = PoeticaDestinations.HOME_ROUTE,
        modifier = modifier
    ) {
        composable(PoeticaDestinations.HOME_ROUTE) {
            HomeScreen(
                viewModel = homeViewModel,
                onPoemClick = { poemId ->
                    navController.navigate("${PoeticaDestinations.POEM_READER_ROUTE}/$poemId")
                }
            )
        }
        
        composable("${PoeticaDestinations.POEM_READER_ROUTE}/{${PoeticaDestinations.POEM_ID_KEY}}") { backStackEntry ->
            val poemId = backStackEntry.arguments?.getString(PoeticaDestinations.POEM_ID_KEY) ?: ""
            val viewModel = poemReaderViewModelFactory(poemId)
            
            PoemReaderScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}