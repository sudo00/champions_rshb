package com.wineapp.presentation.cellar

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wineapp.R
import com.wineapp.domain.model.CellarItem
import com.wineapp.util.HapticHelper
import kotlinx.coroutines.delay

private const val STRIP_SIZE = 36
private const val TARGET_INDEX = 30
private const val SPIN_MS = 4500
private val CELL_WIDTH = 150.dp
private val GOLD = Color(0xFFD9A441)
private val GOLD_BRIGHT = Color(0xFFFFD700)
private val WINE_RED = Color(0xFF8E1B2F)
private val DIALOG_BG = Color(0xFF171114)
private val WINDOW_BG = Color(0xFF0E0A0C)

private data class ConfettiParticle(
    val angle: Float,
    val power: Float,
    val size: Float,
    val color: Color,
    val delay: Float
)

/**
 * «Кейс-рулетка»: барабан с винами из наличия крутится с затуханием
 * и останавливается на случайном победителе. Чисто процедурная анимация,
 * без ассетов: LazyRow + animateScrollToItem с EaseOutExpo, тики вибрации,
 * глиттер-частицы на Canvas.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DrinkRouletteDialog(
    items: List<CellarItem>,
    onDismiss: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    var rollId by remember { mutableIntStateOf(0) }
    var prevWinnerId by remember { mutableStateOf<String?>(null) }
    val winner = remember(rollId, items) {
        val pool = if (items.size > 1) {
            items.filter { it.wine.id != prevWinnerId }.ifEmpty { items }
        } else items
        pool.random()
    }
    val strip = remember(rollId, items, winner) {
        List(TARGET_INDEX) { items.random() } + winner +
                List(STRIP_SIZE - TARGET_INDEX - 1) { items.random() }
    }
    val listState = rememberLazyListState()
    var spinning by remember(rollId) { mutableStateOf(true) }
    var revealed by remember(rollId) { mutableStateOf(false) }
    var lastTick by remember(rollId) { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val confetti = remember(rollId) { Animatable(0f) }
    val particles = remember(rollId) {
        val colors = listOf(GOLD, GOLD_BRIGHT, WINE_RED, Color.White)
        List(90) { i ->
            ConfettiParticle(
                angle = ((i * 137.508f) % 360f) * (Math.PI.toFloat() / 180f),
                power = 0.35f + ((i * 37) % 10) / 10f * 0.65f,
                size = 3f + ((i * 29) % 10) / 10f * 5f,
                color = colors[i % colors.size],
                delay = ((i * 17) % 10) / 10f * 0.25f
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DIALOG_BG),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                BoxWithConstraints {
                    val density = LocalDensity.current
                    // Отрицательный offset центрирует ячейку-победителя под указателем.
                    val centerPx = with(density) { -((maxWidth - CELL_WIDTH) / 2).roundToPx() }
                    val stepPx = with(density) { (CELL_WIDTH + 8.dp).toPx() }
                    // Полный путь барабана: до победителя с центровочным сдвигом.
                    val totalDist = TARGET_INDEX * stepPx + centerPx

                    LaunchedEffect(rollId, centerPx) {
                        prevWinnerId = winner.wine.id
                        spinning = true
                        revealed = false
                        listState.scrollToItem(0)
                        delay(350)
                        // Покадровая прокрутка с EaseOutExpo: быстро вначале, тянется в конце.
                        val startNanos = System.nanoTime()
                        while (true) {
                            val f =
                                ((System.nanoTime() - startNanos) / 1_000_000f / SPIN_MS).coerceIn(
                                    0f,
                                    1f
                                )
                            val dist = EaseOutExpo.transform(f) * totalDist
                            val idx = (dist / stepPx).toInt().coerceIn(0, TARGET_INDEX)
                            listState.scrollToItem(idx, (dist - idx * stepPx).toInt())
                            if (f >= 1f) break
                            withFrameNanos { }
                        }
                        listState.scrollToItem(TARGET_INDEX, centerPx)
                        delay(350)
                        spinning = false
                        HapticHelper.vibrateSuccess(context)
                        revealed = true
                    }
                    LaunchedEffect(revealed) {
                        if (revealed) confetti.animateTo(1f, tween(2200))
                    }

                    // Тик на каждую пролетающую ячейку (с троттлингом).
                    val tickIdx = listState.firstVisibleItemIndex
                    LaunchedEffect(tickIdx) {
                        if (spinning) {
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 70) {
                                lastTick = now
                                HapticHelper.vibrateTick(context)
                            }
                        }
                    }

                    Box {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.cellar_drink_today),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.cancel),
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            RouletteWindow(
                                strip = strip,
                                winner = winner,
                                revealed = revealed,
                                listState = listState
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AnimatedVisibility(
                                visible = revealed,
                                enter = fadeIn() + scaleIn(initialScale = 0.9f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.cellar_roulette_your_pick),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = GOLD
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        winner.wine.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = listOfNotNull(
                                        winner.wine.vintage?.toString(),
                                        winner.wine.region
                                    ).joinToString(" · ")
                                    if (subtitle.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { onOpenDetail(winner.wine.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GOLD,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text(
                                            stringResource(R.string.cellar_roulette_open),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(onClick = { rollId++ }) {
                                        Text(
                                            stringResource(R.string.cellar_roulette_reroll),
                                            color = GOLD
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Глиттер на весь экран после победы.
            if (revealed) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val progress = confetti.value
                    if (progress <= 0f || progress >= 1f) return@Canvas
                    val alpha = (1f - progress).coerceIn(0f, 1f)
                    // Радиальный взрыв из центра: быстро вначале, затухание к концу.
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxDist = size.minDimension * 0.75f
                    particles.forEach { p ->
                        val local = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                        if (local <= 0f) return@forEach
                        val dist = (1f - (1f - local) * (1f - local)) * maxDist * p.power
                        val x = cx + kotlin.math.cos(p.angle) * dist
                        val y = cy + kotlin.math.sin(p.angle) * dist
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = p.size * (size.width / 400f),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun RouletteWindow(
    strip: List<CellarItem>,
    winner: CellarItem,
    revealed: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(WINDOW_BG)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = false
        ) {
            items(strip.size) { index ->
                val cell = strip[index]
                RouletteCell(
                    item = cell,
                    highlighted = revealed && cell.wine.id == winner.wine.id
                )
            }
        }
        // Затемнения по краям для глубины барабана.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(44.dp)
                .height(168.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(WINDOW_BG, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(44.dp)
                .height(168.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, WINDOW_BG)
                    )
                )
        )
        // Указатель по центру.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(3.dp)
                .height(168.dp)
                .background(GOLD_BRIGHT)
        )
    }
}

@Composable
private fun RouletteCell(item: CellarItem, highlighted: Boolean) {
    Card(
        modifier = Modifier
            .width(CELL_WIDTH)
            .height(140.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (highlighted) BorderStroke(2.dp, GOLD_BRIGHT) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFF3A2A12) else Color(0xFF221A1E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                item.wine.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val subtitle = listOfNotNull(
                item.wine.vintage?.toString(),
                item.wine.region
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "× ${item.quantity}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GOLD
            )
        }
    }
}
