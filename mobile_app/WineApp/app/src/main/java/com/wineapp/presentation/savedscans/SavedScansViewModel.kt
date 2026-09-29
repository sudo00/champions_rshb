package com.wineapp.presentation.savedscans

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.usecase.DeleteSavedScanUseCase
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.GetSavedScansUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import com.wineapp.presentation.common.ui.WineListSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedScansViewModel @Inject constructor(
    private val getSavedScansUseCase: GetSavedScansUseCase,
    private val deleteSavedScanUseCase: DeleteSavedScanUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : BaseViewModel<SavedScansState, SavedScansIntent>() {

    private var allScans: List<SavedScan> = emptyList()
    private var likedIds: Set<String> = emptySet()
    private var currentSort: WineListSort = WineListSort.NEWEST
    private var currentStyle: String? = null
    private var currentQuery: String = ""
    private var loadJob: Job? = null

    override fun getInitialState(): SavedScansState = SavedScansState.Loading

    override fun reduce(intent: SavedScansIntent) {
        when (intent) {
            is SavedScansIntent.LoadScans -> loadScans()
            is SavedScansIntent.DeleteScan -> deleteScan(intent.id)
            is SavedScansIntent.SetSort -> { currentSort = intent.sort; emitFiltered() }
            is SavedScansIntent.SetStyleFilter -> { currentStyle = intent.style; emitFiltered() }
            is SavedScansIntent.SetQuery -> { currentQuery = intent.query; emitFiltered() }
            is SavedScansIntent.ToggleFavorite -> toggleFavorite(intent.wineId)
        }
    }

    private fun loadScans() {
        // Экран шлёт LoadScans при каждом входе — не плодим параллельные подписки.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            combine(getSavedScansUseCase(), getFavoritesUseCase()) { scans, favorites ->
                scans to favorites
                    .filter { it.kind == FavoriteKind.LIKED }
                    .map { it.wineId }
                    .toSet()
            }.collect { (scans, liked) ->
                allScans = scans
                likedIds = liked
                emitFiltered()
            }
        }
    }

    private fun emitFiltered() {
        val query = currentQuery.trim()
        val filtered = allScans
            .filter { currentStyle == null || it.wine.style == currentStyle }
            .filter {
                query.isEmpty() ||
                    it.wine.name.contains(query, ignoreCase = true) ||
                    it.wine.winery?.contains(query, ignoreCase = true) == true
            }
        val sorted = when (currentSort) {
            WineListSort.NEWEST -> filtered.sortedByDescending { it.scannedAt }
            WineListSort.OLDEST -> filtered.sortedBy { it.scannedAt }
            WineListSort.RATING -> filtered.sortedByDescending { it.wine.rating ?: -1f }
            WineListSort.NAME -> filtered.sortedBy { it.wine.name.lowercase() }
        }
        updateState(
            SavedScansState.Success(
                scans = sorted,
                sort = currentSort,
                styleFilter = currentStyle,
                query = currentQuery,
                styles = allScans.mapNotNull { it.wine.style }.distinct().sorted(),
                likedIds = likedIds,
                isHistoryEmpty = allScans.isEmpty()
            )
        )
    }

    private fun toggleFavorite(wineId: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(wineId, wineId in likedIds, FavoriteKind.LIKED)
                .onFailure { e -> Log.e("SavedScansVM", "Toggle favorite failed", e) }
        }
    }

    private fun deleteScan(id: String) {
        viewModelScope.launch {
            deleteSavedScanUseCase(id)
                .onFailure { e ->
                    Log.e("SavedScansVM", "Delete failed", e)
                    updateState(SavedScansState.Error(e.message ?: "Ошибка удаления"))
                }
        }
    }
}
