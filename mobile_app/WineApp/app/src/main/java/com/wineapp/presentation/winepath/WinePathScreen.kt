package com.wineapp.presentation.winepath

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.local.TerritoryShapes
import com.wineapp.domain.model.BadgeUi
import com.wineapp.domain.model.TerritoryProgress
import com.wineapp.domain.model.WinePathSummary
import com.wineapp.presentation.common.ui.ErrorMessage
import kotlinx.coroutines.launch

@Composable
fun WinePathScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToScanner: () -> Unit = {}
) {
    val viewModel: WinePathViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    WinePathScreenContent(
        state = state,
        onRetry = { viewModel.sendIntent(WinePathIntent.LoadPath) },
        onConsumeCelebration = { viewModel.sendIntent(WinePathIntent.ConsumeCelebration) },
        onNavigateBack = onNavigateBack,
        onNavigateToScanner = onNavigateToScanner
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinePathScreenContent(
    state: WinePathState,
    onRetry: () -> Unit = {},
    onConsumeCelebration: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToScanner: () -> Unit = {}
) {
    // Тёмная карта на весь экран — светлые иконки статус-бара (другие экраны вернут свои сами).
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = false
        }
    }

    val sheetState = androidx.compose.material3.rememberStandardBottomSheetState(
        initialValue = androidx.compose.material3.SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val mapUi = rememberWinePathMapState()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 112.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContent = {
            val success = state as? WinePathState.Success
            WinePathSheetContent(
                summary = success?.summary,
                territories = success?.territories ?: emptyList(),
                badges = success?.badges ?: emptyList(),
                onSelectTerritory = { id ->
                    selectedId = id
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                }
            )
        },
        containerColor = Color(0xFF141C26)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141C26))
        ) {
            when (state) {
                is WinePathState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color.White) }

                is WinePathState.Error -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { ErrorMessage(message = state.message, onRetry = onRetry) }

                is WinePathState.Success -> {
                    WinePathMap(
                        mapUi = mapUi,
                        territories = state.territories,
                        selectedId = selectedId,
                        onSelect = { hit -> selectedId = if (hit == selectedId) null else hit }
                    )
                    if (state.summary.scansCount == 0) {
                        // Компактная плавающая подсказка: карту не сжимает, висит поверх.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF232D3A).copy(alpha = 0.95f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Map,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.winepath_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.winepath_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.material3.Button(onClick = onNavigateToScanner) {
                                        Text(stringResource(R.string.winepath_scan))
                                    }
                                }
                            }
                        }
                    } else {
                        state.territories.find { it.territoryId == selectedId }?.let { selected ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 124.dp)
                            ) {
                                TerritoryDetailCard(
                                    territory = selected,
                                    onClose = { selectedId = null }
                                )
                            }
                        }
                    }
                }
            }

            MapOverlayTopBar(
                points = (state as? WinePathState.Success)?.summary?.totalPoints,
                onBack = onNavigateBack,
                onZoomToggle = {
                    scope.launch {
                        if (mapUi.isZoomed()) mapUi.resetView() else mapUi.flyToWineRegions()
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    val celebration = (state as? WinePathState.Success)?.celebration
    if (celebration != null) {
        AlertDialog(
            onDismissRequest = onConsumeCelebration,
            icon = {
                androidx.compose.material3.Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text(stringResource(R.string.winepath_celebration_title), textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        celebration.def.title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.winepath_celebration_points, celebration.def.points),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConsumeCelebration) { Text("OK") }
            }
        )
    }
}

@Composable
private fun WinePathHeader(summary: WinePathSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.winepath_level, summary.level),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.winepath_points, summary.totalPoints),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { summary.levelProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.winepath_stats,
                    summary.territoriesOpened,
                    summary.territoriesTotal,
                    summary.scansCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/** Аспект данных карты (lon 19–193 × lat 41–82 с поправкой на косинус широты). */
private const val MAP_DATA_ASPECT = 2.02f
private const val MAP_MIN_SCALE = 1f
private const val MAP_MAX_SCALE = 8f
/** Bbox винного кластера в долях карты — цель кнопки «К винным регионам». */
private val WINE_BBOX_MIN = Offset(0.04f, 0.60f)
private val WINE_BBOX_MAX = Offset(0.28f, 0.98f)

/** Состояние панорамирования/зума карты. Живёт в remember, переживает рекомпозиции жестов. */
class WinePathMapState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var boxW = 0f
        private set
    var boxH = 0f
        private set
    var drawW = 1f
        private set
    var drawH = 1f
        private set
    var offX = 0f
        private set
    var offY = 0f
        private set
    private var initialized = false

    fun onSize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        boxW = w
        boxH = h
        drawW = minOf(w, h * MAP_DATA_ASPECT)
        drawH = drawW / MAP_DATA_ASPECT
        offX = (w - drawW) / 2f
        offY = (h - drawH) / 2f
        if (!initialized) {
            initialized = true
            // Стартовый вид — сразу фокус на юго-западе (винные регионы), а не обзор всей страны.
            val min = toPx(WINE_BBOX_MIN)
            val max = toPx(WINE_BBOX_MAX)
            scale = minOf(w / (max.x - min.x), h / (max.y - min.y)).coerceIn(MAP_MIN_SCALE, MAP_MAX_SCALE)
            val c = Offset((min.x + max.x) / 2f, (min.y + max.y) / 2f)
            offset = clamp(Offset(w / 2f - scale * c.x, h / 2f - scale * c.y))
        } else {
            offset = clamp(offset)
        }
    }

    fun toPx(f: Offset): Offset = Offset(offX + f.x * drawW, offY + f.y * drawH)

    fun toFraction(tap: Offset): Offset {
        val bx = (tap.x - offset.x) / scale
        val by = (tap.y - offset.y) / scale
        return Offset((bx - offX) / drawW, (by - offY) / drawH)
    }

    fun onTransform(zoomChange: Float, panChange: Offset) {
        scale = (scale * zoomChange).coerceIn(MAP_MIN_SCALE, MAP_MAX_SCALE)
        offset = clamp(offset + panChange)
    }

    fun isZoomed(): Boolean = scale > 2f

    suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        val s = targetScale.coerceIn(MAP_MIN_SCALE, MAP_MAX_SCALE)
        val o = clamp(targetOffset, s)
        kotlinx.coroutines.coroutineScope {
            launch { scale = Animatable(scale).animateTo(s, tween(450)).endState.value }
            launch { offset = offset.copy(x = Animatable(offset.x).animateTo(o.x, tween(450)).endState.value) }
            launch { offset = offset.copy(y = Animatable(offset.y).animateTo(o.y, tween(450)).endState.value) }
        }
    }

    suspend fun resetView() = animateTo(1f, Offset.Zero)

    suspend fun flyToWineRegions() {
        if (boxW <= 0f) return
        val min = toPx(WINE_BBOX_MIN)
        val max = toPx(WINE_BBOX_MAX)
        val s = minOf(boxW / (max.x - min.x), boxH / (max.y - min.y)).coerceIn(MAP_MIN_SCALE, MAP_MAX_SCALE)
        val c = Offset((min.x + max.x) / 2f, (min.y + max.y) / 2f)
        animateTo(s, Offset(boxW / 2f - s * c.x, boxH / 2f - s * c.y))
    }

    private fun clamp(o: Offset, s: Float = scale): Offset {
        if (boxW <= 0f || boxH <= 0f) return o
        val sw = drawW * s
        val sh = drawH * s
        val m = 64f
        // Карта больше экрана — не даём увести её за край; меньше — центрируем.
        // Без ветвления диапазон coerceIn инвертируется и падает с IllegalArgumentException.
        val x = if (sw + 2 * m >= boxW) o.x.coerceIn(boxW - sw - m, m) else (boxW - sw) / 2f
        val y = if (sh + 2 * m >= boxH) o.y.coerceIn(boxH - sh - m, m) else (boxH - sh) / 2f
        return Offset(x, y)
    }
}

@Composable
private fun rememberWinePathMapState(): WinePathMapState = remember { WinePathMapState() }

@Composable
private fun MapOverlayTopBar(
    points: Int?,
    onBack: () -> Unit,
    onZoomToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.winepath_back),
                    tint = Color.White
                )
            }
            Text(
                stringResource(R.string.winepath_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            if (points != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Text(
                        stringResource(R.string.winepath_points, points),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFFC107)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onZoomToggle) {
                androidx.compose.material3.Icon(
                    Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.winepath_zoom),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun WinePathSheetContent(
    summary: WinePathSummary?,
    territories: List<TerritoryProgress>,
    badges: List<BadgeUi>,
    onSelectTerritory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        if (summary != null) {
            WinePathHeader(summary = summary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.winepath_sheet_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.winepath_territories),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        territories.forEach { territory ->
            TerritoryRow(territory = territory, onClick = { onSelectTerritory(territory.territoryId) })
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            stringResource(R.string.winepath_badges),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        BadgesGrid(badges = badges)
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun WinePathMap(
    mapUi: WinePathMapState,
    territories: List<TerritoryProgress>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    // Контуры (~100 КБ JSON) грузятся один раз с IO-потока, отрисовка — по готовности.
    val context = LocalContext.current
    var shapesReady by remember { mutableStateOf(TerritoryShapes.isLoaded) }
    LaunchedEffect(Unit) {
        if (!shapesReady) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                TerritoryShapes.ensureLoaded(context)
            }
            shapesReady = true
        }
    }
    if (!shapesReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141C26)),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f)) }
        return
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 11.sp,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    val labelDimStyle = labelStyle.copy(color = Color.White.copy(alpha = 0.45f))
    // Подписи Ставрополья и Осетии — над точкой, иначе сливаются с соседями в плотном кластере.
    val labelsAbove = setOf("stavropol", "ossetia")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141C26))
    ) {
        val density = LocalDensity.current
        val bw = with(density) { maxWidth.toPx() }
        val bh = with(density) { maxHeight.toPx() }
        mapUi.onSize(bw, bh)

        // Пути кэшируются по размеру — жесты зума/пана идут через graphicsLayer без перестроения.
        val sizeKey = "${mapUi.drawW.toInt()}x${mapUi.drawH.toInt()}"
        val bgPaths = remember(sizeKey) {
            TerritoryShapes.backgroundRings.map { ring -> ringPath(ring, mapUi) }
        }
        val winePaths = remember(sizeKey) {
            territories.associate { territory ->
                territory.territoryId to (
                    TerritoryShapes.outlines[territory.territoryId]?.map { ring ->
                        ringPath(ring, mapUi)
                    } ?: emptyList()
                    )
            }
        }

        // Подписи видны только с приближением — на обзоре чистая карта со светящимися регионами.
        val labelAlpha = ((mapUi.scale - 1.2f) / 1.5f).coerceIn(0f, 1f)

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            mapUi.onTransform(zoomChange, panChange)
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformState)
                .graphicsLayer(
                    scaleX = mapUi.scale,
                    scaleY = mapUi.scale,
                    translationX = mapUi.offset.x,
                    translationY = mapUi.offset.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
                .pointerInput(mapUi) {
                    detectTapGestures { tap ->
                        val point = mapUi.toFraction(tap)
                        if (point.x in 0f..1f && point.y in 0f..1f) {
                            onSelect(TerritoryShapes.territoryAt(point))
                        } else {
                            onSelect(null)
                        }
                    }
                }
        ) {
            // Фон: все субъекты России.
            bgPaths.forEach { path ->
                drawPath(path, Color.White.copy(alpha = 0.05f))
                drawPath(path, Color.White.copy(alpha = 0.22f), style = Stroke(width = 1f))
            }
            territories.forEach { territory ->
                val paths = winePaths[territory.territoryId] ?: return@forEach
                val center = mapUi.toPx(Offset(territory.anchorX, territory.anchorY))
                val selected = territory.territoryId == selectedId
                val strokeWidth = if (selected) 3.5f else 2f
                val above = labelsAbove.contains(territory.territoryId)
                when {
                    territory.locked -> {
                        paths.forEach { path ->
                            drawPath(
                                path,
                                Color.White.copy(alpha = 0.30f),
                                style = Stroke(
                                    width = strokeWidth,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                )
                            )
                        }
                        if (labelAlpha > 0.02f) {
                            drawLabel(textMeasurer, territory.name, center, labelDimStyle.copy(color = labelDimStyle.color.copy(alpha = labelAlpha)), above)
                        }
                    }
                    territory.unlocked -> {
                        paths.forEach { path ->
                            drawPath(path, Color(0xFFFFC107).copy(alpha = if (selected) 0.40f else 0.25f))
                            drawPath(path, Color(0xFFFFC107), style = Stroke(width = strokeWidth))
                        }
                        drawCircle(Color(0xFFFFC107).copy(alpha = 0.25f), radius = 18f, center = center)
                        drawCircle(Color(0xFFFFC107), radius = 8f, center = center)
                        if (labelAlpha > 0.02f) {
                            drawLabel(textMeasurer, territory.name, center, labelStyle.copy(color = labelStyle.color.copy(alpha = labelAlpha)), above)
                        }
                    }
                    else -> {
                        // Винный регион из каталога, но ещё не открыт — особая подсветка.
                        val inCatalog = territory.totalWines > 0
                        paths.forEach { path ->
                            if (inCatalog) {
                                drawPath(path, Color(0xFFFFC107).copy(alpha = if (selected) 0.22f else 0.12f))
                            }
                            drawPath(
                                path,
                                if (inCatalog) Color(0xFFFFC107).copy(alpha = 0.70f)
                                else Color.White.copy(alpha = 0.55f),
                                style = Stroke(width = if (inCatalog) strokeWidth else 1.5f)
                            )
                        }
                        if (inCatalog) {
                            drawCircle(Color(0xFFFFC107).copy(alpha = 0.5f), radius = 6f, center = center, style = Stroke(width = 2f))
                        }
                        if (labelAlpha > 0.02f) {
                            drawLabel(textMeasurer, territory.name, center, labelDimStyle.copy(color = labelDimStyle.color.copy(alpha = labelAlpha)), above)
                        }
                    }
                }
            }
        }
    }
}

/** Кольцо полигона в пикселях канвы через метрики карты. */
private fun ringPath(ring: List<Offset>, mapUi: WinePathMapState): Path {
    return Path().apply {
        ring.forEachIndexed { i, p ->
            val pt = mapUi.toPx(p)
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
        close()
    }
}

@Composable
private fun TerritoryDetailCard(
    territory: TerritoryProgress,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    territory.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                IconButton(onClick = onClose) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            when {
                territory.locked -> {
                    Text(
                        stringResource(R.string.winepath_locked_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                territory.unlocked -> {
                    Text(
                        stringResource(R.string.winepath_progress, territory.triedWines, territory.totalWines),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    if (territory.sampleNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.winepath_your_wines),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        territory.sampleNames.forEach { name ->
                            Text(
                                "· $name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.winepath_progress, territory.triedWines, territory.totalWines),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    above: Boolean = false
) {
    val layout = textMeasurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(
            (center.x - layout.size.width / 2f).coerceAtLeast(0f),
            if (above) center.y - layout.size.height - 12f else center.y + 14f
        )
    )
}

@Composable
private fun TerritoryRow(territory: TerritoryProgress, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        when {
                            territory.locked -> MaterialTheme.colorScheme.outlineVariant
                            territory.unlocked -> Color(0xFFFFC107)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(territory.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (territory.locked) {
                    Text(
                        stringResource(R.string.winepath_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.winepath_progress, territory.triedWines, territory.totalWines),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!territory.locked && territory.totalWines > 0) {
                Text(
                    "${(territory.triedWines * 100 / territory.totalWines).coerceAtMost(100)}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesGrid(badges: List<BadgeUi>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        badges.forEach { badge ->
            BadgeChip(badge = badge)
        }
    }
}

@Composable
private fun BadgeChip(badge: BadgeUi) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (badge.earned) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (badge.earned) Icons.Default.EmojiEvents else Icons.Default.Lock,
                contentDescription = null,
                tint = if (badge.earned) {
                    Color(0xFFB8860B)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    badge.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (badge.earned) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
                Text(
                    "+${badge.points}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badge.earned) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WinePathScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        WinePathScreenContent(
            state = WinePathState.Success(
                summary = WinePathSummary(
                    totalPoints = 130,
                    level = 2,
                    levelProgress = 0.3f,
                    territoriesOpened = 2,
                    territoriesTotal = 8,
                    scansCount = 5
                ),
                territories = listOf(
                    TerritoryProgress("kuban", "Кубань", 0.297f, 0.695f, 3, 1067, unlocked = true, locked = false),
                    TerritoryProgress("crimea", "Крым", 0.134f, 0.706f, 2, 769, unlocked = true, locked = false),
                    TerritoryProgress("dagestan", "Дагестан", 0.519f, 0.819f, 0, 97, unlocked = false, locked = false),
                    TerritoryProgress("moscow", "Подмосковье", 0.244f, 0.132f, 0, 0, unlocked = false, locked = true)
                ),
                badges = listOf(
                    BadgeUi("first_scan", "Первый глоток", "", 10, earned = true),
                    BadgeUi("pioneer_kuban", "Первопроходец Кубани", "", 50, earned = true),
                    BadgeUi("pioneer_dagestan", "Первопроходец Дагестана", "", 50, earned = false)
                )
            )
        )
    }
}
