package com.wineapp.presentation.scanner

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.file.CameraHelper
import com.wineapp.domain.usecase.ScanWineUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanWineUseCase: ScanWineUseCase,
    val cameraHelper: CameraHelper
) : BaseViewModel<ScannerState, ScannerIntent>() {

    override fun getInitialState(): ScannerState = ScannerState.Ready()

    override fun reduce(intent: ScannerIntent) {
        when (intent) {
            is ScannerIntent.CapturePhoto -> handleCapture(intent.imagePath)
            is ScannerIntent.ProcessImage -> handleProcess(intent.imagePath)
            is ScannerIntent.PickFromGallery -> handleGalleryPick()
            is ScannerIntent.ToggleFlash -> handleToggleFlash()
            is ScannerIntent.RetryScan -> handleRetry()
            is ScannerIntent.OpenSearch -> { /* Navigation handled in UI */ }
            is ScannerIntent.OpenDetail -> { /* Navigation handled in UI */ }
        }
    }

    private fun handleCapture(imagePath: String) {
        updateState(ScannerState.Capturing(imagePath))
        // Process immediately
        sendIntent(ScannerIntent.ProcessImage(imagePath))
    }

    private fun handleProcess(imagePath: String) {
        updateState(ScannerState.Processing(imagePath))
        viewModelScope.launch {
            val result = scanWineUseCase(imagePath)
            result.onSuccess { scanResult ->
                if (scanResult.wine != null) {
                    updateState(ScannerState.Success(scanResult, imagePath))
                } else {
                    updateState(ScannerState.NotFound(imagePath, scanResult.matches))
                }
            }.onFailure { error ->
                Log.e("ScannerViewModel", "Scan failed", error)
                updateState(ScannerState.Error(error.message ?: "Ошибка сканирования"))
            }
        }
    }

    private fun handleGalleryPick() {
        // TODO: Implement gallery picker launch
        // galleryPicker.pickImage(activity, launcher) { file ->
        //     file?.let { sendIntent(ScannerIntent.ProcessImage(it.absolutePath)) }
        // }
    }

    private fun handleToggleFlash() {
        val newMode = cameraHelper.toggleFlash()
        val currentState = _state.value
        if (currentState is ScannerState.Ready) {
            updateState(ScannerState.Ready(newMode))
        }
    }

    private fun handleRetry() {
        updateState(ScannerState.Ready())
    }

    fun resetToReady() {
        updateState(ScannerState.Ready())
    }

    fun getFlashMode(): Int = cameraHelper.getFlashMode()
}