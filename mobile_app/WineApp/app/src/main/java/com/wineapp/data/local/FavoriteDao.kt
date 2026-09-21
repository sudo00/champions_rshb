package com.wineapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE wineId = :wineId")
    suspend fun deleteByWineId(wineId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE wineId = :wineId)")
    fun isFavorite(wineId: String): Flow<Boolean>

    @Query("SELECT wineId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteIds(): Flow<List<String>>
}
