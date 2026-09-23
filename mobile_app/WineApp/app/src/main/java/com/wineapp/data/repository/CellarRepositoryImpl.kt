package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.local.CellarDao
import com.wineapp.data.local.CellarEntity
import com.wineapp.data.local.CellarStatus
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
                val current = cellarDao.getEntryOnce(wineId)
                if (current == null) {
                    val qty = maxOf(0, delta)
                    cellarDao.insertIgnore(
                        CellarEntity(
                            wineId = wineId,
                            quantity = qty,
                            status = if (qty > 0) CellarStatus.IN_STOCK else CellarStatus.CONSUMED
                        )
                    )
                } else {
                    applyQuantity(wineId, current.quantity + delta)
                }
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
                val current = cellarDao.getEntryOnce(wineId) ?: return@withContext Result.success(Unit)
                applyQuantity(wineId, quantity, current.status)
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
                val current = cellarDao.getEntryOnce(wineId) ?: return@withContext Result.success(Unit)
                // В «наличии» с нулём бутылок не бывает — поднимаем до 1.
                val quantity = if (status == CellarStatus.IN_STOCK && current.quantity <= 0) 1
                else current.quantity
                cellarDao.setQuantity(wineId, quantity)
                cellarDao.setStatus(wineId, status)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("CellarRepo", "Set status failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Единое правило переходов: бутылок больше 0 — «в наличии»,
     * 0 — «выпито» (запись остаётся для истории).
     */
    private suspend fun applyQuantity(wineId: String, quantity: Int, currentStatus: String? = null) {
        val qty = maxOf(0, quantity)
        val status = if (qty > 0) CellarStatus.IN_STOCK else CellarStatus.CONSUMED
        // insertIgnore на случай гонки, затем выставляем точные значения.
        cellarDao.insertIgnore(CellarEntity(wineId = wineId, quantity = qty, status = status))
        cellarDao.setQuantity(wineId, qty)
        if (currentStatus == null || status != currentStatus) {
            cellarDao.setStatus(wineId, status)
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
