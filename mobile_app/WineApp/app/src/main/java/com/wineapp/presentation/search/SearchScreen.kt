package com.wineapp.presentation.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.WineAppTopAppBar
import com.wineapp.presentation.common.WineCard
import com.wineapp.ui.theme.WineAppTheme

enum class WineFilter(val labelRes: Int) {
    ALL(R.string.search_all),
    RED(R.string.search_red),
    WHITE(R.string.search_white),
    ROSE(R.string.search_rose),
    SPARKLING(R.string.search_sparkling)
}

@Composable
fun SearchScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSommelier: () -> Unit = {},
    onNavigateToSavedScans: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {}
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(true) {
        viewModel.sendIntent(SearchIntent.Search(""))
    }

    SearchScreenContent(
        state = state,
        onSearch = { query -> viewModel.sendIntent(SearchIntent.Search(query))},
        onLoadMore = { viewModel.sendIntent(SearchIntent.LoadMore) },
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToSommelier = onNavigateToSommelier,
        onNavigateToSavedScans = onNavigateToSavedScans,
        onNavigateToFavorites = onNavigateToFavorites
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchState,
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onNavigateToScanner: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSommelier: () -> Unit = {},
    onNavigateToSavedScans: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(WineFilter.ALL) }

    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = surfaceColor.toArgb()
            window.navigationBarColor = surfaceColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    Scaffold(
        topBar = {
            WineAppTopAppBar(
                title = stringResource(R.string.search_title),
                actions = {
                    IconButton(onClick = onNavigateToFavorites) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = stringResource(R.string.favorites_title))
                    }
                    IconButton(onClick = onNavigateToSavedScans) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.saved_scans_title))
                    }
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.nav_scanner))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WineFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                stringResource(filter.labelRes),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = selectedFilter == filter
                        )
                    )
                }
            }

            LaunchedEffect(query) {
                if (query.isNotBlank()) {
                    onSearch(query)
                }
            }

            // Content
            when (val current = state) {
                is SearchState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                stringResource(R.string.search_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                is SearchState.Loading -> {
                    if (!current.isLoadMore) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val wines = (state as? SearchState.Success)?.result?.wines ?: emptyList()
                        SearchResultsList(
                            wines = wines,
                            isLoading = true,
                            onItemClick = { onNavigateToDetail(it.id) }
                        )
                    }
                }
                is SearchState.Success -> {
                    SearchResultsList(
                        wines = current.result.wines,
                        isLoading = false,
                        hasMore = current.result.hasMore,
                        onItemClick = { onNavigateToDetail(it.id) },
                        onLoadMore = onLoadMore
                    )
                }
                is SearchState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                stringResource(R.string.search_no_results),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.search_no_results_for, current.query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                is SearchState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                current.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            androidx.compose.material3.TextButton(
                                onClick = { onSearch(query) }
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsList(
    wines: List<Wine>,
    isLoading: Boolean,
    hasMore: Boolean = false,
    onItemClick: (Wine) -> Unit,
    onLoadMore: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(wines, key = { it.id }) { wine ->
            WineCard(wine = wine, onClick = { onItemClick(wine) })
        }
        if (isLoading || hasMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        androidx.compose.material3.TextButton(onClick = onLoadMore) {
                            Text(stringResource(R.string.search_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
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
