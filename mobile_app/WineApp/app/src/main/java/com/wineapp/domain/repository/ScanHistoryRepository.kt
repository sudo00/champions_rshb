package com.wineapp.domain.repository

import com.wineapp.domain.model.SavedScan
import kotlinx.coroutines.flow.Flow

interface ScanHistoryRepository {
    suspend fun saveScan(scan: SavedScan): Result<Unit>
    fun getSavedScans(): Flow<List<SavedScan>>
    suspend fun getSavedScanById(id: String): Result<SavedScan>
    suspend fun deleteScan(id: String): Result<Unit>
    suspend fun getRecentScans(limit: Int): Result<List<SavedScan>>
}
