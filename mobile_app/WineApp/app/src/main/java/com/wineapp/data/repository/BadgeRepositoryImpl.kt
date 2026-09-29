package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.game.BadgeDefs
import com.wineapp.data.game.TerritoryTier
import com.wineapp.data.local.GameDao
import com.wineapp.data.local.PointsEntry
import com.wineapp.data.local.ScanHistoryDao
import com.wineapp.data.local.TerritoryRegistry
import com.wineapp.data.local.UserBadgeEntity
import com.wineapp.domain.repository.BadgeRepository
import com.wineapp.domain.repository.EarnedBadge
import com.wineapp.domain.repository.WinePathReward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BadgeRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val scanHistoryDao: ScanHistoryDao
) : BadgeRepository {

    private val _freshRewards = MutableSharedFlow<List<WinePathReward>>(extraBufferCapacity = 4)
    override val freshRewards: SharedFlow<List<WinePathReward>> = _freshRewards.asSharedFlow()

    override suspend fun awardForScan(scanId: String): List<EarnedBadge> {
        return withContext(Dispatchers.IO) {
            try {
                val fresh = mutableListOf<EarnedBadge>()
                var scanPoints: WinePathReward.ScanPoints? = null
                val levelBefore = BadgeDefs.levelFor(gameDao.totalPoints()).level

                // Очки за скан — один раз на вино: повторный скан той же бутылки
                // (в том числе после удаления прошлого скана) очков не даёт.
                // refId = wineId, поэтому проверка идёт по журналу очков, а не по истории.
                val scan = scanHistoryDao.getScanById(scanId)
                val wineId = scan?.wineId?.takeIf { it.isNotBlank() }
                if (wineId != null && !gameDao.hasPoints(REASON_SCAN, wineId)) {
                    gameDao.insertPoints(PointsEntry(delta = BadgeDefs.POINTS_SCAN, reason = REASON_SCAN, refId = wineId))
                    scanPoints = WinePathReward.ScanPoints(BadgeDefs.POINTS_SCAN, scan.wineName)
                }

                // Первый скан.
                awardBadge(BadgeDefs.FIRST_SCAN)?.let { fresh.add(it) }

                // Сколько разных вин освоено в каждой открытой территории.
                // Считаем из БД, а не из аргумента: так же, как считает шторка,
                // включая нормализацию region у сканов, сделанных до territoryId.
                val scannedByTerritory: Map<String, Int> = scanHistoryDao.getTerritoryWineRows()
                    .groupBy { row -> row.territoryId ?: TerritoryRegistry.normalize(row.region) }
                    .mapNotNull { (id, rows) ->
                        val territory = id?.let { TerritoryRegistry.byId[it] } ?: return@mapNotNull null
                        if (territory.locked || territory.totalWines <= 0) return@mapNotNull null
                        territory.id to rows.map { row -> row.wineId }.toSet().size
                    }
                    .toMap()

                // Ступени территории: первое вино → половина каталога → весь каталог.
                // awardBadge идемпотентен, поэтому проверять «уже есть» заранее не нужно.
                scannedByTerritory.forEach { (id, scanned) ->
                    val totalWines = TerritoryRegistry.byId[id]?.totalWines ?: return@forEach
                    TerritoryTier.entries.forEach { tier ->
                        if (scanned >= tier.requiredWines(totalWines)) {
                            awardBadge(BadgeDefs.codeFor(tier, id))?.let { fresh.add(it) }
                        }
                    }
                }

                // Пороги по территориям и сканам.
                val openedCount = scannedByTerritory.size
                // «Отсканируйте 10 вин» — разных вин, а не сканов.
                val scannedWines = scanHistoryDao.getScannedWinesCount()
                if (openedCount >= 3) awardBadge(BadgeDefs.EXPLORER_3)?.let { fresh.add(it) }
                if (openedCount >= 5) awardBadge(BadgeDefs.EXPLORER_5)?.let { fresh.add(it) }
                if (openedCount >= BadgeDefs.OPENABLE_TERRITORIES) awardBadge(BadgeDefs.EXPLORER_8)?.let { fresh.add(it) }
                if (scannedWines >= 10) awardBadge(BadgeDefs.TASTER_10)?.let { fresh.add(it) }
                if (scannedWines >= 50) awardBadge(BadgeDefs.TASTER_50)?.let { fresh.add(it) }

                // Уровень — итог всех начислений за скан, поэтому показывается последним.
                // За один скан можно перескочить несколько уровней — сообщаем о достигнутом.
                val totalAfter = gameDao.totalPoints()
                val levelAfter = BadgeDefs.levelFor(totalAfter)
                val levelUp = if (levelAfter.level > levelBefore) {
                    WinePathReward.LevelUp(
                        level = levelAfter.level,
                        pointsToNext = (levelAfter.pointsTo - totalAfter)
                            .takeIf { levelAfter.pointsTo > levelAfter.pointsFrom }
                    )
                } else null

                val rewards = listOfNotNull(scanPoints) +
                    fresh.map { WinePathReward.Badge(it) } +
                    listOfNotNull(levelUp)
                if (rewards.isNotEmpty()) _freshRewards.tryEmit(rewards)
                fresh
            } catch (e: Exception) {
                Log.e("BadgeRepo", "awardForScan failed", e)
                emptyList()
            }
        }
    }

    /** Возвращает EarnedBadge если бейдж реально новый, иначе null (идемпотентно). */
    private suspend fun awardBadge(code: String): EarnedBadge? {
        val def = BadgeDefs.byCode[code] ?: return null
        val rowId = gameDao.insertBadgeIgnore(UserBadgeEntity(code = code))
        if (rowId == -1L) return null // уже был
        gameDao.insertPoints(PointsEntry(delta = def.points, reason = "badge", refId = code))
        return EarnedBadge(def)
    }

    override fun getBadges(): Flow<List<UserBadgeEntity>> = gameDao.getBadges()

    override fun getTotalPoints(): Flow<Int> = gameDao.getTotalPoints()

    private companion object {
        const val REASON_SCAN = "scan"
    }
}
