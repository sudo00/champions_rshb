package com.wineapp.presentation.savedscans

import com.wineapp.domain.model.SavedScan
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.ui.WineListSort

sealed interface SavedScansState : BaseState {
    data object Loading : SavedScansState
    data class Success(
        val scans: List<SavedScan>,
        val sort: WineListSort = WineListSort.NEWEST,
        val styleFilter: String? = null,
        val query: String = "",
        val styles: List<String> = emptyList(),
        /** ID вин в избранном («Понравилось») — для сердечка на карточке. */
        val likedIds: Set<String> = emptySet(),
        /** Сканов нет совсем (а не из-за фильтров). */
        val isHistoryEmpty: Boolean = scans.isEmpty()
    ) : SavedScansState
    data class Error(val message: String) : SavedScansState
}

sealed interface SavedScansIntent : BaseIntent {
    data object LoadScans : SavedScansIntent
    data class DeleteScan(val id: String) : SavedScansIntent
    data class SetSort(val sort: WineListSort) : SavedScansIntent
    data class SetStyleFilter(val style: String?) : SavedScansIntent
    data class SetQuery(val query: String) : SavedScansIntent
    data class ToggleFavorite(val wineId: String) : SavedScansIntent
}
