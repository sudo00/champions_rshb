package com.wineapp.presentation.sommelier

import com.wineapp.domain.model.WineContext
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface SommelierState : BaseState {
    data class Idle(
        val messages: List<ChatMessage> = emptyList(),
        val wineContext: WineContext? = null
    ) : SommelierState
    data class Loading(val messages: List<ChatMessage>, val wineContext: WineContext? = null) : SommelierState
    data class Error(val message: String, val messages: List<ChatMessage>, val wineContext: WineContext? = null) : SommelierState
}

sealed interface SommelierIntent : BaseIntent {
    data class SendMessage(val text: String) : SommelierIntent
    data class SetWineContext(val wineContext: WineContext) : SommelierIntent
    data object ClearChat : SommelierIntent
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
