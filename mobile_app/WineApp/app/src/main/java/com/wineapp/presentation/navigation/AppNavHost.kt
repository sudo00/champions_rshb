package com.wineapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wineapp.presentation.agegate.AgeGateScreen
import com.wineapp.presentation.notfound.NotFoundScreen
import com.wineapp.presentation.scanner.ScannerScreen
import com.wineapp.presentation.search.SearchScreen
import com.wineapp.presentation.sommelier.SommelierScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startDestination = if (isAgeVerified()) "search" else "age_gate"

    NavHost(navController, startDestination) {
        composable("age_gate") {
            AgeGateScreen(
                onConfirm = {
                    navController.navigate("search") {
                        popUpTo("age_gate") { inclusive = true }
                    }
                }
            )
        }
        composable("scanner") {
            ScannerScreen()
        }
        composable("search") {
            SearchScreen(
                onNavigateToScanner = { navController.navigate("scanner") },
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") },
                onNavigateToSommelier = { navController.navigate("sommelier") }
            )
        }
        composable(
            route = "detail/{wineId}",
            arguments = listOf(androidx.navigation.navArgument("wineId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId") ?: ""
            DetailScreen(wineId = wineId)
        }
        composable("not_found") {
            NotFoundScreen(
                onRetry = { navController.popBackStack() },
                onSearch = { navController.navigate("search") }
            )
        }
        composable("sommelier") {
            SommelierScreen()
        }
    }
}

private fun isAgeVerified(): Boolean {
    return true
}

@Composable
fun DetailScreen(wineId: String) {
    val viewModel = hiltViewModel<com.wineapp.presentation.detail.DetailViewModel>()
    com.wineapp.presentation.detail.DetailScreenContent(viewModel = viewModel, wineId = wineId)
}
