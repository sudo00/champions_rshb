package com.wineapp.presentation.roulette

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.Wine
import com.wineapp.ui.theme.BrandBurgundy300
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.WineAppTheme
import com.wineapp.util.HapticHelper
import com.wineapp.util.apiImageUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

const val ROULETTE_SECTOR_ANGLE = 360f / ROULETTE_SECTOR_COUNT

// ── Геометрия ────────────────────────────────────────────────────────────────
// Всё считается от ширины экрана W. Колесо огромное, его центр ниже экрана:
// в кадр попадает верхушка дуги — три сектора заполняют ширину экрана,
// остальные уходят за края. Этому соответствует радиус кольца ≈ 1.1·W.

/** Внешний радиус кольца секторов относительно ширины экрана. */
private const val SECTOR_OUTER_RATIO = 1.1f
/** Фоновая окружность (roulette_bg) чуть больше кольца: сектора лежат внутри её золотого обода. */
private const val BACKGROUND_RATIO = 1.1f
/**
 * Центральная окружность (тот же ассет roulette_bg, уменьшенный) доходит до
 * внутреннего края секторов и чуть заходит под них — доля высоты сектора,
 * на которую её золотой обод перекрывает узкий конец плитки.
 */
private const val HUB_OVERLAP = 0.06f
/** Доля хорды сектора, занятая плиткой: оставляет зазор между соседними секторами. */
private const val SECTOR_FILL = 0.94f
/** Ширина стрелки-указателя относительно ширины экрана. */
private const val POINTER_WIDTH_RATIO = 0.18f
/**
 * Тень центральной окружности на сектора (эффект elevation): ширина ореола
 * в долях высоты сектора и его непрозрачность у края окружности.
 */
private const val HUB_SHADOW_WIDTH = 0.14f
private const val HUB_SHADOW_ALPHA = 0.35f
/** Какая доля высоты стрелки выступает над внешней окружностью. */
private const val POINTER_ABOVE_RIM = 0.2f
/**
 * Место под кнопкой «Крутить» внизу полосы. С запасом: кнопка лежит на
 * центральной окружности и не должна задевать её золотой обод.
 */
private val FooterSpace: Dp = 104.dp

// ── Анимация ─────────────────────────────────────────────────────────────────
private const val SPIN_DURATION_MS = 4800
private const val MIN_TURNS = 4
private const val MAX_TURNS = 6
/**
 * Сектор встаёт под стрелку чуть дальше центра (на 5–35% ширины сектора):
 * горлышко победителя успевает проскочить под острием, стрелка отщёлкивает
 * и докачивается, а не застывает, упёршись в бутылку.
 */
private const val LANDING_MIN = 0.05f
private const val LANDING_SPREAD = 0.3f
private const val SETTLE_DELAY_MS = 350L
private const val TICK_THROTTLE_MS = 70L

private val SpinEasing = CubicBezierEasing(0.1f, 0.75f, 0.15f, 1.0f)

// ── Физика указателя ─────────────────────────────────────────────────────────
/** Ось качания стрелки — у её верхнего (широкого) края. */
private val PointerPivot = TransformOrigin(0.5f, 0.15f)
/** Полуширина зоны контакта горлышка с острием, доля угла сектора. */
private const val CONTACT_WINDOW = 0.3f
/** Максимальный отвод острия горлышком, градусы. */
private const val MAX_DEFLECTION = 24f
/** Жёсткость пружины (ω₀² ≈ 26 рад/с ≈ 4 Гц) и затухание (ζ ≈ 0.2). */
private const val STIFFNESS = 700f
private const val DAMPING = 10f
/** Подшаги интегрирования на кадр: контакт с горлышком короткий. */
private const val SUBSTEPS = 4
private const val MAX_FRAME_DT = 1f / 30f
private const val REST_ANGLE = 0.05f
private const val REST_VELOCITY = 0.5f

/**
 * Упругий указатель. Горлышки бутылок стоят в центрах секторов на внешнем
 * крае; подходя под острие, горлышко отводит его по ходу вращения тем
 * сильнее, чем ближе к центру. Прошло центр — острие срывается и качается
 * назад на пружине с затуханием. Угол в градусах, «+» — по ходу вращения.
 */
private class PointerPhysics {
    var angle by mutableFloatStateOf(0f)
        private set
    private var velocity = 0f
    private var lastPush = 0f

    /** Шаг симуляции; true — стрелка ещё движется или её толкают. */
    fun step(wheelRotation: Float, dt: Float): Boolean {
        if (dt <= 0f) return true
        val push = pushAt(wheelRotation)
        // Скорость отвода горлышком: острие не может отставать от него.
        val pushRate = (push - lastPush) / dt
        lastPush = push
        val h = dt / SUBSTEPS
        var a = angle
        repeat(SUBSTEPS) {
            // Полунеявный Эйлер: сначала скорость, потом угол — устойчив на пружине.
            velocity += (-STIFFNESS * a - DAMPING * velocity) * h
            a += velocity * h
            if (a < push) {
                a = push
                if (velocity < pushRate) velocity = pushRate
            }
        }
        angle = a.coerceIn(-MAX_DEFLECTION * 1.5f, MAX_DEFLECTION * 1.5f)
        val atRest = kotlin.math.abs(velocity) < REST_VELOCITY &&
            (push > 0f || kotlin.math.abs(angle) < REST_ANGLE)
        return !atRest
    }

    /**
     * Минимальный отвод острия при данном повороте колеса. Ближайшее горлышко
     * стоит на относительном угле p ∈ [-θ/2, θ/2); при движении по часовой p
     * растёт. Контакт — пока горлышко подходит: p ∈ [-окно, 0].
     */
    private fun pushAt(wheelRotation: Float): Float {
        val half = ROULETTE_SECTOR_ANGLE / 2f
        val p = (wheelRotation + half).mod(ROULETTE_SECTOR_ANGLE) - half
        val window = CONTACT_WINDOW * ROULETTE_SECTOR_ANGLE
        return if (p >= -window && p < 0f) MAX_DEFLECTION * (1f + p / window) else 0f
    }
}

/**
 * Барабан рулетки из трёх ассетов: фоновая окружность roulette_bg, на ней по
 * окружности 17 секторов section с бутылками, в центре — уменьшенная
 * roulette_bg. Сверху неподвижная стрелка stopper.
 *
 * Содержимое секторов ([sectors]) неизменно во время вращения: победителя
 * выбирает ViewModel среди секторов, а барабан лишь докручивается до него.
 */
@Composable
fun WineRouletteWheel(
    sectors: List<Wine>,
    roll: WineRouletteEffect.Roll?,
    canSpin: Boolean,
    onSpin: () -> Unit,
    onSettled: (Wine) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rotation = remember { Animatable(0f) }
    val pointer = remember { PointerPhysics() }

    // Физика указателя живёт отдельно от эффекта спина: после onSettled экран
    // сбрасывает roll, и эффект спина отменяется, а стрелка ещё докачивается.
    // В покое цикл кадров не крутится — ждёт следующего вращения.
    LaunchedEffect(pointer) {
        while (true) {
            snapshotFlow { rotation.isRunning }.first { it }
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(MAX_FRAME_DT)
                last = now
                val moving = pointer.step(rotation.value, dt)
                if (!moving && !rotation.isRunning) break
            }
        }
    }

    LaunchedEffect(roll) {
        val request = roll ?: return@LaunchedEffect
        if (sectors.isEmpty()) return@LaunchedEffect

        val target = spinTarget(
            current = rotation.value,
            sectorIndex = request.sectorIndex,
            turns = Random.nextInt(MIN_TURNS, MAX_TURNS + 1),
            // Отрицательный сдвиг — сектор проехал центр стрелки по ходу вращения.
            jitter = -(LANDING_MIN + Random.nextFloat() * LANDING_SPREAD)
        )

        // Тик на каждом шаге сектора — как в прежней реализации, она хорошо ощущалась.
        var lastTick = 0L
        val ticker = launch {
            snapshotFlow { rotation.value }
                .map { floor(it / ROULETTE_SECTOR_ANGLE).toInt() }
                .distinctUntilChanged()
                .collect {
                    val now = System.currentTimeMillis()
                    if (now - lastTick > TICK_THROTTLE_MS) {
                        lastTick = now
                        HapticHelper.vibrateTick(context)
                    }
                }
        }
        rotation.animateTo(target, tween(SPIN_DURATION_MS, easing = SpinEasing))
        ticker.cancel()

        delay(SETTLE_DELAY_MS)
        HapticHelper.vibrateSuccess(context)
        onSettled(request.winner)
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val geometry = rememberRouletteGeometry(
            widthPx = widthPx,
            footerPx = with(density) { (FooterSpace + navBottom).toPx() }
        )
        val bandHeight = with(density) {
            val desired = geometry.bandHeight.toDp()
            if (maxHeight.value.isFinite()) minOf(desired, maxHeight) else desired
        }

        // Тап по барабану запускает спин без рипла: подсветка на колесе
        // смотрится чужеродно. Голый clickable в проекте запрещён (см. WineCard),
        // поэтому тап ловим жестом.
        val currentCanSpin by rememberUpdatedState(canSpin)
        val currentOnSpin by rememberUpdatedState(onSpin)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bandHeight)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { if (currentCanSpin) currentOnSpin() })
                }
        ) {
            RouletteDrum(
                geometry = geometry,
                sectors = sectors,
                rotation = { rotation.value },
                pointerAngle = { pointer.angle },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Угол, на котором сектор [sectorIndex] окажется под стрелкой.
 * Сектор i стоит на угле i·θ + rotation (по часовой от 12 часов), под стрелкой
 * он при i·θ + rotation ≡ 0 (mod 360). Крутим только вперёд, минимум [turns] оборотов.
 */
private fun spinTarget(current: Float, sectorIndex: Int, turns: Int, jitter: Float): Float {
    val base = current + turns * 360f
    val desired = (-(sectorIndex + jitter) * ROULETTE_SECTOR_ANGLE).mod(360f)
    return base + (desired - base.mod(360f)).mod(360f)
}

/** Все размеры барабана в пикселях. */
private data class RouletteGeometry(
    val centerX: Float,
    val centerY: Float,
    val backgroundRadius: Float,
    val sectorOuterRadius: Float,
    val hubRadius: Float,
    val sectorWidth: Float,
    val sectorHeight: Float,
    val pointerWidth: Float,
    val pointerHeight: Float,
    val pointerTop: Float,
    val bandHeight: Float
) {
    /** Радиус, на котором лежат центры плиток секторов. */
    val sectorCenterRadius: Float get() = sectorOuterRadius - sectorHeight / 2f
}

@Composable
private fun rememberRouletteGeometry(widthPx: Float, footerPx: Float): RouletteGeometry {
    val sectorPainter = painterResource(R.drawable.section)
    val pointerPainter = painterResource(R.drawable.stopper)
    val sectorAspect = sectorPainter.intrinsicSize.let { it.height / it.width }
    val pointerAspect = pointerPainter.intrinsicSize.let { it.height / it.width }
    return remember(widthPx, footerPx, sectorAspect, pointerAspect) {
        val outer = widthPx * SECTOR_OUTER_RATIO
        val chord = 2f * outer * sin(Math.toRadians(ROULETTE_SECTOR_ANGLE / 2.0)).toFloat()
        val sectorW = chord * SECTOR_FILL
        val sectorH = sectorW * sectorAspect
        val background = outer * BACKGROUND_RATIO
        val pointerW = widthPx * POINTER_WIDTH_RATIO
        val pointerH = pointerW * pointerAspect
        // Стрелка выступает над внешней окружностью, верх полосы — её верх;
        // остальное перекрывает обод, острие заходит на сектора.
        // Запас сверху: при качании верхний угол стрелки приподнимается.
        val swingHeadroom = pointerW * 0.25f
        val pointerTop = swingHeadroom
        val centerY = pointerTop + pointerH * POINTER_ABOVE_RIM + background
        val ringTop = centerY - outer
        RouletteGeometry(
            centerX = widthPx / 2f,
            centerY = centerY,
            backgroundRadius = background,
            sectorOuterRadius = outer,
            hubRadius = outer - sectorH * (1f - HUB_OVERLAP),
            sectorWidth = sectorW,
            sectorHeight = sectorH,
            pointerWidth = pointerW,
            pointerHeight = pointerH,
            pointerTop = pointerTop,
            bandHeight = ringTop + sectorH + footerPx
        )
    }
}

/**
 * Кадр барабана. Поворот читается лямбдой в фазах layout/draw — вращение
 * не вызывает рекомпозиций.
 *
 * Центр колеса далеко за полосой, поэтому общий graphicsLayer на всё колесо
 * не годится (слой размером 2·радиуса упирается в лимит текстуры). Фон и центр
 * рисуются на Canvas с поворотом холста, а каждая плитка облетает центр сама:
 * позиция по окружности + поворот вокруг своей оси — это то же жёсткое вращение.
 */
@Composable
private fun RouletteDrum(
    geometry: RouletteGeometry,
    sectors: List<Wine>,
    rotation: () -> Float,
    pointerAngle: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val wheelBitmap = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.roulette_bg)
    }
    val sectorW = with(density) { geometry.sectorWidth.toDp() }
    val sectorH = with(density) { geometry.sectorHeight.toDp() }

    Box(modifier = modifier) {
        // 1. Большая фоновая окружность.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircleAsset(wheelBitmap, geometry.centerX, geometry.centerY, geometry.backgroundRadius, rotation())
        }
        // 2. Сектора по окружности.
        repeat(ROULETTE_SECTOR_COUNT) { index ->
            RouletteSector(
                wine = sectors.getOrNull(index),
                modifier = Modifier
                    .offset {
                        val deg = index * ROULETTE_SECTOR_ANGLE + rotation()
                        val rad = Math.toRadians(deg.toDouble())
                        val x = geometry.centerX + geometry.sectorCenterRadius * sin(rad).toFloat()
                        val y = geometry.centerY - geometry.sectorCenterRadius * cos(rad).toFloat()
                        IntOffset(
                            (x - geometry.sectorWidth / 2f).roundToInt(),
                            (y - geometry.sectorHeight / 2f).roundToInt()
                        )
                    }
                    .size(sectorW, sectorH)
                    .graphicsLayer { rotationZ = index * ROULETTE_SECTOR_ANGLE + rotation() }
            )
        }
        // 3. Маленькая центральная окружность — «приподнята» над секторами:
        // мягкая радиальная тень ложится на узкие концы плиток и низ бутылок.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(geometry.centerX, geometry.centerY)
            val shadowOuter = geometry.hubRadius + geometry.sectorHeight * HUB_SHADOW_WIDTH
            val edge = geometry.hubRadius / shadowOuter
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = HUB_SHADOW_ALPHA),
                    edge to Color.Black.copy(alpha = HUB_SHADOW_ALPHA),
                    (edge + (1f - edge) * 0.4f) to Color.Black.copy(alpha = HUB_SHADOW_ALPHA * 0.35f),
                    1f to Color.Transparent,
                    center = center,
                    radius = shadowOuter
                ),
                radius = shadowOuter,
                center = center
            )
            drawCircleAsset(wheelBitmap, geometry.centerX, geometry.centerY, geometry.hubRadius, rotation())
        }
        // Стрелка всегда сверху и качается на оси у своего верхнего края.
        // Острие внизу, поэтому отклонение «по ходу вращения» (вправо) —
        // это поворот против часовой, отсюда минус.
        Image(
            painter = painterResource(R.drawable.stopper),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, geometry.pointerTop.roundToInt()) }
                .size(
                    with(density) { geometry.pointerWidth.toDp() },
                    with(density) { geometry.pointerHeight.toDp() }
                )
                .graphicsLayer {
                    transformOrigin = PointerPivot
                    rotationZ = -pointerAngle()
                }
        )
    }
}

/**
 * Рисует круглый ассет с центром ([cx], [cy]) и радиусом [radius], повёрнутый
 * на [degrees]. Масштаб — матрицей холста, а не прямоугольником назначения:
 * иначе конвейер материализует пересэмплированную копию размером с колесо
 * ("Canvas: trying to draw too large bitmap" на больших экранах).
 */
private fun DrawScope.drawCircleAsset(
    bitmap: Bitmap?,
    cx: Float,
    cy: Float,
    radius: Float,
    degrees: Float
) {
    val image = bitmap ?: return
    val paint = android.graphics.Paint().apply { isFilterBitmap = true }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.withSave {
            translate(cx, cy)
            rotate(degrees)
            scale(2f * radius / image.width, 2f * radius / image.height)
            translate(-image.width / 2f, -image.height / 2f)
            drawBitmap(image, 0f, 0f, paint)
        }
    }
}

/** Плитка сектора (ассет section, широким краем наружу) с бутылкой внутри. */
@Composable
private fun RouletteSector(
    wine: Wine?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.section),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        if (wine != null) {
            // Размер бутылки прежний (сумма вертикальных отступов та же), но она
            // сдвинута к центру колеса — низ плитки уходит под тень центра.
            RouletteBottleImage(
                wine = wine,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 18.dp, top = 30.dp, bottom = 6.dp)
            )
        }
    }
}

/**
 * Фото бутылки вина; при отсутствии или ошибке загрузки — схематичный силуэт.
 */
@Composable
fun RouletteBottleImage(
    wine: Wine?,
    modifier: Modifier = Modifier
) {
    val url = wine?.imageUrl
    if (wine == null || url == null) {
        BottleSilhouette(modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(apiImageUrl(url))
            .crossfade(true)
            .build(),
        contentDescription = wine.name,
        contentScale = ContentScale.Fit,
        loading = { BottleSilhouette(modifier = Modifier.fillMaxSize()) },
        error = { BottleSilhouette(modifier = Modifier.fillMaxSize()) },
        modifier = modifier
    )
}

/** Схематичный силуэт бутылки — заглушка, пока фото не загрузилось. */
@Composable
private fun BottleSilhouette(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val bodyW = w * 0.46f
        val neckW = bodyW * 0.36f
        val neckH = h * 0.24f
        val shoulderH = h * 0.1f
        val bodyTop = neckH + shoulderH
        val bodyH = (h - bodyTop).coerceAtLeast(1f)
        val left = (w - bodyW) / 2f

        drawRoundRect(
            color = BrandBurgundy300,
            topLeft = Offset((w - neckW) / 2f, 0f),
            size = Size(neckW, bodyTop + 1f),
            cornerRadius = CornerRadius(neckW / 3f)
        )
        drawRoundRect(
            color = BrandBurgundy600,
            topLeft = Offset(left, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.14f)
        )
        drawRoundRect(
            color = BrandCream50,
            topLeft = Offset(left + bodyW * 0.12f, bodyTop + bodyH * 0.28f),
            size = Size(bodyW * 0.76f, bodyH * 0.36f),
            cornerRadius = CornerRadius(bodyW * 0.05f)
        )
    }
}

// ─────────────────────────── Превью ───────────────────────────

@Composable
private fun RouletteWheelPreview(pool: List<Wine>) {
    WineAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandCream50)
        ) {
            WineRouletteWheel(
                sectors = buildRouletteSectors(pool),
                roll = null,
                canSpin = true,
                onSpin = {},
                onSettled = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Preview(name = "6 вин", showBackground = true, widthDp = 411, heightDp = 400)
@Composable
private fun WineRouletteWheelPreview() {
    RouletteWheelPreview(pool = MockDataProvider.wines)
}

@Preview(name = "1 вино", showBackground = true, widthDp = 411, heightDp = 400)
@Composable
private fun WineRouletteWheelOneWinePreview() {
    RouletteWheelPreview(pool = MockDataProvider.wines.take(1))
}

@Preview(name = "пустой пул", showBackground = true, widthDp = 411, heightDp = 400)
@Composable
private fun WineRouletteWheelEmptyPreview() {
    RouletteWheelPreview(pool = emptyList())
}
