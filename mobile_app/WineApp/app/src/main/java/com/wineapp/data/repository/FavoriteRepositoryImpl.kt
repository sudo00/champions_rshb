package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.local.FavoriteDao
import com.wineapp.data.local.FavoriteEntity
import com.wineapp.domain.repository.FavoriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FavoriteRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao
) : FavoriteRepository {

    override suspend fun addFavorite(wineId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                favoriteDao.insert(FavoriteEntity(wineId = wineId))
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("FavoriteRepo", "Add favorite failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun removeFavorite(wineId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                favoriteDao.deleteByWineId(wineId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("FavoriteRepo", "Remove favorite failed", e)
                Result.failure(e)
            }
        }
    }

    override fun isFavorite(wineId: String): Flow<Boolean> {
        return favoriteDao.isFavorite(wineId)
    }

    override fun getAllFavoriteIds(): Flow<List<String>> {
        return favoriteDao.getAllFavoriteIds()
    }
}
