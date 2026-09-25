package com.wineapp.presentation.favorites

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.local.FavoriteKind
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.FavoriteItem
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.EmptyState
import com.wineapp.presentation.common.ui.WineAppTopAppBar

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: FavoritesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    FavoritesScreenContent(
        state = state,
        onRemoveFavorite = { viewModel.sendIntent(FavoritesIntent.RemoveFavorite(it)) },
        onFilter = { viewModel.sendIntent(FavoritesIntent.SetFilter(it)) },
        onSetKind = { wineId, kind -> viewModel.sendIntent(FavoritesIntent.SetKind(wineId, kind)) },
        onNavigateToDetail = onNavigateToDetail,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesState,
    onRemoveFavorite: (String) -> Unit = {},
    onFilter: (String?) -> Unit = {},
    onSetKind: (String, String) -> Unit = { _, _ -> },
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = surfaceColor.toArgb()
            window.navigationBarColor = surfaceColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    var wineToDelete by remember { mutableStateOf<Wine?>(null) }

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
                    Text(stringResource(R.string.favorites_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { wineToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            WineAppTopAppBar(
                title = stringResource(R.string.favorites_title),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        when (state) {
            is FavoritesState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is FavoritesState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is FavoritesState.Success -> {
                if (state.items.isEmpty() && state.filter == null) {
                    EmptyState(
                        icon = Icons.Default.FavoriteBorder,
                        title = stringResource(R.string.favorites_empty),
                        message = stringResource(R.string.favorites_empty_hint),
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                    ) {
                        FavoritesFilterRow(
                            selected = state.filter,
                            onFilter = onFilter
                        )
                        if (state.items.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.FavoriteBorder,
                                title = stringResource(R.string.favorites_empty),
                                message = stringResource(R.string.favorites_empty_hint)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = state.items,
                                    key = { it.wine.id }
                                ) { item ->
                                    FavoriteWineCard(
                                        wine = item.wine,
                                        kind = item.kind,
                                        onClick = { onNavigateToDetail(item.wine.id) },
                                        onRemove = { wineToDelete = item.wine },
                                        onSetKind = { kind -> onSetKind(item.wine.id, kind) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesFilterRow(
    selected: String?,
    onFilter: (String?) -> Unit
) {
    val filters = listOf(
        null to R.string.favorites_filter_all,
        FavoriteKind.WISH to R.string.favorites_wish,
        FavoriteKind.LIKED to R.string.favorites_liked
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (kind, labelRes) ->
            FilterChip(
                selected = selected == kind,
                onClick = { onFilter(kind) },
                label = {
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun FavoriteKindChip(
    kind: String,
    onSetKind: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (kind == FavoriteKind.WISH) {
        stringResource(R.string.favorites_wish)
    } else {
        stringResource(R.string.favorites_liked)
    }
    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = { expanded = true }
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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

@Composable
fun FavoriteWineCard(
    wine: Wine,
    kind: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onSetKind: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                wine.imageUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = wine.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    wine.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                wine.winery?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    wine.vintage?.let {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "$it",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    wine.region?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFF8E1)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                String.format("%.1f", wine.rating),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF795548)
                            )
                        }
                    }
                    wine.reviewsCount?.let {
                        if (it > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "(${wine.reviewsCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    wine.price?.let {
                        Text(
                            "${wine.currency ?: "$"} ${String.format("%.0f", it)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                FavoriteKindChip(kind = kind, onSetKind = onSetKind)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.favorites_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun FavoritesScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wines = MockDataProvider.wines
        FavoritesScreenContent(
            state = FavoritesState.Success(
                items = listOf(
                    FavoriteItem(wine = wines[0], kind = FavoriteKind.LIKED),
                    FavoriteItem(
                        wine = wines.getOrElse(1) { wines[0] }.copy(id = "preview-2"),
                        kind = FavoriteKind.WISH
                    )
                )
            )
        )
    }
}
