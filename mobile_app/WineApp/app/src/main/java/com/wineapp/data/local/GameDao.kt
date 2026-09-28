package com.wineapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadgeIgnore(badge: UserBadgeEntity): Long

    @Query("SELECT * FROM user_badges ORDER BY earnedAt DESC")
    fun getBadges(): Flow<List<UserBadgeEntity>>

    @Insert
    suspend fun insertPoints(entry: PointsEntry)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    fun getTotalPoints(): Flow<Int>
}
