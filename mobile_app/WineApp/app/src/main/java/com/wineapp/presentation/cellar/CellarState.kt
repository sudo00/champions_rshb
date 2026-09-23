package com.wineapp.presentation.cellar

import com.wineapp.domain.model.CellarItem
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface CellarState : BaseState {
    data object Loading : CellarState
    data class Success(
        val items: List<CellarItem>,
        val filter: String? = null
    ) : CellarState
    data class Error(val message: String) : CellarState
}

sealed interface CellarIntent : BaseIntent {
    data object LoadCellar : CellarIntent
    data class SetFilter(val status: String?) : CellarIntent
    data class Increment(val wineId: String) : CellarIntent
    data class Decrement(val wineId: String) : CellarIntent
    data class SetStatus(val wineId: String, val status: String) : CellarIntent
    data class Remove(val wineId: String) : CellarIntent
}
