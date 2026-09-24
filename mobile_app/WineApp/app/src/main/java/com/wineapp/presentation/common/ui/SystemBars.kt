package com.wineapp.presentation.common.ui

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Прозрачные системные панели: фон экрана тянется под статус-бар и кнопки навигации.
 * @param isAppearanceLightStatusBars true — тёмные иконки статус-бара (для светлого фона).
 * @param isAppearanceLightNavigationBars true — тёмные кнопки навигации (для светлого фона).
 */
@Composable
fun TransparentSystemBars(
    statusBarColor: Color = Color.Transparent,
    navigationBarColor: Color = Color.Transparent,
    isAppearanceLightStatusBars: Boolean = true,
    isAppearanceLightNavigationBars: Boolean = true
) {
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = statusBarColor.toArgb()
            window.navigationBarColor = navigationBarColor.toArgb()
            // Иначе с API 29+ система принудительно заливает навбар светлым скрамом
            // поверх прозрачности (minSdk 26 — вызываем только где есть API).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                isAppearanceLightStatusBars
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                isAppearanceLightNavigationBars
        }
    }
}
