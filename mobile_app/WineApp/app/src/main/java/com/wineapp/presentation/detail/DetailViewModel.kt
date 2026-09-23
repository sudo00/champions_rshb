package com.wineapp.presentation.detail

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.usecase.AddToCellarUseCase
import com.wineapp.domain.usecase.GetCellarEntryUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.IsFavoriteUseCase
import com.wineapp.domain.usecase.RemoveFromCellarUseCase
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
    private val isFavoriteUseCase: IsFavoriteUseCase,
    private val addToCellarUseCase: AddToCellarUseCase,
    private val removeFromCellarUseCase: RemoveFromCellarUseCase,
    private val getCellarEntryUseCase: GetCellarEntryUseCase
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
            is DetailIntent.ToggleCellar -> handleToggleCellar()
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
                val cellarEntry = getCellarEntryUseCase(wineId).first()
                updateState(
                    DetailState.Success(
                        wine,
                        photoPath,
                        confidence,
                        isFavorite,
                        isInCellar = cellarEntry != null,
                        cellarQuantity = cellarEntry?.quantity ?: 0
                    )
                )
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

    private fun handleToggleCellar() {
        val state = _state.value
        if (state is DetailState.Success) {
            viewModelScope.launch {
                if (state.isInCellar) {
                    removeFromCellarUseCase(state.wine.id)
                        .onSuccess {
                            updateState(state.copy(isInCellar = false, cellarQuantity = 0))
                        }
                        .onFailure { error ->
                            Log.e("DetailViewModel", "Remove from cellar failed", error)
                        }
                } else {
                    addToCellarUseCase(state.wine.id, 1)
                        .onSuccess {
                            updateState(state.copy(isInCellar = true, cellarQuantity = 1))
                        }
                        .onFailure { error ->
                            Log.e("DetailViewModel", "Add to cellar failed", error)
                        }
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
