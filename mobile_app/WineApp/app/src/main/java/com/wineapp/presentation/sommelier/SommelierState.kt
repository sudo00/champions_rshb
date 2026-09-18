package com.wineapp.presentation.sommelier

import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface SommelierState : BaseState {
    data class Idle(val messages: List<ChatMessage> = emptyList()) : SommelierState
    data class Loading(val messages: List<ChatMessage>) : SommelierState
    data class Error(val message: String, val messages: List<ChatMessage>) : SommelierState
}

sealed interface SommelierIntent : BaseIntent {
    data class SendMessage(val text: String) : SommelierIntent
    data object ClearChat : SommelierIntent
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)