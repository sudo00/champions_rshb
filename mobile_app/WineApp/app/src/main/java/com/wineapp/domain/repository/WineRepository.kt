package com.wineapp.domain.repository

import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import kotlinx.coroutines.flow.Flow

interface WineRepository {
    suspend fun scanWineLabel(imagePath: String): Result<ScanResult>
    suspend fun confirmScan(scanId: String, slug: String): Result<ScanResult>
    suspend fun searchWines(query: String, page: Int, pageSize: Int): Result<SearchResult>
    suspend fun getWineById(id: String): Result<Wine>
    suspend fun saveToHistory(wine: Wine): Result<Unit>
    fun getHistory(): Flow<List<Wine>>
    suspend fun clearHistory(): Result<Unit>
}
