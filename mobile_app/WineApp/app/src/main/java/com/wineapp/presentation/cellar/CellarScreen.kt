package com.wineapp.presentation.cellar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.local.CellarStatus
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.CellarItem
import com.wineapp.presentation.common.ui.EmptyState
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.presentation.common.ui.WineAppTopAppBar

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
fun CellarScreenContent(
    state: CellarState,
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

    Scaffold(
        topBar = {
            WineAppTopAppBar(
                title = stringResource(R.string.cellar_title),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            CellarFilterRow(
                selected = (state as? CellarState.Success)?.filter,
                onFilter = onFilter
            )

            when (state) {
                is CellarState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp),
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
private fun CellarFilterRow(
    selected: String?,
    onFilter: (String?) -> Unit
) {
    val filters = listOf(
        null to R.string.cellar_filter_all,
        CellarStatus.HOME to R.string.cellar_status_home,
        CellarStatus.WISH to R.string.cellar_status_wish,
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
            FilterChip(
                selected = selected == status,
                onClick = { onFilter(status) },
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
        CellarStatus.WISH -> stringResource(R.string.cellar_status_wish)
        CellarStatus.CONSUMED -> stringResource(R.string.cellar_status_consumed)
        else -> stringResource(R.string.cellar_status_home)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onCardClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.wine.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cellar_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { statusMenuExpanded = true }
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cellar_status_home)) },
                            onClick = {
                                onSetStatus(CellarStatus.HOME)
                                statusMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cellar_status_wish)) },
                            onClick = {
                                onSetStatus(CellarStatus.WISH)
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
                IconButton(onClick = onDecrement, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cellar_decrease))
                }
                Text(
                    stringResource(R.string.cellar_bottles, item.quantity),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cellar_increase))
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
                    CellarItem(wine = wine, quantity = 3, status = CellarStatus.HOME, updatedAt = 0L),
                    CellarItem(wine = wine.copy(id = "2", name = "Barolo 2019"), quantity = 1, status = CellarStatus.WISH, updatedAt = 0L)
                )
            )
        )
    }
}
