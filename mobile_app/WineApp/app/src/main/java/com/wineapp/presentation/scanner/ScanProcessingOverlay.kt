package com.wineapp.presentation.scanner

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.wineapp.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Длительность одного цикла анимации переливания. */
private const val POUR_CYCLE_MS = 6000
private const val FACT_ROTATE_MS = 3000L
private const val STAGE_UPLOAD_MS = 2000L
private const val STAGE_RECOGNIZE_MS = 4000L

/** Всё визуальное разнообразие сцены выводится из сида показа — рекомпозиции кадр не меняют. */
internal data class PourScene(
    val bottle: Int, // 0 Bordeaux, 1 Burgundy, 2 Flute
    val glass: Int, // 0 Bordeaux, 1 Flute, 2 Coupe
    val wine: Color,
    val cardBg: Color,
    val cardEdge: Color,
    val waveAmp: Float, // в долях высоты чаши
    val wavePhase: Float, // радианы
    val streamBend: Float, // изгиб струи
    val streamWobble: Float, // дрожание струи
    val glintAt: Float, // момент блика в долях цикла
    val bubbles: List<BubbleSeed>
)

internal data class BubbleSeed(
    val x: Float, // доля ширины чаши
    val speed: Float, // циклов за цикл анимации
    val offset: Float, // сдвиг фазы
    val radius: Float // px при u=100
)

private val WINE_COLORS = listOf(
    Color(0xFF8E1B2F), // рубин
    Color(0xFF5C1A24), // гранат
    Color(0xFFD98A94), // розе
    Color(0xFFD9A441) // янтарь
)

private val CARD_BGS = listOf(
    Color(0xFF2A141B) to Color(0xFF0E070B), // тёмный погреб
    Color(0xFFF3E9D2) to Color(0xFFC9B78F), // пергамент
    Color(0xFF182430) to Color(0xFF070B11) // ночной бар
)

internal fun deriveScene(seed: Long): PourScene {
    val rng = Random(seed)
    val (bg, edge) = CARD_BGS[rng.nextInt(CARD_BGS.size)]
    val bubbles = List(6) {
        BubbleSeed(
            x = rng.nextFloat(),
            speed = 0.6f + rng.nextFloat() * 0.9f,
            offset = rng.nextFloat(),
            radius = 1.2f + rng.nextFloat() * 2.2f
        )
    }
    return PourScene(
        bottle = rng.nextInt(3),
        glass = rng.nextInt(3),
        wine = WINE_COLORS[rng.nextInt(WINE_COLORS.size)],
        cardBg = bg,
        cardEdge = edge,
        waveAmp = 0.03f + rng.nextFloat() * 0.04f,
        wavePhase = rng.nextFloat() * 2f * PI.toFloat(),
        streamBend = -0.15f + rng.nextFloat() * 0.3f,
        streamWobble = 0.5f + rng.nextFloat(),
        glintAt = 0.82f + rng.nextFloat() * 0.12f,
        bubbles = bubbles
    )
}

/**
 * Оверлей обработки скана: зацикленное (~6 с) переливание + стадии + факты о вине.
 * Каждый показ уникален: сцена выводится из [seed], стартовый факт — [factIndex].
 */
@Composable
fun ScanProcessingOverlay(
    seed: Long,
    factIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val facts = remember { context.resources.getStringArray(R.array.scan_facts).toList() }
    val animatorsEnabled = remember {
        try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (e: Exception) {
            true
        }
    }
    val cd = stringResource(R.string.scanner_processing_cd)

    if (!animatorsEnabled) {
        // Системные анимации отключены: статичный почти полный бокал + стартовый факт.
        ScanProcessingContent(
            scene = remember(seed) { deriveScene(seed) },
            progress = 0.88f,
            stage = stringResource(R.string.scanner_stage_recognize),
            fact = facts.getOrElse(factIndex) { "" },
            modifier = modifier.semantics { contentDescription = cd }
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "pour")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(POUR_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pourProgress"
    )

    val startUptime = remember(seed) { SystemClock.uptimeMillis() }
    var elapsed by remember(seed) { mutableLongStateOf(0L) }
    LaunchedEffect(seed) {
        while (true) {
            elapsed = SystemClock.uptimeMillis() - startUptime
            kotlinx.coroutines.delay(250)
        }
    }

    val stage = when {
        elapsed < STAGE_UPLOAD_MS -> stringResource(R.string.scanner_stage_upload)
        elapsed < STAGE_RECOGNIZE_MS -> stringResource(R.string.scanner_stage_recognize)
        else -> stringResource(R.string.scanner_stage_match)
    }
    val fact = if (facts.isEmpty()) {
        ""
    } else {
        facts.getOrElse((factIndex + (elapsed / FACT_ROTATE_MS).toInt()) % facts.size) { "" }
    }

    ScanProcessingContent(
        scene = remember(seed) { deriveScene(seed) },
        progress = progress,
        stage = stage,
        fact = fact,
        modifier = modifier.semantics { contentDescription = cd }
    )
}

@Composable
internal fun ScanProcessingContent(
    scene: PourScene,
    progress: Float,
    stage: String,
    fact: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(scene.cardBg, scene.cardEdge),
                            radius = 420f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    drawPourScene(scene, progress)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stage,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedContent(
                targetState = fact,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "fact"
            ) { currentFact ->
                Text(
                    text = currentFact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
        }
    }
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun DrawScope.drawPourScene(scene: PourScene, progress: Float) {
    val w = size.width
    val h = size.height

    // Фаза 1 (0–0.40): бутылка стоит рядом, затем поднимается по дуге и наклоняется над бокалом.
    // Фаза 2 (0.40–0.78): наливание, уровень растёт. Фаза 3 (0.78–1.0): «глоток» — уровень падает,
    // бутылка возвращается. К моменту рестарта цикла позы совпадают — шва нет.
    val lift = smoothstep((progress - 0.05f) / 0.25f) *
        (1f - smoothstep((progress - 0.88f) / 0.12f))
    val tiltT = smoothstep((progress - 0.15f) / 0.25f) *
        (1f - smoothstep((progress - 0.86f) / 0.12f))
    val hold = smoothstep((progress - 0.40f) / 0.08f) *
        (1f - smoothstep((progress - 0.86f) / 0.10f))
    val tilt = POUR_TILT_DEG * tiltT + sin(progress * 2f * PI.toFloat() * 2f).toFloat() * 0.8f * hold

    // Горлышко: стоянка (бутылка на дне слева) -> висит НАД бокалом, в середине подъёма — дуга вверх.
    val mouthX = lerp(0.28f * w, 0.57f * w, lift)
    val mouthY = lerp(0.40f * h, 0.16f * h, lift) - 0.06f * h * sin(PI.toFloat() * lift).toFloat()
    val mouth = Offset(mouthX, mouthY)

    // Уровень: наливается 0.40–0.75, «выпивается» 0.78–0.98 — в нуле и единице ноль, цикл бесшовный.
    val level = smoothstep((progress - 0.40f) / 0.35f) *
        (1f - smoothstep((progress - 0.78f) / 0.20f))
    // Струя: появляется с наклоном, гаснет раньше «глотка».
    val streamAlpha = smoothstep((progress - 0.42f) / 0.05f) *
        (1f - smoothstep((progress - 0.70f) / 0.08f))

    // Сцена рисуется в натуральных пропорциях, затем целиком вписывается в кадр,
    // чтобы наклонная бутылка не обрезалась краями сильнее задуманного.
    withTransform({
        scale(0.78f, 0.78f, pivot = Offset(0.52f * w, 0.47f * h))
    }) {
        val wineTop = drawGlass(scene, level, progress, w, h)
        // Позиция бутылки считается вручную (поворот по часовой стрелке, как в документации
        // Compose): мировые координаты = mouth + R(tilt) · локальные. Никаких вложенных
        // translate/rotate — они уводили бутылку мимо кадра.
        val rad = Math.toRadians(tilt.toDouble())
        val cosT = cos(rad).toFloat()
        val sinT = sin(rad).toFloat()
        val toWorld: (Float, Float) -> Offset = { lx, ly ->
            Offset(
                mouth.x + lx * cosT - ly * sinT,
                mouth.y + lx * sinT + ly * cosT
            )
        }
        drawBottleBody(scene, w, h, toWorld)
        if (streamAlpha > 0.01f && progress < 0.97f) {
            drawStream(scene, progress, wineTop, mouth, streamAlpha, w, h)
        }
        drawGlint(scene, progress, w, h)
    }
}

/** Чаша бокала: 0 Bordeaux (широкая), 1 Flute (узкая высокая), 2 Coupe (плоская широкая). */
private fun glassBowlPath(glass: Int, w: Float, h: Float): Path {
    val cx = 0.64f * w
    return Path().apply {
        when (glass) {
            1 -> { // Flute
                val topY = 0.18f * h
                val botY = 0.52f * h
                val halfTop = 0.075f * w
                val halfBot = 0.045f * w
                moveTo(cx - halfTop, topY)
                lineTo(cx - halfBot, botY)
                quadraticBezierTo(cx, botY + 0.02f * h, cx + halfBot, botY)
                lineTo(cx + halfTop, topY)
                close()
            }
            2 -> { // Coupe
                val topY = 0.26f * h
                val botY = 0.42f * h
                val halfTop = 0.15f * w
                moveTo(cx - halfTop, topY)
                quadraticBezierTo(cx - halfTop * 0.9f, botY, cx, botY)
                quadraticBezierTo(cx + halfTop * 0.9f, botY, cx + halfTop, topY)
                close()
            }
            else -> { // Bordeaux
                val topY = 0.20f * h
                val botY = 0.52f * h
                val halfTop = 0.105f * w
                moveTo(cx - halfTop, topY)
                cubicTo(
                    cx - halfTop * 1.25f, topY + 0.14f * h,
                    cx - halfTop * 0.8f, botY - 0.02f * h,
                    cx, botY
                )
                cubicTo(
                    cx + halfTop * 0.8f, botY - 0.02f * h,
                    cx + halfTop * 1.25f, topY + 0.14f * h,
                    cx + halfTop, topY
                )
                close()
            }
        }
    }
}

private fun DrawScope.drawGlass(scene: PourScene, level: Float, progress: Float, w: Float, h: Float): Float {
    val cx = 0.64f * w
    val bowl = glassBowlPath(scene.glass, w, h)
    val glassColor = Color.White.copy(alpha = 0.28f)
    val bounds = bowl.getBounds()

    // Ножка стартует с нахлёстом на дно чаши — зазора быть не должно.
    val baseY = 0.86f * h
    drawLine(glassColor, Offset(cx, bounds.bottom - 2f), Offset(cx, baseY - 0.02f * h), strokeWidth = 3f)
    drawLine(
        glassColor, Offset(cx - 0.09f * w, baseY), Offset(cx + 0.09f * w, baseY),
        strokeWidth = 4f, cap = StrokeCap.Round
    )
    // Контур чаши
    drawPath(bowl, glassColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

    // Вино: отсекаем по чаше, уровень растёт с прогрессом, сверху волна.
    val wineTop = lerp(bounds.bottom, bounds.top + 0.02f * h, level)
    clipPath(bowl) {
        drawRect(scene.wine, topLeft = Offset(bounds.left, wineTop), size = Size(bounds.width, bounds.bottom - wineTop))
        val amp = scene.waveAmp * bounds.height
        val wave = Path().apply {
            moveTo(bounds.left, wineTop)
            var x = bounds.left
            while (x <= bounds.right) {
                val y = wineTop + amp * sin((x / bounds.width) * 2f * PI.toFloat() * scene.streamWobble + scene.wavePhase + progress * 6f).toFloat()
                lineTo(x, y)
                x += 2f
            }
            lineTo(bounds.right, bounds.bottom)
            lineTo(bounds.left, bounds.bottom)
            close()
        }
        drawPath(wave, scene.wine.copy(alpha = 0.85f))
        // Пузырьки поднимаются внутри вина
        scene.bubbles.forEach { b ->
            val bx = bounds.left + b.x * bounds.width
            val travel = (bounds.bottom - wineTop).coerceAtLeast(1f)
            val by = bounds.bottom - ((b.offset + progress * b.speed) % 1f) * travel
            if (by > wineTop) {
                drawCircle(Color.White.copy(alpha = 0.35f), radius = b.radius * (w / 200f), center = Offset(bx, by))
            }
        }
    }
    return wineTop
}

/** Угол опрокидывания: 0 — бутылка стоит на дне, 118 — горлышко висит над бокалом. */
private const val POUR_TILT_DEG = 118f

/**
 * Тело бутылки в локальных координатах: горлышко в начале координат, тело ВНИЗ.
 * При нулевом угле бутылка стоит на дне (горлышко сверху), при POUR_TILT_DEG —
 * опрокинута горлышком в бокал. Все координаты переводятся в мировые через [map]
 * (поворот по часовой + перенос к горлышку) — без Canvas-трансформов.
 * Горлышко явное: высокий перешеек + кольцо-губа.
 * Плечи различаются по варианту: Bordeaux — прямые, Burgundy — покатые, Flute — почти без плеч.
 */
private fun DrawScope.drawBottleBody(
    scene: PourScene,
    w: Float,
    h: Float,
    map: (Float, Float) -> Offset
) {
    val bottleColor = Color(0xFF1E3A2B)
    val highlight = Color.White.copy(alpha = 0.18f)
    val bodyLen = 0.46f * h
    val neckLen = 0.12f * h
    val neckHalf = when (scene.bottle) {
        2 -> 0.018f * w // Flute узкая
        1 -> 0.026f * w
        else -> 0.024f * w
    }
    val bodyHalf = when (scene.bottle) {
        1 -> 0.102f * w // Burgundy широкая
        2 -> 0.068f * w // Flute узкая
        else -> 0.088f * w
    }
    val neckBaseY = neckLen
    val baseY = bodyLen
    fun Path.mTo(lx: Float, ly: Float) {
        val p = map(lx, ly)
        moveTo(p.x, p.y)
    }
    fun Path.lTo(lx: Float, ly: Float) {
        val p = map(lx, ly)
        lineTo(p.x, p.y)
    }
    fun Path.qTo(cx: Float, cy: Float, x: Float, y: Float) {
        val c = map(cx, cy)
        val p = map(x, y)
        quadraticBezierTo(c.x, c.y, p.x, p.y)
    }
    val body = Path().apply {
        mTo(-neckHalf, 0f)
        lTo(-neckHalf, neckBaseY)
        when (scene.bottle) {
            0 -> { // Bordeaux: прямые плечи
                lTo(-bodyHalf, neckBaseY + 0.06f * h)
                lTo(-bodyHalf, baseY - 0.02f * h)
            }
            1 -> { // Burgundy: покатые плечи
                qTo(-bodyHalf, neckBaseY, -bodyHalf, neckBaseY + 0.08f * h)
                lTo(-bodyHalf, baseY - 0.02f * h)
            }
            else -> { // Flute: почти без плеч
                qTo(-bodyHalf, neckBaseY - 0.02f * h, -bodyHalf, neckBaseY + 0.04f * h)
                lTo(-bodyHalf, baseY - 0.02f * h)
            }
        }
        // Дно со скруглением
        qTo(-bodyHalf, baseY, -bodyHalf + 0.02f * h, baseY)
        lTo(bodyHalf - 0.02f * h, baseY)
        qTo(bodyHalf, baseY, bodyHalf, baseY - 0.02f * h)
        when (scene.bottle) {
            0 -> {
                lTo(bodyHalf, neckBaseY + 0.06f * h)
                lTo(neckHalf, neckBaseY)
            }
            1 -> {
                lTo(bodyHalf, neckBaseY + 0.08f * h)
                qTo(bodyHalf, neckBaseY, neckHalf, neckBaseY)
            }
            else -> {
                lTo(bodyHalf, neckBaseY + 0.04f * h)
                qTo(bodyHalf, neckBaseY - 0.02f * h, neckHalf, neckBaseY)
            }
        }
        lTo(neckHalf, 0f)
        close()
    }
    drawPath(body, bottleColor)
    // Кольцо-губа прямо у горлышка — читается как открытое горлышко.
    drawPath(
        rectPath(map, -neckHalf - 3f, -1f, neckHalf + 3f, 8f),
        Color(0xFF2E5B40)
    )
    // Этикетка
    val labelTop = neckBaseY + 0.09f * h
    drawPath(
        rectPath(map, -bodyHalf + 5f, labelTop, bodyHalf - 5f, labelTop + 0.13f * h),
        Color(0xFFF3E9D2)
    )
    drawLine(
        Color(0xFF8E1B2F),
        map(-bodyHalf + 9f, labelTop + 0.045f * h),
        map(bodyHalf - 9f, labelTop + 0.045f * h),
        strokeWidth = 2f
    )
    // Блик на стекле
    drawLine(
        highlight,
        map(-bodyHalf * 0.55f, neckBaseY + 0.02f * h),
        map(-bodyHalf * 0.55f, neckBaseY + 0.16f * h),
        strokeWidth = 4f, cap = StrokeCap.Round
    )
}

/** Прямоугольник в локальных координатах бутылки -> Path в мировых. */
private fun rectPath(
    map: (Float, Float) -> Offset,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): Path {
    return Path().apply {
        val a = map(left, top)
        val b = map(right, top)
        val c = map(right, bottom)
        val d = map(left, bottom)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
}

/**
 * Струя падает из текущего положения горлышка почти вертикально в вино.
 * По мере наполнения бокала укорачивается.
 */
private fun DrawScope.drawStream(
    scene: PourScene,
    progress: Float,
    wineTop: Float,
    mouth: Offset,
    alpha: Float,
    w: Float,
    h: Float
) {
    val from = Offset(mouth.x, mouth.y + 4f)
    val to = Offset(0.63f * w, wineTop + 3f)
    if (to.y - from.y < 4f) return
    val midX = (from.x + to.x) / 2f + scene.streamBend * w * 0.3f
    val midY = (from.y + to.y) / 2f
    val wobble = sin(progress * 12f * scene.streamWobble).toFloat() * 2f
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticBezierTo(midX + wobble, midY, to.x, to.y)
    }
    drawPath(path, scene.wine.copy(alpha = alpha.coerceIn(0f, 1f)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, cap = StrokeCap.Round))
    // Капля-утолщение в месте падения струи
    drawCircle(
        scene.wine.copy(alpha = alpha.coerceIn(0f, 1f) * 0.8f),
        radius = 4f,
        center = to
    )
}

/** Блик «бокал полон» в момент максимального уровня — перед «глотком». */
private fun DrawScope.drawGlint(scene: PourScene, progress: Float, w: Float, h: Float) {
    val peak = 0.70f + (scene.glintAt - 0.82f) * 0.25f
    val t = ((progress - peak) / 0.10f).coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f) return
    val alpha = sin(t * PI.toFloat())
    val cx = 0.64f * w
    drawCircle(
        Color.White.copy(alpha = 0.35f * alpha),
        radius = (0.02f + 0.05f * t) * w,
        center = Offset(cx - 0.05f * w, 0.30f * h)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScanProcessingPreviewSeed1() {
    com.wineapp.ui.theme.WineAppTheme {
        ScanProcessingContent(
            scene = deriveScene(123456789L),
            progress = 0.55f,
            stage = "Распознаём этикетку…",
            fact = "Цвет красного вина дают кожица винограда, а не мякоть"
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScanProcessingPreviewSeed2() {
    com.wineapp.ui.theme.WineAppTheme {
        ScanProcessingContent(
            scene = deriveScene(987654321L),
            progress = 0.8f,
            stage = "Подбираем вино…",
            fact = "Бокал держат за ножку, чтобы ладонь не нагревала вино"
        )
    }
}
