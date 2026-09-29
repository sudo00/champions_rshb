package com.wineapp.domain.model

data class FavoriteItem(
    val wine: Wine,
    val kind: String,
    /** Когда вино добавили в избранное (мс) — для сортировки и групп по месяцам. */
    val addedAt: Long = 0L
)
