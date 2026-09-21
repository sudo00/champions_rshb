package com.wineapp.presentation.sommelier

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.SaveScanUseCase
import com.wineapp.domain.usecase.SendSommelierMessageUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SommelierViewModel @Inject constructor(
    private val sendSommelierMessageUseCase: SendSommelierMessageUseCase,
    private val saveScanUseCase: SaveScanUseCase,
    private val getWineDetailsUseCase: GetWineDetailsUseCase
) : BaseViewModel<SommelierState, SommelierIntent>() {

    override fun getInitialState(): SommelierState = SommelierState.Idle()

    override fun reduce(intent: SommelierIntent) {
        when (intent) {
            is SommelierIntent.SendMessage -> handleSendMessage(intent.text)
            is SommelierIntent.SetWineContext -> handleSetWineContext(intent.wineContext)
            is SommelierIntent.SaveAndExit -> handleSaveAndExit(intent.photoPath, intent.confidence)
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

    private fun handleSaveAndExit(photoPath: String?, confidence: Float) {
        val currentState = _state.value
        val wineContext = when (currentState) {
            is SommelierState.Idle -> currentState.wineContext
            is SommelierState.Loading -> currentState.wineContext
            is SommelierState.Error -> currentState.wineContext
        }
        val chatMessages = when (currentState) {
            is SommelierState.Idle -> currentState.messages
            is SommelierState.Loading -> currentState.messages
            is SommelierState.Error -> currentState.messages
        }
        if (wineContext == null || chatMessages.isEmpty()) return

        viewModelScope.launch {
            val wineResult = getWineDetailsUseCase(wineContext.wineId)
            val wine = wineResult.getOrNull() ?: return@launch

            val conversation = chatMessages.map { msg ->
                SommelierMessage(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.text
                )
            }

            val scan = SavedScan(
                id = UUID.randomUUID().toString(),
                wine = wine,
                labelPhotoPath = photoPath,
                confidence = confidence,
                conversation = conversation,
                scannedAt = System.currentTimeMillis()
            )
            saveScanUseCase(scan)
                .onFailure { error ->
                    Log.e("SommelierVM", "Save scan with conversation failed", error)
                }
        }
    }

    private fun handleClear() {
        updateState(SommelierState.Idle())
    }
}
