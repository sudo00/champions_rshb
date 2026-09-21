package com.wineapp.presentation.savedscans

import com.wineapp.domain.model.SavedScan
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface SavedScanDetailState : BaseState {
    data object Loading : SavedScanDetailState
    data class Success(val scan: SavedScan) : SavedScanDetailState
    data class Error(val message: String) : SavedScanDetailState
}

sealed interface SavedScanDetailIntent : BaseIntent {
    data class LoadScan(val scanId: String) : SavedScanDetailIntent
}
