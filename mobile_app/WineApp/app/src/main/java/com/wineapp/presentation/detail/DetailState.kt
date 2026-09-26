package com.wineapp.presentation.detail

import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface DetailState : BaseState {
    data class Loading(val wineId: String) : DetailState
    data class Success(val wine: Wine, val photoPath: String? = null, val confidence: Float = 1.0f, val isFavorite: Boolean = false, val isWished: Boolean = false, val isInCellar: Boolean = false, val cellarQuantity: Int = 0, val cellarStatus: String? = null, val similar: List<Wine> = emptyList(), val recognitionStatus: String? = null) : DetailState
    data class Error(val message: String) : DetailState
}

sealed interface DetailIntent : BaseIntent {
    data class LoadDetail(val wineId: String, val photoPath: String? = null, val confidence: Float = 1.0f, val recognitionStatus: String? = null) : DetailIntent
    data object Retry : DetailIntent
    data object ToggleFavorite : DetailIntent
    data object ToggleWish : DetailIntent
    data object ToggleCellar : DetailIntent
}