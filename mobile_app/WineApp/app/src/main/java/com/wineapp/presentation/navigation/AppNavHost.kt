package com.wineapp.presentation.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wineapp.data.local.AgeGatePrefs
import com.wineapp.presentation.agegate.AgeGateScreen
import com.wineapp.presentation.cellar.CellarScreen
import com.wineapp.presentation.common.ui.BottomTab
import com.wineapp.presentation.common.ui.WineBottomBar
import com.wineapp.presentation.detail.DetailScreen
import com.wineapp.presentation.favorites.FavoritesScreen
import com.wineapp.presentation.mywines.MyWinesScreen
import com.wineapp.presentation.roulette.WineRouletteScreen
import com.wineapp.presentation.savedscans.SavedScanDetailScreen
import com.wineapp.presentation.savedscans.SavedScansScreen
import com.wineapp.presentation.scanner.ScannerScreen
import com.wineapp.presentation.scanresult.ScanResultScreen
import com.wineapp.presentation.search.SearchScreen
import com.wineapp.presentation.search.SearchTabScreen
import com.wineapp.presentation.sommelier.SommelierScreen
import com.wineapp.presentation.winepath.WinePathScreen

/**
 * Маршруты с нижней навигацией. Сканер и винная карта — полноэкранные, без бара:
 * на них выход через стрелку назад на самом экране.
 */
private val TAB_ROUTES = setOf("search", "search_tab", "my_wines")

@Composable
fun AppNavHost(startRoute: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? Activity
    // Плашка возраста — только при первом открытии приложения.
    var ageVerified by remember { mutableStateOf(AgeGatePrefs.isVerified(context)) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentBase = backStackEntry?.destination?.route
        ?.substringBefore("?")
        ?.substringBefore("/")
    val currentTab = BottomTab.entries
        .firstOrNull { it.route == currentBase && it.route in TAB_ROUTES }

    // Бар висит поверх контента, а не отжимает его (как плавающая пилюля раньше).
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController, startDestination = startRoute ?: "search") {
        composable(
            route = "scanner?pickGallery={pickGallery}",
            arguments = listOf(
                navArgument("pickGallery") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            ScannerScreen(
                onNavigateToDetail = { wineId, photoPath, recognitionStatus ->
                    val encoded = photoPath?.let { Uri.encode(it) } ?: ""
                    val status = recognitionStatus?.let { Uri.encode(it) } ?: ""
                    navController.navigate("detail/$wineId?photoPath=$encoded&recognitionStatus=$status")
                },
                onNavigateToScanResult = { confidence, mainWineId, altIds, photoPath, recognitionStatus ->
                    val encoded = photoPath?.let { Uri.encode(it) } ?: ""
                    val status = recognitionStatus?.let { Uri.encode(it) } ?: ""
                    navController.navigate("scan_result/$confidence/$mainWineId/$altIds?photoPath=$encoded&recognitionStatus=$status")
                },
                onNavigateBack = { navController.popBackStack() },
                startGalleryPicker = backStackEntry.arguments?.getBoolean("pickGallery") ?: false
            )
        }
        composable("search") {
            SearchScreen(
                onNavigateToScanner = { navController.navigate("scanner") },
                onNavigateToGallery = { navController.navigate("scanner?pickGallery=true") },
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") }
            )
        }
        composable("search_tab") {
            SearchTabScreen(
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") }
            )
        }
        composable("my_wines") {
            MyWinesScreen(
                onNavigateToCellar = { navController.navigate("cellar") },
                onNavigateToFavorites = { navController.navigate("favorites") },
                onNavigateToSavedScans = { navController.navigate("saved_scans") },
                onNavigateToRoulette = { navController.navigate("roulette") },
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") }
            )
        }
        composable(
            route = "detail/{wineId}?photoPath={photoPath}&confidence={confidence}&recognitionStatus={recognitionStatus}",
            arguments = listOf(
                navArgument("wineId") { type = NavType.StringType },
                navArgument("photoPath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("confidence") {
                    type = NavType.FloatType
                    defaultValue = 1.0f
                },
                navArgument("recognitionStatus") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId") ?: ""
            val photoPath = backStackEntry.arguments?.getString("photoPath")
                ?.takeIf { it.isNotEmpty() }
                ?.let { Uri.decode(it) }
            val confidence = backStackEntry.arguments?.getFloat("confidence") ?: 1.0f
            val recognitionStatus = backStackEntry.arguments?.getString("recognitionStatus")
                ?.takeIf { it.isNotEmpty() }
                ?.let { Uri.decode(it) }
            DetailScreen(
                wineId = wineId,
                photoPath = photoPath,
                confidence = confidence,
                recognitionStatus = recognitionStatus,
                navController = navController
            )
        }
        composable(
            route = "scan_result/{confidence}/{mainWineId}/{altIds}?photoPath={photoPath}&recognitionStatus={recognitionStatus}",
            arguments = listOf(
                navArgument("confidence") { type = NavType.FloatType },
                navArgument("mainWineId") { type = NavType.StringType },
                navArgument("altIds") { type = NavType.StringType },
                navArgument("photoPath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("recognitionStatus") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val confidence = backStackEntry.arguments?.getFloat("confidence") ?: 0f
            val mainWineId = backStackEntry.arguments?.getString("mainWineId") ?: ""
            val altIds = backStackEntry.arguments?.getString("altIds") ?: ""
            val photoPath = backStackEntry.arguments?.getString("photoPath")
                ?.takeIf { it.isNotEmpty() }
                ?.let { Uri.decode(it) }
            val recognitionStatus = backStackEntry.arguments?.getString("recognitionStatus")
                ?.takeIf { it.isNotEmpty() }
                ?.let { Uri.decode(it) }
            ScanResultScreen(
                confidence = confidence,
                mainWineId = mainWineId,
                altIds = altIds,
                photoPath = photoPath,
                recognitionStatus = recognitionStatus,
                onNavigateToDetail = { wineId ->
                    val encoded = photoPath?.let { Uri.encode(it) } ?: ""
                    val status = recognitionStatus?.let { Uri.encode(it) } ?: ""
                    navController.navigate("detail/$wineId?photoPath=$encoded&confidence=$confidence&recognitionStatus=$status")
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "sommelier/{wineId}/{wineName}/{wineRegion}/{wineVariety}/{wineVintage}/{wineRating}/{wineStyle}?photoPath={photoPath}&confidence={confidence}",
            arguments = listOf(
                navArgument("wineId") { type = NavType.StringType; defaultValue = "" },
                navArgument("wineName") { type = NavType.StringType; defaultValue = "" },
                navArgument("wineRegion") { type = NavType.StringType; defaultValue = "" },
                navArgument("wineVariety") { type = NavType.StringType; defaultValue = "" },
                navArgument("wineVintage") { type = NavType.IntType; defaultValue = 0 },
                navArgument("wineRating") { type = NavType.FloatType; defaultValue = 0f },
                navArgument("wineStyle") { type = NavType.StringType; defaultValue = "" },
                navArgument("photoPath") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("confidence") { type = NavType.FloatType; defaultValue = 1.0f }
            )
        ) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId")?.takeIf { it.isNotEmpty() }
            val wineName = backStackEntry.arguments?.getString("wineName")?.takeIf { it.isNotEmpty() }
            val photoPath = backStackEntry.arguments?.getString("photoPath")
                ?.takeIf { it.isNotEmpty() }
                ?.let { Uri.decode(it) }
            val confidence = backStackEntry.arguments?.getFloat("confidence") ?: 1.0f
            SommelierScreen(
                wineId = wineId,
                wineName = wineName,
                wineRegion = backStackEntry.arguments?.getString("wineRegion")?.takeIf { it.isNotEmpty() },
                wineVariety = backStackEntry.arguments?.getString("wineVariety")?.takeIf { it.isNotEmpty() },
                wineVintage = backStackEntry.arguments?.getInt("wineVintage")?.takeIf { it != 0 },
                wineRating = backStackEntry.arguments?.getFloat("wineRating")?.takeIf { it != 0f },
                wineStyle = backStackEntry.arguments?.getString("wineStyle")?.takeIf { it.isNotEmpty() },
                photoPath = photoPath,
                confidence = confidence,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") }
            )
        }
        composable(
            route = "sommelier?question={question}",
            arguments = listOf(
                navArgument("question") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            SommelierScreen(
                initialQuestion = backStackEntry.arguments?.getString("question"),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") }
            )
        }
        composable("saved_scans") {
            SavedScansScreen(
                onNavigateToDetail = { scanId -> navController.navigate("saved_scan_detail/$scanId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("cellar") {
            CellarScreen(
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") },
                onAskSommelier = { question ->
                    navController.navigate(
                        if (question.isBlank()) "sommelier"
                        else "sommelier?question=${Uri.encode(question.trim())}"
                    )
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("roulette") {
            WineRouletteScreen(
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("wine_path") {
            WinePathScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToScanner = { navController.navigate("scanner") }
            )
        }
        composable(
            route = "saved_scan_detail/{scanId}",
            arguments = listOf(
                navArgument("scanId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            SavedScanDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { wineId -> navController.navigate("detail/$wineId") }
            )
        }
        }
        if (currentTab != null) {
            WineBottomBar(
                selected = currentTab,
                onSelect = { tab -> navController.navigateTab(tab.route) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Гейт — строго ПОСЛЕ NavHost: обычный Box-оверлей рисуется поверх только так
    // (раньше порядок не имел значения, т.к. ModalBottomSheet жил в отдельном окне).
    if (!ageVerified) {
        AgeGateScreen(
            onConfirm = {
                AgeGatePrefs.setVerified(context)
                ageVerified = true
            },
            onDeny = {
                activity?.finishAffinity()
                System.exit(0)
            }
        )
    }
}

/** Переключение табов: один экземпляр, состояние сохраняется. */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
