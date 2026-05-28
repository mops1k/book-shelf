package com.bookshelf.app

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bookshelf.app.data.model.Book
import com.bookshelf.app.ui.screens.home.HomeScreen
import com.bookshelf.app.ui.screens.opds.OpdsScreen
import com.bookshelf.app.ui.screens.settings.SettingsScreen
import com.bookshelf.app.ui.screens.details.BookDetailsScreen

@Composable
fun BookShelfApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onBookClick = { book ->
                    navController.navigate("details/${book.id}")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToOpds = {
                    navController.navigate("opds")
                }
            )
        }
        
        composable("opds") {
            OpdsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = "details/{bookId}",
            arguments = listOf()
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
            
            BookDetailsScreen(
                bookId = bookId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenWithExternalReader = { book ->
                    // Open with external reader
                }
            )
        }
    }
}
