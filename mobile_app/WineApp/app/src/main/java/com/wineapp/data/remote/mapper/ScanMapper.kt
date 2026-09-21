package com.wineapp.data.remote.mapper

import com.wineapp.data.remote.dto.ScanResponse
import com.wineapp.domain.model.ScanResult

object ScanMapper {
    fun toDomain(response: ScanResponse): ScanResult {
        val wine = response.wine?.let { WineMapper.toDomain(it) }
        val alternatives = response.alternatives.map { WineMapper.toDomain(it) }
        return ScanResult(
            wine = wine,
            confidence = response.confidence,
            matches = alternatives
        )
    }
}