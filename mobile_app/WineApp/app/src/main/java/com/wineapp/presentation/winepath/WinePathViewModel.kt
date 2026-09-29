package com.wineapp.presentation.winepath

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.GetWinePathUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Диалог новых наград живёт на уровне приложения — см. [BadgeCelebrationHost]. */
@HiltViewModel
class WinePathViewModel @Inject constructor(
    private val getWinePathUseCase: GetWinePathUseCase
) : BaseViewModel<WinePathState, WinePathIntent>() {

    private var loadJob: Job? = null

    override fun getInitialState(): WinePathState = WinePathState.Loading

    init {
        reduce(WinePathIntent.LoadPath)
    }

    override fun reduce(intent: WinePathIntent) {
        when (intent) {
            is WinePathIntent.LoadPath -> loadPath()
        }
    }

    private fun loadPath() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                getWinePathUseCase().collect { data ->
                    updateState(
                        WinePathState.Success(
                            summary = data.summary,
                            territories = data.territories,
                            badges = data.badges
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WinePathVM", "Load path failed", e)
                updateState(WinePathState.Error(e.message ?: "Не удалось загрузить Винный путь"))
            }
        }
    }
}
