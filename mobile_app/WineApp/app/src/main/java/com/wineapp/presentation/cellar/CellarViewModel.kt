package com.wineapp.presentation.cellar

import com.wineapp.presentation.common.ui.WineListSort
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.CellarItem
import com.wineapp.domain.usecase.GetCellarUseCase
import com.wineapp.domain.usecase.RemoveFromCellarUseCase
import com.wineapp.domain.usecase.SetCellarQuantityUseCase
import com.wineapp.domain.usecase.SetCellarStatusUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CellarViewModel @Inject constructor(
    private val getCellarUseCase: GetCellarUseCase,
    private val setCellarQuantityUseCase: SetCellarQuantityUseCase,
    private val setCellarStatusUseCase: SetCellarStatusUseCase,
    private val removeFromCellarUseCase: RemoveFromCellarUseCase
) : BaseViewModel<CellarState, CellarIntent>() {

    private var allItems: List<CellarItem> = emptyList()
    private var currentFilter: String? = null
    private var currentSort: WineListSort = WineListSort.NEWEST
    private var currentStyle: String? = null
    private var currentQuery: String = ""

    override fun getInitialState(): CellarState = CellarState.Loading

    init {
        reduce(CellarIntent.LoadCellar)
    }

    override fun reduce(intent: CellarIntent) {
        when (intent) {
            is CellarIntent.LoadCellar -> loadCellar()
            is CellarIntent.SetFilter -> applyFilter(intent.status)
            is CellarIntent.SetSort -> { currentSort = intent.sort; emitFiltered() }
            is CellarIntent.SetStyleFilter -> { currentStyle = intent.style; emitFiltered() }
            is CellarIntent.SetQuery -> { currentQuery = intent.query; emitFiltered() }
            is CellarIntent.Increment -> changeQuantity(intent.wineId, +1)
            is CellarIntent.Decrement -> changeQuantity(intent.wineId, -1)
            is CellarIntent.SetStatus -> setStatus(intent.wineId, intent.status)
            is CellarIntent.Remove -> remove(intent.wineId)
        }
    }

    private fun loadCellar() {
        viewModelScope.launch {
            try {
                getCellarUseCase().collect { items ->
                    allItems = items
                    emitFiltered()
                }
            } catch (e: Exception) {
                Log.e("CellarVM", "Load cellar failed", e)
                updateState(CellarState.Error(e.message ?: "Не удалось загрузить коллекцию"))
            }
        }
    }

    private fun applyFilter(status: String?) {
        currentFilter = status
        emitFiltered()
    }

    private fun emitFiltered() {
        val query = currentQuery.trim()
        val filtered = allItems
            .filter { currentFilter == null || it.status == currentFilter }
            .filter { currentStyle == null || it.wine.style == currentStyle }
            .filter {
                query.isEmpty() ||
                    it.wine.name.contains(query, ignoreCase = true) ||
                    it.wine.winery?.contains(query, ignoreCase = true) == true
            }
        val sorted = when (currentSort) {
            WineListSort.NEWEST -> filtered.sortedByDescending { it.updatedAt }
            WineListSort.OLDEST -> filtered.sortedBy { it.updatedAt }
            WineListSort.RATING -> filtered.sortedByDescending { it.wine.rating ?: -1f }
            WineListSort.NAME -> filtered.sortedBy { it.wine.name.lowercase() }
        }
        updateState(
            CellarState.Success(
                items = sorted,
                filter = currentFilter,
                sort = currentSort,
                styleFilter = currentStyle,
                query = currentQuery,
                styles = allItems.mapNotNull { it.wine.style }.distinct().sorted(),
                stats = computeStats(allItems),
                isCollectionEmpty = allItems.isEmpty()
            )
        )
    }

    private fun computeStats(items: List<CellarItem>): CellarStats {
        val ratings = items.mapNotNull { it.wine.rating }
        return CellarStats(
            totalBottles = items.sumOf { it.quantity },
            averageRating = if (ratings.isEmpty()) null else ratings.average().toFloat(),
            favoriteStyle = items.mapNotNull { it.wine.style }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key
        )
    }

    private fun changeQuantity(wineId: String, delta: Int) {
        viewModelScope.launch {
            val current = allItems.find { it.wine.id == wineId } ?: return@launch
            // Переходы В наличии <-> Выпито по количеству — внутри репозитория.
            // 0 бутылок = «выпито» (запись остаётся), снова >0 = «в наличии».
            setCellarQuantityUseCase(wineId, current.quantity + delta)
                .onFailure { e -> Log.e("CellarVM", "Set quantity failed", e) }
        }
    }

    private fun setStatus(wineId: String, status: String) {
        viewModelScope.launch {
            setCellarStatusUseCase(wineId, status)
                .onFailure { e -> Log.e("CellarVM", "Set status failed", e) }
        }
    }

    private fun remove(wineId: String) {
        viewModelScope.launch {
            removeFromCellarUseCase(wineId)
                .onFailure { e -> Log.e("CellarVM", "Remove failed", e) }
        }
    }
}
