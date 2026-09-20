package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val wineId: String,
    val addedAt: Long = System.currentTimeMillis()
)
