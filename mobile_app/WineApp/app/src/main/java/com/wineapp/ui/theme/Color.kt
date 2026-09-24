package com.wineapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple700 = Color(0xFF7B61FF)
val Purple500 = Color(0xFF9B7FFF)
val Teal200 = Color(0xFF03DAC5)
val Teal700 = Color(0xFF018786)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariantDark = Color(0xFF2C2C2C)
val RatingGold = Color(0xFFFFD700)

// Фирменная палитра из фигмы (Brand/*). Используется экранами онбординга/гейта,
// в Material-схему не входит — применяется точечно.
val BrandCream50 = Color(0xFFFEFDF9)
val BrandCream100 = Color(0xFFFEFAEC)
val BrandCream300 = Color(0xFFF8EDC9)
val BrandCream400 = Color(0xFFDFC795)
val BrandCream500 = Color(0xFFD8B76A)
val BrandBurgundy600 = Color(0xFF8E3C42)
val BrandBurgundy700 = Color(0xFF713035)
val BrandTextPrimary = Color(0xFF292925)
val BrandTextSecondary = Color(0xFF69645F)

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Purple700,
    primaryContainer = Purple500,
    secondary = Teal200,
    secondaryContainer = Teal700,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    error = Color(0xFFCF6679),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B0B0),
    onError = Color.White,
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFF505050)
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Purple700,
    primaryContainer = Color(0xFFE8E0FF),
    secondary = Teal700,
    secondaryContainer = Color(0xFFCCF3F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F0),
    error = Color(0xFFCF6679),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF404040),
    onError = Color.White,
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFFC0C0C0)
)

@Composable
fun WineAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}