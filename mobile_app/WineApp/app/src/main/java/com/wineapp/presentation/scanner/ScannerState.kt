package com.wineapp.presentation.scanner

import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface ScannerState : BaseState {
    /**
     * Камера готова. Режим вспышки — не здесь, а в ScannerViewModel.flashMode: состояние
     * пересоздаётся после каждого скана и сбрасывало режим (иконка расходилась с камерой).
     */
    data object Ready : ScannerState
    data class Capturing(val imagePath: String) : ScannerState
    data class Processing(
        val imagePath: String,
        /** Уникальный сид показа: каждый оверлей рисует другую сцену. Генерируется в ViewModel. */
        val seed: Long = 0L,
        /** Стартовый индекс факта о вине, гарантированно != факту прошлого показа. */
        val factIndex: Int = 0
    ) : ScannerState
    data class Success(
        val result: ScanResult, val imagePath: String,
        val confirming: Boolean = false, val confirmationError: String? = null
    ) : ScannerState
    data class NotFound(
        val imagePath: String,
        val matches: List<Wine> = emptyList(),
        val recognitionStatus: String? = null,
        val message: String? = null
    ) : ScannerState
    data class Error(val message: String) : ScannerState
}

sealed interface ScannerIntent : BaseIntent {
    data class CapturePhoto(val imagePath: String) : ScannerIntent
    data class ProcessImage(val imagePath: String) : ScannerIntent
    data class GalleryImagePicked(val uriString: String) : ScannerIntent
    data object ToggleFlash : ScannerIntent
    data object RetryScan : ScannerIntent
    data object OpenSearch : ScannerIntent
    data class OpenDetail(val wineId: String) : ScannerIntent
}
