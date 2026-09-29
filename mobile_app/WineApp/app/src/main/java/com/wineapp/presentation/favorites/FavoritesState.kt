package com.wineapp.presentation.favorites

import com.wineapp.domain.model.FavoriteItem
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.ui.WineListSort

sealed interface FavoritesState : BaseState {
    data object Loading : FavoritesState
    data class Success(
        val items: List<FavoriteItem>,
        val filter: String? = null,
        val sort: WineListSort = WineListSort.NEWEST,
        val styleFilter: String? = null,
        val query: String = "",
        val styles: List<String> = emptyList(),
        /** Избранное пусто целиком (а не из-за фильтров). */
        val isFavoritesEmpty: Boolean = items.isEmpty()
    ) : FavoritesState
    data class Error(val message: String) : FavoritesState
}

sealed interface FavoritesIntent : BaseIntent {
    data object LoadFavorites : FavoritesIntent
    data class SetFilter(val kind: String?) : FavoritesIntent
    data class SetSort(val sort: WineListSort) : FavoritesIntent
    data class SetStyleFilter(val style: String?) : FavoritesIntent
    data class SetQuery(val query: String) : FavoritesIntent
    data class RemoveFavorite(val wineId: String) : FavoritesIntent
    data class SetKind(val wineId: String, val kind: String) : FavoritesIntent
}
