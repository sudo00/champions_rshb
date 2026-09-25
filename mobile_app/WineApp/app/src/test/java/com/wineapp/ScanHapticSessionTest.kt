package com.wineapp

import com.wineapp.util.ScanHapticSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanHapticSessionTest {
    @Test fun galleryResultBeforeResumeStartsWhenScannerBecomesActive() {
        val events = mutableListOf<String>()
        val session = ScanHapticSession({ events += "start" }, { events += "stop" })
        session.begin()
        assertEquals(emptyList<String>(), events)
        session.setForeground(true)
        session.begin() // Import finished, network recognition starts: keep the same wave.
        session.setForeground(true)
        assertEquals(listOf("start"), events)
        session.finish()
        session.finish()
        assertEquals(listOf("start", "stop"), events)
    }

    @Test fun completedOrCancelledGalleryWorkCannotVibrateOnLaterResume() {
        var starts = 0
        val session = ScanHapticSession({ starts++ }, {})
        session.begin()
        session.finish()
        session.setForeground(true)
        assertEquals(0, starts)
    }

    @Test fun backgroundStopsWithoutRestartingTheSameWaveAndNextScanCanStart() {
        val events = mutableListOf<String>()
        val session = ScanHapticSession({ events += "start" }, { events += "stop" })
        session.setForeground(true)
        session.begin()
        session.setForeground(false)
        session.setForeground(true)
        assertEquals(listOf("start", "stop"), events)
        session.finish()
        session.begin()
        session.finish()
        assertEquals(listOf("start", "stop", "start", "stop"), events)
    }
}
