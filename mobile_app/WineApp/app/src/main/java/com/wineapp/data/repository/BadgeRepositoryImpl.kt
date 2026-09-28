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

    private val _freshBadges = MutableSharedFlow<List<EarnedBadge>>(extraBufferCapacity = 1)
    override val freshBadges: SharedFlow<List<EarnedBadge>> = _freshBadges.asSharedFlow()

    override suspend fun awardForScan(scanId: String): List<EarnedBadge> {
        return withContext(Dispatchers.IO) {
            try {
                val fresh = mutableListOf<EarnedBadge>()

                // Очки за сам скан.
                gameDao.insertPoints(PointsEntry(delta = BadgeDefs.POINTS_SCAN, reason = "scan", refId = scanId))

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
                val scans = scanHistoryDao.getScansCount()
                if (openedCount >= 3) awardBadge(BadgeDefs.EXPLORER_3)?.let { fresh.add(it) }
                if (openedCount >= 5) awardBadge(BadgeDefs.EXPLORER_5)?.let { fresh.add(it) }
                if (openedCount >= 8) awardBadge(BadgeDefs.EXPLORER_8)?.let { fresh.add(it) }
                if (scans >= 10) awardBadge(BadgeDefs.TASTER_10)?.let { fresh.add(it) }
                if (scans >= 50) awardBadge(BadgeDefs.TASTER_50)?.let { fresh.add(it) }

                if (fresh.isNotEmpty()) _freshBadges.tryEmit(fresh)
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
}
