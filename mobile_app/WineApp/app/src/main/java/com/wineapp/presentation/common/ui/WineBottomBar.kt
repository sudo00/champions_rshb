package com.wineapp.presentation.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wineapp.R
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.WineAppTheme

/**
 * Нижняя навигация из фигмы: 5 круглых кнопок. Активная — белый круг
 * больше и ярче, остальные — полупрозрачные меньше.
 */
enum class BottomTab(
    val route: String,
    val labelRes: Int,
    @DrawableRes val iconRes: Int,
    /** Закрашенный вариант для активной вкладки (если есть в ресурсах). */
    @DrawableRes val selectedIconRes: Int = iconRes
) {
    HOME("search", R.string.nav_home, R.drawable.home, R.drawable.home_filled),
    SEARCH("search_tab", R.string.nav_search, R.drawable.search, R.drawable.search_filled),
    SCANNER("scanner", R.string.nav_scanner, R.drawable.camera_filled),
    MY_WINES("my_wines", R.string.nav_mywines, R.drawable.grape),
    MAP("wine_path", R.string.nav_map, R.drawable.map_marker, R.drawable.map_marker_filled)
}

@Composable
fun WineBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.2f)
                )
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab.entries.forEach { tab ->
                val isSelected = tab == selected
                // Сканер — акцентная бургунди-кнопка всегда, остальные по состоянию.
                val isScanner = tab == BottomTab.SCANNER
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                ) {
                    Surface(
                        onClick = { if (!isSelected) onSelect(tab) },
                        shape = CircleShape,
                        color = when {
                            isScanner -> BrandBurgundy600
                            isSelected -> Color.White
                            else -> Color(0xFFFFFEFA)
                        },
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(if (isSelected || isScanner) 68.dp else 60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                ImageVector.vectorResource(
                                    if (isSelected) tab.selectedIconRes else tab.iconRes
                                ),
                                contentDescription = stringResource(tab.labelRes),
                                tint = if (isScanner) BrandCream50 else BrandTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WineBottomBarPreview() {
    WineAppTheme {
        WineBottomBar(selected = BottomTab.HOME, onSelect = {})
    }
}
