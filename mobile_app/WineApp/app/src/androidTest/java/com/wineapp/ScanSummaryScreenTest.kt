package com.wineapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.wineapp.domain.model.*
import com.wineapp.presentation.scanner.ScanSummaryScreen
import com.wineapp.presentation.scanner.ScannerState
import com.wineapp.ui.theme.WineAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScanSummaryScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun wine(id: String, name: String) = Wine(id, name, null, null, null, null, null,
        "Кубань", "Россия", "Рислинг", "Белое сухое", 12f, null, null, winery = "Винодельня")
    private val first = wine("a", "Рислинг — первый кандидат")
    private val second = wine("b", "Рислинг — второй кандидат")
    private fun base() = ScanResult(first, 0f, scanId = "scan", recognitionStatus = "candidates_unverified",
        scoredCandidates = listOf(ScoredWine(first, "a", 1, .87f), ScoredWine(second, "b", 2, .35f)),
        recommendations = listOf(RecommendedWine(wine("c", "Вино для рекомендации"), listOf("Совпадают цвет и сахар"))),
        recommendationStatus = "available", recommendationCriteria = mapOf("color" to "Белое", "sweetness" to "Сухое"))

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun unverifiedShowsScoreAndConfirmButton() {
        var selected: String? = null
        compose.setContent { WineAppTheme {
            ScanSummaryScreen(ScannerState.Success(base(), ""), { selected = it }, { _, _ -> }, {}, {})
        } }
        compose.onNodeWithText("Похожие варианты из каталога").assertIsDisplayed()
        compose.onNodeWithText("Совпадение 87/100").assertIsDisplayed()
        compose.onAllNodesWithText("Это моё вино")[0].performClick()
        compose.runOnIdle { assertEquals("a", selected) }
        screenshot("scan_unverified")
    }

    @Test fun confirmationShowsChosenWineEvenWhenItWasSecond() {
        compose.setContent { WineAppTheme {
            ScanSummaryScreen(ScannerState.Success(base().copy(userConfirmedSlug = "b"), ""), {}, { _, _ -> }, {}, {})
        } }
        compose.onNodeWithText("Ваше вино").assertIsDisplayed()
        compose.onNodeWithText(second.name).assertIsDisplayed()
        compose.onNodeWithText(first.name).assertDoesNotExist()
        compose.onNodeWithText("Рекомендуем также").assertIsDisplayed()
        screenshot("scan_confirmed")
    }

    @Test fun absentWineShowsRecommendationsWithoutSearchCandidates() {
        val result = base().copy(wine = null, recognitionStatus = "not_in_catalog", scoredCandidates = emptyList())
        compose.setContent { WineAppTheme {
            ScanSummaryScreen(ScannerState.Success(result, ""), {}, { _, _ -> }, {}, {})
        } }
        compose.onNodeWithText("Вино не найдено в каталоге").assertIsDisplayed()
        compose.onNodeWithText("Вино для рекомендации").assertIsDisplayed()
        compose.onNodeWithText("Это моё вино").assertDoesNotExist()
        compose.onNodeWithText(first.name).assertDoesNotExist()
        screenshot("scan_absent")
    }
}
