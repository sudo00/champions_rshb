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

    @Query("UPDATE favorites SET kind = :kind WHERE wineId = :wineId")
    suspend fun setKind(wineId: String, kind: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE wineId = :wineId AND kind = :kind)")
    fun isFavorite(wineId: String, kind: String): Flow<Boolean>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
}
