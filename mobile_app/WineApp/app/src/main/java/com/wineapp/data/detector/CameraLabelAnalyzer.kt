package com.wineapp.data.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform

data class LabelFrame(
    val boxes: List<LabelBox> = emptyList(), val width: Int = 1, val height: Int = 1,
    val transform: OutputTransform? = null, val milliseconds: Float = 0f,
    val backend: String = "", val trigger: Boolean = false, val error: String? = null
)

/** Single executor + KEEP_ONLY_LATEST: never queue a backlog of camera images. */
class CameraLabelAnalyzer(private val context: Context, private val onFrame: (LabelFrame) -> Unit) : ImageAnalysis.Analyzer, AutoCloseable {
    @Volatile var enabled = true
    @Volatile var autoCapture = false
    @Volatile var resetRequested = false
    private var detector: LabelDetector? = null
    private var disabled = false
    private var lastRun = 0L
    private val gate = LabelCaptureGate()
    private val transforms = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!enabled || disabled || now - lastRun < 33) { image.close(); return }
        lastRun = now
        var original: Bitmap? = null
        var cropped: Bitmap? = null
        var upright: Bitmap? = null
        try {
            if (resetRequested) { gate.reset(); resetRequested = false }
            if (!autoCapture) gate.reset()
            val model = detector ?: LabelDetector(context).also { detector = it }
            val sourceTransform = transforms.getOutputTransform(image)
            original = image.toBitmap()
            val crop = image.cropRect
            cropped = Bitmap.createBitmap(original, crop.left, crop.top, crop.width(), crop.height())
            upright = if (image.imageInfo.rotationDegrees == 0) cropped else Bitmap.createBitmap(
                cropped, 0, 0, cropped.width, cropped.height,
                Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true)
            val boxes = model.detect(upright)
            val trigger = autoCapture && gate.update(boxes, now)
            onFrame(LabelFrame(boxes, upright.width, upright.height, sourceTransform,
                (SystemClock.elapsedRealtime() - now).toFloat(), model.backend, trigger))
        } catch (error: Exception) {
            disabled = true
            onFrame(LabelFrame(error = "Детектор недоступен. Можно снять фото кнопкой."))
            android.util.Log.e("CameraLabelAnalyzer", "Label detection failed", error)
        } finally {
            if (upright != null && upright !== cropped && upright !== original) upright.recycle()
            if (cropped != null && cropped !== original) cropped.recycle()
            original?.recycle()
            image.close()
        }
    }

    override fun close() { detector?.close(); detector = null }
}
