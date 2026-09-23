package com.wineapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cellar")
data class CellarEntity(
    @PrimaryKey
    val wineId: String,
    val quantity: Int = 1,
    val status: String = CellarStatus.HOME,
    val note: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Статусы записи погреба. Хранятся как String чтобы не усложнять миграции. */
object CellarStatus {
    const val HOME = "HOME" // дома / в наличии
    const val WISH = "WISH" // хочу купить
    const val CONSUMED = "CONSUMED" // выпито
}
