package com.wineapp.presentation.scanner

import com.wineapp.presentation.common.ui.AppIcons
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
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
import com.wineapp.domain.model.detailRecognitionStatus
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.ui.theme.WineAppTheme

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
    val autoCaptureVm by viewModel.autoCapture.collectAsState()

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

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            viewModel.setScanScreenActive(lifecycleOwner.lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.RESUMED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setScanScreenActive(lifecycleOwner.lifecycle.currentState.isAtLeast(
            androidx.lifecycle.Lifecycle.State.RESUMED))
        onDispose {
            viewModel.consumeAutoOpenWine()
            viewModel.setScanScreenActive(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cameraHelper.shutdown()
        }
    }
    LaunchedEffect(state) { viewModel.cameraHelper.setAnalysisEnabled(state is ScannerState.Ready) }
    LaunchedEffect(viewModel.cameraHelper) {
        viewModel.cameraHelper.onAutoCapture = { file ->
            viewModel.sendIntent(ScannerIntent.CapturePhoto(file.absolutePath))
        }
    }

    val success = state as? ScannerState.Success
    val autoOpenWine by viewModel.autoOpenWine.collectAsState()
    LaunchedEffect(success, autoOpenWine) {
        val wineId = autoOpenWine ?: return@LaunchedEffect
        val current = success ?: return@LaunchedEffect
        // Событие гасится в onDispose ниже: так во время анимации перехода
        // под карточкой остаётся фон, а по «назад» уже показывается итог скана.
        onNavigateToDetail(wineId, current.imagePath, current.result.detailRecognitionStatus(wineId))
    }
    if (success != null && autoOpenWine != null) {
        // Переход на карточку уже запущен — держим фирменный фон вместо вспышки итога.
        Box(modifier = Modifier.fillMaxSize().background(com.wineapp.ui.theme.BrandCream50))
        return
    }
    if (success != null) {
        val alreadyTried by viewModel.alreadyTried.collectAsState()
        ScanSummaryScreen(
            state = success,
            alreadyTried = alreadyTried,
            onConfirm = viewModel::confirmCandidate,
            onOpenWine = { id, isRecognition ->
                onNavigateToDetail(id, if (isRecognition) success.imagePath else null,
                    if (isRecognition) success.result.detailRecognitionStatus(id) else null)
            },
            onNewPhoto = viewModel::resetToReady,
            onBack = onNavigateBack
        )
        return
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
        onGalleryClick = { openGallery() },
        autoCapture = autoCaptureVm,
        onAutoClick = viewModel::toggleAutoCapture
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
    autoCapture: Boolean = true,
    onAutoClick: () -> Unit = {},
) {
    val detector by cameraHelper.detectorPreview.collectAsState()
    val context = LocalContext.current
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
            onRetry()
        }
    }

    LaunchedEffect(badgeMessage) {
        if (badgeMessage != null) {
            snackbarHostState.showSnackbar(badgeMessage, duration = SnackbarDuration.Short)
            onBadgeMessageShown()
        }
    }

    var showOnboarding by remember { mutableStateOf(false) }
    // Режим автораспознавания — источник правды во VM; онбординг-шит только
    // временно гасит его через эффект выше.
    // Этикетка в кадре: уголки желтеют + разовый тик строго на появление боксов.
    // detector.highlight — уже строгий сигнал (1 eligible-бокс, 3 стабильных кадра),
    // сырой boxes.isNotEmpty() давал ложные срабатывания на мусор детектора.
    val labelDetected = state is ScannerState.Ready && detector.highlight
    var wasDetected by remember { mutableStateOf(false) }
    LaunchedEffect(labelDetected) {
        if (labelDetected && !wasDetected) {
            com.wineapp.util.HapticHelper.vibrateTick(context)
        }
        wasDetected = labelDetected
    }
    LaunchedEffect(showOnboarding, autoCapture) { cameraHelper.setAutoCapture(!showOnboarding && autoCapture) }
    LaunchedEffect(detector.error) {
        detector.error?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Единое условие готовности: рамка и центр затвора желтеют синхронно.
        val frameHighlighted = labelDetected ||
            state is ScannerState.Capturing ||
            state is ScannerState.Processing
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

        ScanWindowOverlay(
            modifier = Modifier.fillMaxSize(),
            // Кадр уходит на бэк (съёмка/загрузка) — рамка уже жёлтая,
            // даже если детектор к этому моменту боксы снял.
            highlighted = frameHighlighted
        )

        ScannerTopBar(
            flashMode = flashMode,
            autoCapture = autoCapture,
            onCloseClick = onCloseClick,
            onFlashClick = onToggleFlash,
            onAutoClick = onAutoClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 56.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state is ScannerState.Ready) ScanHintPill()
            ScannerShutterControls(
                onGalleryClick = onGalleryClick,
                onCaptureClick = {
                    if (state is ScannerState.Ready) cameraHelper.takePicture(
                        onSuccess = { file -> onTakePicture(file.absolutePath) },
                        onError = { }
                    )
                },
                onHelpClick = { showOnboarding = true },
                modifier = Modifier.fillMaxWidth(),
                captureReady = frameHighlighted
            )
        }

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
fun ScanWindowOverlay(modifier: Modifier = Modifier, highlighted: Boolean = false) {
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
    // Подсветка уголков (этикетка в кадре / фото уходит на бэк) — плавный
    // переход белый -> золотой. Вызов здесь: внутри Canvas @Composable запрещены.
    val arcColor by animateColorAsState(
        targetValue = if (highlighted) Color(0xFFFFD700) else Color.White,
        animationSpec = tween(durationMillis = 350),
        label = "scanCornerHighlight"
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
            fun cornerArc(cx: Float, cy: Float, startAngle: Float) {
                drawArc(
                    color = arcColor,
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
    autoCapture: Boolean,
    onCloseClick: () -> Unit,
    onFlashClick: () -> Unit,
    onAutoClick: () -> Unit,
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
                    AppIcons.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // Середина: переключатель автораспознавания. Вкл — фото улетает на бэк
        // само при стабильной этикетке, выкл — только по кнопке затвора.
        // Подсветка рамки и вибро от режима не зависят.
        Surface(
            onClick = onAutoClick,
            shape = RoundedCornerShape(percent = 50),
            color = if (autoCapture) Color.White else Color.Black.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (autoCapture) Color.Transparent else Color.White.copy(alpha = 0.6f)
            ),
            modifier = Modifier.height(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(com.wineapp.R.string.scanner_auto_short),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (autoCapture) Color.Black else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        Surface(
            onClick = onFlashClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // В ресурсах одна молния: выключенная вспышка — приглушённая.
                val flashOff = flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_OFF
                Icon(
                    AppIcons.Lightning,
                    contentDescription = stringResource(
                        if (flashOff)
                            com.wineapp.R.string.scanner_flash_off
                        else
                            com.wineapp.R.string.scanner_flash_on
                    ),
                    tint = if (flashOff) Color.White.copy(alpha = 0.4f) else Color.White,
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
    modifier: Modifier = Modifier,
    // Та же логика, что у подсветки рамки: стабильная этикетка в кадре
    // или кадр уже уходит на бэк — центр затвора желтеет.
    captureReady: Boolean = false,
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
                    AppIcons.Gallery,
                    contentDescription = stringResource(com.wineapp.R.string.scanner_gallery),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        // Затвор: кольцо 80dp + середина 66dp (белая, при готовности — золотая).
        val shutterCenter by animateColorAsState(
            targetValue = if (captureReady) Color(0xFFFFD700) else Color.White,
            animationSpec = tween(durationMillis = 350),
            label = "shutterReady"
        )
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
                    color = shutterCenter,
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
                    AppIcons.Question,
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
    WineAppTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        ScanWindowOverlay(modifier = Modifier.fillMaxSize(), highlighted = true)
            ScannerTopBar(
                flashMode = androidx.camera.core.ImageCapture.FLASH_MODE_OFF,
                autoCapture = true,
                onCloseClick = {},
                onFlashClick = {},
                onAutoClick = {},
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
    WineAppTheme {
        ScannerScreenContent(
            state = ScannerState.Ready(),
            cameraHelper = CameraHelper(LocalContext.current,
                com.wineapp.data.detector.LabelDetectorRuntime(LocalContext.current)),
            lifecycleOwner = LocalLifecycleOwner.current,
            onGalleryClick = {},
            onTakePicture = {},
            onToggleFlash = {},
            onRetry = {},
            onCloseClick = {}
        )
    }
}
