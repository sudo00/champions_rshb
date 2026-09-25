package com.wineapp.data.file

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCaseGroup
import androidx.camera.view.transform.CoordinateTransform
import android.graphics.RectF
import com.wineapp.data.detector.CameraLabelAnalyzer
import com.wineapp.data.detector.LabelFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analyzer: CameraLabelAnalyzer? = null
    private var analysis: ImageAnalysis? = null
    private var provider: androidx.camera.lifecycle.ProcessCameraProvider? = null
    private val detectorMutable = MutableStateFlow(DetectorPreview())
    val detectorPreview = detectorMutable.asStateFlow()
    private var autoEnabled = false
    private var analysisEnabled = true
    private var captureInFlight = false
    var onAutoCapture: ((File) -> Unit)? = null
    private var imageCapture: ImageCapture? = null
    var previewView: PreviewView? = null
        private set
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraSelector: androidx.camera.core.CameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var pendingBind = false
    private var bindGeneration = 0

    fun bindToLifecycle(
        owner: LifecycleOwner,
        preview: PreviewView,
        selector: androidx.camera.core.CameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
    ) {
        this.previewView = preview
        this.lifecycleOwner = owner
        this.cameraSelector = selector

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingBind = true
            return
        }
        doBind()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted && pendingBind) {
            pendingBind = false
            doBind()
        }
    }

    private fun doBind() {
        val owner = lifecycleOwner ?: return
        val preview = previewView ?: return
        if (preview.width == 0 || preview.height == 0) {
            preview.post { if (preview === previewView) doBind() }
            return
        }
        val generation = ++bindGeneration
        if (cameraExecutor.isShutdown) cameraExecutor = Executors.newSingleThreadExecutor()
        if (analysisExecutor.isShutdown) analysisExecutor = Executors.newSingleThreadExecutor()

        val surfaceProvider = preview.surfaceProvider
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (generation != bindGeneration || preview !== previewView) return@addListener
            val cameraProvider = cameraProviderFuture.get()
            provider = cameraProvider
            cameraProvider.unbindAll()
            analysis?.clearAnalyzer()
            val oldAnalyzer = analyzer
            analysisExecutor.execute { oldAnalyzer?.close() }

            val previewUseCase = androidx.camera.core.Preview.Builder().build().also {
                it.surfaceProvider = surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()

            analyzer = CameraLabelAnalyzer(context) { frame ->
                ContextCompat.getMainExecutor(context).execute { displayFrame(frame, preview) }
            }.apply { enabled = analysisEnabled; autoCapture = autoEnabled }
            analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(android.util.Size(640, 480))
                .build().also { it.setAnalyzer(analysisExecutor, analyzer!!) }
            val group = UseCaseGroup.Builder().addUseCase(previewUseCase)
                .addUseCase(imageCapture!!).addUseCase(analysis!!)
            preview.viewPort?.let { group.setViewPort(it) }
            cameraProvider.bindToLifecycle(owner, cameraSelector, group.build())
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture(onSuccess: (File) -> Unit, onError: (Exception) -> Unit) {
        val imageCapture = this.imageCapture ?: return
        if (captureInFlight) return
        captureInFlight = true
        analyzer?.enabled = false
        val photoFile = createImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Путь камеры: CameraX пишет EXIF-ориентацию по повороту девайса.
                // Нормализуем пиксели сразу, чтобы локальный photoPath совпадал
                // с тем, что уйдёт на бэк (иначе UI через Coil выглядит нормально,
                // а бэк получает повёрнутое фото).
                try {
                    ImageOrientationHelper.normalizeFileInPlace(photoFile)
                } catch (e: Exception) {
                    android.util.Log.e("CameraHelper", "EXIF normalize failed, sending as-is", e)
                }
                ContextCompat.getMainExecutor(context).execute {
                    captureInFlight = false
                    onSuccess(photoFile)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(context).execute {
                    captureInFlight = false
                    analyzer?.enabled = analysisEnabled
                    onError(Exception(exception.message, exception))
                }
            }
        })
    }

    fun toggleFlash(): Int {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        return flashMode
    }

    fun getFlashMode(): Int = flashMode

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir("images")!!
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }

    fun shutdown() {
        bindGeneration++
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        val oldAnalyzer = analyzer
        if (!analysisExecutor.isShutdown) analysisExecutor.execute { oldAnalyzer?.close() }
        analysisExecutor.shutdown()
        analyzer = null
        previewView = null
        onAutoCapture = null
        cameraExecutor.shutdown()
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        analysisEnabled = enabled
        analyzer?.enabled = enabled && !captureInFlight
    }

    fun setAutoCapture(enabled: Boolean) {
        autoEnabled = enabled
        analyzer?.autoCapture = enabled
        analyzer?.resetRequested = true
        detectorMutable.value = detectorMutable.value.copy(autoCapture = enabled)
    }

    private fun displayFrame(frame: LabelFrame, preview: PreviewView) {
        if (!analysisEnabled || preview !== previewView) return
        val mapped = mutableListOf<RectF>()
        val source = frame.transform
        val target = preview.outputTransform
        if (source != null && target != null) {
            val transform = CoordinateTransform(source, target)
            frame.boxes.forEach {
                val rect = RectF(it.left * frame.width, it.top * frame.height, it.right * frame.width, it.bottom * frame.height)
                transform.mapRect(rect)
                mapped.add(rect)
            }
        }
        detectorMutable.value = DetectorPreview(mapped, frame.milliseconds, frame.backend, autoEnabled, frame.error)
        if (frame.trigger && autoEnabled && mapped.isNotEmpty() && !captureInFlight && onAutoCapture != null) {
            com.wineapp.util.HapticHelper.vibrateSuccess(context)
            takePicture(onSuccess = { onAutoCapture?.invoke(it) }, onError = {
                detectorMutable.value = detectorMutable.value.copy(error = "Не удалось снять фото. Попробуйте кнопкой.")
            })
        }
    }
}

data class DetectorPreview(val boxes: List<RectF> = emptyList(), val milliseconds: Float = 0f,
    val backend: String = "", val autoCapture: Boolean = false, val error: String? = null)
