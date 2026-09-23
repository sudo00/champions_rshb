package com.wineapp.domain.model

data class CellarItem(
    val wine: Wine,
    val quantity: Int,
    val status: String,
    val updatedAt: Long
)
