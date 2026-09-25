package com.wineapp.data.remote.mapper

import com.wineapp.data.remote.dto.ScanStatusResponse
import com.wineapp.data.remote.mapper.WineMapper
import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.Wine
import com.wineapp.domain.model.ScoredWine
import com.wineapp.domain.model.RecommendedWine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ScanMapper {
    fun toDomain(response: ScanStatusResponse): ScanResult {
        val wine = response.wine?.let { WineMapper.toDomain(it) }
        val alternatives = response.alternatives.map { WineMapper.toDomain(it) }
        val candidates = response.candidates.map { candidate ->
            WineMapper.toDomain(candidate.wine)
        }
        return ScanResult(
            wine = wine,
            confidence = response.confidence ?: 0f,
            matches = alternatives,
            slug = response.slug,
            candidates = candidates,
            recognitionStatus = response.recognitionStatus,
            message = response.message,
            observations = emptyList(),
            observedFields = emptyMap(),
            regions = emptyList(),
            imageSize = response.imageSize,
            coordinateSystem = response.coordinateSystem,
            target = emptyMap(),
            warnings = response.warnings,
            version = response.version,
            catalogSha256 = response.catalogSha256,
            timingsSeconds = emptyMap(),
            scoreIsProbability = response.scoreIsProbability,
            scanId = response.scanId,
            scoredCandidates = response.candidates.map {
                ScoredWine(WineMapper.toDomain(it.wine), it.slug, it.rank,
                    it.matchScore?.takeIf { score -> score.isFinite() && score in 0f..1f })
            },
            recommendations = response.recommendations.map {
                RecommendedWine(WineMapper.toDomain(it.wine), it.reasons)
            },
            recommendationStatus = (response.recommendationContext["status"] as? JsonPrimitive)?.content,
            recommendationBasis = (response.recommendationContext["basis"] as? JsonPrimitive)?.content,
            recommendationCriteria = (response.recommendationContext["criteria"] as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    val text = when (value) {
                        JsonNull -> null
                        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(", ")
                        is JsonPrimitive -> value.content
                        else -> null
                    }
                    text?.takeIf { it.isNotBlank() }?.let { key to it }
                }?.toMap() ?: emptyMap(),
            userConfirmedSlug = response.userConfirmation?.takeIf { it.source == "user" }?.slug
        )
    }
}
