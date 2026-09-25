package com.wineapp.data.local

import android.content.Context

/** First-launch age gate, shared by the existing navigation and age screen. */
object AgeGatePrefs {
    fun isVerified(context: Context): Boolean =
        context.getSharedPreferences("age_gate", Context.MODE_PRIVATE).getBoolean("verified", false)

    fun setVerified(context: Context, verified: Boolean = true) {
        context.getSharedPreferences("age_gate", Context.MODE_PRIVATE).edit().putBoolean("verified", verified).apply()
    }
}
