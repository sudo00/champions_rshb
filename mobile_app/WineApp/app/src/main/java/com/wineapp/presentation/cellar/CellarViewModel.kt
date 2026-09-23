package com.wineapp.presentation.cellar

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

    override fun getInitialState(): CellarState = CellarState.Loading

    init {
        reduce(CellarIntent.LoadCellar)
    }

    override fun reduce(intent: CellarIntent) {
        when (intent) {
            is CellarIntent.LoadCellar -> loadCellar()
            is CellarIntent.SetFilter -> applyFilter(intent.status)
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
                updateState(CellarState.Error(e.message ?: "Не удалось загрузить погреб"))
            }
        }
    }

    private fun applyFilter(status: String?) {
        currentFilter = status
        emitFiltered()
    }

    private fun emitFiltered() {
        val filtered = if (currentFilter == null) allItems else allItems.filter { it.status == currentFilter }
        updateState(CellarState.Success(filtered, currentFilter))
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
