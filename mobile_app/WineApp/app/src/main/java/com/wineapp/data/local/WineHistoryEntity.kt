package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "wine_history")
@Serializable
data class WineHistoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val vintage: Int?,
    val rating: Float,
    val reviewsCount: Int,
    val price: Double?,
    val currency: String?,
    val region: String?,
    val country: String?,
    val variety: String?,
    val style: String?,
    val alcoholPercentage: Float?,
    val imageUrl: String?,
    val description: String?,
    val foodPairing: String, // JSON serialized list
    val winery: String?,
    val scannedAt: Long = System.currentTimeMillis()
)