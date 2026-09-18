package com.wineapp.presentation.detail

import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface DetailState : BaseState {
    data class Loading(val wineId: String) : DetailState
    data class Success(val wine: Wine) : DetailState
    data class Error(val message: String) : DetailState
}

sealed interface DetailIntent : BaseIntent {
    data class LoadDetail(val wineId: String) : DetailIntent
    data object Retry : DetailIntent
    data object Share : DetailIntent
    data object AddToHistory : DetailIntent
}