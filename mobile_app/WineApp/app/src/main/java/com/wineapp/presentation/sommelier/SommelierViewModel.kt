package com.wineapp.presentation.sommelier

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.ChatHistoryItem
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.WineContext
import com.wineapp.domain.usecase.GetChatHistoryUseCase
import com.wineapp.domain.usecase.GetSavedScanByIdUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.SaveScanUseCase
import com.wineapp.domain.usecase.SendSommelierMessageUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SommelierViewModel @Inject constructor(
    private val sendSommelierMessageUseCase: SendSommelierMessageUseCase,
    private val saveScanUseCase: SaveScanUseCase,
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val getSavedScanByIdUseCase: GetSavedScanByIdUseCase,
    getChatHistoryUseCase: GetChatHistoryUseCase
) : BaseViewModel<SommelierState, SommelierIntent>() {

    /** История диалогов для бокового меню (drawer). */
    val chatHistory: StateFlow<List<ChatHistoryItem>> = getChatHistoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override fun getInitialState(): SommelierState = SommelierState.Idle()

    override fun reduce(intent: SommelierIntent) {
        when (intent) {
            is SommelierIntent.SendMessage -> handleSendMessage(intent.text)
            is SommelierIntent.SetWineContext -> handleSetWineContext(intent.wineContext)
            is SommelierIntent.SaveAndExit -> handleSaveAndExit(intent.photoPath, intent.confidence)
            is SommelierIntent.OpenHistory -> handleOpenHistory(intent.scanId)
            is SommelierIntent.ClearChat -> handleClear()
        }
    }

    private fun handleSetWineContext(wineContext: com.wineapp.domain.model.WineContext) {
        val currentState = _state.value
        // Контекст новый — вино догружаем отдельно для scan-карточки.
        when (currentState) {
            is SommelierState.Idle -> updateState(
                currentState.copy(wineContext = wineContext, wine = null, isWineLoading = true)
            )
            is SommelierState.Loading -> updateState(
                currentState.copy(wineContext = wineContext, wine = null, isWineLoading = true)
            )
            is SommelierState.Error -> updateState(
                SommelierState.Idle(wineContext = wineContext, wine = null, isWineLoading = true)
            )
        }
        viewModelScope.launch {
            val wine = getWineDetailsUseCase(wineContext.wineId).getOrNull()
            applyWine(wine)
        }
    }

    /** Проставляет загруженное вино (или null при ошибке) в любую ветку стейта. */
    private fun applyWine(wine: com.wineapp.domain.model.Wine?) {
        when (val s = _state.value) {
            is SommelierState.Idle -> updateState(s.copy(wine = wine, isWineLoading = false))
            is SommelierState.Loading -> updateState(s.copy(wine = wine, isWineLoading = false))
            is SommelierState.Error -> updateState(s.copy(wine = wine, isWineLoading = false))
        }
    }

    private fun handleSendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val userMessage = ChatMessage(text = trimmed, isUser = true)
        val currentState = _state.value
        val (messages, wineContext, wine) = when (currentState) {
            is SommelierState.Idle -> Triple(currentState.messages, currentState.wineContext, currentState.wine)
            is SommelierState.Loading -> Triple(currentState.messages, currentState.wineContext, currentState.wine)
            is SommelierState.Error -> Triple(currentState.messages, currentState.wineContext, currentState.wine)
        }
        val newMessages = messages + userMessage
        updateState(SommelierState.Loading(newMessages, wineContext, wine))

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
                    updateState(SommelierState.Idle(newMessages + aiResponse, wineContext, wine))
                }
                .onFailure { e ->
                    updateState(SommelierState.Error(e.message ?: "Ошибка", newMessages, wineContext, wine))
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
                scannedAt = System.currentTimeMillis(),
                // Не скан: без этого статус по умолчанию «legacy» засчитывал каждый
                // диалог как подтверждённый скан — +10 очков, «Первый глоток», «Дегустатор».
                recognitionStatus = SavedScan.STATUS_SOMMELIER_CHAT
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

    /** Открыть диалог из истории: сообщения + контекст + полное вино из скана. */
    private fun handleOpenHistory(scanId: String) {
        viewModelScope.launch {
            val scan = getSavedScanByIdUseCase(scanId).getOrNull() ?: return@launch
            val messages = scan.conversation.map { msg ->
                ChatMessage(text = msg.content, isUser = msg.role == "user")
            }
            val wine = scan.wine
            updateState(
                SommelierState.Idle(
                    messages = messages,
                    wineContext = WineContext(
                        wineId = wine.id,
                        wineName = wine.name,
                        region = wine.region,
                        variety = wine.variety,
                        vintage = wine.vintage,
                        rating = wine.rating,
                        style = wine.style
                    ),
                    wine = wine
                )
            )
        }
    }
}
