package com.wineapp.presentation.favorites

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.FavoriteItem
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.SetFavoriteKindUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val setFavoriteKindUseCase: SetFavoriteKindUseCase
) : BaseViewModel<FavoritesState, FavoritesIntent>() {

    private var allItems: List<FavoriteItem> = emptyList()
    private var currentFilter: String? = null

    override fun getInitialState(): FavoritesState = FavoritesState.Loading

    init {
        reduce(FavoritesIntent.LoadFavorites)
    }

    override fun reduce(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.LoadFavorites -> loadFavorites()
            is FavoritesIntent.SetFilter -> applyFilter(intent.kind)
            is FavoritesIntent.RemoveFavorite -> removeFavorite(intent.wineId)
            is FavoritesIntent.SetKind -> setKind(intent.wineId, intent.kind)
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            getFavoritesUseCase().collect { entities ->
                allItems = entities.mapNotNull { entity ->
                    getWineDetailsUseCase(entity.wineId).getOrNull()
                        ?.let { wine -> FavoriteItem(wine = wine, kind = entity.kind) }
                }
                emitFiltered()
            }
        }
    }

    private fun applyFilter(kind: String?) {
        currentFilter = kind
        emitFiltered()
    }

    private fun emitFiltered() {
        val filtered = if (currentFilter == null) allItems
        else allItems.filter { it.kind == currentFilter }
        updateState(FavoritesState.Success(filtered, currentFilter))
    }

    private fun removeFavorite(wineId: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(wineId, true)
                .onFailure { e ->
                    Log.e("FavoritesVM", "Remove favorite failed", e)
                }
        }
    }

    private fun setKind(wineId: String, kind: String) {
        viewModelScope.launch {
            setFavoriteKindUseCase(wineId, kind)
                .onFailure { e ->
                    Log.e("FavoritesVM", "Set kind failed", e)
                }
        }
    }
}
