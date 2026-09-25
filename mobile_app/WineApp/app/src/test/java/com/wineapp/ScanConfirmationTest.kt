package com.wineapp

import com.wineapp.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ScanConfirmationTest {
    private val wine = Wine("a", "Первый", null, null, null, null, null, null, null,
        null, null, null, null, null, winery = null)
    private fun result(score: Float?) = ScanResult(wine, 1f, scanId = "scan",
        scoredCandidates = listOf(ScoredWine(wine, "a", 1, score),
            ScoredWine(wine.copy(id = "b"), "b", 2, .99f)))

    @Test fun onlyScoresStrictlyAboveFiftyConfirmFirstCandidate() {
        listOf(.50f, .49f, null, Float.NaN, Float.POSITIVE_INFINITY, 1.1f, -.1f).forEach {
            assertNull("score=$it", result(it).confirmation())
        }
        listOf(.5001f, .60f, .70f, .87f, 1f).forEach {
            assertEquals("a", result(it).confirmation()?.candidate?.slug)
            assertEquals("score_confirmed", result(it).confirmation()?.source)
        }
    }

    @Test fun refusalAndNoTargetOverrideEvenHighScoreOrUserChoice() {
        listOf("not_in_catalog", "no_target").forEach {
            assertNull(result(.99f).copy(recognitionStatus = it, userConfirmedSlug = "b").confirmation())
        }
    }

    @Test fun manualChoiceOverridesAutomaticFirstCandidate() {
        val selection = result(.99f).copy(userConfirmedSlug = "b").confirmation()
        assertEquals("b", selection?.candidate?.slug)
        assertEquals("user_confirmed", selection?.source)
    }

    @Test fun missingOrMisorderedTopCandidateCannotAutoConfirm() {
        val base = result(.9f)
        assertNull(base.copy(scoredCandidates = emptyList()).confirmation())
        assertNull(base.copy(scoredCandidates = base.scoredCandidates.reversed()).confirmation())
    }

    @Test fun detailStatusConfirmsOnlySelectedCardAndPreservesRefusal() {
        val base = result(.9f).copy(recognitionStatus = "candidates_unverified")
        assertEquals("score_confirmed", base.detailRecognitionStatus("a"))
        assertEquals("candidates_unverified", base.detailRecognitionStatus("b"))
        val manual = base.copy(userConfirmedSlug = "b")
        assertEquals("confirmed_by_user", manual.detailRecognitionStatus("b"))
        assertEquals("candidates_unverified", manual.detailRecognitionStatus("a"))
        assertEquals("not_in_catalog", base.copy(recognitionStatus = "not_in_catalog").detailRecognitionStatus("a"))
    }
}
