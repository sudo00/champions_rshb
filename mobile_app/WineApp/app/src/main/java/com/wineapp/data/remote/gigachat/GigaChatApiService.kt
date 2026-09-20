package com.wineapp.data.remote.gigachat

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GigaChatApiService {

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Header("X-Client-ID") clientId: String = "wineapp",
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
