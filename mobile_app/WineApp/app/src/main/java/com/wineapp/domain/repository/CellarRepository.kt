package com.wineapp.domain.repository

import com.wineapp.data.local.CellarEntity
import kotlinx.coroutines.flow.Flow

interface CellarRepository {
    suspend fun addWine(wineId: String, delta: Int = 1): Result<Unit>
    suspend fun setQuantity(wineId: String, quantity: Int): Result<Unit>
    suspend fun setStatus(wineId: String, status: String): Result<Unit>
    suspend fun removeWine(wineId: String): Result<Unit>
    fun isInCellar(wineId: String): Flow<Boolean>
    fun getEntry(wineId: String): Flow<CellarEntity?>
    fun getAllEntries(): Flow<List<CellarEntity>>
}
