package com.wineapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CellarDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: CellarEntity): Long

    @Query("UPDATE cellar SET quantity = quantity + :delta, updatedAt = :now WHERE wineId = :wineId")
    suspend fun incrementQuantity(wineId: String, delta: Int, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun addOrIncrement(wineId: String, delta: Int = 1, status: String = CellarStatus.IN_STOCK) {
        val now = System.currentTimeMillis()
        insertIgnore(CellarEntity(wineId = wineId, quantity = 0, status = status, addedAt = now, updatedAt = now))
        incrementQuantity(wineId, delta, now)
    }

    @Query("UPDATE cellar SET quantity = :quantity, updatedAt = :now WHERE wineId = :wineId")
    suspend fun setQuantity(wineId: String, quantity: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE cellar SET status = :status, updatedAt = :now WHERE wineId = :wineId")
    suspend fun setStatus(wineId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM cellar WHERE wineId = :wineId")
    suspend fun deleteByWineId(wineId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM cellar WHERE wineId = :wineId)")
    fun isInCellar(wineId: String): Flow<Boolean>

    @Query("SELECT * FROM cellar WHERE wineId = :wineId")
    fun getEntry(wineId: String): Flow<CellarEntity?>

    @Query("SELECT * FROM cellar WHERE wineId = :wineId")
    suspend fun getEntryOnce(wineId: String): CellarEntity?

    @Query("SELECT * FROM cellar ORDER BY updatedAt DESC")
    fun getAllEntries(): Flow<List<CellarEntity>>

    @Query("SELECT * FROM cellar ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<CellarEntity>
}
