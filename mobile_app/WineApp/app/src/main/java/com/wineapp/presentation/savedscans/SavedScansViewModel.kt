package com.wineapp.presentation.savedscans

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.usecase.DeleteSavedScanUseCase
import com.wineapp.domain.usecase.GetSavedScansUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedScansViewModel @Inject constructor(
    private val getSavedScansUseCase: GetSavedScansUseCase,
    private val deleteSavedScanUseCase: DeleteSavedScanUseCase
) : BaseViewModel<SavedScansState, SavedScansIntent>() {

    override fun getInitialState(): SavedScansState = SavedScansState.Loading

    override fun reduce(intent: SavedScansIntent) {
        when (intent) {
            is SavedScansIntent.LoadScans -> loadScans()
            is SavedScansIntent.DeleteScan -> deleteScan(intent.id)
        }
    }

    private fun loadScans() {
        viewModelScope.launch {
            getSavedScansUseCase().collect { scans ->
                updateState(SavedScansState.Success(scans))
            }
        }
    }

    private fun deleteScan(id: String) {
        viewModelScope.launch {
            deleteSavedScanUseCase(id)
                .onFailure { e ->
                    Log.e("SavedScansVM", "Delete failed", e)
                    updateState(SavedScansState.Error(e.message ?: "Ошибка удаления"))
                }
        }
    }
}
