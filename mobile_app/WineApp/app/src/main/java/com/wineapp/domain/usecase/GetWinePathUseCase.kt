package com.wineapp.domain.usecase

import com.wineapp.data.game.BadgeDefs
import com.wineapp.data.local.ScanHistoryDao
import com.wineapp.data.local.TerritoryRegistry
import com.wineapp.domain.model.BadgeUi
import com.wineapp.domain.model.TerritoryProgress
import com.wineapp.domain.model.WinePathSummary
import com.wineapp.domain.repository.BadgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class WinePathData(
    val summary: WinePathSummary,
    val territories: List<TerritoryProgress>,
    val badges: List<BadgeUi>
)

class GetWinePathUseCase @Inject constructor(
    private val scanHistoryDao: ScanHistoryDao,
    private val badgeRepository: BadgeRepository
) {
    operator fun invoke(): Flow<WinePathData> {
        return combine(
            badgeRepository.getTotalPoints(),
            badgeRepository.getBadges(),
            scanHistoryDao.getAllScans()
        ) { points, badges, scans ->
            val earnedCodes = badges.map { it.code }.toSet()
            // territoryId из колонки; для старых сканов — нормализация сырого region на лету.
            val entitiesByTerritory: Map<String, List<com.wineapp.data.local.ScanHistoryEntity>> = scans
                .groupBy { entity ->
                    entity.territoryId ?: TerritoryRegistry.normalize(entity.region)
                }
                .filterKeys { it != null } as Map<String, List<com.wineapp.data.local.ScanHistoryEntity>>
            val wineIdsByTerritory: Map<String, Set<String>> = entitiesByTerritory
                .mapValues { (_, entities) -> entities.map { it.wineId }.toSet() }

            val territories = TerritoryRegistry.territories.map { territory ->
                val triedIds = if (territory.locked) emptySet()
                else wineIdsByTerritory[territory.id] ?: emptySet()
                val sampleNames = entitiesByTerritory[territory.id]
                    ?.distinctBy { it.wineId }
                    ?.take(3)
                    ?.map { it.wineName }
                    ?: emptyList()
                TerritoryProgress(
                    territoryId = territory.id,
                    name = territory.name,
                    anchorX = territory.anchorX,
                    anchorY = territory.anchorY,
                    triedWines = triedIds.size,
                    totalWines = territory.totalWines,
                    unlocked = !territory.locked && triedIds.isNotEmpty(),
                    locked = territory.locked,
                    sampleNames = sampleNames
                )
            }
            val openCount = territories.count { it.unlocked }
            val (level, levelProgress) = BadgeDefs.levelFor(points)
            val badgeUi = BadgeDefs.all.map { def ->
                BadgeUi(
                    code = def.code,
                    title = def.title,
                    description = def.description,
                    points = def.points,
                    earned = earnedCodes.contains(def.code)
                )
            }
            WinePathData(
                summary = WinePathSummary(
                    totalPoints = points,
                    level = level,
                    levelProgress = levelProgress,
                    territoriesOpened = openCount,
                    territoriesTotal = TerritoryRegistry.territories.count { !it.locked },
                    scansCount = scans.size
                ),
                territories = territories,
                badges = badgeUi
            )
        }
    }
}
