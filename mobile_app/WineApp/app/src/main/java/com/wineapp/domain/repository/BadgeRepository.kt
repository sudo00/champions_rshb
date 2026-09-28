package com.wineapp.domain.repository

import com.wineapp.data.game.BadgeDef
import com.wineapp.data.local.UserBadgeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

data class EarnedBadge(
    val def: BadgeDef,
    val earnedAt: Long = System.currentTimeMillis()
)

interface BadgeRepository {
    /**
     * Наградить за сохранённый скан. Возвращает только что полученные бейджи (для селебрешна).
     * Территория не передаётся: awardForScan сам считает освоенные вины по истории сканов,
     * поэтому ступени «Знаток» и «Легенда» тоже срабатывают без лишних аргументов.
     */
    suspend fun awardForScan(scanId: String): List<EarnedBadge>

    fun getBadges(): Flow<List<UserBadgeEntity>>
    fun getTotalPoints(): Flow<Int>

    /** Трансляция свежих наград для UI (диалог/тост сразу после скана). */
    val freshBadges: SharedFlow<List<EarnedBadge>>
}
