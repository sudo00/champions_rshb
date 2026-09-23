package com.wineapp.presentation.winepath

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.repository.BadgeRepository
import com.wineapp.domain.usecase.GetWinePathUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WinePathViewModel @Inject constructor(
    private val getWinePathUseCase: GetWinePathUseCase,
    private val badgeRepository: BadgeRepository
) : BaseViewModel<WinePathState, WinePathIntent>() {

    private var celebrationJob: Job? = null

    override fun getInitialState(): WinePathState = WinePathState.Loading

    init {
        reduce(WinePathIntent.LoadPath)
    }

    override fun reduce(intent: WinePathIntent) {
        when (intent) {
            is WinePathIntent.LoadPath -> loadPath()
            is WinePathIntent.ConsumeCelebration -> consumeCelebration()
        }
    }

    private fun loadPath() {
        viewModelScope.launch {
            try {
                getWinePathUseCase().collect { data ->
                    val current = (_state.value as? WinePathState.Success)?.celebration
                    updateState(
                        WinePathState.Success(
                            summary = data.summary,
                            territories = data.territories,
                            badges = data.badges,
                            celebration = current
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("WinePathVM", "Load path failed", e)
                updateState(WinePathState.Error(e.message ?: "Не удалось загрузить Винный путь"))
            }
        }
        celebrationJob?.cancel()
        celebrationJob = viewModelScope.launch {
            try {
                badgeRepository.freshBadges.collect { fresh ->
                    val first = fresh.firstOrNull() ?: return@collect
                    val state = _state.value
                    if (state is WinePathState.Success) {
                        updateState(state.copy(celebration = first))
                    }
                }
            } catch (e: Exception) {
                Log.e("WinePathVM", "Fresh badges failed", e)
            }
        }
    }

    private fun consumeCelebration() {
        val state = _state.value
        if (state is WinePathState.Success) {
            updateState(state.copy(celebration = null))
        }
    }
}
