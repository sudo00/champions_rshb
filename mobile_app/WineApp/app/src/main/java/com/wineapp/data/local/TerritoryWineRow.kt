package com.wineapp.data.local

/**
 * Одна строка подтверждённого скана в виде, нужном для подсчёта освоенных
 * вин по территориям. territoryId может быть пустым у старых сканов —
 * тогда территорию восстанавливают через TerritoryRegistry.normalize(region).
 */
data class TerritoryWineRow(
    val territoryId: String?,
    val region: String?,
    val wineId: String
)
