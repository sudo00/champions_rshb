package com.wineapp.presentation.favorites

import com.wineapp.presentation.common.ui.BrandLoader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.local.FavoriteKind
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.FavoriteItem
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.AppIcons
import com.wineapp.presentation.common.ui.EmptyState
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WineListCard
import com.wineapp.presentation.common.ui.WineListHeader
import com.wineapp.presentation.common.ui.WineListHeadline
import com.wineapp.presentation.common.ui.WineListIconButton
import com.wineapp.presentation.common.ui.WineListMonthHeader
import com.wineapp.presentation.common.ui.WineListNothingFound
import com.wineapp.presentation.common.ui.WineListSort
import com.wineapp.presentation.common.ui.WineListSortFilterRow
import com.wineapp.presentation.common.ui.WineListTabs
import com.wineapp.presentation.common.ui.yearMonthOf
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.Inter

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onFindWine: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: FavoritesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    FavoritesScreenContent(
        state = state,
        onRemoveFavorite = { viewModel.sendIntent(FavoritesIntent.RemoveFavorite(it)) },
        onFilter = { viewModel.sendIntent(FavoritesIntent.SetFilter(it)) },
        onSort = { viewModel.sendIntent(FavoritesIntent.SetSort(it)) },
        onStyleFilter = { viewModel.sendIntent(FavoritesIntent.SetStyleFilter(it)) },
        onQuery = { viewModel.sendIntent(FavoritesIntent.SetQuery(it)) },
        onSetKind = { wineId, kind -> viewModel.sendIntent(FavoritesIntent.SetKind(wineId, kind)) },
        onNavigateToDetail = onNavigateToDetail,
        onFindWine = onFindWine,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun FavoritesScreenContent(
    state: FavoritesState,
    onRemoveFavorite: (String) -> Unit = {},
    onFilter: (String?) -> Unit = {},
    onSort: (WineListSort) -> Unit = {},
    onStyleFilter: (String?) -> Unit = {},
    onQuery: (String) -> Unit = {},
    onSetKind: (String, String) -> Unit = { _, _ -> },
    onNavigateToDetail: (String) -> Unit = {},
    onFindWine: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    TransparentSystemBars()
    var wineToDelete by remember { mutableStateOf<Wine?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val success = state as? FavoritesState.Success

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
                        title = stringResource(R.string.favorites_title),
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
                    if (success != null && !success.isFavoritesEmpty) {
                        WineListSortFilterRow(
                            sort = success.sort,
                            styles = success.styles,
                            selectedStyle = success.styleFilter,
                            onSort = onSort,
                            onStyleFilter = onStyleFilter
                        )
                        FavoritesKindTabs(selected = success.filter, onFilter = onFilter)
                    }
                }
            }

            when (state) {
                is FavoritesState.Loading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center
                    ) { BrandLoader() }
                }

                is FavoritesState.Error -> item(key = "error") {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 120.dp)
                    )
                }

                is FavoritesState.Success -> {
                    if (state.isFavoritesEmpty) {
                        item(key = "empty") {
                            EmptyState(
                                icon = AppIcons.Heart,
                                title = stringResource(R.string.favorites_empty),
                                message = stringResource(R.string.favorites_empty_hint),
                                actionText = stringResource(R.string.empty_action_find_wine),
                                actionIcon = AppIcons.Search,
                                onAction = onFindWine,
                                modifier = Modifier.padding(vertical = 80.dp)
                            )
                        }
                    } else if (state.items.isEmpty()) {
                        item(key = "nothing") {
                            WineListNothingFound(modifier = Modifier.padding(top = 32.dp))
                        }
                    } else {
                        val groups = if (state.sort.groupsByMonth) {
                            state.items.groupBy { yearMonthOf(it.addedAt) }.toList()
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
                                FavoriteWineCard(
                                    item = item,
                                    onClick = { onNavigateToDetail(item.wine.id) },
                                    onRemove = { wineToDelete = item.wine },
                                    onSetKind = { kind -> onSetKind(item.wine.id, kind) },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    wineToDelete?.let { wine ->
        AlertDialog(
            onDismissRequest = { wineToDelete = null },
            title = { Text(stringResource(R.string.favorites_delete_title)) },
            text = { Text(stringResource(R.string.favorites_delete_confirm, wine.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFavorite(wine.id)
                    wineToDelete = null
                }) {
                    Text(stringResource(R.string.favorites_delete), color = BrandBurgundy600)
                }
            },
            dismissButton = {
                TextButton(onClick = { wineToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Табы «Все», «Хочу», «Понравилось». */
@Composable
private fun FavoritesKindTabs(
    selected: String?,
    onFilter: (String?) -> Unit
) {
    val kinds = listOf(null, FavoriteKind.WISH, FavoriteKind.LIKED)
    WineListTabs(
        labels = listOf(
            stringResource(R.string.favorites_filter_all),
            stringResource(R.string.favorites_wish),
            stringResource(R.string.favorites_liked)
        ),
        selectedIndex = kinds.indexOf(selected).takeIf { it >= 0 },
        onSelect = { index -> onFilter(kinds[index]) }
    )
}

/**
 * Карточка избранного: общий каркас [WineListCard], закрашенное сердечко
 * справа сверху (убрать из избранного), вместо тега — чип «Хочу»/«Понравилось»
 * с выбором вида.
 */
@Composable
private fun FavoriteWineCard(
    item: FavoriteItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onSetKind: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val wine = item.wine
    WineListCard(
        imageModel = wine.imageUrl?.let { com.wineapp.util.apiImageUrl(it) },
        imageDescription = wine.name,
        onClick = onClick,
        headline = { WineListHeadline(wine) },
        tagContent = { FavoriteKindChip(kind = item.kind, onSetKind = onSetKind) },
        topEndAction = {
            WineListIconButton(
                icon = AppIcons.HeartFilled,
                contentDescription = stringResource(R.string.favorites_remove),
                tint = BrandBurgundy600,
                onClick = onRemove
            )
        },
        modifier = modifier
    )
}

/** Чип вида избранного в стиле Badge / Tag, по тапу — меню смены вида. */
@Composable
private fun FavoriteKindChip(
    kind: String,
    onSetKind: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(percent = 50),
            color = BrandCream50,
            border = BorderStroke(1.dp, BrandBorderDefault)
        ) {
            Text(
                stringResource(
                    if (kind == FavoriteKind.WISH) R.string.favorites_wish else R.string.favorites_liked
                ),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = BrandBurgundy600,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites_wish)) },
                onClick = {
                    onSetKind(FavoriteKind.WISH)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites_liked)) },
                onClick = {
                    onSetKind(FavoriteKind.LIKED)
                    expanded = false
                }
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FavoritesScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wines = MockDataProvider.wines
        FavoritesScreenContent(
            state = FavoritesState.Success(
                items = listOf(
                    FavoriteItem(wine = wines[0], kind = FavoriteKind.LIKED, addedAt = System.currentTimeMillis()),
                    FavoriteItem(
                        wine = wines.getOrElse(1) { wines[0] }.copy(id = "preview-2"),
                        kind = FavoriteKind.WISH,
                        addedAt = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                    )
                ),
                styles = listOfNotNull(wines[0].style)
            )
        )
    }
}
