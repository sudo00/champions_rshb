package com.wineapp.presentation.agegate

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Возрастной гейт как полноэкранный блокирующий оверлей, а не BottomSheet:
 * свайкать нечего, системная кнопка «назад» закрывает приложение.
 * Показывается только при первом открытии (см. AgeGatePrefs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeGateScreen(
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val exitApp = {
        (context as? Activity)?.finishAffinity()
        System.exit(0)
    }

    // Перехватываем системный «назад» раньше всех: выход из приложения.
    BackHandler(enabled = true) {
        onDeny()
        exitApp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            AgeGateBottomSheetContent(
                onConfirm = onConfirm,
                onDeny = {
                    onDeny()
                    exitApp()
                }
            )
        }
    }
}

@Composable
fun AgeGateBottomSheetContent(
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\uD83C\uDF77",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(com.wineapp.R.string.age_gate_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(com.wineapp.R.string.age_gate_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDeny,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(com.wineapp.R.string.age_gate_deny))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(com.wineapp.R.string.age_gate_confirm))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AgeGateBottomSheetPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        AgeGateBottomSheetContent(onConfirm = {}, onDeny = {})
    }
}
