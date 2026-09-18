package com.wineapp.presentation.sommelier

import androidx.lifecycle.viewModelScope
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SommelierViewModel @Inject constructor() : BaseViewModel<SommelierState, SommelierIntent>() {

    override fun getInitialState(): SommelierState = SommelierState.Idle()

    override fun reduce(intent: SommelierIntent) {
        when (intent) {
            is SommelierIntent.SendMessage -> handleSendMessage(intent.text)
            is SommelierIntent.ClearChat -> handleClear()
        }
    }

    private fun handleSendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val userMessage = ChatMessage(text = trimmed, isUser = true)
        val currentState = _state.value
        val messages = when (currentState) {
            is SommelierState.Idle -> currentState.messages
            is SommelierState.Loading -> currentState.messages
            is SommelierState.Error -> currentState.messages
        }
        val newMessages = messages + userMessage
        updateState(SommelierState.Loading(newMessages))

        // TODO: Implement AI Sommelier response
        // Simulate AI response
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val aiResponse = ChatMessage(
                text = "Я — AI-сомелье. Могу помочь с рекомендациями вин, гастрономическими сочетаниями, информацией о регионах и многим другим. Что хотите узнать?",
                isUser = false
            )
            updateState(SommelierState.Idle(newMessages + aiResponse))
        }
    }

    private fun handleClear() {
        updateState(SommelierState.Idle())
    }
}