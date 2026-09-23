package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Полученный бейдж. code — первичный ключ для идемпотентности награждения. */
@Entity(tableName = "user_badges")
data class UserBadgeEntity(
    @PrimaryKey
    val code: String,
    val earnedAt: Long = System.currentTimeMillis()
)

/** Строка очкового леджра. Итог считается SUM(delta). */
@Entity(tableName = "points_ledger")
data class PointsEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val delta: Int,
    val reason: String,
    val refId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
