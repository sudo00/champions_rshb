package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.local.CellarDao
import com.wineapp.data.local.CellarEntity
import com.wineapp.domain.repository.CellarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CellarRepositoryImpl @Inject constructor(
    private val cellarDao: CellarDao
) : CellarRepository {

    override suspend fun addWine(wineId: String, delta: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cellarDao.addOrIncrement(wineId, delta)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("CellarRepo", "Add wine failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun setQuantity(wineId: String, quantity: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (quantity <= 0) {
                    cellarDao.deleteByWineId(wineId)
                } else {
                    cellarDao.setQuantity(wineId, quantity)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("CellarRepo", "Set quantity failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun setStatus(wineId: String, status: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cellarDao.setStatus(wineId, status)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("CellarRepo", "Set status failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun removeWine(wineId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cellarDao.deleteByWineId(wineId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("CellarRepo", "Remove wine failed", e)
                Result.failure(e)
            }
        }
    }

    override fun isInCellar(wineId: String): Flow<Boolean> = cellarDao.isInCellar(wineId)

    override fun getEntry(wineId: String): Flow<CellarEntity?> = cellarDao.getEntry(wineId)

    override fun getAllEntries(): Flow<List<CellarEntity>> = cellarDao.getAllEntries()
}
