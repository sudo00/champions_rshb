package com.wineapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** Строка истории диалогов: скан + агрегаты его переписки. Только для чтения. */
data class ChatHistoryEntry(
    @Embedded val scan: ScanHistoryEntity,
    @ColumnInfo(name = "lastMessageAt") val lastMessageAt: Long,
    @ColumnInfo(name = "lastMessage") val lastMessage: String?,
    @ColumnInfo(name = "messageCount") val messageCount: Int
)
