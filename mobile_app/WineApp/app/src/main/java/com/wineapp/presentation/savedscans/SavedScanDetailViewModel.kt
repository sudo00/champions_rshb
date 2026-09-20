package com.wineapp.presentation.savedscans

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.GetSavedScanByIdUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedScanDetailViewModel @Inject constructor(
    private val getSavedScanByIdUseCase: GetSavedScanByIdUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<SavedScanDetailState, SavedScanDetailIntent>() {

    private val scanId: String = savedStateHandle["scanId"] ?: ""

    override fun getInitialState(): SavedScanDetailState = SavedScanDetailState.Loading

    init {
        if (scanId.isNotEmpty()) {
            loadScan(scanId)
        }
    }

    override fun reduce(intent: SavedScanDetailIntent) {
        when (intent) {
            is SavedScanDetailIntent.LoadScan -> loadScan(intent.scanId)
        }
    }

    private fun loadScan(id: String) {
        updateState(SavedScanDetailState.Loading)
        viewModelScope.launch {
            getSavedScanByIdUseCase(id)
                .onSuccess { scan ->
                    updateState(SavedScanDetailState.Success(scan))
                }
                .onFailure { e ->
                    Log.e("SavedScanDetailVM", "Load scan failed", e)
                    updateState(SavedScanDetailState.Error(e.message ?: "Ошибка загрузки"))
                }
        }
    }
}
