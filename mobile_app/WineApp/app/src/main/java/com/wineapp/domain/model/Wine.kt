package com.wineapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Wine(
    val id: String,
    val name: String,
    val vintage: Int?,
    val rating: Float?,
    val reviewsCount: Int?,
    val price: Double?,
    val currency: String?,
    val region: String?,
    val country: String?,
    val variety: String?,
    val style: String?,
    val alcoholPercentage: Float?,
    val imageUrl: String?,
    val description: String?,
    val foodPairing: List<String> = emptyList(),
    val winery: String?
)

data class ScanResult(
    val wine: Wine?,
    val confidence: Float,
    val matches: List<Wine> = emptyList(),
    val slug: String? = null,
    val candidates: List<Wine> = emptyList(),
    val recognitionStatus: String? = null,
    val message: String? = null,
    val observations: List<Map<String, Any>> = emptyList(),
    val observedFields: Map<String, Any> = emptyMap(),
    val regions: List<Map<String, Any>> = emptyList(),
    val imageSize: List<Int> = emptyList(),
    val coordinateSystem: String? = null,
    val target: Map<String, Any> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val version: String? = null,
    val catalogSha256: String? = null,
    val timingsSeconds: Map<String, Any> = emptyMap(),
    val scoreIsProbability: Boolean = false
)

@Serializable
data class SearchResult(
    val wines: List<Wine> = emptyList(),
    val totalCount: Int,
    val page: Int,
    val hasMore: Boolean
)