package com.wineapp.presentation.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wineapp.ui.theme.BrandBorderLight

/**
 * Круглый флаг страны для карточек вин. Рисуется канвасом по ISO-коду,
 * неизвестные страны — нейтральный кремовый круг.
 */
@Composable
fun CountryFlag(
    country: String?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .border(0.5.dp, BrandBorderLight, CircleShape)
            .clip(CircleShape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            when (countryCode(country)) {
                "RU" -> {
                    drawRect(Color.White, size = Size(w, h / 3))
                    drawRect(Color(0xFF0039A6), topLeft = Offset(0f, h / 3), size = Size(w, h / 3))
                    drawRect(Color(0xFFD52B1E), topLeft = Offset(0f, 2 * h / 3), size = Size(w, h / 3))
                }
                "FR" -> {
                    drawRect(Color(0xFF0055A4), size = Size(w / 3, h))
                    drawRect(Color.White, topLeft = Offset(w / 3, 0f), size = Size(w / 3, h))
                    drawRect(Color(0xFFEF4135), topLeft = Offset(2 * w / 3, 0f), size = Size(w / 3, h))
                }
                "IT" -> {
                    drawRect(Color(0xFF009246), size = Size(w / 3, h))
                    drawRect(Color.White, topLeft = Offset(w / 3, 0f), size = Size(w / 3, h))
                    drawRect(Color(0xFFCE2B37), topLeft = Offset(2 * w / 3, 0f), size = Size(w / 3, h))
                }
                "ES" -> {
                    drawRect(Color(0xFFAA151B), size = Size(w, h / 4))
                    drawRect(Color(0xFFF1BF00), topLeft = Offset(0f, h / 4), size = Size(w, h / 2))
                    drawRect(Color(0xFFAA151B), topLeft = Offset(0f, 3 * h / 4), size = Size(w, h / 4))
                }
                "GE" -> {
                    drawRect(Color.White, size = this.size)
                    val bar = w / 7
                    drawRect(Color(0xFFFF0000), topLeft = Offset(w / 2 - bar / 2, 0f), size = Size(bar, h))
                    drawRect(Color(0xFFFF0000), topLeft = Offset(0f, h / 2 - bar / 2), size = Size(w, bar))
                }
                "CL" -> {
                    drawRect(Color.White, topLeft = Offset(0f, 0f), size = Size(w, h / 2))
                    drawRect(Color(0xFFD52B1E), topLeft = Offset(0f, h / 2), size = Size(w, h / 2))
                    drawRect(Color(0xFF0039A6), size = Size(w / 3, h / 2))
                }
                "AR" -> {
                    drawRect(Color(0xFF74ACDF), size = Size(w, h / 3))
                    drawRect(Color.White, topLeft = Offset(0f, h / 3), size = Size(w, h / 3))
                    drawRect(Color(0xFF74ACDF), topLeft = Offset(0f, 2 * h / 3), size = Size(w, h / 3))
                }
                else -> {
                    drawRect(Color(0xFFF2EEE8), size = this.size)
                }
            }
        }
    }
}

private fun countryCode(country: String?): String = when (country?.lowercase()) {
    "россия", "russia" -> "RU"
    "франция", "france" -> "FR"
    "италия", "italy" -> "IT"
    "испания", "spain" -> "ES"
    "грузия", "georgia" -> "GE"
    "чили", "chile" -> "CL"
    "аргентина", "argentina" -> "AR"
    else -> ""
}
