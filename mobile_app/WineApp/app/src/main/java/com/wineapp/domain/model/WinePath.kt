package com.wineapp.domain.model

data class TerritoryProgress(
    val territoryId: String,
    val name: String,
    val anchorX: Float,
    val anchorY: Float,
    val triedWines: Int,
    val totalWines: Int,
    val unlocked: Boolean,
    val locked: Boolean,
    /** Несколько названий опробованных вин для карточки выбранного региона. */
    val sampleNames: List<String> = emptyList()
)

data class BadgeUi(
    val code: String,
    val title: String,
    val description: String,
    val points: Int,
    val earned: Boolean
)

data class WinePathSummary(
    val totalPoints: Int,
    val level: Int,
    val levelProgress: Float,
    val territoriesOpened: Int,
    val territoriesTotal: Int,
    val scansCount: Int
)
