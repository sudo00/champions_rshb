package com.wineapp.data.remote.gigachat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_at") val expiresAt: Long
)

@Serializable
data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatMessageDto>,
    @SerialName("stream") val stream: Boolean = false,
    @SerialName("temperature") val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class ChatMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

@Serializable
data class ChatCompletionResponse(
    @SerialName("choices") val choices: List<Choice>,
    @SerialName("usage") val usage: Usage? = null
)

@Serializable
data class Choice(
    @SerialName("message") val message: ChatMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null,
    @SerialName("index") val index: Int = 0
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
