package com.wineapp.presentation.scanresult

import android.app.Activity
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.presentation.common.ui.WineAppTopAppBar
import com.wineapp.presentation.common.ui.WineCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    confidence: Float,
    mainWineId: String,
    altIds: String,
    photoPath: String? = null,
    recognitionStatus: String? = null,
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: ScanResultViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val candidatesUnverifiedStr = stringResource(R.string.scan_status_candidates_unverified)

    LaunchedEffect(recognitionStatus) {
        if (recognitionStatus == "candidates_unverified") {
            snackbarHostState.showSnackbar(
                candidatesUnverifiedStr,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(mainWineId) {
        viewModel.sendIntent(ScanResultIntent.LoadWines(mainWineId, altIds))
    }

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

    Scaffold(
        topBar = {
            WineAppTopAppBar(
                title = stringResource(R.string.scan_result_title),
                showBack = true,
                onBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        ScanResultContent(
            state = state,
            confidence = confidence,
            onNavigateToDetail = onNavigateToDetail,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun ScanResultContent(
    state: ScanResultState,
    confidence: Float,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (val current = state) {
        is ScanResultState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is ScanResultState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is ScanResultState.Success -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                item {
                    ConfidenceHeader(confidence = confidence)
                }

                item {
                    Text(
                        stringResource(R.string.scan_result_best_match),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    WineCard(
                        modifier = Modifier.padding(8.dp),
                        wine = current.mainWine,
                        onClick = { onNavigateToDetail(current.mainWine.id) }
                    )
                }

                if (current.alternatives.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.scan_result_alternatives),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    itemsIndexed(current.alternatives) { _, wine ->
                        Spacer(Modifier.height(8.dp))
                        WineCard(
                            wine = wine,
                            onClick = { onNavigateToDetail(wine.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceHeader(confidence: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.scan_result_confidence),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${String.format("%.0f", confidence * 100)}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ScanResultScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wines = MockDataProvider.wines
        ScanResultContent(
            state = ScanResultState.Success(
                mainWine = wines.first(),
                alternatives = wines.drop(1).take(3)
            ),
            confidence = 0.92f,
            onNavigateToDetail = {}
        )
    }
}