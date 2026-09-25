package com.wineapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @androidx.room.Upsert
    suspend fun insertScan(scan: ScanHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(messages: List<ScanConversationEntity>)

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC")
    fun getAllScans(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC LIMIT :limit")
    suspend fun getRecentScans(limit: Int): List<ScanHistoryEntity>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanById(id: String): ScanHistoryEntity?

    @Query("SELECT COUNT(*) FROM scan_history WHERE recognitionStatus IN ('legacy', 'user_confirmed', 'score_confirmed')")
    suspend fun getScansCount(): Int

    @Query("SELECT DISTINCT territoryId FROM scan_history WHERE territoryId IS NOT NULL")
    suspend fun getDistinctTerritoryIds(): List<String>

    @Query("SELECT DISTINCT wineId FROM scan_history WHERE territoryId = :territoryId")
    suspend fun getWineIdsByTerritory(territoryId: String): List<String>

    @Query("SELECT * FROM scan_conversations WHERE scanHistoryId = :scanId ORDER BY timestamp ASC")
    fun getConversationFlow(scanId: String): Flow<List<ScanConversationEntity>>

    @Query("SELECT * FROM scan_conversations WHERE scanHistoryId = :scanId ORDER BY timestamp ASC")
    suspend fun getConversation(scanId: String): List<ScanConversationEntity>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: String)
}
