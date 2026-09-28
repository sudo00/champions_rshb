package com.wineapp.presentation.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.presentation.common.ui.BrandSecondaryButton
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WinePhotoViewer
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme

/**
 * Раздел поиска из фигмы (без навбара — он общий): эллипс Cream300,
 * пилюля поиска с бургунди-кнопкой, ряд сортировки/фильтра, сетка карточек.
 */
@Composable
fun SearchTabScreen(
    onNavigateToDetail: (String) -> Unit = {}
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()
    var query by remember { mutableStateOf("") }
    var viewerWine by remember { mutableStateOf<Wine?>(null) }

    LaunchedEffect(query) {
        viewModel.sendIntent(SearchIntent.Search(query))
    }

    SearchTabContent(
        state = state,
        query = query,
        likedIds = likedIds,
        viewerWine = viewerWine,
        onViewerClose = { viewerWine = null },
        onQueryChange = { query = it },
        onSearch = { q -> viewModel.sendIntent(SearchIntent.Search(q)) },
        onLoadMore = { viewModel.sendIntent(SearchIntent.LoadMore) },
        onToggleFavorite = { wineId -> viewModel.sendIntent(SearchIntent.ToggleFavorite(wineId)) },
        onWineLongClick = { wine -> viewerWine = wine },
        onNavigateToDetail = onNavigateToDetail
    )
}

@Composable
fun SearchTabContent(
    state: SearchState,
    query: String,
    likedIds: Set<String> = emptySet(),
    viewerWine: Wine? = null,
    onViewerClose: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onWineLongClick: (Wine) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    TransparentSystemBars()

    // Автодогрузка при скролле к концу списка. value > 0 — только после
    // реального скролла пользователя, в покое страницы не подкачиваем.
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, max) ->
                if (max > 0 && value > 0 && value >= max - 800) onLoadMore()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        // Фон по состоянию: пустое — центрированный search_empty_state_ellipse
        // вместо верхнего; остальные — верхний search_tab_ellipse.
        val showEmptyBg = (state is SearchState.Empty ||
            (state as? SearchState.Success)?.result?.wines?.isEmpty() == true) &&
            query.isNotBlank()
        if (showEmptyBg) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = painterResource(R.drawable.search_empty_state_ellipse),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.search_tab_ellipse),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
        ) {
            // Блок ввода: пилюля + отделяющаяся капля-крестик при наличии текста.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Пилюля поиска: бургунди-кнопка + поле.
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = BrandCream50,
                    shadowElevation = 8.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Surface(
                            onClick = { focusManager.clearFocus() },
                            shape = CircleShape,
                            color = BrandBurgundy600,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = BrandCream50,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                onQueryChange(it)
                                onSearch(it)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = Inter,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = BrandTextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() }
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandBurgundy600),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    if (query.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_hint),
                                            fontFamily = Inter,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 16.sp,
                                            lineHeight = 24.sp,
                                            color = BrandTextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
                // Капля-крестик: отделяется от пилюли с пружинным scale.
                // Без slide и animateContentSize — они клиппят тени во время анимации.
                androidx.compose.animation.AnimatedVisibility(
                    visible = query.isNotBlank(),
                    enter = androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.6f,
                            stiffness = 500f
                        )
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() +
                        androidx.compose.animation.fadeOut()
                ) {
                    Surface(
                        onClick = {
                            onQueryChange("")
                            onSearch("")
                        },
                        shape = CircleShape,
                        color = BrandCream50,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear),
                                tint = BrandTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Ряд сортировки и фильтра (стабы). В пустом состоянии скрыт.
            if (!showEmptyBg) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(onClick = {}, color = Color.Transparent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = BrandBurgundy600,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.search_sort),
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            color = BrandBurgundy600
                        )
                    }
                }
                Surface(onClick = {}, color = Color.Transparent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = BrandBurgundy600,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.home_filter),
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            color = BrandBurgundy600
                        )
                    }
                }
            }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                SearchResultsContent(
                    state = state,
                    query = query,
                    onSearch = onSearch,
                    onNavigateToDetail = onNavigateToDetail,
                    likedIds = likedIds,
                    onToggleFavorite = onToggleFavorite,
                    onWineLongClick = onWineLongClick,
                    useRichEmpty = true,
                    onAddWinery = { openWineryForm(context) }
                )
                // Место под висящий поверх нижний бар.
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

/** Форма добавления винодельни (открывается в браузере). */
private const val WINERY_FORM_URL = "https://forms.yandex.ru/cloud/6911d889e010db6b5d3f3410/"

private fun openWineryForm(context: android.content.Context) {
    try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(WINERY_FORM_URL)
        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("SearchTab", "Open winery form failed", e)
    }
}

/**
 * Богатый empty state: Display-заголовок, подзаголовок и одна кнопка
 * «Добавить винодельню» (открывает Яндекс-форму в браузере).
 */
@Composable
fun SearchRichEmpty(
    onAddWinery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 120.dp)
    ) {
        Text(
            text = stringResource(R.string.search_empty_title),
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 48.sp,
            lineHeight = 54.sp,
            color = BrandTextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.search_empty_subtitle),
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = BrandTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        BrandButton(
            text = stringResource(R.string.search_add_winery),
            onClick = onAddWinery,
            leadingIcon = Icons.Default.Add
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SearchTabPreview() {
    WineAppTheme {
        SearchTabContent(
            state = SearchState.Success(
                result = SearchResult(
                    wines = MockDataProvider.wines,
                    totalCount = MockDataProvider.wines.size,
                    page = 1,
                    hasMore = false
                ),
                query = ""
            ),
            query = "",
            likedIds = emptySet(),
            onQueryChange = {},
            onSearch = {},
            onLoadMore = {},
            onToggleFavorite = {},
            onNavigateToDetail = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SearchTabEmptyPreview() {
    WineAppTheme {
        SearchTabContent(
            state = SearchState.Empty(query = "Каберне"),
            query = "Каберне",
            likedIds = emptySet(),
            onQueryChange = {},
            onSearch = {},
            onLoadMore = {},
            onToggleFavorite = {},
            onNavigateToDetail = {}
        )
    }
}
