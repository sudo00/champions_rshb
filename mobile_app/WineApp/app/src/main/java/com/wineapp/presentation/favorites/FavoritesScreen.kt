package com.wineapp.presentation.favorites

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
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
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair

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
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = BrandCream50.toArgb()
            window.navigationBarColor = BrandCream50.toArgb()
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
                    Text(
                        stringResource(R.string.favorites_delete),
                        color = BrandBurgundy600
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { wineToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        FavoritesHeader(
            title = stringResource(R.string.favorites_title),
            onBack = onNavigateBack
        )
        when (state) {
            is FavoritesState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandBurgundy600)
                }
            }
            is FavoritesState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        message = stringResource(R.string.favorites_empty_hint)
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
private fun FavoritesHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = BrandBurgundy600,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = BrandTextPrimary
        )
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
            val isSelected = selected == kind
            Surface(
                onClick = { onFilter(kind) },
                shape = RoundedCornerShape(percent = 50),
                color = if (isSelected) BrandBurgundy600 else BrandCream100,
                border = if (isSelected) null else BorderStroke(1.dp, BrandBorderLight),
                modifier = Modifier.height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(labelRes),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = if (isSelected) BrandCream50 else BrandTextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
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
            shape = RoundedCornerShape(percent = 50),
            color = BrandCream50,
            border = BorderStroke(1.dp, BrandBorderLight),
            onClick = { expanded = true }
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = BrandBurgundy600
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandCream100),
        border = BorderStroke(1.dp, BrandBorderLight),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandBorderLight)
            ) {
                wine.imageUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.wineapp.util.apiImageUrl(url))
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
                    fontFamily = Playfair,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    color = BrandTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                wine.winery?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        it,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    wine.vintage?.let {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BrandCream50,
                            border = BorderStroke(1.dp, BrandBorderLight)
                        ) {
                            Text(
                                "$it",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = BrandTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    wine.region?.let {
                        Text(
                            it,
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = BrandTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (wine.rating != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = BrandCream500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                String.format("%.1f", wine.rating),
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = BrandTextPrimary
                            )
                        }
                    }
                    wine.reviewsCount?.let {
                        if (it > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "(${wine.reviewsCount})",
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = BrandTextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    wine.price?.let {
                        Text(
                            "${wine.currency ?: "$"} ${String.format("%.0f", it)}",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            color = BrandBurgundy600
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                FavoriteKindChip(kind = kind, onSetKind = onSetKind)
            }
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.favorites_remove),
                        tint = BrandTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
