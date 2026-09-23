package com.wineapp.domain.repository

import com.wineapp.data.local.FavoriteEntity
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    suspend fun addFavorite(wineId: String, kind: String): Result<Unit>
    suspend fun removeFavorite(wineId: String): Result<Unit>
    suspend fun setKind(wineId: String, kind: String): Result<Unit>
    fun isFavorite(wineId: String, kind: String): Flow<Boolean>
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
}
