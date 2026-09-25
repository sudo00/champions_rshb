package com.wineapp.presentation.scanresult

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.usecase.GetCellarEntryUseCase
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.domain.usecase.IsFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanResultViewModel @Inject constructor(
    private val getWineDetailsUseCase: GetWineDetailsUseCase,
    private val getCellarEntryUseCase: GetCellarEntryUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase
) : BaseViewModel<ScanResultState, ScanResultIntent>() {

    override fun getInitialState(): ScanResultState = ScanResultState.Loading

    override fun reduce(intent: ScanResultIntent) {
        when (intent) {
            is ScanResultIntent.LoadWines -> handleLoad(intent.mainWineId, intent.altIds)
        }
    }

    private fun handleLoad(mainWineId: String, altIds: String) {
        updateState(ScanResultState.Loading)
        viewModelScope.launch {
            try {
                val mainResult = getWineDetailsUseCase(mainWineId)
                val mainWine = mainResult.getOrNull()
                if (mainWine == null) {
                    updateState(ScanResultState.Error("Вино не найдено"))
                    return@launch
                }

                val altIdList = altIds.split("|").filter { it.isNotBlank() }
                val alternatives = if (altIdList.isNotEmpty()) {
                    coroutineScope {
                        altIdList.map { id ->
                            async { getWineDetailsUseCase(id).getOrNull() }
                        }.awaitAll().filterNotNull()
                    }
                } else {
                    emptyList()
                }

                updateState(ScanResultState.Success(mainWine, alternatives, alreadyTried(mainWineId)))
            } catch (e: Exception) {
                Log.e("ScanResultVM", "Load wines failed", e)
                updateState(ScanResultState.Error(e.message ?: "Ошибка загрузки"))
            }
        }
    }

    /**
     * Вино уже пробовали, если оно есть в погребе (статус неважен) или
     * в избранном с kind LIKED («Понравилось», не «Хочу»).
     */
    private suspend fun alreadyTried(wineId: String): Boolean {
        return try {
            combine(
                getCellarEntryUseCase(wineId),
                isFavoriteUseCase(wineId, FavoriteKind.LIKED)
            ) { entry, liked -> entry != null || liked }.first()
        } catch (e: Exception) {
            Log.e("ScanResultVM", "Already-tried check failed", e)
            false
        }
    }
}
