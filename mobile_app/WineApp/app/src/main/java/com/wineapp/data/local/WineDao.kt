package com.wineapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wine: WineHistoryEntity)

    @Query("SELECT * FROM wine_history ORDER BY scannedAt DESC")
    fun getAll(): Flow<List<WineHistoryEntity>>

    @Query("SELECT * FROM wine_history WHERE id = :id")
    suspend fun getById(id: String): WineHistoryEntity?

    @Query("DELETE FROM wine_history")
    suspend fun clearAll()

    @Query("DELETE FROM wine_history WHERE id = :id")
    suspend fun deleteById(id: String)
}