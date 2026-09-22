package com.wineapp.presentation.scanresult

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.GetWineDetailsUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanResultViewModel @Inject constructor(
    private val getWineDetailsUseCase: GetWineDetailsUseCase
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

                updateState(ScanResultState.Success(mainWine, alternatives))
            } catch (e: Exception) {
                Log.e("ScanResultVM", "Load wines failed", e)
                updateState(ScanResultState.Error(e.message ?: "Ошибка загрузки"))
            }
        }
    }
}
