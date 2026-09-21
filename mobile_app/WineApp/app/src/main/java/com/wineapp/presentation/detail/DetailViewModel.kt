package com.wineapp.presentation.detail

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.IsFavoriteUseCase
import com.wineapp.domain.usecase.SaveScanUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val saveScanUseCase: SaveScanUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase
) : BaseViewModel<DetailState, DetailIntent>() {

    private var currentWineId = ""
    private var currentPhotoPath: String? = null
    private var currentConfidence: Float = 1.0f

    override fun getInitialState(): DetailState = DetailState.Loading("")

    override fun reduce(intent: DetailIntent) {
        when (intent) {
            is DetailIntent.LoadDetail -> handleLoad(intent.wineId, intent.photoPath, intent.confidence)
            is DetailIntent.Retry -> handleLoad(currentWineId, currentPhotoPath, currentConfidence)
            is DetailIntent.AddToHistory -> handleAddToHistory()
            is DetailIntent.ToggleFavorite -> handleToggleFavorite()
        }
    }

    private fun handleLoad(wineId: String, photoPath: String?, confidence: Float) {
        currentWineId = wineId
        currentPhotoPath = photoPath
        currentConfidence = confidence
        updateState(DetailState.Loading(wineId))
        viewModelScope.launch {
            val result = getWineDetailsUseCase(wineId)
            result.onSuccess { wine ->
                val isFavorite = isFavoriteUseCase(wineId).first()
                updateState(DetailState.Success(wine, photoPath, confidence, isFavorite))
            }.onFailure { error ->
                Log.e("DetailViewModel", "Load detail failed", error)
                updateState(DetailState.Error(error.message ?: "Не удалось загрузить данные о вине"))
            }
        }
    }

    private fun handleToggleFavorite() {
        val state = _state.value
        if (state is DetailState.Success) {
            viewModelScope.launch {
                toggleFavoriteUseCase(state.wine.id, state.isFavorite)
                    .onSuccess {
                        updateState(state.copy(isFavorite = !state.isFavorite))
                    }
                    .onFailure { error ->
                        Log.e("DetailViewModel", "Toggle favorite failed", error)
                    }
            }
        }
    }

    private fun handleAddToHistory() {
        val state = _state.value
        if (state is DetailState.Success) {
            viewModelScope.launch {
                val scan = SavedScan(
                    id = UUID.randomUUID().toString(),
                    wine = state.wine,
                    labelPhotoPath = currentPhotoPath,
                    confidence = state.confidence,
                    conversation = emptyList(),
                    scannedAt = System.currentTimeMillis()
                )
                saveScanUseCase(scan)
                    .onFailure { error ->
                        Log.e("DetailViewModel", "Save scan failed", error)
                    }
            }
        }
    }
}
