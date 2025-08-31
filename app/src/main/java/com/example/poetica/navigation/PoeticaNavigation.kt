package com.example.poetica.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.poetica.ui.screens.HomeScreen
import com.example.poetica.ui.screens.DiscoverScreen
import com.example.poetica.ui.screens.PoemReaderScreen
import com.example.poetica.ui.screens.AuthorsScreen
import com.example.poetica.ui.screens.AuthorPoemsScreen
import com.example.poetica.ui.viewmodel.HomeViewModel
import com.example.poetica.ui.viewmodel.DiscoverViewModel
import com.example.poetica.ui.viewmodel.PoemReaderViewModel
import com.example.poetica.ui.viewmodel.AuthorsViewModel
import com.example.poetica.ui.viewmodel.AuthorPoemsViewModel

sealed class PoeticaDestinations(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : PoeticaDestinations("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Discover : PoeticaDestinations("discover", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    object PoemReader : PoeticaDestinations("poem_reader", "Reader", Icons.Filled.Home, Icons.Outlined.Home)
    object Authors : PoeticaDestinations("authors", "Authors", Icons.Filled.Home, Icons.Outlined.Home)
    object AuthorPoems : PoeticaDestinations("author_poems", "Author Poems", Icons.Filled.Home, Icons.Outlined.Home)
    
    companion object {
        const val POEM_ID_KEY = "poem_id"
        const val AUTHOR_NAME_KEY = "author_name"
        val bottomNavItems = listOf(Home, Discover)
    }
}

@Composable
fun PoeticaNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    homeViewModel: HomeViewModel,
    discoverViewModel: DiscoverViewModel,
    authorsViewModel: AuthorsViewModel,
    poemReaderViewModelFactory: (String) -> PoemReaderViewModel,
    authorPoemsViewModelFactory: (String) -> AuthorPoemsViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val shouldHideBottomBar = currentDestination?.route?.let { route ->
        route.contains("poem_reader") || route.contains("authors") || route.contains("author_poems")
    } == true

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (!shouldHideBottomBar) {
                NavigationBar {
                    PoeticaDestinations.bottomNavItems.forEach { destination ->
                        val isSelected = currentDestination?.hierarchy?.any { 
                            it.route == destination.route 
                        } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) },
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = PoeticaDestinations.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(PoeticaDestinations.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onPoemClick = { poemId ->
                        navController.navigate("${PoeticaDestinations.PoemReader.route}/$poemId")
                    },
                    onAuthorClick = { authorName ->
                        navController.navigate("${PoeticaDestinations.AuthorPoems.route}/$authorName")
                    },
                    onSeeAllAuthorsClick = {
                        navController.navigate(PoeticaDestinations.Authors.route)
                    }
                )
            }
            
            composable(PoeticaDestinations.Discover.route) {
                DiscoverScreen(
                    viewModel = discoverViewModel,
                    onPoemClick = { poemId ->
                        navController.navigate("${PoeticaDestinations.PoemReader.route}/$poemId")
                    },
                    onAuthorClick = { authorName ->
                        navController.navigate("${PoeticaDestinations.AuthorPoems.route}/$authorName")
                    }
                )
            }
            
            composable("${PoeticaDestinations.PoemReader.route}/{${PoeticaDestinations.POEM_ID_KEY}}") { backStackEntry ->
                val poemId = backStackEntry.arguments?.getString(PoeticaDestinations.POEM_ID_KEY) ?: ""
                val viewModel = poemReaderViewModelFactory(poemId)
                
                PoemReaderScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(PoeticaDestinations.Authors.route) {
                AuthorsScreen(
                    viewModel = authorsViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAuthorClick = { authorName ->
                        navController.navigate("${PoeticaDestinations.AuthorPoems.route}/$authorName")
                    }
                )
            }
            
            composable("${PoeticaDestinations.AuthorPoems.route}/{${PoeticaDestinations.AUTHOR_NAME_KEY}}") { backStackEntry ->
                val authorName = backStackEntry.arguments?.getString(PoeticaDestinations.AUTHOR_NAME_KEY) ?: ""
                val viewModel = authorPoemsViewModelFactory(authorName)
                
                AuthorPoemsScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onPoemClick = { poemId ->
                        navController.navigate("${PoeticaDestinations.PoemReader.route}/$poemId")
                    }
                )
            }
        }
    }
}