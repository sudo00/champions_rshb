package com.wineapp.data.remote

import com.wineapp.data.remote.dto.ScanRequest
import com.wineapp.data.remote.dto.ScanResponse
import com.wineapp.data.remote.dto.SearchResponse
import com.wineapp.data.remote.dto.SommelierChatRequest
import com.wineapp.data.remote.dto.SommelierChatResponse
import com.wineapp.data.remote.dto.WineDetailResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("v1/wines/scan")
    suspend fun scanLabel(@Body request: ScanRequest): ScanResponse

    @GET("v1/wines/search")
    suspend fun searchWines(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("per_page") pageSize: Int
    ): SearchResponse

    @GET("v1/wines/{id}")
    suspend fun getWineDetail(@Path("id") id: String): WineDetailResponse

    @POST("v1/sommelier/chat")
    suspend fun sommelierChat(@Body request: SommelierChatRequest): SommelierChatResponse
}