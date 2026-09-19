package com.wineapp.data.remote.mapper

import com.wineapp.data.remote.dto.SommelierChatMessageDto
import com.wineapp.data.remote.dto.SommelierChatRequest
import com.wineapp.data.remote.dto.SommelierChatResponse
import com.wineapp.data.remote.dto.SommelierWineContextDto
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.WineContext

object SommelierMapper {

    fun toRequest(messages: List<SommelierMessage>, wineContext: WineContext?): SommelierChatRequest {
        return SommelierChatRequest(
            messages = messages.map { SommelierChatMessageDto(role = it.role, content = it.content) },
            wineContext = wineContext?.toDto()
        )
    }

    fun toDomain(response: SommelierChatResponse): Result<SommelierMessage> {
        return if (response.success && response.message != null) {
            Result.success(
                SommelierMessage(role = response.message.role, content = response.message.content)
            )
        } else {
            Result.failure(Exception(response.error ?: "Ошибка сомелье"))
        }
    }

    private fun WineContext.toDto(): SommelierWineContextDto {
        return SommelierWineContextDto(
            wineId = wineId,
            wineName = wineName,
            region = region,
            variety = variety,
            vintage = vintage,
            rating = rating,
            style = style
        )
    }
}
