package com.wineapp.presentation.cellar

import com.wineapp.presentation.common.ui.WineListSort
import com.wineapp.domain.model.CellarItem
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

/** Сводка по всей коллекции (без учёта фильтров). */
data class CellarStats(
    val totalBottles: Int = 0,
    val averageRating: Float? = null,
    val favoriteStyle: String? = null
)

sealed interface CellarState : BaseState {
    data object Loading : CellarState
    data class Success(
        val items: List<CellarItem>,
        val filter: String? = null,
        val sort: WineListSort = WineListSort.NEWEST,
        val styleFilter: String? = null,
        val query: String = "",
        val styles: List<String> = emptyList(),
        val stats: CellarStats = CellarStats(),
        /** Коллекция пуста целиком (а не из-за фильтров). */
        val isCollectionEmpty: Boolean = items.isEmpty()
    ) : CellarState
    data class Error(val message: String) : CellarState
}

sealed interface CellarIntent : BaseIntent {
    data object LoadCellar : CellarIntent
    data class SetFilter(val status: String?) : CellarIntent
    data class SetSort(val sort: WineListSort) : CellarIntent
    data class SetStyleFilter(val style: String?) : CellarIntent
    data class SetQuery(val query: String) : CellarIntent
    data class Increment(val wineId: String) : CellarIntent
    data class Decrement(val wineId: String) : CellarIntent
    data class SetStatus(val wineId: String, val status: String) : CellarIntent
    data class Remove(val wineId: String) : CellarIntent
}
