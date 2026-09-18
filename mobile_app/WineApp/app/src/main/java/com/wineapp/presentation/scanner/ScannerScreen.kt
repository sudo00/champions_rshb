package com.wineapp.presentation.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import com.wineapp.presentation.common.ErrorMessage
import com.wineapp.presentation.common.LoadingOverlay
import java.io.File
import java.io.FileOutputStream

@Composable
fun ScannerScreen() {
    val viewModel: ScannerViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.cameraHelper.onCameraPermissionResult(granted) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val file = copyUriToFile(context, uri)
                file?.let { viewModel.sendIntent(ScannerIntent.ProcessImage(it.absolutePath)) }
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            galleryLauncher.launch(Intent(Intent.ACTION_PICK).apply { type = "image/*" })
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.cameraHelper.shutdown() }
    }
    ScannerScreenContent(
        viewModel = viewModel,
        lifecycleOwner = lifecycleOwner,
        onGalleryClick = {
            val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch(Intent(Intent.ACTION_PICK).apply { type = "image/*" })
            } else {
                storagePermissionLauncher.launch(storagePermission)
            }
        }
    )
}

private fun copyUriToFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("gallery_", ".jpg", context.cacheDir)
        val outputStream = FileOutputStream(tempFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        tempFile
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ScannerScreenContent(
    viewModel: ScannerViewModel,
    lifecycleOwner: LifecycleOwner,
    onGalleryClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val flashMode = when (val s = state) {
        is ScannerState.Ready -> s.flashMode
        else -> androidx.camera.core.ImageCapture.FLASH_MODE_OFF
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview using AndroidView
        AndroidView(
            factory = { PreviewView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            } },
            update = { previewView ->
                if (viewModel.cameraHelper.previewView != previewView) {
                    viewModel.cameraHelper.bindToLifecycle(lifecycleOwner, previewView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scanner Frame Overlay
        ScannerFrameOverlay(modifier = Modifier.fillMaxSize())

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScannerControls(
                flashMode = flashMode,
                onGalleryClick = onGalleryClick,
                onCaptureClick = {
                    viewModel.cameraHelper.takePicture(
                        onSuccess = { file -> viewModel.sendIntent(ScannerIntent.CapturePhoto(file.absolutePath)) },
                        onError = { /* Capture error */ }
                    )
                },
                onFlashClick = { viewModel.sendIntent(ScannerIntent.ToggleFlash) }
            )
        }

        // State Overlays
        when (val current = state) {
            is ScannerState.Processing -> LoadingOverlay(message = stringResource(com.wineapp.R.string.scanner_processing))
            is ScannerState.Success -> {
                // Navigation will be handled by parent via callbacks
            }
            is ScannerState.NotFound -> {
                // Navigation will be handled by parent via callbacks
            }
            is ScannerState.Error -> ErrorMessage(message = current.message, onRetry = { viewModel.sendIntent(ScannerIntent.RetryScan) })
            else -> {}
        }
    }
}

@Composable
fun ScannerFrameOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameSize = 280.dp
        Box(
            modifier = Modifier
                .size(frameSize)
                .background(
                    Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                )
                .border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            // Corner indicators
            Box(modifier = Modifier.align(Alignment.TopStart).size(24.dp).padding(8.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPath(
                        color = primaryColor,
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height * 0.3f)
                            lineTo(0f, 0f)
                            lineTo(size.width * 0.3f, 0f)
                        },
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).size(24.dp).padding(8.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPath(
                        color = primaryColor,
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.7f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height * 0.3f)
                        },
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomStart).size(24.dp).padding(8.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPath(
                        color = primaryColor,
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height * 0.7f)
                            lineTo(0f, size.height)
                            lineTo(size.width * 0.3f, size.height)
                        },
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).size(24.dp).padding(8.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPath(
                        color = primaryColor,
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.7f, size.height)
                            lineTo(size.width, size.height)
                            lineTo(size.width, size.height * 0.7f)
                        },
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerControls(
    flashMode: Int,
    onGalleryClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onFlashClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onGalleryClick) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(com.wineapp.R.string.scanner_gallery), tint = Color.White, modifier = Modifier.size(28.dp))
        }

        Button(
            onClick = onCaptureClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Circle, contentDescription = stringResource(com.wineapp.R.string.scanner_capture), modifier = Modifier.size(40.dp))
        }

        IconButton(onClick = onFlashClick) {
            val icon = when (flashMode) {
                androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                else -> Icons.Default.FlashOff
            }
            Icon(icon, contentDescription = stringResource(if (flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_OFF) com.wineapp.R.string.scanner_flash_off else com.wineapp.R.string.scanner_flash_on), tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, heightDp = 800)
@Composable
private fun ScannerControlsPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
            Column(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScannerControls(
                    flashMode = androidx.camera.core.ImageCapture.FLASH_MODE_OFF,
                    onGalleryClick = {},
                    onCaptureClick = {},
                    onFlashClick = {}
                )
            }
        }
    }
}