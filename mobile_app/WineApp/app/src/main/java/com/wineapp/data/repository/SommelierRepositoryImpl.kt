package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.remote.gigachat.ChatCompletionRequest
import com.wineapp.data.remote.gigachat.ChatMessageDto
import com.wineapp.data.remote.gigachat.GigaChatApiService
import com.wineapp.data.remote.gigachat.GigaChatTokenManager
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.WineContext
import com.wineapp.domain.repository.SommelierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SommelierRepositoryImpl @Inject constructor(
    private val apiService: GigaChatApiService,
    private val tokenManager: GigaChatTokenManager
) : SommelierRepository {

    override suspend fun sendMessage(
        messages: List<SommelierMessage>,
        wineContext: WineContext?
    ): Result<SommelierMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val token = tokenManager.getAccessToken()

                val systemPrompt = buildSystemPrompt(wineContext)
                val gigaMessages = buildMessageList(systemPrompt, messages)

                val request = ChatCompletionRequest(
                    model = MODEL,
                    messages = gigaMessages,
                    stream = false,
                    temperature = TEMPERATURE,
                    maxTokens = MAX_TOKENS
                )

                val response = apiService.chatCompletions(
                    authorization = "Bearer $token",
                    request = request
                )

                val assistantContent = response.choices.firstOrNull()?.message?.content
                if (assistantContent != null) {
                    Result.success(
                        SommelierMessage(role = "assistant", content = assistantContent)
                    )
                } else {
                    Result.failure(Exception("Пустой ответ от GigaChat"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "GigaChat API call failed", e)
                Result.failure(e)
            }
        }
    }

    private fun buildSystemPrompt(wineContext: WineContext?): String {
        val sb = StringBuilder(SYSTEM_PROMPT_BASE)

        if (wineContext != null) {
            sb.append("\n\nПользователь спрашивает о конкретном вине:\n")
            sb.append("- Название: ${wineContext.wineName}\n")
            wineContext.region?.let { sb.append("- Регион: $it\n") }
            wineContext.variety?.let { sb.append("- Сорт винограда: $it\n") }
            wineContext.vintage?.let { sb.append("- Год урожая: $it\n") }
            wineContext.rating?.let { sb.append("- Рейтинг: $it\n") }
            wineContext.style?.let { sb.append("- Стиль: $it\n") }
        }

        return sb.toString()
    }

    private fun buildMessageList(
        systemPrompt: String,
        messages: List<SommelierMessage>
    ): List<ChatMessageDto> {
        val result = mutableListOf<ChatMessageDto>()
        result.add(ChatMessageDto(role = "system", content = systemPrompt))

        for (msg in messages) {
            result.add(ChatMessageDto(role = msg.role, content = msg.content))
        }

        return result
    }

    companion object {
        private const val TAG = "SommelierRepo"
        private const val MODEL = "GigaChat-2"
        private const val TEMPERATURE = 0.7
        private const val MAX_TOKENS = 1024

        private const val SYSTEM_PROMPT_BASE = """Ты — профессиональный сомелье с многолетним опытом работы в ресторанах высокой кухни. Общаешься с гостем лично, как живой эксперт за барной стойкой.

Обязательные правила:
- Пиши ТОЛЬКО простой текст, никакого markdown, никаких символов *, #, `, -, >
- Никаких списков, заголовков, форматирования — только сплошной текст
- Отвечай по-русски, дружелюбно и профессионально
- Будь как живой сомелье — тепло, по-человечески, с экспертным знанием
- Отвечай кратко: 2-4 предложения, максимум 5
- Говори конкретно: называй блюда, температуры, регионы
- Если не знаешь точный ответ — честно скажи, но предложи альтернативу"""
    }
}
