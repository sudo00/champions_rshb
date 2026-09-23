package com.wineapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wineapp.presentation.navigation.AppNavHost
import com.wineapp.presentation.widget.RecentWinesWidget
import com.wineapp.ui.theme.WineAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startRoute = resolveStartRoute(intent)
        setContent {
            WineAppTheme {
                AppNavHost(startRoute = startRoute)
            }
        }
    }

    /**
     * Глубокий вход с виджета: ActionParameters виджета приезжают как extras интента.
     * Возвращает стартовый роут NavHost либо null для обычного запуска ("search").
     */
    private fun resolveStartRoute(intent: Intent?): String? {
        val destination = intent?.getStringExtra(RecentWinesWidget.EXTRA_DESTINATION)
            ?: return null
        return when (destination) {
            RecentWinesWidget.DEST_SCANNER -> "scanner"
            RecentWinesWidget.DEST_DETAIL -> intent
                .getStringExtra(RecentWinesWidget.EXTRA_WINE_ID)
                ?.takeIf { it.isNotBlank() }
                ?.let { "detail/$it" }
            else -> null
        }
    }
}
