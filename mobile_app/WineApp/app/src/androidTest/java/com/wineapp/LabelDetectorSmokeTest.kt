package com.wineapp

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wineapp.data.detector.LabelDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.io.File
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class LabelDetectorSmokeTest {
    @Test fun preloadedModelSurvivesRepeatedCameraSessions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = (context.applicationContext as WineApplication).labelDetectorRuntime
        runtime.preload()
        runtime.executor.submit {
            val detector = runtime.detector()
            val fixture = File(context.filesDir, "detector_fixture.png")
            val bitmap = if (fixture.exists()) requireNotNull(BitmapFactory.decodeFile(fixture.path))
                else Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            try {
                val reference = detector.detect(bitmap)
                val heapBefore = android.os.Debug.getNativeHeapAllocatedSize()
                val timings = mutableListOf<Long>()
                repeat(3) {
                    val analyzer = com.wineapp.data.detector.CameraLabelAnalyzer(runtime) { }
                    repeat(100) {
                        val start = android.os.SystemClock.elapsedRealtimeNanos()
                        val boxes = runtime.detector().detect(bitmap)
                        timings += (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                        assertEquals(reference.size, boxes.size)
                        boxes.zip(reference).forEach { (actual, expected) ->
                            assertEquals(expected.score, actual.score, 0.001f)
                            assertEquals(expected.left, actual.left, 0.001f)
                            assertEquals(expected.top, actual.top, 0.001f)
                            assertEquals(expected.right, actual.right, 0.001f)
                            assertEquals(expected.bottom, actual.bottom, 0.001f)
                        }
                    }
                    analyzer.close()
                    runtime.preload()
                    assertSame(detector, runtime.detector())
                }
                val sorted = timings.sorted()
                android.util.Log.i("LabelDetectorSmoke", "Shared runtime: frames=${timings.size} " +
                    "fixture=${fixture.exists()} boxes=${reference.size} medianMs=${sorted[150]} " +
                    "p95Ms=${sorted[284]} maxMs=${sorted.last()} " +
                    "nativeHeapDeltaBytes=${android.os.Debug.getNativeHeapAllocatedSize() - heapBefore}")
            } finally { bitmap.recycle() }
        }.get(60, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Test fun bundledModelLoadsAndRunsOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = context.assets.open("models/wine_label_yolo26n_320.tflite").use { it.readBytes() }
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("ecd817f904b8d159ee924c15b2ca06dce80bf28e4c6d7086a35601cca5a59abf", checksum)
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        LabelDetector(context).use { detector ->
            val boxes = detector.detect(bitmap)
            assertTrue(boxes.all { it.score.isFinite() && it.area > 0 })
            assertTrue("Plain gray square must remain below detection threshold", boxes.isEmpty())
            android.util.Log.i("LabelDetectorSmoke", "Runtime=${detector.backend}; boxes=${boxes.size}")
            if (InstrumentationRegistry.getArguments().getString("detectorFixture") == "true") {
                val photo = BitmapFactory.decodeFile(File(context.filesDir, "detector_fixture.png").path)
                requireNotNull(photo) { "Copy detector_fixture.png and photo_expected.json to app files first" }
                try {
                    val expected = JSONObject(File(context.filesDir, "photo_expected.json").readText())
                    val actual = detector.detect(photo).maxByOrNull { it.score }
                    requireNotNull(actual) { "GPU did not detect reference label" }
                    assertEquals(expected.getDouble("score"), actual.score.toDouble(), 0.05)
                    val coordinates = expected.getJSONArray("box")
                    listOf(actual.left, actual.top, actual.right, actual.bottom).forEachIndexed { i, value ->
                        assertEquals(coordinates.getDouble(i), value.toDouble(), 0.02)
                    }
                    android.util.Log.i("LabelDetectorSmoke", "Fixture parity passed: $actual")
                } finally { photo.recycle() }
            }
        }
        bitmap.recycle()
    }
}
