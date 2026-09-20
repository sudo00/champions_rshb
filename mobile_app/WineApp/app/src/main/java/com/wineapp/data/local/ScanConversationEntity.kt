package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_conversations",
    foreignKeys = [
        ForeignKey(
            entity = ScanHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanHistoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scanHistoryId")]
)
data class ScanConversationEntity(
    @PrimaryKey
    val id: String,
    val scanHistoryId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
