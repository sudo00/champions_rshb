package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cellar")
data class CellarEntity(
    @PrimaryKey
    val wineId: String,
    val quantity: Int = 1,
    val status: String = CellarStatus.IN_STOCK,
    val note: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Статусы записи погреба: в наличии / выпито. */
object CellarStatus {
    const val IN_STOCK = "IN_STOCK"
    const val CONSUMED = "CONSUMED"
}
