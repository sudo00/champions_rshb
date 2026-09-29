package com.wineapp.presentation.roulette

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.model.RouletteSource
import com.wineapp.domain.model.Wine
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.GetRoulettePoolUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WineRouletteViewModel @Inject constructor(
    private val getRoulettePoolUseCase: GetRoulettePoolUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : BaseViewModel<WineRouletteState, WineRouletteIntent>() {

    private val _effects = MutableSharedFlow<WineRouletteEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<WineRouletteEffect> = _effects.asSharedFlow()

    /** Прошлый победитель исключается из пула, чтобы спины не повторялись. */
    private var previousWinnerId: String? = null

    /**
     * Последнее значение потока избранного. Поток может прийти, пока состояние
     * ещё Loading, поэтому кэшируем его и подставляем в новый Success.
     */
    private var latestLikedIds: Set<String> = emptySet()

    override fun getInitialState(): WineRouletteState = WineRouletteState.Loading

    init {
        reduce(WineRouletteIntent.Load)
        observeLiked()
    }

    override fun reduce(intent: WineRouletteIntent) {
        when (intent) {
            is WineRouletteIntent.Load -> loadPool(RouletteSource.COLLECTION)
            is WineRouletteIntent.SetSource -> loadPool(intent.source)
            is WineRouletteIntent.Spin -> spin()
            is WineRouletteIntent.Settle -> settle(intent.wine)
            is WineRouletteIntent.ToggleFavorite -> toggleFavorite(intent.wineId)
        }
    }

    private fun loadPool(source: RouletteSource) {
        updateState(WineRouletteState.Loading)
        viewModelScope.launch {
            getRoulettePoolUseCase(source)
                .onSuccess { pool ->
                    updateState(
                        WineRouletteState.Success(
                            source = source,
                            pool = pool,
                            likedIds = latestLikedIds
                        )
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "Roulette pool load failed", e)
                    updateState(WineRouletteState.Error(e.message ?: ""))
                }
        }
    }

    private fun spin() {
        val current = _state.value as? WineRouletteState.Success ?: return
        if (!current.canSpin) return
        val pool = if (current.pool.size > 1) {
            current.pool.filter { it.id != previousWinnerId }.ifEmpty { current.pool }
        } else {
            current.pool
        }
        val winner: Wine = pool.random()
        previousWinnerId = winner.id
        updateState(current.copy(isSpinning = true, result = null))
        viewModelScope.launch { _effects.emit(WineRouletteEffect.Roll(winner)) }
    }

    private fun settle(winner: Wine) {
        val current = _state.value as? WineRouletteState.Success ?: return
        if (current.result?.id == winner.id) return
        updateState(current.copy(isSpinning = false, result = winner))
    }

    private fun toggleFavorite(wineId: String) {
        val current = _state.value as? WineRouletteState.Success ?: return
        val isFavorite = current.likedIds.contains(wineId)
        viewModelScope.launch {
            toggleFavoriteUseCase(wineId, isFavorite)
                .onFailure { e -> Log.e(TAG, "Toggle favorite failed", e) }
        }
    }

    /** Сердечки карточек: только «Понравилось», обновляется реактивно. */
    private fun observeLiked() {
        viewModelScope.launch {
            getFavoritesUseCase().collect { entities ->
                val liked = entities
                    .filter { it.kind == FavoriteKind.LIKED }
                    .map { it.wineId }
                    .toSet()
                latestLikedIds = liked
                val current = _state.value as? WineRouletteState.Success ?: return@collect
                if (current.likedIds != liked) updateState(current.copy(likedIds = liked))
            }
        }
    }

    private companion object {
        const val TAG = "WineRouletteVM"
    }
}
