package com.wineapp.util

/** Main-thread lifecycle for one scan, including a gallery result arriving before RESUMED. */
class ScanHapticSession(private val start: () -> Unit, private val stop: () -> Unit) {
    private var foreground = false
    private var requested = false
    private var started = false
    private var running = false

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active && running) {
            stop()
            running = false
        }
        startIfReady()
    }

    fun begin() {
        requested = true
        startIfReady()
    }

    fun finish() {
        if (running) stop()
        requested = false
        started = false
        running = false
    }

    private fun startIfReady() {
        if (foreground && requested && !started) {
            started = true
            running = true
            start()
        }
    }
}
