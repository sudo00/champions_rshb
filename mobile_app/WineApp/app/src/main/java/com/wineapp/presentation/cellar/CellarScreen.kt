package com.wineapp.presentation.cellar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.local.CellarStatus
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.CellarItem
import com.wineapp.presentation.common.ui.AppIcons
import com.wineapp.presentation.common.ui.BrandLoader
import com.wineapp.presentation.common.ui.EmptyState
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WineListBodyM
import com.wineapp.presentation.common.ui.WineListCard
import com.wineapp.presentation.common.ui.WineListHeader
import com.wineapp.presentation.common.ui.WineListHeadline
import com.wineapp.presentation.common.ui.WineListMonthHeader
import com.wineapp.presentation.common.ui.WineListNothingFound
import com.wineapp.presentation.common.ui.WineListSort
import com.wineapp.presentation.common.ui.WineListSortFilterRow
import com.wineapp.presentation.common.ui.WineListTabs
import com.wineapp.presentation.common.ui.yearMonthOf
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandDivider
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import java.util.Locale

@Composable
fun CellarScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onFindWine: () -> Unit = {},
    onAskSommelier: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: CellarViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    CellarScreenContent(
        state = state,
        onFilter = { viewModel.sendIntent(CellarIntent.SetFilter(it)) },
        onSort = { viewModel.sendIntent(CellarIntent.SetSort(it)) },
        onStyleFilter = { viewModel.sendIntent(CellarIntent.SetStyleFilter(it)) },
        onQuery = { viewModel.sendIntent(CellarIntent.SetQuery(it)) },
        onRetry = { viewModel.sendIntent(CellarIntent.LoadCellar) },
        onIncrement = { viewModel.sendIntent(CellarIntent.Increment(it)) },
        onDecrement = { viewModel.sendIntent(CellarIntent.Decrement(it)) },
        onRemove = { viewModel.sendIntent(CellarIntent.Remove(it)) },
        onAskSommelier = onAskSommelier,
        onNavigateToDetail = onNavigateToDetail,
        onFindWine = onFindWine,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun CellarScreenContent(
    state: CellarState,
    onFilter: (String?) -> Unit = {},
    onSort: (WineListSort) -> Unit = {},
    onStyleFilter: (String?) -> Unit = {},
    onQuery: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onAskSommelier: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onFindWine: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    TransparentSystemBars()
    var pendingDelete: CellarItem? by remember { mutableStateOf(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val success = state as? CellarState.Success

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        Image(
            painter = painterResource(R.drawable.search_tab_ellipse),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WineListHeader(
                        title = stringResource(R.string.cellar_title),
                        searchOpen = searchOpen,
                        onBack = onNavigateBack,
                        onSearchClick = {
                            if (searchOpen) onQuery("")
                            searchOpen = !searchOpen
                        },
                        query = success?.query.orEmpty(),
                        searchHint = stringResource(R.string.list_search_hint),
                        onQuery = onQuery
                    )
                    if (success != null && !success.isCollectionEmpty) {
                        WineListSortFilterRow(
                            sort = success.sort,
                            styles = success.styles,
                            selectedStyle = success.styleFilter,
                            onSort = onSort,
                            onStyleFilter = onStyleFilter
                        )
                        CellarStatusTabs(
                            selected = success.filter,
                            onFilter = onFilter
                        )
                    }
                }
            }

            when (state) {
                is CellarState.Loading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center
                    ) { BrandLoader() }
                }

                is CellarState.Error -> item(key = "error") {
                    ErrorMessage(message = state.message, onRetry = onRetry)
                }

                is CellarState.Success -> {
                    if (state.isCollectionEmpty) {
                        item(key = "empty") {
                            EmptyState(
                                icon = AppIcons.WineBottle,
                                title = stringResource(R.string.cellar_empty),
                                message = stringResource(R.string.cellar_empty_hint),
                                actionText = stringResource(R.string.empty_action_find_wine),
                                actionIcon = AppIcons.Search,
                                onAction = onFindWine,
                                modifier = Modifier.padding(vertical = 80.dp)
                            )
                        }
                    } else {
                        item(key = "stats") {
                            CellarStatsCard(
                                stats = state.stats,
                                modifier = Modifier.padding(top = 32.dp)
                            )
                        }
                        item(key = "sommelier") {
                            CellarSommelierBlock(
                                onAsk = onAskSommelier,
                                modifier = Modifier.padding(top = 32.dp)
                            )
                        }
                        if (state.items.isEmpty()) {
                            item(key = "nothing") {
                                WineListNothingFound(modifier = Modifier.padding(top = 32.dp))
                            }
                        } else {
                            val groups = if (state.sort.groupsByMonth) {
                                state.items.groupBy { yearMonthOf(it.updatedAt) }.toList()
                            } else {
                                listOf(null to state.items)
                            }
                            groups.forEachIndexed { index, (month, monthItems) ->
                                if (month != null) {
                                    item(key = "month:$month") {
                                        WineListMonthHeader(month, isFirst = index == 0)
                                    }
                                } else {
                                    item(key = "list-top") { Spacer(Modifier.height(32.dp)) }
                                }
                                items(monthItems, key = { it.wine.id }) { item ->
                                    CellarCard(
                                        item = item,
                                        onClick = { onNavigateToDetail(item.wine.id) },
                                        onIncrement = { onIncrement(item.wine.id) },
                                        onDecrement = { onDecrement(item.wine.id) },
                                        onDelete = { pendingDelete = item },
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.cellar_delete_title)) },
            text = { Text(stringResource(R.string.cellar_delete_confirm, item.wine.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(item.wine.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.cellar_delete), color = BrandBurgundy600) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Два таба-фильтра статуса: «В наличии» и «Выпито». Повторный тап снимает фильтр. */
@Composable
private fun CellarStatusTabs(
    selected: String?,
    onFilter: (String?) -> Unit
) {
    val statuses = listOf(CellarStatus.IN_STOCK, CellarStatus.CONSUMED)
    WineListTabs(
        labels = listOf(
            stringResource(R.string.cellar_status_home),
            stringResource(R.string.cellar_status_consumed)
        ),
        selectedIndex = statuses.indexOf(selected).takeIf { it >= 0 },
        onSelect = { index ->
            val status = statuses[index]
            onFilter(if (selected == status) null else status)
        }
    )
}

/** Сводка: бутылок, средняя оценка, любимая категория — через вертикальные разделители. */
@Composable
private fun CellarStatsCard(
    stats: CellarStats,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.9f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(
                value = stats.totalBottles.toString(),
                caption = stringResource(R.string.cellar_stat_bottles),
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatCell(
                value = stats.averageRating?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                caption = stringResource(R.string.cellar_stat_rating),
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stats.favoriteStyle ?: "—",
                    fontFamily = Playfair,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                    color = BrandTextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                StatCaption(stringResource(R.string.cellar_stat_category))
            }
        }
    }
}

@Composable
private fun StatCell(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Text(
            value,
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            color = BrandTextPrimary,
            textAlign = TextAlign.Center
        )
        StatCaption(caption)
    }
}

@Composable
private fun StatCaption(text: String) {
    Text(
        text,
        fontFamily = Inter,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = BrandTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(BrandDivider)
    )
}

/** «Спросите сомелье» + поле вопроса с бордовой кнопкой отправки (Search bar из макета). */
@Composable
private fun CellarSommelierBlock(
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var question by remember { mutableStateOf("") }
    val send = {
        onAsk(question)
        question = ""
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                AppIcons.AiStarFilled,
                contentDescription = null,
                tint = BrandBurgundy600,
                modifier = Modifier.size(20.dp)
            )
            Text(
                stringResource(R.string.cellar_ask_sommelier),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = BrandBurgundy600
            )
        }
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = BrandCream50,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(start = 24.dp, end = 8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (question.isEmpty()) {
                        Text(
                            stringResource(R.string.sommelier_input_hint),
                            // Подсказка — серая, как во всех полях ввода (поиск, чат сомелье).
                            style = WineListBodyM.copy(color = BrandTextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = question,
                        onValueChange = { question = it },
                        singleLine = true,
                        textStyle = WineListBodyM,
                        cursorBrush = SolidColor(BrandBurgundy600),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Surface(
                    onClick = send,
                    shape = CircleShape,
                    color = BrandBurgundy600,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.ArrowUp,
                            contentDescription = stringResource(R.string.sommelier_send),
                            tint = BrandCream50,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка вина в коллекции: общий каркас [WineListCard], тег — категория и год,
 * справа снизу — степпер количества (при 0 бутылок минус превращается в удаление).
 */
@Composable
private fun CellarCard(
    item: CellarItem,
    onClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wine = item.wine
    WineListCard(
        imageModel = wine.imageUrl?.let { com.wineapp.util.apiImageUrl(it) },
        imageDescription = wine.name,
        onClick = onClick,
        headline = { WineListHeadline(wine) },
        tag = listOfNotNull(wine.style, wine.vintage?.toString()).joinToString(" · "),
        modifier = modifier
    ) {
        QuantityStepper(
            quantity = item.quantity,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
            onDelete = onDelete
        )
    }
}

/** Компактный степпер: [−] N [+]. При нуле бутылок минус становится удалением. */
@Composable
private fun QuantityStepper(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = if (quantity > 0) onDecrement else onDelete,
            shape = CircleShape,
            color = BrandCream50,
            border = BorderStroke(1.dp, BrandBorderDefault),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (quantity > 0) Icons.Default.Remove else AppIcons.Trash,
                    contentDescription = stringResource(
                        if (quantity > 0) R.string.cellar_decrease else R.string.cellar_delete
                    ),
                    tint = BrandBurgundy600,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            quantity.toString(),
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = BrandTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Surface(
            onClick = onIncrement,
            shape = CircleShape,
            color = BrandBurgundy600,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppIcons.Plus,
                    contentDescription = stringResource(R.string.cellar_increase),
                    tint = BrandCream50,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun CellarScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wine = MockDataProvider.wines.first()
        val items = listOf(
            CellarItem(wine = wine, quantity = 3, status = CellarStatus.IN_STOCK, updatedAt = System.currentTimeMillis()),
            CellarItem(
                wine = wine.copy(id = "2", name = "Barolo 2019"),
                quantity = 0,
                status = CellarStatus.CONSUMED,
                updatedAt = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            )
        )
        CellarScreenContent(
            state = CellarState.Success(
                items = items,
                styles = listOfNotNull(wine.style),
                stats = CellarStats(totalBottles = 3, averageRating = 4.8f, favoriteStyle = "Красное сухое")
            )
        )
    }
}
