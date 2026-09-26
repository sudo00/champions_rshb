package com.wineapp.domain.model

/** Один диалог для бокового меню истории чатов сомелье. */
data class ChatHistoryItem(
    val scanId: String,
    val wineId: String,
    val wineName: String,
    val lastMessage: String?,
    val lastMessageAt: Long,
    val messageCount: Int
)
