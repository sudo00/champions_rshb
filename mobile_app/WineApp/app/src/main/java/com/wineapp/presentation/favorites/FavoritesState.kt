package com.wineapp.presentation.favorites

import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseIntent
import com.wineapp.presentation.common.BaseState

sealed interface FavoritesState : BaseState {
    data object Loading : FavoritesState
    data class Success(val wines: List<Wine>) : FavoritesState
    data class Error(val message: String) : FavoritesState
}

sealed interface FavoritesIntent : BaseIntent {
    data object LoadFavorites : FavoritesIntent
    data class RemoveFavorite(val wineId: String) : FavoritesIntent
}
