package com.wineapp.presentation.sommelier

import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.usecase.SendSommelierMessageUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SommelierViewModel @Inject constructor(
    private val sendSommelierMessageUseCase: SendSommelierMessageUseCase
) : BaseViewModel<SommelierState, SommelierIntent>() {

    override fun getInitialState(): SommelierState = SommelierState.Idle()

    override fun reduce(intent: SommelierIntent) {
        when (intent) {
            is SommelierIntent.SendMessage -> handleSendMessage(intent.text)
            is SommelierIntent.SetWineContext -> handleSetWineContext(intent.wineContext)
            is SommelierIntent.ClearChat -> handleClear()
        }
    }

    private fun handleSetWineContext(wineContext: com.wineapp.domain.model.WineContext) {
        val currentState = _state.value
        when (currentState) {
            is SommelierState.Idle -> updateState(currentState.copy(wineContext = wineContext))
            is SommelierState.Loading -> updateState(currentState.copy(wineContext = wineContext))
            is SommelierState.Error -> updateState(SommelierState.Idle(wineContext = wineContext))
        }
    }

    private fun handleSendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val userMessage = ChatMessage(text = trimmed, isUser = true)
        val currentState = _state.value
        val (messages, wineContext) = when (currentState) {
            is SommelierState.Idle -> currentState.messages to currentState.wineContext
            is SommelierState.Loading -> currentState.messages to currentState.wineContext
            is SommelierState.Error -> currentState.messages to currentState.wineContext
        }
        val newMessages = messages + userMessage
        updateState(SommelierState.Loading(newMessages, wineContext))

        viewModelScope.launch {
            val domainMessages = newMessages.map {
                SommelierMessage(
                    role = if (it.isUser) "user" else "assistant",
                    content = it.text
                )
            }
            sendSommelierMessageUseCase(domainMessages, wineContext)
                .onSuccess { reply ->
                    val aiResponse = ChatMessage(text = reply.content, isUser = false)
                    updateState(SommelierState.Idle(newMessages + aiResponse, wineContext))
                }
                .onFailure { e ->
                    updateState(SommelierState.Error(e.message ?: "Ошибка", newMessages, wineContext))
                }
        }
    }

    private fun handleClear() {
        updateState(SommelierState.Idle())
    }
}
