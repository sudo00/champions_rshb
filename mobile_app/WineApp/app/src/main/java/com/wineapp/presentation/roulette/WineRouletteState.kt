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
        val isSpinning: Boolean = false,
        val result: Wine? = null,
        /** id вин, отмеченных «Понравилось» — для сердечка в карточке. */
        val likedIds: Set<String> = emptySet()
    ) : WineRouletteState {
        val canSpin: Boolean get() = pool.isNotEmpty() && !isSpinning
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

/** Одноразовый сигнал: барабан должен прокрутиться до этого вина. */
sealed interface WineRouletteEffect {
    data class Roll(val winner: Wine) : WineRouletteEffect
}
