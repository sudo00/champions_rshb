package com.wineapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(messages: List<ScanConversationEntity>)

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC")
    fun getAllScans(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanById(id: String): ScanHistoryEntity?

    @Query("SELECT * FROM scan_conversations WHERE scanHistoryId = :scanId ORDER BY timestamp ASC")
    fun getConversationFlow(scanId: String): Flow<List<ScanConversationEntity>>

    @Query("SELECT * FROM scan_conversations WHERE scanHistoryId = :scanId ORDER BY timestamp ASC")
    suspend fun getConversation(scanId: String): List<ScanConversationEntity>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: String)
}
