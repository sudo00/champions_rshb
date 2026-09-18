package com.wineapp.data.remote.mapper

import com.wineapp.data.remote.dto.SearchResponse
import com.wineapp.domain.model.SearchResult

object SearchMapper {
    fun toDomain(response: SearchResponse): SearchResult {
        return SearchResult(
            wines = response.wines.map { WineMapper.toDomain(it) },
            totalCount = response.totalCount,
            page = response.page,
            hasMore = response.hasMore
        )
    }
}