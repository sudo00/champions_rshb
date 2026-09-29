package com.wineapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wineapp.presentation.navigation.AppNavHost
import com.wineapp.presentation.widget.RecentWinesWidget
import com.wineapp.ui.theme.WineAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Роут, открытый с виджета; NavHost переходит на него поверх главной и сбрасывает в null. */
    private var widgetRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // При пересоздании активити бэкстек восстанавливается сам — интент повторно не обрабатываем.
        if (savedInstanceState == null) widgetRoute = resolveWidgetRoute(intent)
        setContent {
            WineAppTheme {
                AppNavHost(
                    widgetRoute = widgetRoute,
                    onWidgetRouteHandled = { widgetRoute = null }
                )
            }
        }
    }

    /** Приложение уже запущено (launchMode=singleTask) — клик по виджету приходит сюда. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveWidgetRoute(intent)?.let { widgetRoute = it }
    }

    /**
     * Глубокий вход с виджета: ActionParameters виджета приезжают как extras интента.
     * Возвращает роут для перехода либо null для обычного запуска.
     */
    private fun resolveWidgetRoute(intent: Intent?): String? {
        val destination = intent?.getStringExtra(RecentWinesWidget.EXTRA_DESTINATION)
            ?: return null
        return when (destination) {
            RecentWinesWidget.DEST_SCANNER -> "scanner"
            RecentWinesWidget.DEST_SCAN -> intent
                .getStringExtra(RecentWinesWidget.EXTRA_SCAN_ID)
                ?.takeIf { it.isNotBlank() }
                ?.let { "saved_scan_detail/${Uri.encode(it)}" }
            else -> null
        }
    }
}
