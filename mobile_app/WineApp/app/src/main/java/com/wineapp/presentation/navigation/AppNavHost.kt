package com.wineapp.presentation.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wineapp.presentation.agegate.AgeGateBottomSheet
import com.wineapp.presentation.notfound.NotFoundScreen
import com.wineapp.presentation.scanresult.ScanResultScreen
import com.wineapp.presentation.scanner.ScannerScreen
import com.wineapp.presentation.search.SearchScreen
import com.wineapp.presentation.sommelier.SommelierScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? Activity
    var ageVerified by remember { mutableStateOf(false) }

    if (!ageVerified) {
        AgeGateBottomSheet(
            onConfirm = {
                ageVerified = true
            },
            onDeny = {
                activity?.finishAffinity()
                System.exit(0)
            }
        )
    }

    NavHost(navController, startDestination = "search") {
        composable("scanner") {
            ScannerScreen(
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") },
                onNavigateToScanResult = { confidence, mainWineId, altIds ->
                    navController.navigate("scan_result/$confidence/$mainWineId/$altIds")
                },
                onNavigateBack = { navController.popBackStack() }
            )
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
            DetailScreen(wineId = wineId, navController = navController)
        }
        composable(
            route = "scan_result/{confidence}/{mainWineId}/{altIds}",
            arguments = listOf(
                androidx.navigation.navArgument("confidence") { type = NavType.FloatType },
                androidx.navigation.navArgument("mainWineId") { type = NavType.StringType },
                androidx.navigation.navArgument("altIds") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val confidence = backStackEntry.arguments?.getFloat("confidence") ?: 0f
            val mainWineId = backStackEntry.arguments?.getString("mainWineId") ?: ""
            val altIds = backStackEntry.arguments?.getString("altIds") ?: ""
            val altIdsList = altIds.split("-").filter { it.isNotBlank() }
            ScanResultScreen(
                confidence = confidence,
                mainWineId = mainWineId,
                alternativeIds = altIdsList,
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("not_found") {
            NotFoundScreen(
                onRetry = { navController.popBackStack() },
                onSearch = { navController.navigate("search") }
            )
        }
        composable(
            route = "sommelier/{wineId}/{wineName}/{wineRegion}/{wineVariety}/{wineVintage}/{wineRating}/{wineStyle}",
            arguments = listOf(
                androidx.navigation.navArgument("wineId") { type = NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("wineName") { type = NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("wineRegion") { type = NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("wineVariety") { type = NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("wineVintage") { type = NavType.IntType; defaultValue = 0 },
                androidx.navigation.navArgument("wineRating") { type = NavType.FloatType; defaultValue = 0f },
                androidx.navigation.navArgument("wineStyle") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId")?.takeIf { it.isNotEmpty() }
            val wineName = backStackEntry.arguments?.getString("wineName")?.takeIf { it.isNotEmpty() }
            SommelierScreen(
                wineId = wineId,
                wineName = wineName,
                wineRegion = backStackEntry.arguments?.getString("wineRegion")?.takeIf { it.isNotEmpty() },
                wineVariety = backStackEntry.arguments?.getString("wineVariety")?.takeIf { it.isNotEmpty() },
                wineVintage = backStackEntry.arguments?.getInt("wineVintage")?.takeIf { it != 0 },
                wineRating = backStackEntry.arguments?.getFloat("wineRating")?.takeIf { it != 0f },
                wineStyle = backStackEntry.arguments?.getString("wineStyle")?.takeIf { it.isNotEmpty() },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("sommelier") {
            SommelierScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun DetailScreen(wineId: String, navController: NavHostController) {
    val viewModel = hiltViewModel<com.wineapp.presentation.detail.DetailViewModel>()
    com.wineapp.presentation.detail.DetailScreenContent(
        viewModel = viewModel,
        wineId = wineId,
        onNavigateToSommelier = { sWineId, sWineName, sRegion, sVariety, sVintage, sRating, sStyle ->
            val uriWineName = Uri.encode(sWineName)
            val uriRegion = Uri.encode(sRegion ?: "")
            val uriVariety = Uri.encode(sVariety ?: "")
            val uriStyle = Uri.encode(sStyle ?: "")
            navController.navigate(
                "sommelier/$sWineId/$uriWineName/$uriRegion/$uriVariety/${sVintage ?: 0}/${sRating ?: 0f}/$uriStyle"
            )
        }
    )
}
