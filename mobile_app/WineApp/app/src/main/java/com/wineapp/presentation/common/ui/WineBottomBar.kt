package com.wineapp.presentation.common.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
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
    MY_WINES("my_wines", R.string.nav_mywines, R.drawable.wine_bottle_and_glass, R.drawable.wine_bottle_and_glass_filled),
    MAP("wine_path", R.string.nav_map, R.drawable.map_marker, R.drawable.map_marker_filled)
}

/** Высота нижнего бара без системной навигации: отступы 12 + кнопка 68 + 12. */
private val BottomBarHeight = 92.dp

/** На сколько выше бара начинается растворение контента в фоне. */
private val BottomBarFadeAbove = 24.dp

/**
 * Место под висящий поверх контента нижний бар — в конец прокручиваемого экрана-таба.
 * Учитывает системную навигацию (жесты/кнопки), под которой бар стоит, плюс
 * небольшой зазор, чтобы последняя кнопка не прилипала к бару.
 */
@Composable
fun BottomBarSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .navigationBarsPadding()
            .height(BottomBarHeight + 16.dp)
    )
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
            // Имитация градиентного размытия: контент под баром плавно «растворяется»
            // в кремовом фоне экранов-табов. Настоящий backdrop blur требует Compose 1.7 + Haze.
            // Градиент начинается выше бара (рисуем за его границей — клипа нет),
            // к уровню кнопок почти непрозрачен, внизу (под системной навигацией) — сплошной.
            .drawBehind {
                val fadeAbove = BottomBarFadeAbove.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to BrandCream50.copy(alpha = 0f),
                        0.45f to BrandCream50.copy(alpha = 0.85f),
                        1f to BrandCream50,
                        startY = -fadeAbove,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, -fadeAbove),
                    size = Size(size.width, size.height + fadeAbove)
                )
            }
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
