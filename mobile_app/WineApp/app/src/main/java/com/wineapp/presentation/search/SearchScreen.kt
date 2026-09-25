package com.wineapp.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.SearchResult
import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.presentation.common.ui.BrandSecondaryButton
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WineCard
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream300
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSommelier: () -> Unit = {},
    onNavigateToSavedScans: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToCellar: () -> Unit = {},
    onNavigateToWinePath: () -> Unit = {}
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(true) {
        viewModel.sendIntent(SearchIntent.Search(""))
    }

    SearchScreenContent(
        state = state,
        onSearch = { query -> viewModel.sendIntent(SearchIntent.Search(query)) },
        onLoadMore = { viewModel.sendIntent(SearchIntent.LoadMore) },
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToGallery = onNavigateToGallery,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToSommelier = onNavigateToSommelier,
        onNavigateToSavedScans = onNavigateToSavedScans,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToCellar = onNavigateToCellar,
        onNavigateToWinePath = onNavigateToWinePath
    )
}

@Composable
fun SearchScreenContent(
    state: SearchState,
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onNavigateToScanner: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSommelier: () -> Unit = {},
    onNavigateToSavedScans: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToCellar: () -> Unit = {},
    onNavigateToWinePath: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    TransparentSystemBars()

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            onSearch(query)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                AppDrawer(
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToCellar = onNavigateToCellar,
                    onNavigateToWinePath = onNavigateToWinePath,
                    onNavigateToSavedScans = onNavigateToSavedScans,
                    onNavigateToSommelier = onNavigateToSommelier,
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    HeroBlock(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateToScanner = onNavigateToScanner,
                        onNavigateToGallery = onNavigateToGallery
                    )
                    PopularBlock(
                        state = state,
                        query = query,
                        onSearch = onSearch,
                        onLoadMore = onLoadMore,
                        onNavigateToDetail = onNavigateToDetail
                    )
                    // Место под плавающую нижнюю панель.
                    Spacer(modifier = Modifier.height(110.dp))
                }
                // Плавающая нижняя панель: свернута — только круг справа,
                // тап — круг уезжает влево, выезжают поле и камера.
                // Плавающая нижняя панель: выше системных кнопок за счёт инсетов,
                // при открытой клавиатуре висит над ней поверх экрана.
                SearchBottomBar(
                    expanded = searchExpanded,
                    query = query,
                    onQueryChange = { query = it },
                    onToggleExpand = { searchExpanded = !searchExpanded },
                    onCameraClick = onNavigateToScanner,
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .imePadding()
                )
            }
        }
    }
}

@Composable
private fun AppDrawer(
    onNavigateToFavorites: () -> Unit,
    onNavigateToCellar: () -> Unit,
    onNavigateToWinePath: () -> Unit,
    onNavigateToSavedScans: () -> Unit,
    onNavigateToSommelier: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = BrandCream50,
        drawerContentColor = BrandTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.svoe_vino_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 140.dp, height = 35.dp)
                    .padding(start = 12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BrandCream300)
            Spacer(modifier = Modifier.height(12.dp))
            DrawerEntry(
                icon = Icons.Default.FavoriteBorder,
                label = stringResource(R.string.favorites_title),
                onClick = { onClose(); onNavigateToFavorites() }
            )
            DrawerEntry(
                icon = Icons.Default.Inventory2,
                label = stringResource(R.string.cellar_title),
                onClick = { onClose(); onNavigateToCellar() }
            )
            DrawerEntry(
                icon = Icons.Default.Explore,
                label = stringResource(R.string.winepath_title),
                onClick = { onClose(); onNavigateToWinePath() }
            )
            DrawerEntry(
                icon = Icons.Default.History,
                label = stringResource(R.string.saved_scans_title),
                onClick = { onClose(); onNavigateToSavedScans() }
            )
            DrawerEntry(
                icon = Icons.Default.WineBar,
                label = stringResource(R.string.nav_sommelier),
                onClick = { onClose(); onNavigateToSommelier() }
            )
        }
    }
}

@Composable
private fun DrawerEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                label,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
        },
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        selected = false,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unselectedIconColor = BrandBurgundy600,
            unselectedTextColor = BrandTextPrimary
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun HeroBlock(
    onMenuClick: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(770.dp)
            .clip(RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp))
            .background(BrandCream300)
            .clipToBounds()
    ) {
        // Декоративный эллипс Cream 100 (686x741, обрезан краями).
        Box(
            modifier = Modifier
                .size(width = 686.dp, height = 741.dp)
                .offset(y = (-162).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(BrandCream100)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Шапка: лого + бургер-меню разделов.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter = painterResource(id = R.drawable.svoe_vino_logo),
                    contentDescription = null,
                    modifier = Modifier.size(width = 160.dp, height = 40.dp)
                )
                Surface(
                    onClick = onMenuClick,
                    shape = CircleShape,
                    color = BrandBurgundy600,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            tint = BrandCream50,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            // Заголовок.
            Text(
                stringResource(R.string.home_title),
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 48.sp,
                lineHeight = 54.sp,
                textAlign = TextAlign.Center,
                color = BrandTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_subtitle),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                color = BrandTextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Hero-фото бутылки (TODO ассет bg_removal из фигмы).
            Image(
                painter = painterResource(id = R.drawable.wine_scan),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(200.dp)
                    .height(350.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.weight(1f))
            // Кнопки.
            BrandButton(
                text = stringResource(R.string.home_scan),
                onClick = onNavigateToScanner,
                leadingIcon = Icons.Default.PhotoCamera
            )
            Spacer(modifier = Modifier.height(12.dp))
            BrandSecondaryButton(
                text = stringResource(R.string.home_gallery),
                onClick = onNavigateToGallery
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PopularBlock(
    state: SearchState,
    query: String,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.home_all),
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = BrandTextPrimary
            )
            // TODO: фильтры по вину из макета следующим этапом.
            TextButton(onClick = {}) {
                Text(
                    stringResource(R.string.home_filter),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandBurgundy600
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = BrandBurgundy600,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        when (state) {
            is SearchState.Success -> {
                val wines = state.result.wines
                if (wines.isEmpty()) {
                    PopularEmpty(query = state.query)
                } else {
                    wines.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { wine ->
                                WineCard(
                                    wine = wine,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToDetail(wine.id) }
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (state.result.hasMore) {
                        TextButton(onClick = onLoadMore) {
                            Text(stringResource(R.string.search_load_more))
                        }
                    }
                }
            }
            is SearchState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandBurgundy600)
                }
            }
            is SearchState.Empty -> PopularEmpty(query = state.query)
            is SearchState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        state.message,
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { onSearch(query) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is SearchState.Idle -> PopularEmpty(query = "")
        }
    }
}

@Composable
private fun PopularEmpty(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            tint = BrandTextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (query.isBlank()) stringResource(R.string.search_empty)
            else stringResource(R.string.search_no_results_for, query),
            fontFamily = Inter,
            fontSize = 14.sp,
            color = BrandTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun SearchBottomBar(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onCameraClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // Системный «назад» сначала схлопывает поиск.
    BackHandler(enabled = expanded) { onToggleExpand() }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(200)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally { w -> w / 2 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally { w -> -w / 2 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally { w -> -w / 2 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally { w -> w / 2 } + fadeOut(tween(300)))
            }
        },
        label = "searchBar",
        modifier = modifier
    ) { isExpanded ->
        if (!isExpanded) {
            // Свернуто: только круг поиска справа.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                BarCircleButton(
                    onClick = onToggleExpand,
                    containerColor = BrandBurgundy600,
                    contentDescription = stringResource(R.string.search_title)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = BrandCream50,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            // Развернуто: круг уехал влево, поле по центру, камера справа.
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = BrandCream50,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BarCircleButton(
                        onClick = onToggleExpand,
                        containerColor = BrandBurgundy600,
                        contentDescription = stringResource(R.string.search_title)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = BrandCream50,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                stringResource(R.string.search_hint),
                                fontFamily = Inter,
                                fontSize = 14.sp,
                                color = BrandTextSecondary
                            )
                        },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.search_clear),
                                        tint = BrandTextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = BrandBurgundy600
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BarCircleButton(
                        onClick = onCameraClick,
                        containerColor = BrandCream50,
                        contentDescription = null
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = BrandTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarCircleButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentDescription: String?,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        shadowElevation = if (containerColor == BrandBurgundy600) 8.dp else 0.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Composable
private fun SearchScreenPreview() {
    WineAppTheme {
        SearchScreenContent(
            state = SearchState.Success(
                result = SearchResult(
                    wines = MockDataProvider.wines,
                    totalCount = MockDataProvider.wines.size,
                    page = 1,
                    hasMore = false,
                ),
                query = "",
            )
        )
    }
}
