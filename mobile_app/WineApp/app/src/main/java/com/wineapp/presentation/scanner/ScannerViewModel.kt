package com.wineapp.presentation.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.file.CameraHelper
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.usecase.SaveScanUseCase
import com.wineapp.domain.usecase.ScanWineUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanWineUseCase: ScanWineUseCase,
    private val saveScanUseCase: SaveScanUseCase,
    private val badgeRepository: com.wineapp.domain.repository.BadgeRepository,
    val cameraHelper: CameraHelper
) : BaseViewModel<ScannerState, ScannerIntent>() {

    override fun getInitialState(): ScannerState = ScannerState.Ready()

    /** Текст тоста о новой награде «Винного пути». Нуллабельный одноразовый сигнал для UI. */
    private val _badgeMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val badgeMessage: kotlinx.coroutines.flow.StateFlow<String?> = _badgeMessage

    init {
        viewModelScope.launch {
            try {
                badgeRepository.freshBadges.collect { fresh ->
                    val first = fresh.firstOrNull() ?: return@collect
                    _badgeMessage.value = context.getString(
                        com.wineapp.R.string.winepath_new_badge,
                        first.def.title
                    )
                }
            } catch (e: Exception) {
                Log.e("ScannerViewModel", "Fresh badges failed", e)
            }
        }
    }

    override fun reduce(intent: ScannerIntent) {
        when (intent) {
            is ScannerIntent.CapturePhoto -> handleCapture(intent.imagePath)
            is ScannerIntent.ProcessImage -> handleProcess(intent.imagePath)
            is ScannerIntent.GalleryImagePicked -> handleGalleryPicked(intent.uriString)
            is ScannerIntent.ToggleFlash -> handleToggleFlash()
            is ScannerIntent.RetryScan -> handleRetry()
            is ScannerIntent.OpenSearch -> { /* Navigation handled in UI */
            }

            is ScannerIntent.OpenDetail -> { /* Navigation handled in UI */
            }
        }
    }

    private var lastFactIndex: Int = -1

    private fun handleCapture(imagePath: String) {
        updateState(ScannerState.Capturing(imagePath))
        sendIntent(ScannerIntent.ProcessImage(imagePath))
    }

    private fun handleProcess(imagePath: String) {
        updateState(ScannerState.Processing(imagePath, nextSeed(), nextFactIndex()))
        viewModelScope.launch {
            val result = scanWineUseCase(imagePath)
            result.onSuccess { scanResult ->
                if (scanResult.wine != null) {
                    // Автосейв каждого успешного скана — до перехода в лучшую карточку.
                    // Без фанатизма по ошибкам: скан уже распознан, история вторична.
                    viewModelScope.launch {
                        saveScanUseCase(
                            SavedScan(
                                id = java.util.UUID.randomUUID().toString(),
                                wine = scanResult.wine,
                                labelPhotoPath = imagePath,
                                confidence = scanResult.confidence,
                                conversation = emptyList(),
                                scannedAt = System.currentTimeMillis()
                            )
                        ).onFailure { e ->
                            Log.e("ScannerViewModel", "Auto-save scan failed", e)
                        }
                    }
                    updateState(ScannerState.Success(scanResult, imagePath))
                } else {
                    updateState(ScannerState.NotFound(imagePath, scanResult.matches, scanResult.recognitionStatus, scanResult.message))
                }
            }.onFailure { error ->
                Log.e("ScannerViewModel", "Scan failed", error)
                updateState(ScannerState.Error(error.message ?: "Ошибка сканирования"))
            }
        }
    }

    private fun handleGalleryPicked(uriString: String) {
        viewModelScope.launch {
            val uri = try {
                uriString.toUri()
            } catch (e: Exception) {
                Log.e("ScannerViewModel", "Invalid URI: $uriString", e)
                return@launch
            }
            val file = copyUriToFile(uri)
            if (file != null) {
                sendIntent(ScannerIntent.ProcessImage(file.absolutePath))
            } else {
                Log.e("ScannerViewModel", "Failed to copy gallery image")
            }
        }
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("gallery_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            // Путь галереи: копия сохраняет исходный EXIF. Нормализуем пиксели,
            // чтобы локальный файл и Base64 на бэк были upright.
            try {
                com.wineapp.data.file.ImageOrientationHelper.normalizeFileInPlace(tempFile)
            } catch (e: Exception) {
                Log.e("ScannerViewModel", "EXIF normalize failed, using as-is", e)
            }
            tempFile
        } catch (e: Exception) {
            Log.e("ScannerViewModel", "copyUriToFile failed", e)
            null
        }
    }

    /**
     * Сид анимации уникален на каждый показ: нано-время практически исключает повторы.
     * Факт стартует с индекса, отличного от прошлого показа.
     */
    private fun nextSeed(): Long = System.nanoTime()

    private fun nextFactIndex(): Int {
        val count = try {
            context.resources.getStringArray(com.wineapp.R.array.scan_facts).size
        } catch (e: Exception) {
            Log.e("ScannerViewModel", "scan_facts missing", e)
            1
        }.coerceAtLeast(1)
        var index = (nextSeed() % count).toInt().let { if (it < 0) it + count else it }
        if (index == lastFactIndex) index = (index + 1) % count
        lastFactIndex = index
        return index
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

    fun consumeBadgeMessage() {
        _badgeMessage.value = null
    }

    fun getFlashMode(): Int = cameraHelper.getFlashMode()
}
