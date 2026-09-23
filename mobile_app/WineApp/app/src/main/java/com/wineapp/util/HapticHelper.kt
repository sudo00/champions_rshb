package com.wineapp.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Короткий виброотклик на успешное сканирование.
 * Работает с API 26 (minSdk проекта) без дополнительных проверок версий.
 */
object HapticHelper {

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
