package com.wineapp.presentation.notfound

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun NotFoundScreen(
    onRetry: () -> Unit = {},
    onSearch: () -> Unit = {}
) {
    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = surfaceColor.toArgb()
        window.navigationBarColor = surfaceColor.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
    }
    NotFoundScreenContent(
        onRetry = onRetry,
        onSearch = onSearch
    )
}

@Composable
fun NotFoundScreenContent(
    onRetry: () -> Unit,
    onSearch: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.WineBar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(com.wineapp.R.string.notfound_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(com.wineapp.R.string.notfound_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(stringResource(com.wineapp.R.string.notfound_retry))
                }
                Button(
                    onClick = onSearch,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(com.wineapp.R.string.notfound_search))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotFoundScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        NotFoundScreenContent(onRetry = {}, onSearch = {})
    }
}