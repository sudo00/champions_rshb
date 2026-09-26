package com.wineapp.presentation.common.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Пилюля-информер из фигмы (Type=Success/Warning/Error):
 * иконка 20dp + Caption Medium (Inter 600, 12/16), паддинги 4/8/4/4, зазор 4dp.
 */
enum class BadgeType(
    val background: Color,
    val main: Color,
    val icon: ImageVector?
) {
    SUCCESS(
        background = Color(0xFFEDF2E8),
        main = Color(0xFF657A57),
        icon = Icons.Default.CheckCircle
    ),
    WARNING(
        background = Color(0xFFF8EFD6),
        main = Color(0xFFB1873E),
        icon = Icons.Default.Warning
    ),
    ERROR(
        background = Color(0xFFF7E5E1),
        main = Color(0xFFB65349),
        icon = Icons.Default.Error
    ),
    NEUTRAL(
        background = Color(0xFFF2EEE8),
        main = Color(0xFF6F6962),
        icon = null
    )
}

@Composable
fun WineBadge(
    type: BadgeType,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = type.background,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            type.icon?.let { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    tint = type.main,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = type.main
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WineBadgePreview() {
    com.wineapp.ui.theme.WineAppTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            WineBadge(type = BadgeType.SUCCESS, text = "Успешно")
            WineBadge(type = BadgeType.WARNING, text = "Предупреждение")
            WineBadge(type = BadgeType.ERROR, text = "Ошибка")
        }
    }
}
