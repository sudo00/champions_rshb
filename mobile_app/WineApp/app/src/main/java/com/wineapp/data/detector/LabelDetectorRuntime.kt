package com.wineapp.data.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Process-wide model. GPU creation, warmup and camera inference share one thread. */
@Singleton
class LabelDetectorRuntime @Inject constructor(@ApplicationContext private val context: Context) {
    private var owner: Thread? = null
    val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "WineLabelInference").also { owner = it }
    }
    private val started = AtomicBoolean(false)
    private var model: LabelDetector? = null
    private var failure: Exception? = null

    fun preload() {
        if (!started.compareAndSet(false, true)) return
        val requested = SystemClock.elapsedRealtime()
        executor.execute {
            var candidate: LabelDetector? = null
            try {
                val begin = SystemClock.elapsedRealtime()
                val detector = LabelDetector(context)
                candidate = detector
                val loaded = SystemClock.elapsedRealtime()
                val sample = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
                val durations = mutableListOf<Long>()
                try {
                    sample.eraseColor(Color.rgb(114, 114, 114))
                    repeat(3) {
                        val start = SystemClock.elapsedRealtime()
                        detector.detect(sample)
                        durations += SystemClock.elapsedRealtime() - start
                    }
                } finally { sample.recycle() }
                model = detector
                Log.i("WineLabelDetector", "ready backend=${detector.backend} loadMs=${loaded - begin} " +
                    "warmupMs=$durations totalMs=${SystemClock.elapsedRealtime() - requested}")
            } catch (error: Exception) {
                failure = error
                candidate?.close()
                Log.e("WineLabelDetector", "Preload failed; manual capture remains available", error)
            }
        }
    }

    /** Only call from [executor]; preload is queued before any camera analysis. */
    fun detector(): LabelDetector {
        check(Thread.currentThread() === owner) { "Detector must run on its owning executor" }
        return model ?: throw IllegalStateException("Detector is unavailable", failure)
    }

    // Retain model while the process lives, including navigation and background/foreground.
    // Android reclaims native resources when the process dies; no background inference loop.
}
