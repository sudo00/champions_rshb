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
val BrandCream200 = Color(0xFFF8F0D6)
val BrandCream300 = Color(0xFFF8EDC9)
val BrandCream400 = Color(0xFFDFC795)
val BrandCream500 = Color(0xFFD8B76A)
val BrandBurgundy300 = Color(0xFFD9A8AC)
val BrandBurgundy600 = Color(0xFF8E3C42)
val BrandBurgundy700 = Color(0xFF713035)
val BrandTextPrimary = Color(0xFF292925)
val BrandTextSecondary = Color(0xFF69645F)
val BrandTextTertiary = Color(0xFFA7A29D)

// System/Warning из фигмы — плашка в заголовке карты.
val WarningBackground = Color(0xFFF8EFD6)
val WarningMain = Color(0xFFB1873E)
val BrandBorderLight = Color(0xFFF7F2E6)
val BrandDivider = Color(0x66A7A29D)
val BrandOutline = Color(0xFF79747E)
val BrandBorderDefault = Color(0xFFEAE1C9)

// Палитра карты «Винного пути» из фигмы. Значения ссылаются на бренд-токены,
// чтобы не расходились с остальными экранами.
val MapBackground = Color(0xFF292925)
val MapRegionFill = BrandTextTertiary
val MapRegionBorder = BrandCream50
val MapWineFill = BrandBurgundy300
val MapSelectedFill = BrandBurgundy600
val MapSelectedBorder = BrandCream300

// Редизайн шторки «Винного пути»: полупрозрачные белые карточки и плитки наград.
val SheetCardSurface = Color(0xE6FFFFFF)
val SheetSurfaceTop = Color(0xE6FFFFFF)
// Низ шторки непрозрачный и темнее верха: карта сквозь него не просвечивает.
val SheetSurfaceBottom = Color(0xFF383838)
val TileBorder = Color(0xCCF3F2F1)
val RewardLockedTile = Color(0xFFEAEAEA)
val RewardLockedText = Color(0xFFA9A9A9)
val RewardBronze = Color(0xFFEA6320)
val RewardBronzeSoft = Color(0x1AEA6320)
val RewardSilver = Color(0xFFAFAFAF)
val RewardSilverSoft = Color(0x1AAFAFAF)
val RewardGold = Color(0xFFEAB020)
val RewardGoldSoft = Color(0x1AEAB020)

// Градиенты медальона (147° от левого верхнего угла).
val MedalBronzeTop = Color(0xFFFFBC92)
val MedalBronzeBottom = Color(0xFFB95D00)
val MedalSilverTop = Color(0xFFF0F0F0)
val MedalSilverBottom = Color(0xFF888888)
val MedalGoldTop = Color(0xFFFFE092)
val MedalGoldBottom = Color(0xFFE3A302)
val MedalLocked = Color(0xFFACACAC)

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