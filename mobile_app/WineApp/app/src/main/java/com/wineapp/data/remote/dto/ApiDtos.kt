package com.wineapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ScanRequest(
    val imageBase64: String,
    val includeAlternatives: Boolean = true
)

@Serializable
data class ScanResponse(
    val success: Boolean,
    val wine: WineDto?,
    val confidence: Float,
    val alternatives: List<WineDto> = emptyList(),
    val error: String?
)

@Serializable
data class SearchResponse(
    val wines: List<WineDto> = emptyList(),
    val totalCount: Int,
    val page: Int,
    val hasMore: Boolean
)

@Serializable
data class WineDetailResponse(
    val wine: WineDto?
)

@Serializable
data class WineDto(
    val id: String,
    val name: String,
    val vintage: Int?,
    val rating: Float,
    val reviewsCount: Int,
    val price: Double?,
    val currency: String?,
    val region: RegionDto?,
    val country: CountryDto?,
    val variety: VarietyDto?,
    val style: StyleDto?,
    val alcoholPercentage: Float?,
    val imageUrl: String?,
    val description: String?,
    val foodPairing: List<String> = emptyList(),
    val winery: WineryDto?
)

@Serializable
data class RegionDto(val name: String, val country: CountryDto?)
@Serializable
data class CountryDto(val name: String, val code: String)
@Serializable
data class VarietyDto(val name: String)
@Serializable
data class StyleDto(val name: String)
@Serializable
data class WineryDto(val name: String)

// --- Sommelier Chat DTOs ---

@Serializable
data class SommelierChatRequest(
    val messages: List<SommelierChatMessageDto>,
    val wineContext: SommelierWineContextDto? = null
)

@Serializable
data class SommelierChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class SommelierWineContextDto(
    val wineId: String,
    val wineName: String,
    val region: String? = null,
    val variety: String? = null,
    val vintage: Int? = null,
    val rating: Float? = null,
    val style: String? = null
)

@Serializable
data class SommelierChatResponse(
    val success: Boolean,
    val message: SommelierChatMessageDto?,
    val error: String? = null
)