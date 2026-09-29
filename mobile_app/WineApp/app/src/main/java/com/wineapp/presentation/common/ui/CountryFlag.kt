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
 * Круглый флаг для карточек вин. На платформе только российские вина
 * (а страну API не отдаёт), поэтому флаг всегда российский.
 */
@Composable
fun CountryFlag(
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
            drawRect(Color.White, size = Size(w, h / 3))
            drawRect(Color(0xFF0039A6), topLeft = Offset(0f, h / 3), size = Size(w, h / 3))
            drawRect(Color(0xFFD52B1E), topLeft = Offset(0f, 2 * h / 3), size = Size(w, h / 3))
        }
    }
}
