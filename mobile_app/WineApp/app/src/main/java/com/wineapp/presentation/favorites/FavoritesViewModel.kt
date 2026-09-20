package com.wineapp.presentation.favorites

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : BaseViewModel<FavoritesState, FavoritesIntent>() {

    override fun getInitialState(): FavoritesState = FavoritesState.Loading

    init {
        reduce(FavoritesIntent.LoadFavorites)
    }

    override fun reduce(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.LoadFavorites -> loadFavorites()
            is FavoritesIntent.RemoveFavorite -> removeFavorite(intent.wineId)
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            getFavoritesUseCase().collect { favoriteIds ->
                if (favoriteIds.isEmpty()) {
                    updateState(FavoritesState.Success(emptyList()))
                } else {
                    val wines = favoriteIds.mapNotNull { id ->
                        getWineDetailsUseCase(id).getOrNull()
                    }
                    updateState(FavoritesState.Success(wines))
                }
            }
        }
    }

    private fun removeFavorite(wineId: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(wineId, true)
                .onFailure { e ->
                    Log.e("FavoritesVM", "Remove favorite failed", e)
                }
        }
    }
}
