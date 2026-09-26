package com.wineapp.presentation.detail

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.AddToCellarUseCase
import com.wineapp.domain.usecase.GetCellarEntryUseCase
import com.wineapp.domain.usecase.GetSimilarWinesUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.IsFavoriteUseCase
import com.wineapp.domain.usecase.RemoveFromCellarUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase,
    private val addToCellarUseCase: AddToCellarUseCase,
    private val removeFromCellarUseCase: RemoveFromCellarUseCase,
    private val getCellarEntryUseCase: GetCellarEntryUseCase,
    private val getSimilarWinesUseCase: GetSimilarWinesUseCase
) : BaseViewModel<DetailState, DetailIntent>() {

    private var currentWineId = ""
    private var currentPhotoPath: String? = null
    private var currentConfidence: Float = 1.0f
    private var currentRecognitionStatus: String? = null

    override fun getInitialState(): DetailState = DetailState.Loading("")

    override fun reduce(intent: DetailIntent) {
        when (intent) {
            is DetailIntent.LoadDetail -> handleLoad(intent.wineId, intent.photoPath, intent.confidence, intent.recognitionStatus)
            is DetailIntent.Retry -> handleLoad(currentWineId, currentPhotoPath, currentConfidence, currentRecognitionStatus)
            is DetailIntent.ToggleFavorite -> handleToggleFavorite()
            is DetailIntent.ToggleWish -> handleToggleWish()
            is DetailIntent.ToggleCellar -> handleToggleCellar()
        }
    }

    private fun handleLoad(wineId: String, photoPath: String?, confidence: Float, recognitionStatus: String?) {
        currentWineId = wineId
        currentPhotoPath = photoPath
        currentConfidence = confidence
        currentRecognitionStatus = recognitionStatus
        updateState(DetailState.Loading(wineId))
        viewModelScope.launch {
            val result = getWineDetailsUseCase(wineId)
            result.onSuccess { wine ->
                val isFavorite = isFavoriteUseCase(wineId, com.wineapp.data.local.FavoriteKind.LIKED).first()
                val isWished = isFavoriteUseCase(wineId, com.wineapp.data.local.FavoriteKind.WISH).first()
                val cellarEntry = getCellarEntryUseCase(wineId).first()
                updateState(
                    DetailState.Success(
                        wine,
                        photoPath,
                        confidence,
                        isFavorite,
                        isWished = isWished,
                        isInCellar = cellarEntry != null,
                        cellarQuantity = cellarEntry?.quantity ?: 0,
                        cellarStatus = cellarEntry?.status,
                        recognitionStatus = recognitionStatus
                    )
                )
                // Похожие — отдельно, не блокируем основной контент; секция скрыта пока пусто.
                val similar = getSimilarWinesUseCase(wine).getOrNull().orEmpty()
                if (similar.isNotEmpty()) {
                    val s = _state.value
                    if (s is DetailState.Success && s.wine.id == wineId) {
                        updateState(s.copy(similar = similar))
                    }
                }
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
                toggleFavoriteUseCase(
                    state.wine.id,
                    state.isFavorite,
                    com.wineapp.data.local.FavoriteKind.LIKED
                )
                    .onSuccess {
                        updateState(state.copy(isFavorite = !state.isFavorite))
                    }
                    .onFailure { error ->
                        Log.e("DetailViewModel", "Toggle favorite failed", error)
                    }
            }
        }
    }

    private fun handleToggleWish() {
        val state = _state.value
        if (state is DetailState.Success) {
            viewModelScope.launch {
                toggleFavoriteUseCase(
                    state.wine.id,
                    state.isWished,
                    com.wineapp.data.local.FavoriteKind.WISH
                )
                    .onSuccess {
                        updateState(state.copy(isWished = !state.isWished))
                    }
                    .onFailure { error ->
                        Log.e("DetailViewModel", "Toggle wish failed", error)
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
                            updateState(state.copy(isInCellar = false, cellarQuantity = 0, cellarStatus = null))
                        }
                        .onFailure { error ->
                            Log.e("DetailViewModel", "Remove from cellar failed", error)
                        }
                } else {
                    addToCellarUseCase(state.wine.id, 1)
                        .onSuccess {
                            updateState(
                                state.copy(
                                    isInCellar = true,
                                    cellarQuantity = 1,
                                    cellarStatus = com.wineapp.data.local.CellarStatus.IN_STOCK
                                )
                            )
                        }
                        .onFailure { error ->
                            Log.e("DetailViewModel", "Add to cellar failed", error)
                        }
                }
            }
        }
    }
}
