package com.wineapp.presentation.common.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineapp.ui.theme.BrandBurgundy300
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.WineAppTheme

/**
 * Фирменный лоадер: бордовая дуга крутится по светлому треку,
 * внутри «дышит» бутылка. Заменяет дефолтный Material-спиннер.
 */
@Composable
fun BrandLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val transition = rememberInfiniteTransition(label = "brandLoader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation"
    )
    // Дуга то растёт, то сжимается — живее равномерного вращения.
    val sweep by transition.animateFloat(
        initialValue = 40f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
            // this.size — размер канваса (DrawScope), а не Dp-параметр лоадера.
            val strokeWidth = this.size.minDimension / 16f
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            // Дугу вписываем внутрь с отступом в полтолщины, чтобы штрих не вылезал за границы.
            val inset = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            drawArc(
                color = BrandBurgundy300.copy(alpha = 0.35f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = inset,
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = BrandBurgundy600,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = inset,
                size = arcSize,
                style = stroke
            )
        }
        Icon(
            AppIcons.WineBottle,
            contentDescription = null,
            tint = BrandBurgundy600,
            modifier = Modifier
                .size(size * 0.42f)
                .scale(pulse)
        )
    }
}

/** Полноэкранная загрузка на кремовом фоне приложения. */
@Composable
fun LoadingOverlay(message: String = "", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandCream50),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BrandLoader()
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = BrandTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview() {
    WineAppTheme { LoadingOverlay(message = "Загружаем вино…") }
}
