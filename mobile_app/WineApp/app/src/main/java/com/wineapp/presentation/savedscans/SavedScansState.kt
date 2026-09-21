package com.wineapp.presentation.savedscans

import com.wineapp.domain.model.SavedScan
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface SavedScansState : BaseState {
    data object Loading : SavedScansState
    data class Success(val scans: List<SavedScan>) : SavedScansState
    data class Error(val message: String) : SavedScansState
}

sealed interface SavedScansIntent : BaseIntent {
    data object LoadScans : SavedScansIntent
    data class DeleteScan(val id: String) : SavedScansIntent
}
