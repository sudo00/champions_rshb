package com.wineapp.presentation.common.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.domain.model.Wine
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream200
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/*
 * Общие блоки экранов-списков вин из макетов «Коллекция» и «Сканы»:
 * шапка с поиском, сортировка/фильтр, заголовки месяцев, карточка вина.
 */

/** Порядок карточек в списке. По дате — с разбивкой по месяцам. */
enum class WineListSort(@StringRes val labelRes: Int) {
    NEWEST(R.string.list_sort_newest),
    OLDEST(R.string.list_sort_oldest),
    RATING(R.string.list_sort_rating),
    NAME(R.string.list_sort_name);

    val groupsByMonth: Boolean get() = this == NEWEST || this == OLDEST
}

val WineListBodyM = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    color = BrandTextPrimary
)

fun yearMonthOf(millis: Long): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val MonthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))

fun monthLabel(month: YearMonth): String =
    month.format(MonthFormatter).replaceFirstChar { it.titlecase(Locale("ru")) }

/**
 * Шапка: «назад» (Overlay 30), заголовок H3 по центру, бордовая кнопка поиска.
 * Поиск встроен в шапку: по кнопке поле выезжает влево из-под неё и занимает
 * всю шапку (заголовок растворяется, «назад» уезжает влево), кнопка становится
 * крестиком — по нему поле въезжает обратно, заголовок и «назад» возвращаются.
 */
@Composable
fun WineListHeader(
    title: String,
    searchOpen: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    query: String = "",
    searchHint: String = "",
    onQuery: (String) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        // Сразу фокус и клавиатура — ради этого поле и открывали.
        if (searchOpen) {
            delay(SEARCH_ANIM_MS.toLong())
            runCatching { focusRequester.requestFocus() }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // «Назад» уезжает влево на время поиска — поле получает всю ширину шапки.
        androidx.compose.animation.AnimatedVisibility(
            visible = !searchOpen,
            enter = expandHorizontally(tween(SEARCH_ANIM_MS), expandFrom = Alignment.Start) +
                fadeIn(tween(SEARCH_ANIM_MS)),
            exit = shrinkHorizontally(tween(SEARCH_ANIM_MS), shrinkTowards = Alignment.Start) +
                fadeOut(tween(SEARCH_ANIM_MS / 2))
        ) {
            BackCircleButton(onClick = onBack)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            // Полное имя: внутри Row неявный RowScope подсовывает перегрузку
            // RowScope.AnimatedVisibility, которую из Box вызвать нельзя.
            androidx.compose.animation.AnimatedVisibility(
                visible = !searchOpen,
                enter = fadeIn(tween(SEARCH_ANIM_MS)),
                exit = fadeOut(tween(SEARCH_ANIM_MS / 2))
            ) {
                Text(
                    text = title,
                    fontFamily = Playfair,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    color = BrandTextPrimary,
                    maxLines = 1
                )
            }
            // Поле растёт от правого края (от кнопки поиска) влево и так же схлопывается.
            androidx.compose.animation.AnimatedVisibility(
                visible = searchOpen,
                enter = expandHorizontally(tween(SEARCH_ANIM_MS), expandFrom = Alignment.End) +
                    fadeIn(tween(SEARCH_ANIM_MS)),
                exit = shrinkHorizontally(tween(SEARCH_ANIM_MS), shrinkTowards = Alignment.End) +
                    fadeOut(tween(SEARCH_ANIM_MS)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Слева — край шапки («назад» спрятан), справа — зазор до крестика.
                    .padding(end = 8.dp)
            ) {
                WineListSearchField(
                    query = query,
                    hint = searchHint,
                    onQuery = onQuery,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }
        }
        Surface(
            onClick = onSearchClick,
            shape = CircleShape,
            color = BrandBurgundy600,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (searchOpen) AppIcons.Close else AppIcons.Search,
                    contentDescription = stringResource(R.string.list_search),
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Длительность выезда/въезда поля поиска в шапке, мс. */
private const val SEARCH_ANIM_MS = 280

/** Поле поиска в шапке — пилюля высотой с кнопки шапки (44dp). */
@Composable
fun WineListSearchField(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = BrandCream50,
        border = BorderStroke(1.dp, BrandBorderDefault),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                AppIcons.Search,
                contentDescription = null,
                tint = BrandTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        hint,
                        style = WineListBodyM.copy(color = BrandTextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = WineListBodyM,
                    cursorBrush = SolidColor(BrandBurgundy600),
                    // Внешний modifier — на само поле: на нём висит FocusRequester из шапки.
                    modifier = modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Кнопки-текст «сортировка» и «фильтры» (Label L, Burgundy 600) с выпадающими меню. */
@Composable
fun WineListSortFilterRow(
    sort: WineListSort,
    styles: List<String>,
    selectedStyle: String?,
    onSort: (WineListSort) -> Unit,
    onStyleFilter: (String?) -> Unit
) {
    SortFilterRow(
        sort = sort,
        sortOptions = WineListSort.entries,
        defaultSortLabel = null,
        styles = styles,
        selectedStyle = selectedStyle,
        onSort = { it?.let(onSort) },
        onStyleFilter = onStyleFilter
    )
}

/**
 * Ряд сортировки/фильтра с настраиваемым набором сортировок.
 * [defaultSortLabel] != null — в меню есть пункт «по умолчанию» (sort = null),
 * а кнопка без выбранной сортировки подписана «Сортировка».
 */
@Composable
fun SortFilterRow(
    sort: WineListSort?,
    sortOptions: List<WineListSort>,
    defaultSortLabel: String?,
    styles: List<String>,
    selectedStyle: String?,
    onSort: (WineListSort?) -> Unit,
    onStyleFilter: (String?) -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            WineListTextButton(
                text = sort?.let { stringResource(it.labelRes) } ?: stringResource(R.string.search_sort),
                icon = AppIcons.Sort,
                onClick = { sortMenu = true }
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                if (defaultSortLabel != null) {
                    DropdownMenuItem(
                        text = { Text(defaultSortLabel) },
                        onClick = {
                            onSort(null)
                            sortMenu = false
                        }
                    )
                }
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            onSort(option)
                            sortMenu = false
                        }
                    )
                }
            }
        }
        if (styles.isNotEmpty()) {
            Box {
                WineListTextButton(
                    text = selectedStyle ?: stringResource(R.string.list_filters),
                    icon = AppIcons.Filter,
                    onClick = { filterMenu = true }
                )
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_filter_all_styles)) },
                        onClick = {
                            onStyleFilter(null)
                            filterMenu = false
                        }
                    )
                    styles.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style) },
                            onClick = {
                                onStyleFilter(style)
                                filterMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WineListTextButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = Color.Transparent,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = BrandBurgundy600,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = BrandBurgundy600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Табы-фильтры из макета (Tabs): Cream 100 с обводкой и тенью, активный — Burgundy 600.
 * Что делать с повторным тапом по активному табу, решает вызывающий.
 */
@Composable
fun WineListTabs(
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(percent = 50),
                color = if (isSelected) BrandBurgundy600 else BrandCream100,
                border = if (isSelected) null else BorderStroke(1.dp, BrandBorderDefault),
                shadowElevation = 3.dp,
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        label,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        color = if (isSelected) BrandCream50 else BrandBurgundy600
                    )
                }
            }
        }
    }
}

/** Заголовок группы «Сентябрь 2026» (Label L, Text/Secondary). */
@Composable
fun WineListMonthHeader(month: YearMonth, isFirst: Boolean) {
    Text(
        monthLabel(month),
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = BrandTextSecondary,
        modifier = Modifier.padding(top = if (isFirst) 32.dp else 24.dp, bottom = 24.dp)
    )
}

@Composable
fun WineListNothingFound(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Text(
            stringResource(R.string.list_nothing_found),
            fontFamily = Playfair,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            color = BrandTextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.list_nothing_found_hint),
            fontFamily = Inter,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = BrandTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Карточка из макета (Scan card): фото 80×120 слева, справа контент [headline],
 * снизу тег [tag] и [bottomEnd] (степпер/корзина). [topEndAction] — круглая
 * кнопка 36dp в правом верхнем углу (сердечко), под неё резервируется отступ.
 */
@Composable
fun WineListCard(
    imageModel: Any?,
    imageDescription: String?,
    onClick: () -> Unit,
    headline: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    /** Свой тег вместо текстового [tag] (например, чип с выпадающим меню). */
    tagContent: (@Composable () -> Unit)? = null,
    containerColor: Color = BrandCream100,
    imageContentScale: ContentScale = ContentScale.Fit,
    imageOverlay: @Composable BoxScope.() -> Unit = {},
    topEndAction: (@Composable () -> Unit)? = null,
    bottomEnd: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(modifier = Modifier.size(width = 80.dp, height = 120.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (imageModel == null) BrandCream200 else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = imageDescription,
                                contentScale = imageContentScale,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                AppIcons.WineBottle,
                                contentDescription = null,
                                tint = BrandTextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    imageOverlay()
                }
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 120.dp)
                        .padding(top = 8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(end = if (topEndAction != null) 36.dp else 0.dp),
                        content = headline
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (tagContent != null) {
                            Box(modifier = Modifier.weight(1f, fill = false)) { tagContent() }
                            Spacer(Modifier.width(8.dp))
                        } else if (!tag.isNullOrEmpty()) {
                            WineListTag(tag, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        bottomEnd()
                    }
                }
            }
            if (topEndAction != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) { topEndAction() }
            }
        }
    }
}

/** Badge / Tag: пилюля Cream 50 с обводкой Border/Default, Caption Medium. */
@Composable
fun WineListTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = BrandCream50,
        border = BorderStroke(1.dp, BrandBorderDefault),
        modifier = modifier
    ) {
        Text(
            text,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = BrandTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

/** Верх карточки вина: винодельня, название, регион с флагом, рейтинг с отзывами. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WineListHeadline(wine: Wine) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        wine.winery?.let { WineListCaption(it) }
        Text(
            wine.name,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            color = BrandTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    val place = listOfNotNull(wine.region, wine.country).distinct().joinToString(", ")
    if (place.isNotEmpty() || wine.rating != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (place.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountryFlag()
                    WineListCaption(place)
                }
            }
            wine.rating?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        AppIcons.Star,
                        contentDescription = null,
                        tint = BrandCream500,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        com.wineapp.util.formatRating(rating),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextPrimary
                    )
                    wine.reviewsCount?.takeIf { it > 0 }?.let { WineListCaption("($it)") }
                }
            }
        }
    }
}

@Composable
fun WineListCaption(text: String, maxLines: Int = 1) {
    Text(
        text,
        fontFamily = Inter,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = BrandTextSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** Круглая прозрачная иконка-кнопка 36dp (сердечко, корзина на карточке). */
@Composable
fun WineListIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
