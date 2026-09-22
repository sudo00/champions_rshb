package com.wineapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

const val USE_MOCK = false

@HiltAndroidApp
class WineApplication : Application()