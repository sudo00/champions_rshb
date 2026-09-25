package com.wineapp.data.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

/** Construct, run and close on the same camera analysis executor. */
class LabelDetector(context: Context) : AutoCloseable {
    private val asset = "models/wine_label_yolo26n_320.tflite"
    val backend: String
    private val model: CompiledModel
    init {
        var runtime = "GPU"
        model = try {
            CompiledModel.create(context.assets, asset, CompiledModel.Options(Accelerator.GPU))
        } catch (failure: Exception) {
            runtime = "CPU"
            CompiledModel.create(context.assets, asset, CompiledModel.Options(Accelerator.CPU).apply {
                cpuOptions = CompiledModel.CpuOptions(numThreads = 4)
            })
        }
        backend = runtime
    }
    private val inputs = model.createInputBuffers()
    private val outputs = model.createOutputBuffers()
    private val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(320 * 320)
    private val values = FloatArray(320 * 320 * 3)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun detect(image: Bitmap): List<LabelBox> {
        val geometry = LabelLetterbox(image.width, image.height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(image, null, Rect(geometry.left, geometry.top,
            geometry.left + geometry.resizedWidth, geometry.top + geometry.resizedHeight), paint)
        bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 320)
        pixels.forEachIndexed { i, rgb ->
            values[i] = Color.red(rgb) / 255f
            values[102400 + i] = Color.green(rgb) / 255f
            values[204800 + i] = Color.blue(rgb) / 255f
        }
        inputs[0].writeFloat(values)
        model.run(inputs, outputs)
        return geometry.decode(outputs[0].readFloat())
    }

    override fun close() {
        inputs.forEach { it.close() }; outputs.forEach { it.close() }; model.close(); bitmap.recycle()
    }
}
