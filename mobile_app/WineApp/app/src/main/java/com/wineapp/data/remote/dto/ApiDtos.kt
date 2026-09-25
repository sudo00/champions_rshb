package com.wineapp.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ScanRequest(
    val imageBase64: String,
    val includeAlternatives: Boolean = true
)

@Serializable
data class ScanAcceptedResponse(
    val success: Boolean,
    val scanId: String? = null,
    val status: String,
    val error: String? = null
)

@Serializable
data class ScanConfirmationRequest(val slug: String)

@Serializable
data class UserConfirmationDto(val slug: String, val source: String = "user")

@Serializable
data class RecommendationDto(
    val slug: String,
    val wine: WineDto,
    val reasons: List<String> = emptyList(),
    val matchedFields: List<String> = emptyList(),
    val textSimilarity: Float? = null
)

@Serializable
data class ScanStatusResponse(
    val success: Boolean,
    val scanId: String? = null,
    val status: String,
    val wine: WineDto? = null,
    val confidence: Float? = null,
    val alternatives: List<WineDto> = emptyList(),
    val slug: String? = null,
    val candidates: List<ScanCandidateDto> = emptyList(),
    val recognitionStatus: String? = null,
    val userConfirmation: UserConfirmationDto? = null,
    val recommendations: List<RecommendationDto> = emptyList(),
    val recommendationContext: JsonObject = JsonObject(emptyMap()),
    val message: String? = null,
    val cylinder: JsonObject = JsonObject(emptyMap()),
    val scoreIsProbability: Boolean = false,
    val observations: List<ObservationDto> = emptyList(),
    val observedFields: ObservedFieldsDto = ObservedFieldsDto(),
    val regions: List<RegionScanDto> = emptyList(),
    val imageSize: List<Int> = emptyList(),
    val coordinateSystem: String? = null,
    val target: TargetDto = TargetDto(),
    val warnings: List<String> = emptyList(),
    val version: String? = null,
    val catalogSha256: String? = null,
    val timingsSeconds: TimingsDto = TimingsDto(),
    val error: String? = null
)

@Serializable
data class ScanCandidateDto(
    val slug: String = "",
    val rank: Int = 0,
    val matchScore: Float? = null,
    val matchScoreIsProbability: Boolean = false,
    val wine: WineDto = WineDto(
        id = "", slug = "", name = ""
    ),
    val scores: Map<String, Float> = emptyMap()
)

@Serializable
data class CylinderDto(
    val diameter: Float = 0f,
    val height: Float = 0f,
    val axis: List<Float> = emptyList(),
    val points: List<List<Float>> = emptyList()
)

@Serializable
data class ObservationDto(
    val text: String = "",
    val label: String = "",
    val confidence: Float = 0f,
    val bbox: List<Float> = emptyList()
)

@Serializable
data class ObservedFieldsDto(
    val fields: Map<String, String> = emptyMap()
)

@Serializable
data class RegionScanDto(
    val label: String = "",
    val bbox: List<Float> = emptyList(),
    val points: List<List<Float>> = emptyList()
)

@Serializable
data class TargetDto(
    val label: String = "",
    val bbox: List<Float> = emptyList(),
    val points: List<List<Float>> = emptyList()
)

@Serializable
data class TimingsDto(
    val totalSeconds: Float = 0f,
    val recognitionSeconds: Float = 0f,
    val ocrSeconds: Float = 0f
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
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val vintage: Int? = null,
    val rating: Float? = null,
    val reviewsCount: Int? = null,
    val category: String? = null,
    val grapes: String? = null,
    val colorShade: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val region: RegionDto? = null,
    val country: CountryDto? = null,
    val variety: VarietyDto? = null,
    val style: StyleDto? = null,
    val alcoholPercentage: Float? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val foodPairing: List<String> = emptyList(),
    val winery: WineryDto? = null
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

@Serializable
data class SommelierWineContextDto(
    val wineId: String,
    val wineName: String,
    val region: String?,
    val variety: String?,
    val vintage: Int?,
    val rating: Float?,
    val style: String?
)

@Serializable
data class SommelierChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class SommelierChatRequest(
    val messages: List<SommelierChatMessageDto>,
    val wineContext: SommelierWineContextDto?
)

@Serializable
data class SommelierChatResponse(
    val success: Boolean,
    val message: SommelierChatMessageDto?,
    val error: String?
)
