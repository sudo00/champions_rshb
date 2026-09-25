package com.wineapp.data.detector

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform

@OptIn(TransformExperimental::class)
data class LabelFrame(
    val boxes: List<LabelBox> = emptyList(), val width: Int = 1, val height: Int = 1,
    val transform: OutputTransform? = null, val milliseconds: Float = 0f,
    val backend: String = "", val trigger: Boolean = false, val error: String? = null,
    /** Строгая стабильность для UI-подсветки: ровно 1 eligible-бокс, 3 кадра подряд. */
    val highlight: Boolean = false
)

/** Single executor + KEEP_ONLY_LATEST: never queue a backlog of camera images. */
@TransformExperimental
class CameraLabelAnalyzer(
    private val runtime: LabelDetectorRuntime,
    private val onFrame: (LabelFrame) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    @Volatile
    var enabled = true
    @Volatile
    var autoCapture = true
    @Volatile
    var resetRequested = false
    private var disabled = false
    private var measuredSince = 0L
    private var measuredFrames = 0
    private var measuredTimeMs = 0L
    private var lastFrameAt = 0L
    private var maxGapMs = 0L
    private var maxTimeMs = 0L
    private var firstFrame = true
    private val gate = LabelCaptureGate()

    // UI-подсветка: та же строгость, что у гейта автозахвата, но без защёлки —
    // подсветка следует за присутствием стабильного бокса и гаснет сразу при пропадании.
    private var highlightPrevious: LabelBox? = null
    private var highlightFrames = 0
    private val transforms = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!enabled || disabled) {
            resetMetrics(); image.close(); return
        }
        if (lastFrameAt != 0L && now - lastFrameAt > 1000) resetMetrics()
        var original: Bitmap? = null
        var cropped: Bitmap? = null
        var upright: Bitmap? = null
        try {
            if (resetRequested) {
                gate.reset(); resetHighlight(); resetRequested = false
            }
            if (!autoCapture) {
                gate.reset(); resetHighlight()
            }
            val model = runtime.detector()
            val sourceTransform = transforms.getOutputTransform(image)
            original = image.toBitmap()
            val crop = image.cropRect
            cropped =
                Bitmap.createBitmap(original, crop.left, crop.top, crop.width(), crop.height())
            upright = if (image.imageInfo.rotationDegrees == 0) cropped else Bitmap.createBitmap(
                cropped, 0, 0, cropped.width, cropped.height,
                Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true
            )
            val boxes = model.detect(upright)
            val trigger = autoCapture && gate.update(boxes, now)
            val highlight = updateHighlight(boxes)
            val elapsed = SystemClock.elapsedRealtime() - now
            if (firstFrame) {
                android.util.Log.i(
                    "WineLabelDetector",
                    "firstCameraFrameMs=$elapsed backend=${model.backend}"
                )
                firstFrame = false
            }
            if (com.wineapp.BuildConfig.DEBUG) {
                if (lastFrameAt != 0L) maxGapMs = maxOf(maxGapMs, now - lastFrameAt)
                lastFrameAt = now
                maxTimeMs = maxOf(maxTimeMs, elapsed)
                if (measuredSince == 0L) measuredSince = now
                measuredFrames++
                measuredTimeMs += elapsed
                val windowMs = SystemClock.elapsedRealtime() - measuredSince
                if (windowMs >= 1000 || trigger) {
                    val fps = (measuredFrames - 1) * 1000L / (now - measuredSince).coerceAtLeast(1)
                    android.util.Log.d(
                        "WineLabelDetector", "fps=$fps frames=$measuredFrames " +
                            "meanMs=${measuredTimeMs / measuredFrames} maxMs=$maxTimeMs maxGapMs=$maxGapMs backend=${model.backend} " +
                            "boxes=${boxes.size} eligible=${boxes.count { it.eligible() }} trigger=$trigger"
                    )
                    measuredSince = 0L; measuredFrames = 0; measuredTimeMs = 0L
                    maxTimeMs = 0L; maxGapMs = 0L
                }
            }
            onFrame(
                LabelFrame(
                    boxes, upright.width, upright.height, sourceTransform,
                    elapsed.toFloat(), model.backend, trigger, highlight = highlight
                )
            )
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

    /**
     * Однокадровый мусор детектора подсветку не зажигает: нужен ровно один
     * eligible-бокс, совпадающий с предыдущим кадром (IoU >= .75), три кадра подряд.
     * В отличие от гейта — без защёлки: пропал бокс — погасла подсветка.
     */
    private fun updateHighlight(boxes: List<LabelBox>): Boolean {
        val single = boxes.filter { it.eligible() }.singleOrNull()
        if (single != null && highlightPrevious?.iou(single)?.let { it >= .75f } == true) {
            highlightFrames++
        } else {
            highlightFrames = if (single != null) 1 else 0
        }
        highlightPrevious = single
        return single != null && highlightFrames >= 3
    }

    private fun resetMetrics() {
        measuredSince = 0L; measuredFrames = 0; measuredTimeMs = 0L
        lastFrameAt = 0L; maxGapMs = 0L; maxTimeMs = 0L
    }

    private fun resetHighlight() {
        highlightPrevious = null; highlightFrames = 0
    }

    override fun close() {
        enabled = false; gate.reset(); resetHighlight(); resetMetrics()
    }
}
