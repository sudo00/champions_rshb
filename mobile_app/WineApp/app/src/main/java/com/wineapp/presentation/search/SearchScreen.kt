package com.wineapp.presentation.search

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
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
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.EmptyState
import com.wineapp.presentation.common.ErrorMessage
import com.wineapp.presentation.common.LoadingOverlay
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
    onNavigateToSommelier: () -> Unit = {}
) {
    val viewModel: SearchViewModel = hiltViewModel()
    SearchScreenContent(
        viewModel = viewModel,
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToSommelier = onNavigateToSommelier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    viewModel: SearchViewModel,
    onNavigateToScanner: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSommelier: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(WineFilter.ALL) }

    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = surfaceColor.toArgb()
        window.navigationBarColor = surfaceColor.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
    }

    Scaffold(
        topBar = {
            WineAppTopAppBar(
                title = stringResource(R.string.search_title),
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.nav_scanner))
                    }
                }
            )
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = MaterialTheme.shapes.large
                )
            }

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WineFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(stringResource(filter.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            LaunchedEffect(query) {
                if (query.isNotBlank()) {
                    viewModel.sendIntent(SearchIntent.Search(query))
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = state) {
                    is SearchState.Idle -> {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.search_title),
                            message = stringResource(R.string.search_empty)
                        )
                    }
                    is SearchState.Loading -> {
                        if (!current.isLoadMore) {
                            LoadingOverlay()
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
                            onLoadMore = { viewModel.sendIntent(SearchIntent.LoadMore) }
                        )
                    }
                    is SearchState.Empty -> {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = stringResource(R.string.search_no_results),
                            message = stringResource(R.string.search_no_results_for, current.query)
                        )
                    }
                    is SearchState.Error -> {
                        ErrorMessage(
                            message = current.message,
                            onRetry = { viewModel.sendIntent(SearchIntent.Search(query)) }
                        )
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(wines) { wine ->
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
        SearchResultsList(
            wines = MockDataProvider.wines,
            isLoading = false,
            hasMore = false,
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 800, name = "Загрузка")
@Composable
private fun SearchScreenLoadingPreview() {
    WineAppTheme {
        SearchResultsList(
            wines = MockDataProvider.wines.take(3),
            isLoading = true,
            onItemClick = {}
        )
    }
}
