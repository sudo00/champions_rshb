package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.remote.ApiService
import com.wineapp.data.remote.mapper.SommelierMapper
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.WineContext
import com.wineapp.domain.repository.SommelierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val USE_MOCK = true

class SommelierRepositoryImpl @javax.inject.Inject constructor(
    private val apiService: ApiService
) : SommelierRepository {

    override suspend fun sendMessage(
        messages: List<SommelierMessage>,
        wineContext: WineContext?
    ): Result<SommelierMessage> {
        return withContext(Dispatchers.IO) {
            if (USE_MOCK) {
                delay(1000)
                val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
                val response = generateMockResponse(lastUserMessage, wineContext)
                return@withContext Result.success(
                    SommelierMessage(role = "assistant", content = response)
                )
            }
            try {
                val request = SommelierMapper.toRequest(messages, wineContext)
                val response = apiService.sommelierChat(request)
                SommelierMapper.toDomain(response)
            } catch (e: Exception) {
                Log.e("SommelierRepositoryImpl", "Sommelier chat failed, using mock data", e)
                val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
                val mockResponse = generateMockResponse(lastUserMessage, wineContext)
                Result.success(SommelierMessage(role = "assistant", content = mockResponse))
            }
        }
    }

    private fun generateMockResponse(question: String, wineContext: WineContext?): String {
        val wineName = wineContext?.wineName ?: "это вино"
        val region = wineContext?.region
        val variety = wineContext?.variety

        return when {
            question.contains("подавать", ignoreCase = true) || question.contains("сочетан", ignoreCase = true) -> {
                val base = "$wineName сочетается с "
                val pairings = when {
                    wineContext?.style?.contains("Red", ignoreCase = true) == true ->
                        "мясными блюдами, сырами с плесенью, тёмным шоколадом. Попробуйте со стейком или бараниной."
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
                    "$region — один из ключевых винодельческих регионов. Климат и терруар создают уникальный характер вин. $wineName — отличный пример этого стиля."
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
                "Подавайте $wineName при температуре $temp. Перед подачей подержите в бокале 5-10 минут."
            }
            else -> {
                "$wineName — отличный выбор. ${if (region != null) "Регион: $region. " else ""}${if (variety != null) "Сорт: $variety. " else ""}Задайте конкретный вопрос, и я помогу с подробной информацией!"
            }
        }
    }
}
