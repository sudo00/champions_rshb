package com.wineapp.util

import com.wineapp.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun apiImageUrl(path: String?): String? =
    path?.let { BuildConfig.BASE_URL.toHttpUrlOrNull()?.resolve(it)?.toString() }
