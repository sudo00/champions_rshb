package com.wineapp.presentation.winepath

import com.wineapp.presentation.common.ui.AppIcons
import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.game.TerritoryTier
import com.wineapp.data.local.TerritoryShapes
import com.wineapp.domain.model.BadgeUi
import com.wineapp.domain.model.TerritoryProgress
import com.wineapp.domain.model.WinePathSummary
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBurgundy300
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream300
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandDivider
import com.wineapp.ui.theme.BrandOutline
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.BrandTextTertiary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.MapBackground
import com.wineapp.ui.theme.MapRegionBorder
import com.wineapp.ui.theme.MapRegionFill
import com.wineapp.ui.theme.MapSelectedBorder
import com.wineapp.ui.theme.MapSelectedFill
import com.wineapp.ui.theme.MapWineFill
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.RewardBronze
import com.wineapp.ui.theme.RewardBronzeSoft
import com.wineapp.ui.theme.RewardGold
import com.wineapp.ui.theme.RewardGoldSoft
import com.wineapp.ui.theme.RewardLockedText
import com.wineapp.ui.theme.RewardLockedTile
import com.wineapp.ui.theme.RewardSilver
import com.wineapp.ui.theme.RewardSilverSoft
import com.wineapp.ui.theme.SheetCardSurface
import com.wineapp.ui.theme.SheetSurfaceBottom
import com.wineapp.ui.theme.SheetSurfaceTop
import com.wineapp.ui.theme.TileBorder
import com.wineapp.ui.theme.WarningBackground
import com.wineapp.ui.theme.WarningMain
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
        onNavigateBack = onNavigateBack,
        onNavigateToScanner = onNavigateToScanner
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinePathScreenContent(
    state: WinePathState,
    onRetry: () -> Unit = {},
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
        sheetPeekHeight = SheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        sheetDragHandle = null,
        sheetContainerColor = Color.Transparent,
        sheetTonalElevation = 0.dp,
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
        containerColor = MapBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapBackground)
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
                        // Одноразовый закрываемый диалог: после закрытия карта свободна для изучения.
                        var emptyDismissed by rememberSaveable { mutableStateOf(false) }
                        if (!emptyDismissed) {
                            AlertDialog(
                                onDismissRequest = { emptyDismissed = true },
                                title = {
                                    Text(
                                        stringResource(R.string.winepath_title),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                text = {
                                    Text(
                                        stringResource(R.string.winepath_empty),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        emptyDismissed = true
                                        onNavigateToScanner()
                                    }) { Text(stringResource(R.string.winepath_scan)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { emptyDismissed = true }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }
                    } else {
                        state.territories.find { it.territoryId == selectedId }?.let { selected ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    // Шторка перекрывает контент, поэтому карточка региона
                                    // отсчитывается от её свёрнутой высоты, а не от низа экрана.
                                    .padding(bottom = SheetPeekHeight + 12.dp)
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
}

@Composable
private fun WinePathSheetContent(
    summary: WinePathSummary?,
    territories: List<TerritoryProgress>,
    badges: List<BadgeUi>,
    onSelectTerritory: (String) -> Unit
) {
    // Фон шторки — градиент по контейнеру, а не по колонке: он не скроллится вместе с ней.
    // Низ непрозрачный и слегка темнее верха, а не прозрачный: карта сквозь него не просвечивает.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to SheetSurfaceTop,
                    0.5f to SheetSurfaceTop,
                    1f to SheetSurfaceBottom
                )
            )
    ) {
        // Отступы как в макете: 32 между блоками, 24 от заголовка до списка, 4 внутри списка.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 56.dp)
        ) {
            WinePathDragHandle()

            if (summary != null) {
                LevelProgressCard(summary = summary)
                Spacer(modifier = Modifier.height(32.dp))
            }

            WinePathSection(
                title = stringResource(R.string.winepath_territories),
                description = stringResource(R.string.winepath_territories_desc)
            )
            Spacer(modifier = Modifier.height(24.dp))
            // Территории из каталога: у locked нет вин, процент по ним не считается.
            territories.filterNot { it.locked }.forEachIndexed { index, territory ->
                if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                TerritoryProgressCard(
                    territory = territory,
                    onClick = { onSelectTerritory(territory.territoryId) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            WinePathSection(
                title = stringResource(R.string.winepath_rewards),
                description = stringResource(R.string.winepath_rewards_desc)
            )
            Spacer(modifier = Modifier.height(24.dp))
            MedallionGrid(badges = badges)
        }
    }
}

@Composable
private fun WinePathDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(BrandOutline)
        )
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

    fun onTransformAround(centroid: Offset, pan: Offset, zoom: Float) {
        // Демпфируем щипок: сырой zoom слишком резкий, берём 60% отклонения от 1.
        val dampedZoom = 1f + (zoom - 1f) * 0.6f
        val newScale = (scale * dampedZoom).coerceIn(MAP_MIN_SCALE, MAP_MAX_SCALE)
        // Точка под пальцами стоит на месте: сначала пан, затем зум вокруг центроида.
        // Формула согласована с graphicsLayer(transformOrigin = 0): screen = scale*base + offset.
        val moved = offset + pan
        val base = Offset((centroid.x - moved.x) / scale, (centroid.y - moved.y) / scale)
        scale = newScale
        offset = clamp(centroid - Offset(base.x * newScale, base.y * newScale))
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
        // Прямоугольник карты с учётом letterbox-базы (offX/offY!) и зума.
        // Правило: центр экрана не покидает прямоугольник ± margin.
        // Диапазон всегда непуст (ширина = scaled + 2m), рецентринга нет,
        // поэтому фокус стартового вида и flyTo проходят без искажений.
        val m = 64f
        val xMin = boxW / 2f - m - s * (offX + drawW)
        val xMax = boxW / 2f + m - s * offX
        val yMin = boxH / 2f - m - s * (offY + drawH)
        val yMax = boxH / 2f + m - s * offY
        return Offset(o.x.coerceIn(xMin, xMax), o.y.coerceIn(yMin, yMax))
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
    // Макет: три колонки в ряд, space-between, выравнивание по верху.
    Box(modifier = modifier.fillMaxWidth()) {
        // Тёмный градиент идёт от самого верха (из-под системного бара) и гаснет
        // ниже кнопок: заголовок и иконки светлые, на светлой карте без подложки
        // они сливаются. Тот же приём с плавным затуханием даёт drop-shadow.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(bottom = MapScrimFadeOut)
                .background(
                    Brush.verticalGradient(
                        0f to MapScrimTop,
                        0.6f to MapScrimMid,
                        1f to Color.Transparent
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            MapCircleButton(
                onClick = onBack,
                background = Color(0x4D000000),
                contentDescription = stringResource(R.string.winepath_back)
            ) {
                Icon(
                    imageVector = AppIcons.ChevronLeft,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.width(154.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.winepath_title),
                    style = MapTitleStyle,
                    color = BrandCream50
                )
                if (points != null) {
                    Surface(shape = RoundedCornerShape(50), color = WarningBackground) {
                        Text(
                            text = pointsText(points),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = SheetCaptionMediumStyle,
                            color = WarningMain
                        )
                    }
                }
            }

            MapCircleButton(
                onClick = onZoomToggle,
                background = BrandBurgundy600,
                contentDescription = stringResource(R.string.winepath_zoom)
            ) {
                Icon(
                    imageVector = AppIcons.MyLocation,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Круглая кнопка 44dp из макета: подложка + иконка 24dp по центру. */
@Composable
private fun MapCircleButton(
    onClick: () -> Unit,
    background: Color,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

@Preview
@Composable
private fun MapOverlayTopBarPreview() {
    // Фон — светлая заливка региона: именно на ней заголовок сливался без скрима.
    Surface(color = MapRegionFill) {
        MapOverlayTopBar(
            points = 130,
            onBack = {},
            onZoomToggle = {}
        )
    }
}

@Preview
@Composable
private fun MapOverlayTopBarNoPointsPreview() {
    Surface(color = MapRegionFill) {
        MapOverlayTopBar(
            points = null,
            onBack = {},
            onZoomToggle = {}
        )
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
                .background(MapBackground),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f)) }
        return
    }
    val textMeasurer = rememberTextMeasurer()
    // Кегль и отступ делим на зум: graphicsLayer масштабирует весь Canvas,
    // поэтому на экране подпись всегда одного размера, а не растёт с картой.
    // Подпись рисуется только у выбранного региона — ярко-жёлтая.
    val zoomSafe = mapUi.scale.coerceAtLeast(1f)
    val selectedLabelStyle = TextStyle(
        fontSize = (15f / zoomSafe).sp,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    // Подписи Ставрополья и Осетии — над точкой, иначе сливаются с соседями в плотном кластере.
    val labelsAbove = setOf("stavropol", "ossetia")
    // Отступ подписи тоже делим на зум, иначе он визуально растёт вместе с картой.
    val labelGap = 14f / zoomSafe

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MapBackground)
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

        // Подписи рисуем только для выбранного региона (см. ниже) — карта остаётся чистой.

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mapUi) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        mapUi.onTransformAround(centroid, pan, zoom)
                    }
                }
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
            // Рисуем в два прохода: сначала ВСЕ заливки, потом ВСЕ обводки поверх них.
            // Обводка отдельным проходом обязательна: контуры соседей совпадают,
            // и если рисовать границу до заливки соседа, она съедает её половину —
            // на стыках регионов появляются дырки, а винные регионы теряют границу целиком.
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

            // Проход 1: заливки.
            bgPaths.forEach { path -> drawPath(path, MapRegionFill) }
            territories.forEach { territory ->
                val paths = winePaths[territory.territoryId] ?: return@forEach
                val isWine = !territory.locked && territory.totalWines > 0
                val selected = territory.territoryId == selectedId
                val fill = when {
                    selected -> MapSelectedFill
                    isWine -> MapWineFill
                    else -> null
                }
                if (fill != null) paths.forEach { path -> drawPath(path, fill) }
            }

            // Проход 2: обводки.
            bgPaths.forEach { path ->
                drawPath(path, MapRegionBorder, style = Stroke(width = MapStrokeBase))
            }
            territories.forEach { territory ->
                val paths = winePaths[territory.territoryId] ?: return@forEach
                val isWine = !territory.locked && territory.totalWines > 0
                val selected = territory.territoryId == selectedId
                paths.forEach { path ->
                    when {
                        // Выбранный — Brand/Cream 300, толще остальных.
                        selected -> drawPath(
                            path = path,
                            color = MapSelectedBorder,
                            style = Stroke(width = MapStrokeSelected)
                        )
                        // Locked: сплошная линия базового слоя лежит ровно под пунктиром,
                        // поэтому сначала закрашиваем её цветом региона.
                        territory.locked -> {
                            drawPath(
                                path = path,
                                color = MapRegionFill,
                                style = Stroke(width = MapStrokeLocked)
                            )
                            drawPath(
                                path = path,
                                color = MapRegionBorder,
                                style = Stroke(width = MapStrokeLocked, pathEffect = dash)
                            )
                        }
                        // Винный невыбранный: границы базового слоя недостаточно —
                        // заливка проходит по той же геометрии, поэтому рисуем свою.
                        isWine -> drawPath(
                            path = path,
                            color = MapRegionBorder,
                            style = Stroke(width = MapStrokeBase)
                        )
                    }
                }
            }

            territories.forEach { territory ->
                if (territory.territoryId != selectedId) return@forEach
                val center = mapUi.toPx(Offset(territory.anchorX, territory.anchorY))
                val above = labelsAbove.contains(territory.territoryId)
                drawLabel(textMeasurer, territory.name, center, selectedLabelStyle, above, labelGap)
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

/**
 * Progress Bar — Type 5 (Progressing), карточка выбранного региона:
 * круг прогресса 76dp с процентом, название региона, разделитель и теги снятых вин.
 * Кремовый фон карточки — Brand/Cream 50, обводки тегов — Border/Default.
 */
@Composable
private fun TerritoryDetailCard(
    territory: TerritoryProgress,
    onClose: () -> Unit
) {
    val percent = territoryPercent(territory)
    val description = if (territory.locked) {
        stringResource(R.string.winepath_locked_hint)
    } else {
        stringResource(R.string.winepath_progress, territory.triedWines, territory.totalWines)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        // Карточка висит поверх тёмной карты, поэтому фон плотный: сквозь полупрозрачный
        // слой просвечивали бы контуры регионов.
        color = Color.White,
        shadowElevation = 10.dp
    ) {
        Box {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularTerritoryProgress(
                        progress = percent / 100f,
                        percentText = formatPercent(percent)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = territory.name, style = SheetHeadingStyle, color = BrandBurgundy600)
                        Text(text = description, style = DetailBodyStyle, color = BrandTextSecondary)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = BrandDivider)

                if (territory.sampleNames.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.winepath_your_wines),
                            style = SheetCaptionMediumStyle,
                            color = BrandTextSecondary
                        )
                        WineTagRow(names = territory.sampleNames)
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.winepath_close),
                    tint = BrandTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Доля попробованных вин территории в процентах (0–100). */
private fun territoryPercent(territory: TerritoryProgress): Float =
    if (territory.totalWines > 0) {
        (territory.triedWines * 100f / territory.totalWines).coerceIn(0f, 100f)
    } else {
        0f
    }

/**
 * Процент с одной десятой: каталог регионов большой, и целые проценты
 * показывали «0%» даже при нескольких винах. Ненулевой прогресс не округляем
 * до «0», неполный — до «100».
 */
private fun formatPercent(percent: Float): String = when {
    percent <= 0f -> "0%"
    percent >= 100f -> "100%"
    else -> String.format(java.util.Locale("ru"), "%.1f%%", percent.coerceIn(0.1f, 99.9f))
}

/** Circular Progress indicator из макета: трек Burgundy 300, заливка Burgundy 600, процент по центру. */
@Composable
private fun CircularTerritoryProgress(progress: Float, percentText: String) {
    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = BrandBurgundy300,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = BrandBurgundy600,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = percentText,
            style = SheetTitleStyle,
            color = BrandTextPrimary
        )
    }
}

/** Ряд тегов с переносом: Badge/Tag из макета — капсула Cream 50 с обводкой Border/Default. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WineTagRow(names: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        names.forEach { name ->
            Surface(
                shape = RoundedCornerShape(50),
                color = BrandCream50,
                border = BorderStroke(1.dp, BrandBorderDefault)
            ) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = SheetCaptionMediumStyle,
                    color = BrandTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    above: Boolean = false,
    gapPx: Float = 14f
) {
    val layout = textMeasurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(
            (center.x - layout.size.width / 2f).coerceAtLeast(0f),
            if (above) center.y - layout.size.height - gapPx else center.y + gapPx
        )
    )
}

/* ------------------------------- Шторка «прогресс» ------------------------------- */

/**
 * Метки-вехи на шкале уровня: равные шаги по 20% очков уровня. Раньше были
 * 12,5/25/50/75% — неравномерные засечки ни к чему не привязаны и путали.
 */
private val LevelMilestones = listOf(0.2f, 0.4f, 0.6f, 0.8f)

/** «1 очко», «2 очка», «10 очков» — сумма очков бывает любой. */
@Composable
private fun pointsText(points: Int): String =
    LocalContext.current.resources.getQuantityString(R.plurals.winepath_points, points, points)

/**
 * Свёрнутая высота шторки. От неё зависит и сам BottomSheetScaffold, и отступ
 * карточки выбранного региона — шторка рисуется поверх контента и без этого
 * отступа перекрыла бы карточку снизу.
 */
private val SheetPeekHeight = 180.dp

// Толщины обводок карты, px в системе координат канвы (до graphicsLayer-масштаба).
// Базовый слой рисует обводку один раз на весь регион, остальные состояния
// подчёркивают её поверх, поэтому базовая заметно тоньше выбранной.
private const val MapStrokeBase = 1f
private const val MapStrokeSelected = 2.5f
private const val MapStrokeLocked = 1.5f

/** Размер медали совпадает с intrinsic-размером импортированных векторов (50×50). */
private val MedalIconSize = 50.dp

/** Тень незаработанной медали — приглушённый серый, чтобы не спорить с earned-цветом. */
private val MedalLockedShadow = Color(0x66BFBEBA)

// Скрим под верхним баром карты. Идёт от системного бара вниз и полностью гаснет
// ниже кнопок, чтобы не резать карту горизонтальной границей.
private val MapScrimTop = Color(0x8C000000)
private val MapScrimMid = Color(0x4D000000)
private val MapScrimFadeOut = 28.dp

/** H3 в макете: Playfair 500, 24/30. */
private val SheetHeadingStyle = TextStyle(
    fontFamily = Playfair,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 30.sp
)

/** H1 в макете: цифра уровня, Playfair 600, 36/42. */
private val SheetLevelNumberStyle = TextStyle(
    fontFamily = Playfair,
    fontWeight = FontWeight.SemiBold,
    fontSize = 36.sp,
    lineHeight = 42.sp
)

/** H2 в макете: подпись «Уровень», Playfair 600, 28/34. */
private val SheetLevelWordStyle = TextStyle(
    fontFamily = Playfair,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp
)

/** Label L в макете: Inter 600, 16/20. */
private val SheetTitleStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 20.sp
)

/** Label M в макете: Inter 500, 14/18. */
private val SheetSubtitleStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp
)

/** Caption в макете: Inter 400, 12/16. */
private val SheetCaptionStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

/** Caption Medium в макете: Inter 600, 12/16. */
private val SheetCaptionMediumStyle = SheetCaptionStyle.copy(fontWeight = FontWeight.SemiBold)

/** Body S в макете карточки региона: Inter 400, 14/20. */
private val DetailBodyStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

/**
 * Заголовок карты: тот же H3, но с drop-shadow из макета.
 * Пять наложенных drop-shadow сводятся к одной тени — по слоям они не воспроизводятся.
 * Объявлен после SheetHeadingStyle: top-level val инициализируются по порядку.
 */
private val MapTitleStyle = SheetHeadingStyle.copy(
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), blurRadius = 10f)
)

/** Progress Bar — Type 4 (Show Step): номер уровня, шкала с вехами и сводка. */
@Composable
private fun LevelProgressCard(summary: WinePathSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SheetCardSurface
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = summary.level.toString(),
                        style = SheetLevelNumberStyle,
                        color = BrandBurgundy600
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.winepath_level_word),
                        style = SheetLevelWordStyle,
                        color = BrandBurgundy600
                    )
                }
                Text(
                    text = if (summary.isMaxLevel) {
                        stringResource(R.string.winepath_level_max)
                    } else {
                        // Раньше тут был «N% до уровня X», где N — уже пройденная доля:
                        // «90% до уровня 2» читалось как «осталось 90%». Показываем остаток в очках.
                        val left = (summary.levelPointsTo - summary.totalPoints).coerceAtLeast(0)
                        LocalContext.current.resources.getQuantityString(
                            R.plurals.winepath_points_to_level, left, left, summary.level + 1
                        )
                    },
                    style = SheetCaptionMediumStyle,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            LevelIndicator(progress = summary.levelProgress)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pointsText(summary.levelPointsFrom),
                    style = SheetCaptionStyle,
                    color = BrandTextTertiary
                )
                // На максимуме обе границы совпадают — вторую не дублируем.
                if (!summary.isMaxLevel) {
                    Text(
                        text = pointsText(summary.levelPointsTo),
                        style = SheetCaptionStyle,
                        color = BrandTextTertiary
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                color = BrandCream50,
                border = BorderStroke(1.dp, BrandBorderDefault)
            ) {
                Text(
                    text = stringResource(
                        R.string.winepath_stats,
                        summary.territoriesOpened,
                        summary.territoriesTotal,
                        summary.scansCount
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    style = SheetCaptionStyle,
                    color = BrandTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Шкала уровня: кремовый трек, заливка burgundy и четыре маркера-вехи.
 * Достигнутая веха (левее конца заливки) рисуется кремовой, недостигнутая — burgundy.
 */
@Composable
private fun LevelIndicator(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(BrandCream300)
    ) {
        val trackWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(safeProgress)
                .background(BrandBurgundy600)
        )
        LevelMilestones.forEach { fraction ->
            val reached = safeProgress >= fraction
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = trackWidth * fraction - 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (reached) BrandCream50 else BrandBurgundy600)
            )
        }
    }
}

/** Заголовок раздела шторки: H3 + пояснение Label M. */
@Composable
private fun WinePathSection(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = SheetHeadingStyle, color = BrandTextPrimary)
        Text(text = description, style = SheetSubtitleStyle, color = BrandTextSecondary)
    }
}

/** Progress Bar — Type 5: кольцо-чек, название территории, счётчик и процент. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerritoryProgressCard(territory: TerritoryProgress, onClick: () -> Unit) {
    val percent = territoryPercent(territory)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SheetCardSurface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerritoryRing(unlocked = territory.unlocked)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = territory.name, style = SheetTitleStyle, color = BrandBurgundy600)
                Text(
                    text = stringResource(R.string.winepath_progress, territory.triedWines, territory.totalWines),
                    style = SheetCaptionStyle,
                    color = BrandTextSecondary
                )
            }
            Text(
                text = formatPercent(percent),
                style = SheetTitleStyle,
                color = BrandTextPrimary
            )
        }
    }
}

/** Кольцо 20dp с обводкой 2dp; для открытой территории внутри — галочка. */
@Composable
private fun TerritoryRing(unlocked: Boolean) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        drawCircle(
            color = BrandBurgundy600.copy(alpha = if (unlocked) 1f else 0.35f),
            radius = radius,
            style = Stroke(width = stroke)
        )
        if (unlocked) {
            val start = Offset(center.x - radius * 0.36f, center.y + radius * 0.02f)
            val mid = Offset(center.x - radius * 0.10f, center.y + radius * 0.28f)
            val end = Offset(center.x + radius * 0.38f, center.y - radius * 0.26f)
            val check = Path().apply {
                moveTo(start.x, start.y)
                lineTo(mid.x, mid.y)
                lineTo(end.x, end.y)
            }
            drawPath(
                path = check,
                color = BrandBurgundy600,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/** Сетка плиток наград: ровно две колонки с зазором 4dp. */
@Composable
private fun MedallionGrid(badges: List<BadgeUi>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - 4.dp) / 2
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Ровно две колонки: список режется на пары, поэтому вёрстка не зависит
            // от того, влезет ли вторая плитка в строку FlowRow.
            badges.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { badge ->
                        // В неполной паре weight(1f) растянул бы плитку на весь ряд —
                        // поэтому последняя задаётся фиксированной шириной.
                        // Обёртка обязательна: TooltipBox прячет свой modifier на
                        // внутреннем Box, а Row читает parent data только с прямых
                        // потомков — иначе обе плитки получают полную ширину по очереди.
                        Box(
                            modifier = if (row.size == 2) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.width(tileWidth)
                            }
                        ) {
                            MedallionTile(badge = badge)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Плитка медали: сама медаль, название награды и чип с очками.
 *
 * По тапу показывается тултип с условием получения. Material3 [TooltipBox] на Android
 * ловит только долгий тап и наведение мыши, поэтому встроенные жесты выключены
 * (enableUserInput = false), а показом управляет onClick самой плитки.
 *
 * Ширину задаёт вызывающий код — см. обёртку в [MedallionGrid].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedallionTile(badge: BadgeUi) {
    val tier = if (badge.earned) MedalTier.forPoints(badge.points) else null
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        modifier = Modifier.fillMaxWidth(),
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        state = tooltipState,
        enableUserInput = false,
        tooltip = {
            RichTooltip(
                shape = RoundedCornerShape(16.dp),
                colors = TooltipDefaults.richTooltipColors(
                    containerColor = SheetCardSurface,
                    contentColor = BrandTextSecondary,
                    titleContentColor = BrandTextPrimary
                ),
                title = {
                    Text(
                        text = badge.title,
                        style = SheetTitleStyle,
                        color = BrandTextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.winepath_celebration_points, badge.points),
                            style = SheetCaptionMediumStyle,
                            color = tier?.accent ?: RewardLockedText
                        )
                        Text(
                            text = badge.description,
                            style = DetailBodyStyle,
                            color = BrandTextSecondary
                        )
                    }
                }
            )
        }
    ) {
        Surface(
            onClick = { scope.launch { tooltipState.show() } },
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (tier != null) Color.White else RewardLockedTile,
            border = BorderStroke(1.dp, TileBorder),
            shadowElevation = if (tier != null) 2.dp else 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MedalIcon(tier = tier)
                    Text(
                        text = badge.title,
                        style = SheetTitleStyle,
                        color = if (tier != null) BrandTextPrimary else RewardLockedText,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MedalPointsChip(points = badge.points, tier = tier)
            }
        }
    }
}

@Composable
private fun MedalPointsChip(points: Int, tier: MedalTier?) {
    val color = tier?.accent ?: RewardLockedText
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tier?.accentSoft ?: Color.Transparent)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$points",
            style = SheetCaptionStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = color
        )
    }
}

/** Уровень награды определяет иконку медали, цвет тени, чип очков и тост. */
internal enum class MedalTier(
    val accent: Color,
    val accentSoft: Color,
    @DrawableRes val iconRes: Int,
    val shadowColor: Color
) {
    BRONZE(
        accent = RewardBronze,
        accentSoft = RewardBronzeSoft,
        iconRes = R.drawable.medallions_bronze,
        shadowColor = Color(0x66EA6320)
    ),
    SILVER(
        accent = RewardSilver,
        accentSoft = RewardSilverSoft,
        iconRes = R.drawable.medallions_silver,
        shadowColor = Color(0x66AFAFAF)
    ),
    GOLD(
        accent = RewardGold,
        accentSoft = RewardGoldSoft,
        iconRes = R.drawable.medallions_gold,
        shadowColor = Color(0x66EAB020)
    );

    companion object {
        /**
         * Пороги медали совпадают со ступенями освоения территории: Первопроходец —
         * бронза, Знаток — серебро, Легенда — золото. Берём очки прямо из [TerritoryTier],
         * чтобы при правке контент-пака медаль не отстала от ступени. Общие бейджи
         * (10–30 очков) попадают в бронзу, 100 — в серебро, 200 — в золото.
         */
        fun forPoints(points: Int): MedalTier = when {
            points < TerritoryTier.EXPERT.points -> BRONZE
            points < TerritoryTier.LEGEND.points -> SILVER
            else -> GOLD
        }
    }
}

/**
 * Медаль из импортированных векторов: [R.drawable.medallions_bronze],
 * `medallions_silver`, `medallions_gold` и `medallions_in_progress` для незаработанных.
 *
 * Тень задана через [Modifier.shadow] с ambient/spot-цветом медали: на тёмной шторке
 * чёрная тень из Figma не читается, а цветная даёт мягкий объём. Внешний контур печати
 * почти круглый, поэтому размытая тень по [CircleShape] повторяет силуэт.
 */
@Composable
private fun MedalIcon(tier: MedalTier?) {
    Image(
        painter = painterResource(tier?.iconRes ?: R.drawable.medallions_in_progress),
        contentDescription = null,
        modifier = Modifier
            .size(MedalIconSize)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = tier?.shadowColor ?: MedalLockedShadow,
                spotColor = tier?.shadowColor ?: MedalLockedShadow
            )
    )
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
                    scansCount = 5,
                    levelPointsFrom = 100,
                    levelPointsTo = 300
                ),
                territories = listOf(
                    TerritoryProgress("kuban", "Кубань", 0.117f, 0.889f, 3, 1067, unlocked = true, locked = false),
                    TerritoryProgress("crimea", "Крым", 0.088f, 0.895f, 2, 769, unlocked = true, locked = false),
                    TerritoryProgress("dagestan", "Дагестан", 0.158f, 0.942f, 0, 97, unlocked = false, locked = false),
                    TerritoryProgress("volga", "Нижняя Волга", 0.154f, 0.825f, 0, 22, unlocked = false, locked = false),
                    TerritoryProgress("moscow", "Подмосковье", 0.107f, 0.643f, 0, 0, unlocked = false, locked = true)
                ),
                badges = listOf(
                    BadgeUi("pioneer_kuban", "Первопроходец Кубани", "", 50, earned = true),
                    BadgeUi("expert_crimea", "Знаток Крыма", "", 100, earned = true),
                    BadgeUi("legend_kuban", "Легенда Кубани", "", 200, earned = true),
                    BadgeUi("pioneer_dagestan", "Первопроходец Дагестана", "", 50, earned = false),
                    BadgeUi("legend_dagestan", "Легенда Дагестана", "", 200, earned = false)
                )
            )
        )
    }
}

/** Карточка выбранного региона отдельно от карты — так её удобнее сверять с макетом. */
@Preview(showBackground = true, backgroundColor = 0xFF292925, widthDp = 427, heightDp = 300)
@Composable
private fun TerritoryDetailCardPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapBackground)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            TerritoryDetailCard(
                territory = TerritoryProgress(
                    territoryId = "kuban",
                    name = "Кубань",
                    anchorX = 0.117f,
                    anchorY = 0.889f,
                    triedWines = 534,
                    totalWines = 1067,
                    unlocked = true,
                    locked = false,
                    sampleNames = listOf("Кубань Премьер", "Винодельня Гай-Кодзор", "Винный долина")
                ),
                onClose = {}
            )
        }
    }
}
