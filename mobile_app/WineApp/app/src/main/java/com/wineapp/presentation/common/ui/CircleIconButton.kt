package com.wineapp.presentation.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wineapp.ui.theme.BrandCream50

/** Подложка круглых кнопок шапки: Overlay 30 (чёрный 30%) — читается и на светлом, и на фото. */
val CircleButtonOverlay = Color.Black.copy(alpha = 0.3f)

/**
 * Круглая кнопка шапки из макета: 44dp, подложка Overlay 30, кремовая иконка 24dp.
 * Единый вид для «назад», «закрыть», меню и т. п. на всех экранах.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = CircleButtonOverlay,
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = BrandCream50,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Кнопка «назад» — [CircleIconButton] с шевроном влево. */
@Composable
fun BackCircleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircleIconButton(icon = AppIcons.ChevronLeft, onClick = onClick, modifier = modifier)
}
