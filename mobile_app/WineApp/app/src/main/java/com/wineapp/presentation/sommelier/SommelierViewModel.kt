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
            is SommelierIntent.SetWineContext -> handleSetWineContext(intent.wineContext)
            is SommelierIntent.ClearChat -> handleClear()
        }
    }

    private fun handleSetWineContext(wineContext: WineContext) {
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
            kotlinx.coroutines.delay(1000)
            val aiResponse = ChatMessage(
                text = generateResponse(trimmed, wineContext),
                isUser = false
            )
            updateState(SommelierState.Idle(newMessages + aiResponse, wineContext))
        }
    }

    private fun generateResponse(question: String, wineContext: WineContext?): String {
        val wineName = wineContext?.wineName ?: "это вино"
        val region = wineContext?.region
        val variety = wineContext?.variety

        return when {
            question.contains("подавать", ignoreCase = true) || question.contains("сочетан", ignoreCase = true) -> {
                val base = "$wineName отлично сочетается с "
                val pairings = when {
                    wineContext?.style?.contains("Red", ignoreCase = true) == true ->
                        "мясными блюдами, сырами с плесенью, тёмным шоколадом. Попробуйте с стейком или бараниной."
                    wineContext?.style?.contains("White", ignoreCase = true) == true ->
                        "морепродуктами, птицей, салатами. Идеально подойдёт к рыбе на гриле."
                    wineContext?.style?.contains("Sparkling", ignoreCase = true) == true ->
                        "закусками, устрицами, лёгкими салатами. Отличный выбор для аперитива."
                    else -> "разнообразными блюдами. Рекомендую попробовать с сырами и орехами."
                }
                base + pairings
            }
            question.contains("аналог", ignoreCase = true) || question.contains("дешев", ignoreCase = true) -> {
                "Для $wineName из ${region ?: "известного региона"} (${variety ?: "сорт"}): ищите вина из того же региона с похожим сортом винограда. Обратите внимание на менее известные domaine — часто качество сопоставимо при более низкой цене."
            }
            question.contains("регион", ignoreCase = true) -> {
                if (region != null) {
                    "$region — один из ключевых винодельческих регионов. Климат и терруар создают уникальный характер вин. ${wineName} — отличный пример этого стиля."
                } else {
                    "$wineName — интересное вино. Расскажите, что именно вас интересует в регионе, и я дам подробную информацию."
                }
            }
            question.contains("выдерж", ignoreCase = true) || question.contains("созрев", ignoreCase = true) -> {
                val years = when {
                    wineContext?.style?.contains("Red", ignoreCase = true) == true -> "3-5 лет"
                    wineContext?.style?.contains("White", ignoreCase = true) == true -> "1-3 года"
                    else -> "2-4 года"
                }
                "$wineName рекомендуется выдерживать $years. Крепость ${wineContext?.let { "%.1f".format(it.rating) } ?: "?"}% позволяет вину развиваться."
            }
            question.contains("температ", ignoreCase = true) -> {
                val temp = when {
                    wineContext?.style?.contains("Red", ignoreCase = true) == true -> "16-18°C"
                    wineContext?.style?.contains("White", ignoreCase = true) == true -> "8-12°C"
                    wineContext?.style?.contains("Sparkling", ignoreCase = true) == true -> "6-8°C"
                    else -> "10-14°C"
                }
                "Подавайте $wineName при температуре $temp. Перед подержанием в бокале 5-10 минут."
            }
            else -> {
                "$wineName — отличный выбор. ${if (region != null) "Регион: $region. " else ""}${if (variety != null) "Сорт: $variety. " else ""}Задайте конкретный вопрос, и я помогу с подробной информацией!"
            }
        }
    }

    private fun handleClear() {
        updateState(SommelierState.Idle())
    }
}
