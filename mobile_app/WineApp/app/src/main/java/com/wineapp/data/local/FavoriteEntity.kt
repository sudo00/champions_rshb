package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тег записи избранного: понравилось / хочу попробовать. */
object FavoriteKind {
    const val LIKED = "LIKED"
    const val WISH = "WISH"
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val wineId: String,
    val kind: String = FavoriteKind.LIKED,
    val addedAt: Long = System.currentTimeMillis()
)
