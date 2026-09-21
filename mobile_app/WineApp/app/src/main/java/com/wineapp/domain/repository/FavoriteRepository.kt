package com.wineapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    suspend fun addFavorite(wineId: String): Result<Unit>
    suspend fun removeFavorite(wineId: String): Result<Unit>
    fun isFavorite(wineId: String): Flow<Boolean>
    fun getAllFavoriteIds(): Flow<List<String>>
}
