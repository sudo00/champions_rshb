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

    /** Сколько разных вин отсканировано (подтверждённые сканы): повторы одного вина не считаются. */
    @Query(
        """SELECT COUNT(DISTINCT wineId) FROM scan_history
           WHERE recognitionStatus IN ('legacy', 'user_confirmed', 'score_confirmed') AND wineId != ''"""
    )
    suspend fun getScannedWinesCount(): Int

    /**
     * Подтверждённые сканы в проекции для подсчёта вин по территориям.
     * Нужен region, потому что у сканов, сделанных до появления territoryId,
     * территория восстанавливается только из сырого региона.
     */
    @Query(
        """SELECT territoryId AS territoryId, region AS region, wineId AS wineId
           FROM scan_history
           WHERE recognitionStatus IN ('legacy', 'user_confirmed', 'score_confirmed')"""
    )
    suspend fun getTerritoryWineRows(): List<TerritoryWineRow>

    @Query("SELECT * FROM scan_conversations WHERE scanHistoryId = :scanId ORDER BY timestamp ASC")
    fun getConversationFlow(scanId: String): Flow<List<ScanConversationEntity>>

    @Query("SELECT * FROM scan_conversations WHERE scanHistoryId = :scanId ORDER BY timestamp ASC")
    suspend fun getConversation(scanId: String): List<ScanConversationEntity>

    @androidx.room.Transaction
    @Query(
        """SELECT sh.*,
           (SELECT MAX(timestamp) FROM scan_conversations WHERE scanHistoryId = sh.id) AS lastMessageAt,
           (SELECT content FROM scan_conversations WHERE scanHistoryId = sh.id ORDER BY timestamp DESC LIMIT 1) AS lastMessage,
           (SELECT COUNT(id) FROM scan_conversations WHERE scanHistoryId = sh.id) AS messageCount
           FROM scan_history sh
           WHERE EXISTS (SELECT 1 FROM scan_conversations WHERE scanHistoryId = sh.id)
           ORDER BY lastMessageAt DESC"""
    )
    fun getChatsHistory(): Flow<List<ChatHistoryEntry>>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: String)
}
