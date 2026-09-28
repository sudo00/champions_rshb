package com.wineapp.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineapp.R
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.WineCard
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter

/**
 * Общий грид результатов поиска: главная (популярное) и отдельный экран поиска.
 */
@Composable
fun SearchResultsContent(
    state: SearchState,
    query: String,
    onSearch: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    likedIds: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onWineLongClick: ((Wine) -> Unit)? = null,
    // Богатый empty state только для экрана поиска; главная keeps компактный.
    useRichEmpty: Boolean = false,
    onAddWinery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Показанные вина переживают догрузку: при Loading(isLoadMore) список
    // не пропадает, новые страницы дописываются в конец, снизу спиннер.
    var shownWines by remember { mutableStateOf<List<Wine>>(emptyList()) }
    var appending by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is SearchState.Success -> {
                shownWines = state.result.wines
                appending = false
            }
            is SearchState.Loading -> {
                appending = state.isLoadMore && shownWines.isNotEmpty()
            }
            else -> {
                appending = false
                if (state is SearchState.Empty) shownWines = emptyList()
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        // Свежий Success рисуем сразу из стейта — иначе лаг-кадр показывает
        // stale (пусто после Empty или старое после нового поиска).
        val gridWines = (state as? SearchState.Success)
            ?.result?.wines?.takeIf { it.isNotEmpty() }
            ?: shownWines
        if (gridWines.isNotEmpty()) {
            WineGrid(
                wines = gridWines,
                likedIds = likedIds,
                onNavigateToDetail = onNavigateToDetail,
                onToggleFavorite = onToggleFavorite,
                onWineLongClick = onWineLongClick
            )
            if (appending) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandBurgundy600)
                }
            }
        } else when (state) {
            is SearchState.Success -> SearchEmptyBlock(
                query = state.query,
                useRichEmpty = useRichEmpty,
                onAddWinery = onAddWinery
            )
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
            is SearchState.Empty -> SearchEmptyBlock(
                query = state.query,
                useRichEmpty = useRichEmpty,
                onAddWinery = onAddWinery
            )
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
            is SearchState.Idle -> SearchEmpty(query = "")
        }
    }
}

@Composable
private fun WineGrid(
    wines: List<Wine>,
    likedIds: Set<String>,
    onNavigateToDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onWineLongClick: ((Wine) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        wines.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { wine ->
                    WineCard(
                        wine = wine,
                        modifier = Modifier.weight(1f),
                        isFavorite = wine.id in likedIds,
                        onClick = { onNavigateToDetail(wine.id) },
                        onFavoriteClick = { onToggleFavorite(wine.id) },
                        onLongClick = onWineLongClick?.let { callback -> { callback(wine) } }
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SearchEmptyBlock(
    query: String,
    useRichEmpty: Boolean,
    onAddWinery: () -> Unit
) {
    // На табе поиска компактный empty не показываем вообще — иначе он
    // проскакивает кадром при смене состояний (запрос уже стёрт/ещё летит).
    // В начальном Idle before первого запроса — пусто, без вспышек.
    if (useRichEmpty && query.isNotBlank()) {
        SearchRichEmpty(onAddWinery = onAddWinery)
    } else if (!useRichEmpty) {
        SearchEmpty(query = query)
    }
}

@Composable
private fun SearchEmpty(query: String) {
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
