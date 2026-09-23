package com.wineapp.presentation.favorites

import com.wineapp.domain.model.FavoriteItem
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface FavoritesState : BaseState {
    data object Loading : FavoritesState
    data class Success(
        val items: List<FavoriteItem>,
        val filter: String? = null
    ) : FavoritesState
    data class Error(val message: String) : FavoritesState
}

sealed interface FavoritesIntent : BaseIntent {
    data object LoadFavorites : FavoritesIntent
    data class SetFilter(val kind: String?) : FavoritesIntent
    data class RemoveFavorite(val wineId: String) : FavoritesIntent
    data class SetKind(val wineId: String, val kind: String) : FavoritesIntent
}
