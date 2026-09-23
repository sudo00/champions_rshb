package com.wineapp.presentation.widget

import com.wineapp.domain.repository.ScanHistoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt-доступ для виджета: GlanceAppWidget создаётся системой,
 * поэтому @AndroidEntryPoint здесь ненадёжен — используем EntryPoint.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun scanHistoryRepository(): ScanHistoryRepository
}
