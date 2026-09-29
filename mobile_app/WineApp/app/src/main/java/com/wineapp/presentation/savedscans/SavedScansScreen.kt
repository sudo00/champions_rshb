package com.wineapp.presentation.savedscans

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.SavedScan
import com.wineapp.presentation.common.ui.AppIcons
import com.wineapp.presentation.common.ui.BrandLoader
import com.wineapp.presentation.common.ui.EmptyState
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WineListCaption
import com.wineapp.presentation.common.ui.WineListCard
import com.wineapp.presentation.common.ui.WineListHeader
import com.wineapp.presentation.common.ui.WineListHeadline
import com.wineapp.presentation.common.ui.WineListIconButton
import com.wineapp.presentation.common.ui.WineListMonthHeader
import com.wineapp.presentation.common.ui.WineListNothingFound
import com.wineapp.presentation.common.ui.WineListSort
import com.wineapp.presentation.common.ui.WineListSortFilterRow
import com.wineapp.presentation.common.ui.yearMonthOf
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.WineAppTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** System/Error из фигмы — карточка нераспознанного скана. */
private val ErrorBackground = Color(0xFFF7E5E1)
private val ErrorMain = Color(0xFFB65349)

@Composable
fun SavedScansScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: SavedScansViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(SavedScansIntent.LoadScans)
    }

    SavedScansScreenContent(
        state = state,
        onDeleteScan = { viewModel.sendIntent(SavedScansIntent.DeleteScan(it)) },
        onSort = { viewModel.sendIntent(SavedScansIntent.SetSort(it)) },
        onStyleFilter = { viewModel.sendIntent(SavedScansIntent.SetStyleFilter(it)) },
        onQuery = { viewModel.sendIntent(SavedScansIntent.SetQuery(it)) },
        onToggleFavorite = { viewModel.sendIntent(SavedScansIntent.ToggleFavorite(it)) },
        onNavigateToDetail = onNavigateToDetail,
        onScan = onScan,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun SavedScansScreenContent(
    state: SavedScansState,
    onDeleteScan: (String) -> Unit = {},
    onSort: (WineListSort) -> Unit = {},
    onStyleFilter: (String?) -> Unit = {},
    onQuery: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    TransparentSystemBars()
    var scanToDelete by remember { mutableStateOf<SavedScan?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val success = state as? SavedScansState.Success

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        Image(
            painter = painterResource(R.drawable.search_tab_ellipse),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WineListHeader(
                        title = stringResource(R.string.saved_scans_title),
                        searchOpen = searchOpen,
                        onBack = onNavigateBack,
                        onSearchClick = {
                            if (searchOpen) onQuery("")
                            searchOpen = !searchOpen
                        },
                        query = success?.query.orEmpty(),
                        searchHint = stringResource(R.string.list_search_hint),
                        onQuery = onQuery
                    )
                    if (success != null && !success.isHistoryEmpty) {
                        WineListSortFilterRow(
                            sort = success.sort,
                            styles = success.styles,
                            selectedStyle = success.styleFilter,
                            onSort = onSort,
                            onStyleFilter = onStyleFilter
                        )
                    }
                }
            }

            when (state) {
                is SavedScansState.Loading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center
                    ) { BrandLoader() }
                }

                is SavedScansState.Error -> item(key = "error") {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 120.dp)
                    )
                }

                is SavedScansState.Success -> {
                    if (state.isHistoryEmpty) {
                        item(key = "empty") {
                            EmptyState(
                                icon = AppIcons.Camera,
                                title = stringResource(R.string.saved_scans_empty),
                                message = stringResource(R.string.mywines_scans_empty_hint),
                                actionText = stringResource(R.string.home_scan),
                                actionIcon = AppIcons.Camera,
                                onAction = onScan,
                                modifier = Modifier.padding(vertical = 80.dp)
                            )
                        }
                    } else if (state.scans.isEmpty()) {
                        item(key = "nothing") {
                            WineListNothingFound(modifier = Modifier.padding(top = 32.dp))
                        }
                    } else {
                        val groups = if (state.sort.groupsByMonth) {
                            state.scans.groupBy { yearMonthOf(it.scannedAt) }.toList()
                        } else {
                            listOf(null to state.scans)
                        }
                        groups.forEachIndexed { index, (month, monthScans) ->
                            if (month != null) {
                                item(key = "month:$month") {
                                    WineListMonthHeader(month, isFirst = index == 0)
                                }
                            } else {
                                item(key = "list-top") { Spacer(Modifier.height(32.dp)) }
                            }
                            items(monthScans, key = { it.id }) { scan ->
                                SavedScanCard(
                                    scan = scan,
                                    isFavorite = scan.wine.id in state.likedIds,
                                    onClick = { onNavigateToDetail(scan.id) },
                                    onToggleFavorite = { onToggleFavorite(scan.wine.id) },
                                    onDelete = { scanToDelete = scan },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    scanToDelete?.let { scan ->
        DeleteScanDialog(
            scan = scan,
            onConfirm = {
                onDeleteScan(scan.id)
                scanToDelete = null
            },
            onDismiss = { scanToDelete = null }
        )
    }
}

/** Подтверждение удаления скана — общее для списка сканов и секции на «Моих винах». */
@Composable
internal fun DeleteScanDialog(scan: SavedScan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_scans_delete_title)) },
        text = { Text(stringResource(R.string.saved_scans_delete_confirm, scan.wine.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.saved_scans_delete), color = BrandBurgundy600)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Карточка скана (Scan card из макета). Фото — снимок пользователя. Распознанное вино —
 * сердечко «в избранное» сверху и корзина снизу. Нераспознанный скан — красная
 * карточка с фото этикетки, значком ошибки и подсказкой, только корзина.
 * Общая с секцией «Сканы» на экране «Мои вина» — там карточки те же, что в полном списке.
 */
@Composable
internal fun SavedScanCard(
    scan: SavedScan,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelPhoto = scan.labelPhotoPath?.let { File(it) }?.takeIf { it.exists() }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }
    val scanDate = dateFormat.format(Date(scan.scannedAt))
    val trash: @Composable () -> Unit = {
        WineListIconButton(
            icon = AppIcons.Trash,
            contentDescription = stringResource(R.string.saved_scans_delete),
            tint = BrandTextPrimary,
            onClick = onDelete
        )
    }

    if (scan.recognitionStatus == "not_in_catalog") {
        WineListCard(
            imageModel = labelPhoto,
            imageDescription = null,
            imageContentScale = ContentScale.Crop,
            onClick = onClick,
            containerColor = ErrorBackground,
            tag = scanDate,
            imageOverlay = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ErrorBackground)
                ) {
                    Icon(
                        AppIcons.Alert,
                        contentDescription = null,
                        tint = ErrorMain,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            headline = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.saved_scans_failed_title),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        color = BrandTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    WineListCaption(stringResource(R.string.saved_scans_failed_hint), maxLines = 2)
                }
            },
            modifier = modifier
        ) { trash() }
        return
    }

    WineListCard(
        imageModel = labelPhoto,
        imageDescription = scan.wine.name,
        imageContentScale = ContentScale.Crop,
        onClick = onClick,
        tag = scanDate,
        headline = { WineListHeadline(scan.wine) },
        topEndAction = {
            WineListIconButton(
                icon = if (isFavorite) AppIcons.HeartFilled else AppIcons.Heart,
                contentDescription = stringResource(
                    if (isFavorite) R.string.favorites_remove else R.string.saved_scans_add_favorite
                ),
                tint = BrandBurgundy600,
                onClick = onToggleFavorite
            )
        },
        modifier = modifier
    ) { trash() }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SavedScansScreenPreview() {
    val wines = MockDataProvider.wines
    val now = System.currentTimeMillis()
    WineAppTheme {
        SavedScansScreenContent(
            state = SavedScansState.Success(
                scans = listOf(
                    SavedScan(
                        id = "1", wine = wines[0], labelPhotoPath = null, confidence = 0.92f,
                        conversation = emptyList(), scannedAt = now, recognitionStatus = "score_confirmed"
                    ),
                    SavedScan(
                        id = "2", wine = wines[1], labelPhotoPath = null, confidence = 0.4f,
                        conversation = emptyList(), scannedAt = now - 3_600_000L,
                        recognitionStatus = "not_in_catalog"
                    )
                ),
                styles = listOfNotNull(wines[0].style),
                likedIds = setOf(wines[0].id)
            )
        )
    }
}
