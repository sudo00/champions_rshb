package com.wineapp.presentation.favorites

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.FavoriteItem
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.SetFavoriteKindUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import com.wineapp.presentation.common.ui.WineListSort
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
    private var currentSort: WineListSort = WineListSort.NEWEST
    private var currentStyle: String? = null
    private var currentQuery: String = ""

    override fun getInitialState(): FavoritesState = FavoritesState.Loading

    init {
        reduce(FavoritesIntent.LoadFavorites)
    }

    override fun reduce(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.LoadFavorites -> loadFavorites()
            is FavoritesIntent.SetFilter -> applyFilter(intent.kind)
            is FavoritesIntent.SetSort -> { currentSort = intent.sort; emitFiltered() }
            is FavoritesIntent.SetStyleFilter -> { currentStyle = intent.style; emitFiltered() }
            is FavoritesIntent.SetQuery -> { currentQuery = intent.query; emitFiltered() }
            is FavoritesIntent.RemoveFavorite -> removeFavorite(intent.wineId)
            is FavoritesIntent.SetKind -> setKind(intent.wineId, intent.kind)
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            getFavoritesUseCase().collect { entities ->
                allItems = entities.mapNotNull { entity ->
                    getWineDetailsUseCase(entity.wineId).getOrNull()
                        ?.let { wine -> FavoriteItem(wine = wine, kind = entity.kind, addedAt = entity.addedAt) }
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
        val query = currentQuery.trim()
        val filtered = allItems
            .filter { currentFilter == null || it.kind == currentFilter }
            .filter { currentStyle == null || it.wine.style == currentStyle }
            .filter {
                query.isEmpty() ||
                    it.wine.name.contains(query, ignoreCase = true) ||
                    it.wine.winery?.contains(query, ignoreCase = true) == true
            }
        val sorted = when (currentSort) {
            WineListSort.NEWEST -> filtered.sortedByDescending { it.addedAt }
            WineListSort.OLDEST -> filtered.sortedBy { it.addedAt }
            WineListSort.RATING -> filtered.sortedByDescending { it.wine.rating ?: -1f }
            WineListSort.NAME -> filtered.sortedBy { it.wine.name.lowercase() }
        }
        updateState(
            FavoritesState.Success(
                items = sorted,
                filter = currentFilter,
                sort = currentSort,
                styleFilter = currentStyle,
                query = currentQuery,
                styles = allItems.mapNotNull { it.wine.style }.distinct().sorted(),
                isFavoritesEmpty = allItems.isEmpty()
            )
        )
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
