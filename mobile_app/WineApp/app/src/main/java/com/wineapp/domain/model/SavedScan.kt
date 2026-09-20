package com.wineapp.domain.model

data class SavedScan(
    val id: String,
    val wine: Wine,
    val labelPhotoPath: String?,
    val confidence: Float,
    val conversation: List<SommelierMessage>,
    val scannedAt: Long
)
