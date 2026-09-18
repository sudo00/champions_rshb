package com.wineapp.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ErrorMessage
import com.wineapp.presentation.common.LoadingOverlay

@Composable
fun DetailScreenContent(viewModel: DetailViewModel, wineId: String) {
    val state by viewModel.state.collectAsState()

    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = Color.Black.copy(alpha = 0.5f).toArgb()
        window.navigationBarColor = surfaceColor.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
    }

    LaunchedEffect(wineId) {
        viewModel.sendIntent(DetailIntent.LoadDetail(wineId))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is DetailState.Loading -> LoadingOverlay()
            is DetailState.Error -> ErrorMessage(message = current.message, onRetry = { viewModel.sendIntent(DetailIntent.Retry) })
            is DetailState.Success -> DetailContent(wine = current.wine, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContent(wine: Wine, viewModel: DetailViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            // Header with Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(wine.imageUrl).crossfade(true).build(),
                    contentDescription = wine.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )
                // Wine name and rating
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(wine.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        wine.vintage?.let {
                            Text("$it", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        wine.region?.let {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("· $it", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Text(String.format("%.1f", wine.rating), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        if (wine.reviewsCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("(${wine.reviewsCount} ${stringResource(R.string.detail_reviews)})", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(24.dp)) {
                // Price
                wine.price?.let {
                    Row {
                        Text(stringResource(com.wineapp.R.string.detail_price), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${wine.currency ?: "$"} ${String.format("%.2f", it)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Details Grid
                DetailRow(label = stringResource(com.wineapp.R.string.detail_region), value = wine.region ?: "—")
                DetailRow(label = stringResource(com.wineapp.R.string.detail_variety), value = wine.variety ?: "—")
                DetailRow(label = stringResource(R.string.detail_year), value = wine.vintage?.toString() ?: "—")
                wine.alcoholPercentage?.let {
                    DetailRow(label = stringResource(R.string.detail_alcohol), value = "${String.format("%.1f", it)}%")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Description
                wine.description?.let {
                    Text(stringResource(com.wineapp.R.string.detail_description), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Food Pairing
                wine.foodPairing.takeIf { it.isNotEmpty() }?.let { pairings ->
                    Text(stringResource(com.wineapp.R.string.detail_pairing), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairings.forEach { pairing ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    pairing,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.sendIntent(DetailIntent.AddToHistory) },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.detail_save))
                    }
                    Button(
                        onClick = { viewModel.sendIntent(DetailIntent.Share) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.detail_share))
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DetailScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wine = MockDataProvider.wines.first()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(wine.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                wine.vintage?.let { Text("$it", style = MaterialTheme.typography.titleMedium, color = Color.White) }
                                wine.region?.let { Text(" \u00b7 $it", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                                Text(String.format("%.1f", wine.rating), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(24.dp)) {
                    wine.price?.let {
                        Row {
                            Text("${wine.currency ?: "$"} ${String.format("%.2f", it)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    DetailRow(label = "Region", value = wine.region ?: "\u2014")
                    DetailRow(label = "Variety", value = wine.variety ?: "\u2014")
                    wine.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}