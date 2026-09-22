package com.wineapp.data.remote.mapper

import com.wineapp.data.remote.dto.ScanStatusResponse
import com.wineapp.data.remote.mapper.WineMapper
import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.Wine

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
            scoreIsProbability = response.scoreIsProbability
        )
    }
}
