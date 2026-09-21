package com.wineapp.presentation.scanresult

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.presentation.common.WineAppTopAppBar
import com.wineapp.presentation.common.WineCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    confidence: Float,
    mainWineId: String?,
    alternativeIds: List<String>,
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface

    val mainWine = mainWineId?.let { id -> MockDataProvider.wines.find { it.id == id } }
    val alternatives = alternativeIds.mapNotNull { id -> MockDataProvider.wines.find { it.id == id } }

    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                ConfidenceHeader(confidence = confidence)
            }

            if (mainWine != null) {
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
                        wine = mainWine,
                        onClick = { onNavigateToDetail(mainWine.id) }
                    )
                }
            }

            if (alternatives.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scan_result_alternatives),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                itemsIndexed(alternatives) { _, wine ->
                    WineCard(
                        wine = wine,
                        onClick = { onNavigateToDetail(wine.id) }
                    )
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
                    "${String.format("%.0f", confidence * 100)}%",
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
        ScanResultScreen(
            confidence = 0.92f,
            mainWineId = "1",
            alternativeIds = listOf("2", "3", "4"),
            onNavigateToDetail = {},
            onNavigateBack = {}
        )
    }
}
