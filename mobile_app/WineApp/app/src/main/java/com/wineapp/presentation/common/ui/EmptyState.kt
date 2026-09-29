package com.wineapp.presentation.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream200
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme

/**
 * Пустое состояние экранов-списков в фирменном стиле: бордовая иконка раздела
 * в кремовых кольцах, заголовок Playfair, пояснение и (опционально) кнопка —
 * следующий шаг, чтобы наполнить раздел.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Две окружности: внешнее мягкое кольцо и плотный круг с иконкой.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(BrandCream100)
                .border(1.dp, BrandBorderDefault, CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BrandCream200)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = BrandBurgundy600,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = BrandTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = BrandTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(28.dp))
            BrandButton(
                text = actionText,
                onClick = onAction,
                leadingIcon = actionIcon
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFEFDF9)
@Composable
private fun EmptyStatePreview() {
    WineAppTheme {
        Box(modifier = Modifier.background(BrandCream50).padding(vertical = 48.dp)) {
            EmptyState(
                icon = AppIcons.Heart,
                title = "Избранного пока нет",
                message = "Нажмите сердечко на карточке вина, чтобы добавить",
                actionText = "Найти вино",
                actionIcon = AppIcons.Search,
                onAction = {}
            )
        }
    }
}
