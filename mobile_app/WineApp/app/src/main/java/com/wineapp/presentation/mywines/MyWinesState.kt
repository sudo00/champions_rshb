package com.wineapp.presentation.mywines

import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

data class MyWinesState(
    val favorites: List<Wine> = emptyList(),
    val collection: List<Wine> = emptyList(),
    /** Последние сканы — на карточках фото пользователя, а не из каталога. */
    val scans: List<SavedScan> = emptyList(),
    val likedIds: Set<String> = emptySet(),
    val isLoading: Boolean = true
) : BaseState

sealed interface MyWinesIntent : BaseIntent {
    data object Load : MyWinesIntent
    data class ToggleFavorite(val wineId: String) : MyWinesIntent
}
