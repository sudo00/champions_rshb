package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.game.BadgeDefs
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

    override suspend fun awardForScan(scanId: String, territoryId: String?): List<EarnedBadge> {
        return withContext(Dispatchers.IO) {
            try {
                val fresh = mutableListOf<EarnedBadge>()

                // Очки за сам скан.
                gameDao.insertPoints(PointsEntry(delta = BadgeDefs.POINTS_SCAN, reason = "scan", refId = scanId))

                // Первый скан.
                awardBadge(BadgeDefs.FIRST_SCAN)?.let { fresh.add(it) }

                // Новая территория: бейдж первопроходца (очки уже в номинале бейджа).
                if (territoryId != null && TerritoryRegistry.byId[territoryId]?.locked != true) {
                    val known = gameDao.getBadgeCodes().toSet()
                    val pioneerCode = BadgeDefs.pioneerCode(territoryId)
                    if (!known.contains(pioneerCode) && BadgeDefs.byCode.containsKey(pioneerCode)) {
                        awardBadge(pioneerCode)?.let { fresh.add(it) }
                    }
                }

                // Пороги по территориям и сканам.
                val territories = scanHistoryDao.getDistinctTerritoryIds()
                    .filter { TerritoryRegistry.byId[it]?.locked != true }
                val scans = scanHistoryDao.getScansCount()
                if (territories.size >= 3) awardBadge(BadgeDefs.EXPLORER_3)?.let { fresh.add(it) }
                if (territories.size >= 5) awardBadge(BadgeDefs.EXPLORER_5)?.let { fresh.add(it) }
                if (territories.size >= 8) awardBadge(BadgeDefs.EXPLORER_8)?.let { fresh.add(it) }
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
