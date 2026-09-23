package com.wineapp.presentation.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import com.wineapp.data.file.CameraHelper
import com.wineapp.presentation.common.ui.ErrorMessage

@Composable
fun ScannerScreen(
    onNavigateToDetail: (wineId: String, photoPath: String?, recognitionStatus: String?) -> Unit = { _, _, _ -> },
    onNavigateToScanResult: (confidence: Float, mainWineId: String, alternativeIds: String, photoPath: String?, recognitionStatus: String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: ScannerViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val badgeMessage by viewModel.badgeMessage.collectAsState()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.cameraHelper.onCameraPermissionResult(granted) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.sendIntent(ScannerIntent.GalleryImagePicked(uri.toString()))
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

    BackHandler { onNavigateBack() }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state) {
        when (val current = state) {
            is ScannerState.Success -> {
                val result = current.result
                val allWines = result.matches
                val mainWine = result.wine
                val mainWineId = mainWine?.id
                if (mainWineId == null) {
                    viewModel.resetToReady()
                    return@LaunchedEffect
                }
                if (allWines.size <= 1) {
                    viewModel.resetToReady()
                    onNavigateToDetail(mainWineId ?: "", current.imagePath, result.recognitionStatus)
                } else {
                    val altIds = allWines.filter { it.id != mainWineId }.joinToString("|") { it.id }
                    viewModel.resetToReady()
                    onNavigateToScanResult(result.confidence, mainWineId, altIds, current.imagePath, result.recognitionStatus)
                }
            }
            else -> {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.cameraHelper.shutdown() }
    }
    ScannerScreenContent(
        state = state,
        cameraHelper = viewModel.cameraHelper,
        lifecycleOwner = lifecycleOwner,
        badgeMessage = badgeMessage,
        onBadgeMessageShown = { viewModel.consumeBadgeMessage() },
        onToggleFlash = { viewModel.sendIntent(ScannerIntent.ToggleFlash) },
        onTakePicture = { path -> viewModel.sendIntent(ScannerIntent.CapturePhoto(path))},
        onRetry = { viewModel.sendIntent(ScannerIntent.RetryScan) },
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

@Composable
fun ScannerScreenContent(
    state: ScannerState,
    cameraHelper: CameraHelper,
    lifecycleOwner: LifecycleOwner,
    onGalleryClick: () -> Unit,
    onTakePicture: (String) -> Unit,
    onToggleFlash: () -> Unit,
    onRetry: () -> Unit,
    badgeMessage: String? = null,
    onBadgeMessageShown: () -> Unit = {},
) {
    val flashMode = when (val s = state) {
        is ScannerState.Ready -> s.flashMode
        else -> androidx.camera.core.ImageCapture.FLASH_MODE_OFF
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val noTargetStr = stringResource(com.wineapp.R.string.scan_status_no_target)
    val candidatesUnverifiedStr = stringResource(com.wineapp.R.string.scan_status_candidates_unverified)
    val notFoundFallbackStr = stringResource(com.wineapp.R.string.notfound_message)

    val notFoundMessage = when (val current = state) {
        is ScannerState.NotFound -> when (current.recognitionStatus) {
            "no_target" -> noTargetStr
            "candidates_unverified" -> candidatesUnverifiedStr
            else -> current.message ?: notFoundFallbackStr
        }
        else -> null
    }

    LaunchedEffect(notFoundMessage) {
        if (notFoundMessage != null) {
            snackbarHostState.showSnackbar(notFoundMessage, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(badgeMessage) {
        if (badgeMessage != null) {
            snackbarHostState.showSnackbar(badgeMessage, duration = SnackbarDuration.Short)
            onBadgeMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                if (cameraHelper.previewView != previewView) {
                    cameraHelper.bindToLifecycle(lifecycleOwner, previewView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        ScannerFrameOverlay(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = stringResource(com.wineapp.R.string.scanner_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

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
                    cameraHelper.takePicture(
                        onSuccess = { file -> onTakePicture(file.absolutePath) },
                        onError = { }
                    )
                },
                onFlashClick = onToggleFlash,
            )
        }

        when (val current = state) {
            is ScannerState.Processing -> ScanProcessingOverlay(seed = current.seed, factIndex = current.factIndex)
            is ScannerState.Success -> { }
            is ScannerState.NotFound -> { }
            is ScannerState.Error -> ErrorMessage(message = current.message, onRetry = onRetry)
            else -> {}
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 140.dp)
        )
    }
}

@Composable
fun ScannerFrameOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameSize = 280.dp
        val bracketLength = 36.dp
        val bracketWidth = 3.5f.dp
        val primaryColor = MaterialTheme.colorScheme.primary

        Box(modifier = Modifier.size(frameSize)) {
            val scrimAlpha = 0.45f
            val innerGap = bracketWidth

            // Scrim layers around the frame
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).height(innerGap).background(Color.Black.copy(alpha = scrimAlpha)))
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).height(innerGap).background(Color.Black.copy(alpha = scrimAlpha)))
            Box(modifier = Modifier.align(Alignment.TopStart).width(innerGap).height(frameSize).background(Color.Black.copy(alpha = scrimAlpha)))
            Box(modifier = Modifier.align(Alignment.TopEnd).width(innerGap).height(frameSize).background(Color.Black.copy(alpha = scrimAlpha)))

            // Corner brackets
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw = bracketWidth.toPx()
                val len = bracketLength.toPx()
                val half = sw / 2
                val gap = 2.dp.toPx()

                // Top-left
                drawLine(primaryColor, Offset(half, half + len), Offset(half, half + gap), sw, cap = StrokeCap.Round)
                drawLine(primaryColor, Offset(half + gap, half), Offset(half + len, half), sw, cap = StrokeCap.Round)
                // Top-right
                drawLine(primaryColor, Offset(size.width - half - len, half), Offset(size.width - half - gap, half), sw, cap = StrokeCap.Round)
                drawLine(primaryColor, Offset(size.width - half, half + gap), Offset(size.width - half, half + len), sw, cap = StrokeCap.Round)
                // Bottom-left
                drawLine(primaryColor, Offset(half, size.height - half - len), Offset(half, size.height - half - gap), sw, cap = StrokeCap.Round)
                drawLine(primaryColor, Offset(half + gap, size.height - half), Offset(half + len, size.height - half), sw, cap = StrokeCap.Round)
                // Bottom-right
                drawLine(primaryColor, Offset(size.width - half, size.height - half - len), Offset(size.width - half, size.height - half - gap), sw, cap = StrokeCap.Round)
                drawLine(primaryColor, Offset(size.width - half - gap, size.height - half), Offset(size.width - half - len, size.height - half), sw, cap = StrokeCap.Round)
            }

            // Scan line
            val frameSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { frameSize.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = innerGap)
                    .height(2.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer { translationY = (scanLineY - 0.5f) * frameSizePx }
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.4f),
                                primaryColor.copy(alpha = 0.9f),
                                primaryColor.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                )
            )
            .padding(horizontal = 32.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGalleryClick) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(com.wineapp.R.string.scanner_gallery),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Surface(
                onClick = onCaptureClick,
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(76.dp),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = stringResource(com.wineapp.R.string.scanner_capture),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onFlashClick) {
                val icon = when (flashMode) {
                    androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    else -> Icons.Default.FlashOff
                }
                Icon(
                    icon,
                    contentDescription = stringResource(
                        if (flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_OFF)
                            com.wineapp.R.string.scanner_flash_off
                        else
                            com.wineapp.R.string.scanner_flash_on
                    ),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, heightDp = 800)
@Composable
private fun ScannerControlsPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
            ScannerFrameOverlay(modifier = Modifier.fillMaxSize())
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

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ScannerScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        ScannerScreenContent(
            state = ScannerState.Ready(),
            cameraHelper = CameraHelper(LocalContext.current),
            lifecycleOwner = LocalLifecycleOwner.current,
            onGalleryClick = {},
            onTakePicture = {},
            onToggleFlash = {},
            onRetry = {}
        )
    }
}
