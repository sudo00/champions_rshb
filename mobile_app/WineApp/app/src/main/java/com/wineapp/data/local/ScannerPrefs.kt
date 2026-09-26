package com.wineapp.data.local

import android.content.Context

/** Режим автораспознавания на экране сканера. Дефолт — вкл, как было раньше. */
object ScannerPrefs {
    private const val FILE = "scanner"
    private const val KEY_AUTO_CAPTURE = "auto_capture"

    fun isAutoCapture(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CAPTURE, true)

    fun setAutoCapture(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_CAPTURE, enabled).apply()
    }
}
