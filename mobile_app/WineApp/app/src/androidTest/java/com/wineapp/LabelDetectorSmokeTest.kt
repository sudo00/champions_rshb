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
