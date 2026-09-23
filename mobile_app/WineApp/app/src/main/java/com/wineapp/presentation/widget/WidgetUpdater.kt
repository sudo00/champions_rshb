package com.wineapp.presentation.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Обновление всех экземпляров виджета. Вызывать после сохранения нового скана. */
object WidgetUpdater {

    suspend fun updateAll(context: Context) {
        try {
            withContext(Dispatchers.IO) {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(RecentWinesWidget::class.java)
                glanceIds.forEach { glanceId ->
                    RecentWinesWidget().update(context, glanceId)
                }
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "Widget update failed", e)
        }
    }
}
