package com.wineapp.presentation.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.file.CameraHelper
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.model.confirmation
import com.wineapp.domain.model.historySnapshot
import com.wineapp.domain.usecase.GetCellarEntryUseCase
import com.wineapp.domain.usecase.IsFavoriteUseCase
import com.wineapp.domain.usecase.SaveScanUseCase
import com.wineapp.domain.usecase.ScanWineUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import androidx.core.net.toUri
import com.wineapp.domain.repository.BadgeRepository
import com.wineapp.domain.repository.WineRepository
import com.wineapp.util.HapticHelper

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanWineUseCase: ScanWineUseCase,
    private val wineRepository: WineRepository,
    private val saveScanUseCase: SaveScanUseCase,
    private val badgeRepository: BadgeRepository,
    private val getCellarEntryUseCase: GetCellarEntryUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase,
    val cameraHelper: CameraHelper
) : BaseViewModel<ScannerState, ScannerIntent>() {

    override fun getInitialState(): ScannerState = ScannerState.Ready()

    /** Текст тоста о новой награде «Винного пути». Нуллабельный одноразовый сигнал для UI. */
    private val _badgeMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val badgeMessage: kotlinx.coroutines.flow.StateFlow<String?> = _badgeMessage

    /** Найденное вино уже пробовали: есть в погребе или в «Понравилось». Для баннера итога. */
    private val _alreadyTried = kotlinx.coroutines.flow.MutableStateFlow(false)
    val alreadyTried: kotlinx.coroutines.flow.StateFlow<Boolean> = _alreadyTried

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
            } catch (e: CancellationException) {
                throw e
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
    private var scanScreenActive = false

    fun setScanScreenActive(active: Boolean) {
        scanScreenActive = active
        cameraHelper.setScreenActive(active)
    }

    private fun handleCapture(imagePath: String) {
        if (_state.value is ScannerState.Processing) return
        updateState(ScannerState.Capturing(imagePath))
        sendIntent(ScannerIntent.ProcessImage(imagePath))
    }

    private fun handleProcess(imagePath: String) {
        if (_state.value is ScannerState.Processing) return
        _alreadyTried.value = false
        updateState(ScannerState.Processing(imagePath, nextSeed(), nextFactIndex()))
        viewModelScope.launch {
            val result = scanWineUseCase(imagePath)
            var triedCandidateId: String? = null
            result.onSuccess { scanResult ->
                val wineFound = scanResult.wine != null
                if (scanScreenActive) {
                    // Успешный ответ бэка — мощная победная вибрация,
                    // остальные исходы — короткий штатный отклик.
                    if (wineFound) HapticHelper.vibrateVictory(context)
                    else HapticHelper.vibrateScanResult(context)
                }
                val snapshot = scanResult.historySnapshot(imagePath, System.currentTimeMillis())
                val saveError = snapshot?.let { saveScanUseCase(it).exceptionOrNull() }
                if (saveError != null) Log.e("ScannerViewModel", "Automatic scan save failed", saveError)
                if (scanResult.wine != null || scanResult.recognitionStatus == "not_in_catalog") {
                    // ID найденного вина запоминаем здесь, а suspend-проверку погреба/
                    // избранного делаем ниже — внутри onSuccess нет корутинного контекста.
                    triedCandidateId = scanResult.confirmation()?.candidate?.wine?.id
                        ?: scanResult.wine?.id
                    updateState(ScannerState.Success(scanResult, imagePath, confirmationError =
                        if (saveError != null) "Результат получен, но сохранить сканирование не удалось." else null))
                } else {
                    updateState(ScannerState.NotFound(imagePath, scanResult.matches, scanResult.recognitionStatus, scanResult.message))
                }
            }.onFailure { error ->
                Log.e("ScannerViewModel", "Scan failed", error)
                updateState(ScannerState.Error(if (error is java.io.IOException)
                    "Не удалось связаться с сервером. Проверьте подключение и попробуйте ещё раз."
                    else "Не удалось распознать фото. Попробуйте ещё раз."))
            }
            triedCandidateId?.let { _alreadyTried.value = alreadyTriedWine(it) }
        }
    }

    private fun handleGalleryPicked(uriString: String) {
        if (_state.value is ScannerState.Processing || _state.value is ScannerState.Capturing) return
        updateState(ScannerState.Capturing(uriString))
        cameraHelper.setAnalysisEnabled(false)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyUriToFile(uriString.toUri()) }
                if (file != null) {
                    handleProcess(file.absolutePath)
                } else {
                    updateState(ScannerState.Error("Не удалось открыть фото из галереи. Попробуйте другое изображение."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ScannerViewModel", "Gallery import failed", e)
                updateState(ScannerState.Error("Не удалось открыть фото из галереи."))
            }
        }
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val tempFile = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                File.createTempFile("gallery_", ".jpg", context.cacheDir).also { file ->
                    FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
                }
            } ?: return null
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
        _alreadyTried.value = false
        updateState(ScannerState.Ready())
    }

    fun resetToReady() {
        _alreadyTried.value = false
        updateState(ScannerState.Ready())
    }

    /**
     * Вино уже пробовали, если оно есть в погребе (статус неважен) или
     * в избранном с kind LIKED («Понравилось», не «Хочу»).
     */
    private suspend fun alreadyTriedWine(wineId: String): Boolean {
        return try {
            combine(
                getCellarEntryUseCase(wineId),
                isFavoriteUseCase(wineId, FavoriteKind.LIKED)
            ) { entry, liked -> entry != null || liked }.first()
        } catch (e: Exception) {
            Log.e("ScannerViewModel", "Already-tried check failed", e)
            false
        }
    }

    fun confirmCandidate(slug: String) {
        val current = _state.value as? ScannerState.Success ?: return
        val scanId = current.result.scanId ?: return
        if (current.confirming || current.result.scoredCandidates.none { it.slug == slug }) return
        updateState(current.copy(confirming = true, confirmationError = null))
        viewModelScope.launch {
            wineRepository.confirmScan(scanId, slug).onSuccess { result ->
                if ((_state.value as? ScannerState.Success)?.result?.scanId == scanId) {
                    val snapshot = result.historySnapshot(current.imagePath, System.currentTimeMillis())
                    val saveError = snapshot?.let { saveScanUseCase(it).exceptionOrNull() }
                    if ((_state.value as? ScannerState.Success)?.result?.scanId == scanId) {
                        if (saveError == null && snapshot != null) {
                            updateState(current.copy(result = result, confirming = false))
                        } else {
                            Log.e("ScannerViewModel", "Save confirmed scan failed", saveError)
                            updateState(current.copy(confirming = false,
                                confirmationError = "Не удалось сохранить выбор на телефоне. Попробуйте ещё раз."))
                        }
                    }
                }
            }.onFailure {
                if ((_state.value as? ScannerState.Success)?.result?.scanId == scanId) {
                    updateState(current.copy(confirming = false, confirmationError = "Не удалось сохранить выбор. Попробуйте ещё раз."))
                }
            }
        }
    }

    fun consumeBadgeMessage() {
        _badgeMessage.value = null
    }

    fun getFlashMode(): Int = cameraHelper.getFlashMode()
}
