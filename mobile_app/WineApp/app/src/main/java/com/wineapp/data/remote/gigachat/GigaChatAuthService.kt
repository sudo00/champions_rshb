package com.wineapp.data.remote.gigachat

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface GigaChatAuthService {

    @FormUrlEncoded
    @POST("api/v2/oauth")
    suspend fun getAccessToken(
        @Header("RqUID") rqUid: String,
        @Header("Authorization") authorization: String,
        @Field("scope") scope: String = "GIGACHAT_API_PERS"
    ): OAuthTokenResponse
}
