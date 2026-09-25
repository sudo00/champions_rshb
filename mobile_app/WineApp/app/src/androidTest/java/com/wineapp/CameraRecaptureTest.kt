package com.wineapp

import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.wineapp.data.detector.LabelBox
import com.wineapp.data.detector.LabelFrame
import com.wineapp.data.file.CameraHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraRecaptureTest {
    @Test fun newPreviewCanCaptureAgainEvenWithoutDetectionTransform() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = (context.applicationContext as WineApplication).labelDetectorRuntime
        val helper = CameraHelper(context, runtime)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            try {
                repeat(3) { session ->
                    val captured = CountDownLatch(1)
                    lateinit var preview: PreviewView
                    activity.onActivity {
                        helper.setScreenActive(true)
                        helper.setAutoCapture(false)
                        helper.setAnalysisEnabled(true)
                        helper.onAutoCapture = { photo ->
                            // Simulate showing the result. Do not send test photos to the API.
                            helper.setAnalysisEnabled(false)
                            photo.delete()
                            captured.countDown()
                        }
                        preview = PreviewView(it)
                        it.setContentView(preview)
                        helper.bindToLifecycle(it, preview)
                    }
                    // New preview must be bound before requesting capture.
                    runBlocking {
                        withTimeout(15_000) { helper.detectorPreview.first { it.milliseconds > 0f } }
                    }
                    activity.onActivity {
                        helper.setAutoCapture(true)
                        helper.displayFrame(LabelFrame(
                            boxes = listOf(LabelBox(.25f, .25f, .75f, .75f, .9f)),
                            trigger = true, transform = null
                        ), preview)
                    }
                    assertTrue("Capture $session must complete without a transform", captured.await(10, TimeUnit.SECONDS))
                }
            } finally { activity.onActivity { helper.shutdown() } }
        }
    }
}
