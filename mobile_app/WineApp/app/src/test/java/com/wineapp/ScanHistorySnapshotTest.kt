package com.wineapp

import com.wineapp.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ScanHistorySnapshotTest {
    private val wine = Wine("a", "Первый", null, null, null, null, null, null, null,
        null, null, null, null, null, winery = null)
    private val other = wine.copy(id = "b", name = "Второй")
    private fun result() = ScanResult(wine, .8f, scanId = "scan-id",
        scoredCandidates = listOf(ScoredWine(wine, "a", 1, .8f), ScoredWine(other, "b", 2, .4f)))

    @Test fun cameraAndGallerySaveWithoutConfirmation() {
        listOf("/cache/gallery.jpg", "/images/camera.jpg").forEach { path ->
            val saved = requireNotNull(result().historySnapshot(path, 100))
            assertEquals("score_confirmed", saved.recognitionStatus)
            assertEquals(path, saved.labelPhotoPath)
            assertEquals(wine, saved.wine)
        }
    }

    @Test fun confirmationKeepsIdAndReplacesFirstCandidate() {
        val saved = requireNotNull(result().copy(userConfirmedSlug = "b").historySnapshot("photo", 100))
        assertEquals("scan-id", saved.id)
        assertEquals(other, saved.wine)
        assertEquals("user_confirmed", saved.recognitionStatus)
        assertEquals(.4f, saved.confidence)
    }

    @Test fun absentDoesNotSaveRejectedWineAsMatch() {
        val saved = requireNotNull(result().copy(recognitionStatus = "not_in_catalog").historySnapshot("photo", 100))
        assertEquals("not_in_catalog", saved.recognitionStatus)
        assertEquals("", saved.wine.id)
        assertEquals(0f, saved.confidence)
    }

    @Test fun noTargetAndMissingScanIdAreNotWineHistory() {
        assertNull(result().copy(recognitionStatus = "no_target").historySnapshot("photo", 100))
        assertNull(result().copy(scanId = null).historySnapshot("photo", 100))
    }
}
