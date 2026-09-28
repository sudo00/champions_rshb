package com.wineapp.presentation.savedscans

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wineapp.domain.model.SavedScan
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
import com.wineapp.ui.theme.WineAppTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedScansScreen(
    onNavigateToDetail: (String) -> Unit = {},
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
        onNavigateToDetail = onNavigateToDetail,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScansScreenContent(
    state: SavedScansState,
    onDeleteScan: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
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

    var scanToDelete by remember { mutableStateOf<SavedScan?>(null) }

    scanToDelete?.let { scan ->
        AlertDialog(
            onDismissRequest = { scanToDelete = null },
            title = { Text(stringResource(R.string.saved_scans_delete_title)) },
            text = { Text(stringResource(R.string.saved_scans_delete_confirm, scan.wine.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteScan(scan.id)
                    scanToDelete = null
                }) {
                    Text(
                        stringResource(R.string.saved_scans_delete),
                        color = BrandBurgundy600
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { scanToDelete = null }) {
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
        SavedScansHeader(
            title = stringResource(R.string.saved_scans_title),
            onBack = onNavigateBack
        )
        when (state) {
            is SavedScansState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandBurgundy600)
                }
            }
            is SavedScansState.Error -> {
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
            is SavedScansState.Success -> {
                if (state.scans.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Scanner,
                        title = stringResource(R.string.saved_scans_empty),
                        message = stringResource(R.string.saved_scans_empty_hint)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            items = state.scans,
                            key = { it.id }
                        ) { scan ->
                            SavedScanCard(
                                scan = scan,
                                onClick = { onNavigateToDetail(scan.id) },
                                onLongClick = { scanToDelete = scan }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedScansHeader(
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
fun SavedScanCard(
    scan: SavedScan,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }

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
                scan.labelPhotoPath?.let { path ->
                    if (File(path).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(path))
                                .crossfade(true)
                                .build(),
                            contentDescription = scan.wine.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scan.wine.name,
                    fontFamily = Playfair,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    color = BrandTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                scan.recognitionStatus.takeUnless { it == "legacy" }?.let { status ->
                    Text(
                        text = when (status) {
                            "user_confirmed" -> "Подтверждено вами"
                            "score_confirmed" -> "Распознано автоматически"
                            "not_in_catalog" -> "Нет в каталоге"
                            else -> "Не подтверждено · первый кандидат"
                        },
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextSecondary
                    )
                }
                scan.wine.winery?.let {
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
                    scan.wine.vintage?.let {
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
                    scan.wine.region?.let {
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
                    if (scan.wine.rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = BrandCream500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                String.format("%.1f", scan.wine.rating),
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = BrandTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(R.string.saved_scans_confidence, (scan.confidence * 100).toInt()),
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    dateFormat.format(Date(scan.scannedAt)),
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BrandTextSecondary
                )
            }
            Surface(
                onClick = onLongClick,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.saved_scans_delete),
                        tint = BrandTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SavedScanCardPreview() {
    val previewScan = SavedScan(
        id = "1",
        wine = Wine(
            id = "w1",
            name = "Chateau Margaux 2018",
            vintage = 2018,
            rating = 4.7f,
            reviewsCount = 2341,
            price = 450.0,
            currency = "$",
            region = "Bordeaux",
            country = "France",
            variety = "Cabernet Sauvignon",
            style = "Dry Red",
            alcoholPercentage = 13.5f,
            imageUrl = null,
            description = "A majestic wine",
            foodPairing = listOf("Lamb", "Cheese"),
            winery = "Chateau Margaux"
        ),
        labelPhotoPath = null,
        confidence = 0.92f,
        conversation = emptyList(),
        scannedAt = System.currentTimeMillis()
    )
    WineAppTheme {
        SavedScansScreenContent(
            state = SavedScansState.Success(
                scans = listOf(previewScan),
            ),
        )
    }
}
