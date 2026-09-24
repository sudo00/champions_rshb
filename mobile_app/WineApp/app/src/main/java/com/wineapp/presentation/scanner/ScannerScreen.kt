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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    onNavigateBack: () -> Unit = {},
    startGalleryPicker: Boolean = false
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

    val openGallery = {
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

    LaunchedEffect(startGalleryPicker) {
        if (startGalleryPicker) openGallery()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state) {
        when (val current = state) {
            is ScannerState.Success -> {
                com.wineapp.util.HapticHelper.vibrateSuccess(context)
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
        onCloseClick = onNavigateBack,
        onGalleryClick = { openGallery() }
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
    onCloseClick: () -> Unit = {},
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

    var showOnboarding by remember { mutableStateOf(false) }

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

        ScanWindowOverlay(modifier = Modifier.fillMaxSize())

        ScannerTopBar(
            flashMode = flashMode,
            onCloseClick = onCloseClick,
            onFlashClick = onToggleFlash,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 56.dp)
        )

        ScanHintPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (112 + 520 - 51).dp)
        )

        ScannerShutterControls(
            onGalleryClick = onGalleryClick,
            onCaptureClick = {
                cameraHelper.takePicture(
                    onSuccess = { file -> onTakePicture(file.absolutePath) },
                    onError = { }
                )
            },
            onHelpClick = { showOnboarding = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 56.dp)
        )

        if (showOnboarding) {
            ScannerOnboardingSheet(onDismiss = { showOnboarding = false })
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

// Размеры окна сканирования из макета (фрейм 427px).
private val CutoutSideMargin = 16.dp
private val CutoutTop = 112.dp
private val CutoutHeight = 520.dp
private val CutoutRadius = 44.dp
private val BracketWidth = 6.dp

/**
 * Затемнение всего экрана с вырезом-окном + уголки-дуги + бегущая скан-линия.
 * Скан-линия из прод-версии сохранена, ход пересчитан на высоту выреза.
 */
@Composable
fun ScanWindowOverlay(modifier: Modifier = Modifier) {
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

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val margin = CutoutSideMargin.toPx()

            val top = CutoutTop.toPx()
            val cutoutH = CutoutHeight.toPx()
            val cutout = RoundRect(
                rect = Rect(
                    Offset(margin, top),
                    Size(
                        size.width - margin * 2,
                        cutoutH
                    )
                ),
                cornerRadius = CornerRadius(
                    CutoutRadius.toPx(),
                    CutoutRadius.toPx()
                )
            )
            val dimmed = Path().apply {
                addRect(Rect(Offset.Zero, size))
            }.let { full ->
                Path.combine(
                    operation = PathOperation.Difference,
                    path1 = full,
                    path2 = Path().apply { addRoundRect(cutout) }
                )
            }
            drawPath(dimmed, Color.Black.copy(alpha = 0.3f))

            // Уголки рамки: дуги тем же радиусом, что и вырез (44), на его углах.
            val r = CutoutRadius.toPx()
            val sw = BracketWidth.toPx()
            val arcStyle = Stroke(width = sw, cap = StrokeCap.Round)
            val white = Color.White
            fun cornerArc(cx: Float, cy: Float, startAngle: Float) {
                drawArc(
                    color = white,
                    startAngle = startAngle,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = arcStyle
                )
            }
            val bottom = top + cutoutH
            cornerArc(margin + r, top + r, 180f) // top-left
            cornerArc(size.width - margin - r, top + r, 270f) // top-right
            cornerArc(size.width - margin - r, bottom - r, 0f) // bottom-right
            cornerArc(margin + r, bottom - r, 90f) // bottom-left

            // Бегущая скан-линия внутри выреза. Обрезана по контуру окна,
            // чтобы у скруглённых краёв не вылезать за рамки, и чуть уже окна.
            val lineInset = margin + 16.dp.toPx()
            clipPath(Path().apply { addRoundRect(cutout) }) {
                val lineY = top + scanLineY * cutoutH
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(lineInset, lineY),
                    end = Offset(size.width - lineInset, lineY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun ScannerTopBar(
    flashMode: Int,
    onCloseClick: () -> Unit,
    onFlashClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onCloseClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = when (flashMode) {
                androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> stringResource(com.wineapp.R.string.scanner_flash_auto_short)
                androidx.camera.core.ImageCapture.FLASH_MODE_ON -> stringResource(com.wineapp.R.string.scanner_flash_on_short)
                else -> stringResource(com.wineapp.R.string.scanner_flash_off_short)
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Surface(
            onClick = onFlashClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    when (flashMode) {
                        androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = stringResource(
                        if (flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_OFF)
                            com.wineapp.R.string.scanner_flash_off
                        else
                            com.wineapp.R.string.scanner_flash_on
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ScanHintPill(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.White.copy(alpha = 0.8f),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Text(
            stringResource(com.wineapp.R.string.scanner_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ScannerShutterControls(
    onGalleryClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Галерея: круг 66dp, белая рамка 1px.
        Surface(
            onClick = onGalleryClick,
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            modifier = Modifier.size(66.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(com.wineapp.R.string.scanner_gallery),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        // Затвор: кольцо 80dp + белая середина 66dp.
        Surface(
            onClick = onCaptureClick,
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(4.dp, Color.White),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    onClick = onCaptureClick,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(66.dp)
                ) {}
            }
        }
        // Помощь: круг 66dp, белая рамка 1px. Открывает онбординг-шит.
        Surface(
            onClick = onHelpClick,
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            modifier = Modifier.size(66.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.QuestionMark,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerOnboardingSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        // Шит едет от самой границы экрана: системные инсеты не резервируем в окне,
        // а отбивку контента делаем вручную — иначе снизу просвечивает камера.
        windowInsets = WindowInsets(0.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 36.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(com.wineapp.R.string.scanner_onboarding_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(com.wineapp.R.string.scanner_onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, heightDp = 800)
@Composable
private fun ScannerShutterPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
            ScanWindowOverlay(modifier = Modifier.fillMaxSize())
            ScannerTopBar(
                flashMode = androidx.camera.core.ImageCapture.FLASH_MODE_OFF,
                onCloseClick = {},
                onFlashClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 56.dp)
            )
        ScanHintPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (112 + 520 - 51).dp)
        )
            ScannerShutterControls(
                onGalleryClick = {},
                onCaptureClick = {},
                onHelpClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 56.dp)
            )
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
            onRetry = {},
            onCloseClick = {}
        )
    }
}
