package com.wineapp.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Короткие отклики на результат/действия.
 * Работает с API 26 (minSdk проекта) без дополнительных проверок версий.
 */
object HapticHelper {

    /** The answer lands with a crisp peak and a short tail, without a second pulse. */
    fun vibrateScanResult(context: Context) {        try {
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

    /**
     * Мощная победная вибрация на успешный ответ бэка: тройной нарастающий
     * раскат ~0.7 с. Отличается от короткого vibrateScanResult именно весом.
     */
    fun vibrateVictory(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 120, 80, 150, 80, 300),
                        intArrayOf(0, 180, 0, 220, 0, 255),
                        -1
                    )
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 150, 80, 300), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 120, 80, 150, 80, 300), -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticHelper", "Victory haptic failed", e)
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
