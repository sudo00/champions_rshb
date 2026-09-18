package com.wineapp.presentation.detail

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.SaveToHistoryUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val saveToHistoryUseCase: SaveToHistoryUseCase
) : BaseViewModel<DetailState, DetailIntent>() {

    private var currentWineId = ""

    override fun getInitialState(): DetailState = DetailState.Loading("")

    override fun reduce(intent: DetailIntent) {
        when (intent) {
            is DetailIntent.LoadDetail -> handleLoad(intent.wineId)
            is DetailIntent.Retry -> handleLoad(currentWineId)
            is DetailIntent.Share -> handleShare()
            is DetailIntent.AddToHistory -> handleAddToHistory()
        }
    }

    private fun handleLoad(wineId: String) {
        currentWineId = wineId
        updateState(DetailState.Loading(wineId))
        viewModelScope.launch {
            val result = getWineDetailsUseCase(wineId)
            result.onSuccess { wine ->
                updateState(DetailState.Success(wine))
            }.onFailure { error ->
                Log.e("DetailViewModel", "Load detail failed", error)
                updateState(DetailState.Error(error.message ?: "Не удалось загрузить данные о вине"))
            }
        }
    }

    private fun handleShare() {
        // TODO: Implement share functionality
    }

    private fun handleAddToHistory() {
        val state = _state.value
        if (state is DetailState.Success) {
            viewModelScope.launch {
                val result = saveToHistoryUseCase(state.wine)
                result.onFailure { error ->
                    Log.e("DetailViewModel", "Save to history failed", error)
                }
            }
        }
    }
}