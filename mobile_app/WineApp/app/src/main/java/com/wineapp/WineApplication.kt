package com.wineapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

const val USE_MOCK = false

@HiltAndroidApp
class WineApplication : Application() {
    @javax.inject.Inject lateinit var labelDetectorRuntime: com.wineapp.data.detector.LabelDetectorRuntime

    override fun onCreate() {
        super.onCreate()
        labelDetectorRuntime.preload()
    }
}
