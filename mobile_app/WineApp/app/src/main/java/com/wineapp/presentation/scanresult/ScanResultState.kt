package com.wineapp.presentation.scanresult

import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface ScanResultState : BaseState {
    data object Loading : ScanResultState
    data class Success(val mainWine: Wine, val alternatives: List<Wine>) : ScanResultState
    data class Error(val message: String) : ScanResultState
}

sealed interface ScanResultIntent : BaseIntent {
    data class LoadWines(val mainWineId: String, val altIds: String) : ScanResultIntent
}
