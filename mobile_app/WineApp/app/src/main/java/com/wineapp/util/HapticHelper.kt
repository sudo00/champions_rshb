package com.wineapp.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Нарастающее ожидание сканирования и короткие отклики на результат/действия.
 * Работает с API 26 (minSdk проекта) без дополнительных проверок версий.
 */
object HapticHelper {

    /** One unbroken, softly eased rise. No repeated tail or intermediate off segments. */
    fun startScanWaiting(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            val effect = if (vibrator.hasAmplitudeControl()) {
                // Fine steps approximate a continuous envelope on amplitude-only motors.
                // The response normally cancels this early; 60 s also bounds a stalled request.
                val timings = LongArray(3000) { 20L }
                val amplitudes = IntArray(timings.size) { index ->
                    val seconds = index * .02
                    (12 + 78 * (1 - exp(-seconds * seconds / 32))).roundToInt()
                }
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                // Avoid a continuous full-strength buzz on devices without amplitude control.
                VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            android.util.Log.e("HapticHelper", "Scan haptic failed", e)
        }
    }

    fun stopScanWaiting(context: Context) {
        try {
            vibrator(context)?.cancel()
        } catch (e: Exception) {
            android.util.Log.e("HapticHelper", "Stopping scan haptic failed", e)
        }
    }

    /** The answer lands with a crisp peak and a short tail, without a second pulse. */
    fun vibrateScanResult(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(
                    longArrayOf(0, 20, 12, 8, 6, 8),
                    intArrayOf(0, 230, 130, 60, 20, 0),
                    -1
                )
            } else {
                VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            android.util.Log.e("HapticHelper", "Result haptic failed", e)
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return v?.takeIf { it.hasVibrator() }
    }

    /** Лёгкий тик для пролетающих ячеек рулетки. */
    fun vibrateTick(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20L, 120))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20L)
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticHelper", "Tick failed", e)
        }
    }

    fun vibrateSuccess(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            // Уверенный долгий отклик: два нарастающих импульса (~0.5 с суммарно).
            val timings = longArrayOf(0L, 180L, 100L, 300L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 200, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticHelper", "Vibration failed", e)
        }
    }
}
