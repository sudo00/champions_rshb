package com.wineapp.presentation.mywines

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.usecase.GetCellarUseCase
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.GetSavedScansUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SECTION_LIMIT = 3

@HiltViewModel
class MyWinesViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getCellarUseCase: GetCellarUseCase,
    private val getSavedScansUseCase: GetSavedScansUseCase,
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : BaseViewModel<MyWinesState, MyWinesIntent>() {

    override fun getInitialState(): MyWinesState = MyWinesState()

    init {
        handleLoad()
    }

    override fun reduce(intent: MyWinesIntent) {
        when (intent) {
            is MyWinesIntent.Load -> handleLoad()
            is MyWinesIntent.ToggleFavorite -> handleToggleFavorite(intent.wineId)
        }
    }

    private fun handleLoad() {
        viewModelScope.launch {
            combine(
                getFavoritesUseCase(),
                getCellarUseCase(),
                getSavedScansUseCase()
            ) { favorites, cellar, scans -> Triple(favorites, cellar, scans) }
                .collectLatest { (favoriteEntities, cellarItems, savedScans) ->
                    val likedIds = favoriteEntities
                        .filter { it.kind == FavoriteKind.LIKED }
                        .map { it.wineId }
                        .toSet()
                    // Избранное хранит только ID — догружаем детали (последние сверху).
                    val favoriteWines = favoriteEntities
                        .sortedByDescending { it.addedAt }
                        .take(SECTION_LIMIT)
                        .map { entity ->
                            async { getWineDetailsUseCase(entity.wineId).getOrNull() }
                        }
                        .awaitAll()
                        .filterNotNull()
                    val collection = cellarItems
                        .sortedByDescending { it.updatedAt }
                        .take(SECTION_LIMIT)
                        .map { it.wine }
                    val scans = savedScans
                        .sortedByDescending { it.scannedAt }
                        .take(SECTION_LIMIT)
                        .map { it.wine }
                    updateState(
                        MyWinesState(
                            favorites = favoriteWines,
                            collection = collection,
                            scans = scans,
                            likedIds = likedIds,
                            isLoading = false
                        )
                    )
                }
        }
    }

    private fun handleToggleFavorite(wineId: String) {
        viewModelScope.launch {
            val isFavorite = wineId in _state.value.likedIds
            toggleFavoriteUseCase(wineId, isFavorite, FavoriteKind.LIKED)
                .onFailure { error ->
                    Log.e("MyWinesViewModel", "Toggle favorite failed", error)
                }
        }
    }
}
