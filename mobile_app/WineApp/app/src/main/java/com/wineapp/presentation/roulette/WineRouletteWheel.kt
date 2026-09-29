package com.wineapp.presentation.roulette

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import androidx.core.graphics.withTranslation

/** Секторов в барабане фиксировано: 360° / 17 ≈ 21.176°. */
const val ROULETTE_SECTOR_COUNT = 19
const val ROULETTE_SECTOR_ANGLE = 360f / ROULETTE_SECTOR_COUNT

// Геометрия барабана. Всё считается от ширины экрана, центр окружности —
// точно на оси стрелки (widthPx/2), стрелка стоит по центру полосы.
//
// Верх окружности (rOuter) приходится на верх полосы, поэтому в кадр попадает
// верхушка дуги: центральный сектор виден целиком, крайние уходят за края.
private const val R_MID_RATIO = 0.8f
private const val SECTOR_W_RATIO = 0.13f
private const val SECTOR_H_RATIO = 0.15f

/** Высота видимой полосы: центральный сектор плюс загнутые соседние. */
private const val BAND_RATIO = 0.56f

private const val SPIN_DURATION_MS = 4500
private const val MIN_TURNS = 5
private const val MAX_TURNS = 7
/** Последние полтора оборота не пересобираются — подвод к победителю читаем. */
private const val FREEZE_TURNS = 1.5f
private const val SETTLE_DELAY_MS = 350L
private const val TICK_THROTTLE_MS = 70L

private val SpinEasing = CubicBezierEasing(0.08f, 0.82f, 0.16f, 1.0f)

/**
 * Круговой барабан рулетки: 17 секторов, в кадре видно около трёх, по краям
 * экрана бутылки уходят за границу. Угол живёт в [Animatable], а не в
 * состоянии, — иначе каждое пересоздание 17 секторов бьёт по всему экрану.
 *
 * Пул больше 17 вин не кончается: на каждом пройденном секторе содержимое
 * пересобирается, а победитель заранее закреплён в том секторе, который
 * встанет под стрелку.
 */
@Composable
fun WineRouletteWheel(
    pool: List<Wine>,
    roll: WineRouletteEffect.Roll?,
    canSpin: Boolean,
    onSpin: () -> Unit,
    onSettled: (Wine) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val angle = remember { Animatable(0f) }
    var slots by remember { mutableStateOf(buildSlots(pool, 0, null)) }

    LaunchedEffect(pool) {
        slots = buildSlots(pool, 0, null)
    }

    LaunchedEffect(roll) {
        val request = roll ?: return@LaunchedEffect
        if (pool.isEmpty()) return@LaunchedEffect

        val winnerIndex = pool.indexOfFirst { it.id == request.winner.id }
        val targetSlot = if (winnerIndex >= 0) winnerIndex % ROULETTE_SECTOR_COUNT else 0
        val currentU = angle.value / ROULETTE_SECTOR_ANGLE
        val underPointer = floor(currentU).toInt().mod(ROULETTE_SECTOR_COUNT)
        val stepsToTarget = (targetSlot - underPointer).mod(ROULETTE_SECTOR_COUNT)
        val turns = Random.nextInt(MIN_TURNS, MAX_TURNS + 1)
        // +0.5 — сектор встаёт по центру под стрелкой, а не углом.
        val targetAngle =
            (currentU + turns * ROULETTE_SECTOR_COUNT + stepsToTarget + 0.5f) * ROULETTE_SECTOR_ANGLE
        val freezeFrom = targetAngle - FREEZE_TURNS * 360f

        var lastTick = 0L
        val watcher = launch {
            snapshotFlow { angle.value }
                .map { floor(it / ROULETTE_SECTOR_ANGLE).toInt() }
                .distinctUntilChanged()
                .collect {
                    val now = System.currentTimeMillis()
                    if (now - lastTick > TICK_THROTTLE_MS) {
                        lastTick = now
                        HapticHelper.vibrateTick(context)
                    }
                    if (angle.value < freezeFrom) {
                        slots = buildSlots(pool, targetSlot, request.winner)
                    }
                }
        }

        angle.animateTo(targetAngle, tween(SPIN_DURATION_MS, easing = SpinEasing))
        watcher.cancel()

        // Финальный срез: победитель ровно в секторе под стрелкой.
        slots = buildSlots(pool, targetSlot, request.winner)
        delay(SETTLE_DELAY_MS)
        HapticHelper.vibrateSuccess(context)
        onSettled(request.winner)
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        // На низких экранах полоса не должна съесть футер: режем по доступной высоте.
        val availableHeightPx = with(density) { maxHeight.toPx() }
        val bandHeight = when {
            availableHeightPx.isFinite() ->
                minOf(widthPx * BAND_RATIO, availableHeightPx).coerceAtLeast(1f)
            else -> widthPx * BAND_RATIO
        }.dp

        Surface(
            onClick = onSpin,
            enabled = canSpin,
            shape = RoundedCornerShape(0.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(bandHeight)
                .clipToBounds()
        ) {
            RouletteDrum(
                geometry = rouletteGeometry(widthPx),
                slots = slots,
                angle = { angle.value },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Кадр барабана. Угол на входе, а не внутри: так превью может показать
 * реальное положение плиток в середине вращения, а не только покой.
 *
 * Центр колеса лежит за пределами полосы, поэтому общий слой на всё колесо не
 * годится: transformOrigin не может уйти за границы слоя, а бокс 2*радиуса не
 * влезает в лимит размера слоя. Вместо этого плитка сама облетает центр:
 * позиция считается от центра колеса, а поворот идёт вокруг её собственной
 * оси — для жёсткого вращения плоскости это то же самое.
 */
@Composable
private fun RouletteDrum(
    geometry: RouletteGeometry,
    slots: List<Wine?>,
    angle: () -> Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        RouletteWheelBackdrop(
            centerX = geometry.widthPx / 2f,
            centerY = geometry.rOuter,
            rInner = geometry.rInner,
            rOuter = geometry.rOuter,
            angle = angle,
            modifier = Modifier.fillMaxSize()
        )
        slots.forEachIndexed { index, wine ->
            val sectorAngle = index * ROULETTE_SECTOR_ANGLE
            RouletteSector(
                wine = wine,
                modifier = Modifier
                    .offset {
                        // Сектор 0 — верхний (12 часов), дальше по часовой стрелке.
                        val rad = Math.toRadians((sectorAngle + angle()).toDouble())
                        val x = geometry.widthPx / 2f + geometry.rMid * sin(rad).toFloat() - 130
                        val y = geometry.rOuter - geometry.rMid * cos(rad).toFloat() + 120
                        IntOffset(
                            (x - geometry.sectorW / 2f).roundToInt(),
                            (y - geometry.sectorH / 2f).roundToInt()
                        )
                    }
                    .size(geometry.sectorW.dp, geometry.sectorH.dp)
                    .graphicsLayer { rotationZ = sectorAngle + angle() }
            )
        }
        RoulettePointer(modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** Все размеры барабана в пикселях, посчитанные от ширины экрана. */
private data class RouletteGeometry(
    val widthPx: Float,
    val rMid: Float,
    val rInner: Float,
    val rOuter: Float,
    val sectorW: Float,
    val sectorH: Float
)

private fun rouletteGeometry(widthPx: Float): RouletteGeometry {
    val rMid = widthPx * R_MID_RATIO
    val sectorW = widthPx * SECTOR_W_RATIO
    val sectorH = widthPx * SECTOR_H_RATIO
    return RouletteGeometry(
        widthPx = widthPx,
        rMid = rMid,
        rInner = (rMid - sectorH / 2f).coerceAtLeast(1f),
        rOuter = rMid + sectorH / 2f,
        sectorW = sectorW,
        sectorH = sectorH
    )
}

/**
 * «Фон-колесо»: ассет roulette_bg, вписанный в прямоугольник, в который
 * раньше рисовались дуги. Радиус этого прямоугольника — середина обруча
 * (rOuter + rInner) / 2, то есть ровно rMid: вписанная в картинку окружность
 * совпадает с орбитой, по которой выстроены центры секторов.
 */
@Composable
private fun RouletteWheelBackdrop(
    centerX: Float,
    centerY: Float,
    rInner: Float,
    rOuter: Float,
    angle: () -> Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.roulette_bg)
    }
    val arcRadius = (rOuter + rInner) / 1.90f
    val side = arcRadius * 2f
    val left = centerX - arcRadius
    val top = centerY - arcRadius
    Canvas(modifier = modifier) {
        val image = source ?: return@Canvas
        val paint = android.graphics.Paint().apply { isFilterBitmap = true }
        drawIntoCanvas { canvas ->
            // Масштаб задаём матрицей, а не прямоугольником назначения: перегрузка
            // drawBitmap(bitmap, srcRect, dstRect, paint) заставляет конвейер
            // материализовать пересэмплированную копию размером с приёмником, а он
            // здесь квадрат 1.6*ширина экрана — на планшете это сотни мегабайт и
            // падение "Canvas: trying to draw too large bitmap". С матрицей исходник
            // остаётся 26 КБ, а растягивает его GPU.
            val native = canvas.nativeCanvas
            native.withTranslation(left, top) {
                // Фон вращается вместе с барабаном. Pivot — центр картинки, который
                // совпадает с центром колеса, иначе битмап крутился бы вокруг своего
                // левого верхнего угла и уезжал из-под секторов.
                rotate(angle(), arcRadius, arcRadius)
                scale(side / image.width, side / image.height)
                drawBitmap(image, 0f, 0f, paint)
            }
        }
    }
}

/**
 * Ассет «сектор»: плитка с фото бутылки, которая вращается вместе с колесом.
 * Без фото рисуется схематичный силуэт — пока не подставлены ассеты из фигмы.
 */
@Composable
private fun RouletteSector(
    wine: Wine?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp))
    ) {
        // Фон сектора — ассет из макета; раньше здесь была однотонная Surface
        // с бордером, из-за которой сектор сливался с кремовой панелью.
        Image(
            painter = painterResource(R.drawable.section),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )
        RouletteBottleImage(
            wine = wine,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        )
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

/** Тестовый силуэт бутылки, пока нет ассетов из фигмы. */
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

/** Ассет «стрелка-указатель»: неподвижная, всегда поверх барабана. */
@Composable
private fun RoulettePointer(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.stopper),
        contentDescription = null,
        modifier = modifier.size(width = 60.dp, height = 72.dp)
    )
}

/**
 * Собирает 17 содержимых секторов. Победитель (если задан) закреплён в
 * [targetSlot] и больше нигде не встречается, остальные — случайные из пула.
 * Пул короче 17 просто повторяется, единственное вино заполняет всё.
 */
private fun buildSlots(pool: List<Wine>, targetSlot: Int, winner: Wine?): List<Wine?> {
    if (pool.isEmpty()) return List(ROULETTE_SECTOR_COUNT) { null }
    val out = arrayOfNulls<Wine>(ROULETTE_SECTOR_COUNT)
    if (winner != null) {
        out[targetSlot.mod(ROULETTE_SECTOR_COUNT)] = winner
    }
    val bag = ArrayDeque(pool.filter { winner == null || it.id != winner.id }.shuffled())
    for (i in out.indices) {
        if (out[i] != null) continue
        if (bag.isEmpty()) bag.addAll(pool.shuffled())
        out[i] = bag.removeFirst()
    }
    return out.toList()
}

// ─────────────────────────── Превью ───────────────────────────

/** Ширина, под которую считается геометрия превью: 411dp — типичный телефон. */
private val PreviewWidth = 411.dp

/**
 * Кадр барабана в том же окружении, что и на экране: кремовая панель, полоса
 * прижата к низу и уходит под системные кнопки. [angle] задаёт положение
 * плиток, поэтому превью показывает и покой, и середину вращения.
 */
@Composable
private fun RouletteDrumPreview(
    pool: List<Wine>,
    angle: Float = 0f
) {
    val widthPx = with(LocalDensity.current) { PreviewWidth.toPx() }
    WineAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandCream50)
        ) {
            RouletteDrum(
                geometry = rouletteGeometry(widthPx),
                slots = buildSlots(pool, targetSlot = 0, winner = null),
                angle = { angle },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((PreviewWidth.value * BAND_RATIO).dp)
                    .align(Alignment.BottomCenter)
                    .clipToBounds()
            )
        }
    }
}

@Preview(name = "6 вин", showBackground = true, widthDp = 411, heightDp = 280)
@Composable
private fun WineRouletteWheelPreview() {
    RouletteDrumPreview(pool = MockDataProvider.wines)
}

@Preview(name = "1 вин", showBackground = true, widthDp = 411, heightDp = 280)
@Composable
private fun WineRouletteWheelOneWinePreview() {
    RouletteDrumPreview(pool = MockDataProvider.wines.take(1))
}

@Preview(name = "24 вина", showBackground = true, widthDp = 411, heightDp = 280)
@Composable
private fun WineRouletteWheelManyPreview() {
    // Больше 17 вин: плитки пересобираются по ходу вращения, пул не заканчивается.
    RouletteDrumPreview(
        pool = List(24) { index ->
            MockDataProvider.wines[index % MockDataProvider.wines.size]
                .copy(id = "pool-$index")
        }
    )
}

@Preview(name = "сдвиг на сектор", showBackground = true, widthDp = 411, heightDp = 280)
@Composable
private fun WineRouletteWheelShiftedPreview() {
    // Ровно один шаг сектора: барабан сдвинут, но плитка снова стоит под
    // стрелкой, поэтому кадр остаётся зеркальным. Угол, не кратный шагу,
    // даёт намеренно несимметричную дугу — для превью это только путает.
    RouletteDrumPreview(pool = MockDataProvider.wines, angle = ROULETTE_SECTOR_ANGLE)
}

@Preview(name = "пустой пул", showBackground = true, widthDp = 411, heightDp = 280)
@Composable
private fun WineRouletteWheelEmptyPreview() {
    RouletteDrumPreview(pool = emptyList())
}
