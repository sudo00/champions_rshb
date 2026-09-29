package com.wineapp.presentation.cellar

import com.wineapp.presentation.common.ui.AppIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.local.CellarStatus
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.CellarItem
import com.wineapp.presentation.common.ui.EmptyState
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair

@Composable
fun CellarScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: CellarViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    CellarScreenContent(
        state = state,
        onFilter = { viewModel.sendIntent(CellarIntent.SetFilter(it)) },
        onRetry = { viewModel.sendIntent(CellarIntent.LoadCellar) },
        onIncrement = { viewModel.sendIntent(CellarIntent.Increment(it)) },
        onDecrement = { viewModel.sendIntent(CellarIntent.Decrement(it)) },
        onSetStatus = { wineId, status -> viewModel.sendIntent(CellarIntent.SetStatus(wineId, status)) },
        onRemove = { viewModel.sendIntent(CellarIntent.Remove(it)) },
        onNavigateToDetail = onNavigateToDetail,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun CellarScreenContent(    state: CellarState,
    onFilter: (String?) -> Unit = {},
    onRetry: () -> Unit = {},
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
    onSetStatus: (String, String) -> Unit = { _, _ -> },
    onRemove: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var pendingDelete: CellarItem? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        CellarHeader(
            title = stringResource(R.string.cellar_title),
            onBack = onNavigateBack
        )
        CellarFilterRow(
            selected = (state as? CellarState.Success)?.filter,
            onFilter = onFilter
        )

        when (state) {
            is CellarState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BrandBurgundy600) }

            is CellarState.Error -> ErrorMessage(
                message = state.message,
                onRetry = onRetry
            )

            is CellarState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Inventory2,
                        title = stringResource(R.string.cellar_empty),
                        message = stringResource(R.string.cellar_empty_hint)
                    )
                } else {
                    val totalBottles = state.items.sumOf { it.quantity }
                    Text(
                        text = stringResource(R.string.cellar_total, state.items.size, totalBottles),
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        color = BrandTextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.items, key = { it.wine.id }) { item ->
                            CellarRow(
                                item = item,
                                onCardClick = { onNavigateToDetail(item.wine.id) },
                                onIncrement = { onIncrement(item.wine.id) },
                                onDecrement = { onDecrement(item.wine.id) },
                                onSetStatus = { status -> onSetStatus(item.wine.id, status) },
                                onDeleteClick = { pendingDelete = item }
                            )
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
                }) { Text(stringResource(R.string.cellar_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun CellarHeader(
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
                    AppIcons.ChevronLeft,
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
private fun CellarFilterRow(
    selected: String?,
    onFilter: (String?) -> Unit
) {
    val filters = listOf(
        null to R.string.cellar_filter_all,
        CellarStatus.IN_STOCK to R.string.cellar_status_home,
        CellarStatus.CONSUMED to R.string.cellar_status_consumed
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (status, labelRes) ->
            val isSelected = selected == status
            Surface(
                onClick = { onFilter(status) },
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
private fun CellarRow(
    item: CellarItem,
    onCardClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetStatus: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }
    val statusLabel = when (item.status) {
        CellarStatus.CONSUMED -> stringResource(R.string.cellar_status_consumed)
        else -> stringResource(R.string.cellar_status_home)
    }

    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.wine.imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.wineapp.util.apiImageUrl(item.wine.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = item.wine.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.wine.name,
                        fontFamily = Playfair,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        color = BrandTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = listOfNotNull(
                        item.wine.vintage?.toString(),
                        item.wine.region,
                        item.wine.variety
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = BrandTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    onClick = onDeleteClick,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.Trash,
                            contentDescription = stringResource(R.string.cellar_delete),
                            tint = BrandTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Статус со сменой через меню
                Box {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = BrandCream50,
                        border = BorderStroke(1.dp, BrandBorderLight),
                        onClick = { statusMenuExpanded = true }
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = BrandBurgundy600
                        )
                    }
                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cellar_status_home)) },
                            onClick = {
                                onSetStatus(CellarStatus.IN_STOCK)
                                statusMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cellar_status_consumed)) },
                            onClick = {
                                onSetStatus(CellarStatus.CONSUMED)
                                statusMenuExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Степпер количества
                Surface(
                    onClick = onDecrement,
                    shape = CircleShape,
                    color = BrandCream50,
                    border = BorderStroke(1.dp, BrandBorderLight),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = stringResource(R.string.cellar_decrease),
                            tint = BrandBurgundy600,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.cellar_bottles, item.quantity),
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BrandTextPrimary,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Surface(
                    onClick = onIncrement,
                    shape = CircleShape,
                    color = BrandBurgundy600,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.Plus,
                            contentDescription = stringResource(R.string.cellar_increase),
                            tint = BrandCream50,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CellarScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wine = MockDataProvider.wines.first()
        CellarScreenContent(
            state = CellarState.Success(
                items = listOf(
                    CellarItem(wine = wine, quantity = 3, status = CellarStatus.IN_STOCK, updatedAt = 0L),
                    CellarItem(wine = wine.copy(id = "2", name = "Barolo 2019"), quantity = 0, status = CellarStatus.CONSUMED, updatedAt = 0L)
                )
            )
        )
    }
}
