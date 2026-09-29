package com.wineapp.presentation.roulette

import com.wineapp.domain.model.RouletteSource
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface WineRouletteState : BaseState {
    data object Loading : WineRouletteState

    data class Success(
        val source: RouletteSource,
        val pool: List<Wine>,
        /**
         * Содержимое секторов барабана (ровно [ROULETTE_SECTOR_COUNT] или пусто).
         * Собирается один раз на пул и не меняется во время вращения.
         */
        val sectors: List<Wine> = emptyList(),
        val isSpinning: Boolean = false,
        val result: Wine? = null,
        /** id вин, отмеченных «Понравилось» — для сердечка в карточке. */
        val likedIds: Set<String> = emptySet()
    ) : WineRouletteState {
        val canSpin: Boolean get() = sectors.isNotEmpty() && !isSpinning
    }

    data class Error(val message: String) : WineRouletteState
}

sealed interface WineRouletteIntent : BaseIntent {
    data object Load : WineRouletteIntent
    data class SetSource(val source: RouletteSource) : WineRouletteIntent
    data object Spin : WineRouletteIntent
    /** Барабан довернулся — показываем карточку. */
    data class Settle(val wine: Wine) : WineRouletteIntent
    data class ToggleFavorite(val wineId: String) : WineRouletteIntent
}

/** Одноразовый сигнал: барабан должен остановиться на секторе [sectorIndex]. */
sealed interface WineRouletteEffect {
    data class Roll(val sectorIndex: Int, val winner: Wine) : WineRouletteEffect
}

/** Секторов в барабане фиксировано: 360° / 17 ≈ 21.18°. */
const val ROULETTE_SECTOR_COUNT = 17

/**
 * Раскладка вин по секторам: меньше 17 — идут по кругу с повторами,
 * больше — 17 случайных без повторов. Пустой пул — пустой барабан.
 */
fun buildRouletteSectors(pool: List<Wine>): List<Wine> {
    if (pool.isEmpty()) return emptyList()
    val shuffled = pool.shuffled()
    return if (shuffled.size >= ROULETTE_SECTOR_COUNT) {
        shuffled.take(ROULETTE_SECTOR_COUNT)
    } else {
        List(ROULETTE_SECTOR_COUNT) { shuffled[it % shuffled.size] }
    }
}
