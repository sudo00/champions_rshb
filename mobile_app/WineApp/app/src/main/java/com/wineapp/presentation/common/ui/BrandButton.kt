package com.wineapp.presentation.common.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandBurgundy700
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream400
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.WineAppTheme

/**
 * Фирменная CTA-кнопка (пилюля 52dp, Burgundy 600, текст Cream 50 16/600).
 * Опциональные leading/trailing иконки 20dp в цвете текста.
 */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(percent = 50),
        color = if (pressed) BrandBurgundy700 else BrandBurgundy600,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = BrandCream50,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp)
                    )
                }
                Text(
                    text = text,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandCream50
                )
                if (trailingIcon != null) {
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        tint = BrandCream50,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFEFDF9)
@Composable
private fun BrandButtonPreview() {
    WineAppTheme {
        BrandButton(text = "Начать", onClick = {}, trailingIcon = AppIcons.ChevronRight)
    }
}

/**
 * Вторичная фирменная кнопка (пилюля 54dp, Cream 100, бордер Cream 400, текст Primary 16/600).
 */
@Composable
fun BrandSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = BrandCream100,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandCream400),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = BrandTextPrimary,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp)
                    )
                }
                Text(
                    text = text,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandTextPrimary
                )
                if (trailingIcon != null) {
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        tint = BrandTextPrimary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFEFDF9)
@Composable
private fun BrandSecondaryButtonPreview() {
    WineAppTheme {
        BrandSecondaryButton(text = "Загрузить фото", onClick = {})
    }
}
