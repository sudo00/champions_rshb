package com.wineapp.presentation.scanner

import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface ScannerState : BaseState {
    data class Ready(val flashMode: Int = 0) : ScannerState
    data class Capturing(val imagePath: String) : ScannerState
    data class Processing(val imagePath: String) : ScannerState
    data class Success(val result: ScanResult, val imagePath: String) : ScannerState
    data class NotFound(val imagePath: String, val matches: List<Wine> = emptyList()) : ScannerState
    data class Error(val message: String) : ScannerState
}

sealed interface ScannerIntent : BaseIntent {
    data class CapturePhoto(val imagePath: String) : ScannerIntent
    data class ProcessImage(val imagePath: String) : ScannerIntent
    data object PickFromGallery : ScannerIntent
    data object ToggleFlash : ScannerIntent
    data object RetryScan : ScannerIntent
    data object OpenSearch : ScannerIntent
    data class OpenDetail(val wineId: String) : ScannerIntent
}