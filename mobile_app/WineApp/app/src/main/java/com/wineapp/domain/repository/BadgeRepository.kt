package com.wineapp.domain.repository

import com.wineapp.data.game.BadgeDef
import com.wineapp.data.local.UserBadgeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

data class EarnedBadge(
    val def: BadgeDef,
    val earnedAt: Long = System.currentTimeMillis()
)

/** Что получил пользователь за скан — для уведомлений поверх приложения. */
sealed interface WinePathReward {
    /** Очки за новое (ещё не сканированное) вино. */
    data class ScanPoints(val points: Int, val wineName: String) : WinePathReward

    data class Badge(val badge: EarnedBadge) : WinePathReward

    /**
     * Новый уровень по сумме очков. [pointsToNext] — сколько осталось до следующего,
     * null на максимальном уровне.
     */
    data class LevelUp(val level: Int, val pointsToNext: Int?) : WinePathReward
}

interface BadgeRepository {
    /**
     * Наградить за сохранённый скан. Возвращает только что полученные бейджи.
     * Территория не передаётся: awardForScan сам считает освоенные вины по истории сканов,
     * поэтому ступени «Знаток» и «Легенда» тоже срабатывают без лишних аргументов.
     */
    suspend fun awardForScan(scanId: String): List<EarnedBadge>

    fun getBadges(): Flow<List<UserBadgeEntity>>
    fun getTotalPoints(): Flow<Int>

    /**
     * Трансляция свежих наград для UI сразу после скана: сначала очки за новое вино,
     * затем бейджи — в порядке, в котором их стоит показывать.
     */
    val freshRewards: SharedFlow<List<WinePathReward>>
}
