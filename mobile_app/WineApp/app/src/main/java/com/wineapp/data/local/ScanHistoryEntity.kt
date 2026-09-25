package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey
    val id: String,
    val wineId: String,
    val labelPhotoPath: String?,
    val confidence: Float,
    val wineName: String,
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
    val foodPairing: String,
    val winery: String?,
    val scannedAt: Long = System.currentTimeMillis(),
    /** Нормализованная территория «Винного пути» (TerritoryRegistry id), заполняется при сохранении. */
    val territoryId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'legacy'")
    val recognitionStatus: String = "legacy"
)
