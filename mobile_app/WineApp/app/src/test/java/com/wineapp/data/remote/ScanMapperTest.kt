package com.wineapp.data.remote

import com.wineapp.data.remote.dto.ScanStatusResponse
import com.wineapp.data.remote.mapper.ScanMapper
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ScanMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun scoresAndRecommendationsAreNotLostOrMixedWithCandidates() {
        val response = json.decodeFromString<ScanStatusResponse>("""
            {"success":true,"scanId":"s","status":"done","recognitionStatus":"candidates_unverified",
             "candidates":[{"slug":"a","rank":1,"matchScore":0.87,"wine":{"id":"a","slug":"a","name":"Wine A"}}],
             "recommendations":[{"slug":"b","wine":{"id":"b","slug":"b","name":"Wine B"},"reasons":["Совпадает цвет"]}],
             "recommendationContext":{"basis":"retrieval_top1","status":"available","criteria":{"color":"Белое","grapes":["Рислинг"],"producer":null}}}
        """.trimIndent())
        val result = ScanMapper.toDomain(response)
        assertEquals(.87f, result.scoredCandidates.single().matchScore!!, .001f)
        assertEquals("b", result.recommendations.single().wine.id)
        assertEquals("a", result.scoredCandidates.single().wine.id)
        assertEquals("Рислинг", result.recommendationCriteria["grapes"])
        assertFalse(result.recommendationCriteria.containsKey("producer"))
    }

    @Test fun absentWineKeepsRecommendationsWithoutInventingAMatch() {
        val response = json.decodeFromString<ScanStatusResponse>("""
            {"success":true,"status":"done","recognitionStatus":"not_in_catalog","wine":null,
             "recommendations":[{"slug":"b","wine":{"id":"b","slug":"b","name":"Wine B"}}]}
        """.trimIndent())
        val result = ScanMapper.toDomain(response)
        assertNull(result.wine)
        assertTrue(result.scoredCandidates.isEmpty())
        assertEquals(1, result.recommendations.size)
    }
}
