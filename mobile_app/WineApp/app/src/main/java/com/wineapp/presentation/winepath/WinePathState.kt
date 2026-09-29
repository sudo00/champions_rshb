package com.wineapp.presentation.winepath

import com.wineapp.domain.model.BadgeUi
import com.wineapp.domain.model.TerritoryProgress
import com.wineapp.domain.model.WinePathSummary
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface WinePathState : BaseState {
    data object Loading : WinePathState
    data class Success(
        val summary: WinePathSummary,
        val territories: List<TerritoryProgress>,
        val badges: List<BadgeUi>
    ) : WinePathState
    data class Error(val message: String) : WinePathState
}

sealed interface WinePathIntent : BaseIntent {
    data object LoadPath : WinePathIntent
}
