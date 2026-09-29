package com.wineapp.presentation.winepath

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wineapp.R
import com.wineapp.data.game.BadgeDef
import com.wineapp.domain.repository.BadgeRepository
import com.wineapp.domain.repository.WinePathReward
import com.wineapp.presentation.common.ui.AppIcons
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream200
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Очередь свежих наград «Винного пути» на уровне всего приложения: очки за новое вино
 * и бейджи. Выдаются при сохранении скана, а в этот момент сканер уже уводит
 * на карточку вина — поэтому показываем их поверх любого экрана.
 */
@HiltViewModel
class BadgeCelebrationViewModel @Inject constructor(
    badgeRepository: BadgeRepository
) : ViewModel() {

    private val queue = ArrayDeque<WinePathReward>()
    private val _current = MutableStateFlow<WinePathReward?>(null)
    val current: StateFlow<WinePathReward?> = _current.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                badgeRepository.freshRewards.collect { fresh ->
                    if (fresh.isEmpty()) return@collect
                    queue.addAll(fresh)
                    if (_current.value == null) {
                        // Даём завершиться переходу сканер → карточка вина.
                        delay(SHOW_DELAY_MS)
                        showNext()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BadgeCelebrationVM", "Fresh rewards failed", e)
            }
        }
    }

    /**
     * Скрыть текущее уведомление. Следующее из очереди (скан даёт очки и может дать
     * несколько наград) выезжает после паузы — чтобы уход предыдущего успел отыграть.
     */
    fun consume() {
        if (_current.value == null) return
        _current.value = null
        viewModelScope.launch {
            delay(NEXT_DELAY_MS)
            showNext()
        }
    }

    private fun showNext() {
        if (_current.value != null) return
        _current.value = queue.removeFirstOrNull()
    }

    private companion object {
        const val SHOW_DELAY_MS = 700L
        const val NEXT_DELAY_MS = 350L
    }
}

/**
 * Неблокирующий снекбар наград поверх приложения: выезжает сверху, сам уходит,
 * смахивается вверх, по тапу — «Винный путь». Очки за скан висят короче наград:
 * это рядовое событие, а за ним в очереди может ждать бейдж.
 * Размещается в корне после NavHost, чтобы рисоваться поверх экранов и нижнего бара.
 */
@Composable
fun BadgeCelebrationHost(onOpenWinePath: () -> Unit) {
    val viewModel: BadgeCelebrationViewModel = hiltViewModel()
    val reward by viewModel.current.collectAsState()
    // Держим последнее уведомление, чтобы карточка не опустела во время анимации ухода.
    var shown by remember { mutableStateOf<WinePathReward?>(null) }
    LaunchedEffect(reward) {
        val current = reward ?: return@LaunchedEffect
        shown = current
        delay(if (current is WinePathReward.ScanPoints) SCAN_POINTS_HIDE_MS else BADGE_HIDE_MS)
        viewModel.consume()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(
            visible = reward != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut()
        ) {
            val onClick = {
                viewModel.consume()
                onOpenWinePath()
            }
            when (val current = shown) {
                is WinePathReward.Badge -> BadgeSnackbar(
                    badge = current.badge.def,
                    onClick = onClick,
                    onSwipeAway = viewModel::consume
                )
                is WinePathReward.ScanPoints -> ScanPointsSnackbar(
                    reward = current,
                    onClick = onClick,
                    onSwipeAway = viewModel::consume
                )
                is WinePathReward.LevelUp -> LevelUpSnackbar(
                    reward = current,
                    onClick = onClick,
                    onSwipeAway = viewModel::consume
                )
                null -> Unit
            }
        }
    }
}

/** Бейдж: медаль его уровня из res, «Новая награда!», название и описание. */
@Composable
private fun BadgeSnackbar(
    badge: BadgeDef,
    onClick: () -> Unit,
    onSwipeAway: () -> Unit
) {
    val tier = MedalTier.forPoints(badge.points)
    RewardSnackbar(
        caption = stringResource(R.string.winepath_celebration_title),
        accent = tier.accent,
        accentSoft = tier.accentSoft,
        title = badge.title,
        subtitle = badge.description,
        points = badge.points,
        onClick = onClick,
        onSwipeAway = onSwipeAway
    ) {
        // Медаль награды из res — та же, что в сетке наград «Винного пути».
        Image(
            painter = painterResource(tier.iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tier.shadowColor,
                    spotColor = tier.shadowColor
                )
        )
    }
}

/** Очки за новое вино: бутылка в кремовом круге, «Новое вино», название вина. */
@Composable
private fun ScanPointsSnackbar(
    reward: WinePathReward.ScanPoints,
    onClick: () -> Unit,
    onSwipeAway: () -> Unit
) {
    RewardSnackbar(
        caption = stringResource(R.string.winepath_scan_points_title),
        accent = BrandBurgundy600,
        accentSoft = BrandBurgundy600.copy(alpha = 0.1f),
        title = reward.wineName,
        subtitle = stringResource(R.string.winepath_scan_points_hint),
        points = reward.points,
        onClick = onClick,
        onSwipeAway = onSwipeAway
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(BrandCream200)
        ) {
            Icon(
                AppIcons.WineBottle,
                contentDescription = null,
                tint = BrandBurgundy600,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Новый уровень: номер уровня в бордовом круге (как крупная цифра в карточке
 * уровня «Винного пути»), «Новый уровень!» и сколько осталось до следующего.
 */
@Composable
private fun LevelUpSnackbar(
    reward: WinePathReward.LevelUp,
    onClick: () -> Unit,
    onSwipeAway: () -> Unit
) {
    val subtitle = reward.pointsToNext?.let { left ->
        LocalContext.current.resources.getQuantityString(
            R.plurals.winepath_points_to_level, left, left, reward.level + 1
        )
    } ?: stringResource(R.string.winepath_level_max)
    RewardSnackbar(
        caption = stringResource(R.string.winepath_level_up_title),
        accent = BrandBurgundy600,
        accentSoft = BrandBurgundy600.copy(alpha = 0.1f),
        title = stringResource(R.string.winepath_level_up_name, reward.level),
        subtitle = subtitle,
        points = null,
        onClick = onClick,
        onSwipeAway = onSwipeAway
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = BrandBurgundy600,
                    spotColor = BrandBurgundy600
                )
                .clip(CircleShape)
                .background(BrandBurgundy600)
        ) {
            Text(
                text = reward.level.toString(),
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                color = BrandCream50
            )
        }
    }
}

/** Общая карточка уведомления: иконка, подпись в цвете акцента, заголовок, пояснение, «+N». */
@Composable
private fun RewardSnackbar(
    caption: String,
    accent: Color,
    accentSoft: Color,
    title: String,
    subtitle: String,
    /** «+N» справа; null — без чипа (новый уровень очков не даёт). */
    points: Int?,
    onClick: () -> Unit,
    onSwipeAway: () -> Unit,
    leading: @Composable () -> Unit
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = BrandCream50,
        border = BorderStroke(1.dp, BrandBorderDefault),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -SWIPE_THRESHOLD_PX) onSwipeAway() },
                    onVerticalDrag = { _, delta -> dragged += delta }
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            leading()
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = caption,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = accent
                )
                Text(
                    text = title,
                    fontFamily = Playfair,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    color = BrandTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = BrandTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (points != null) {
                    Text(
                        text = "+$points",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    AppIcons.ChevronRight,
                    contentDescription = stringResource(R.string.winepath_title),
                    tint = BrandTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private const val BADGE_HIDE_MS = 4_000L
private const val SCAN_POINTS_HIDE_MS = 2_500L
private const val SWIPE_THRESHOLD_PX = 40f

@Preview(showBackground = true)
@Composable
private fun BadgeSnackbarPreview() {
    WineAppTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            ScanPointsSnackbar(
                reward = WinePathReward.ScanPoints(10, "Шато Тамань Каберне"),
                onClick = {},
                onSwipeAway = {}
            )
            BadgeSnackbar(
                badge = BadgeDef("first_scan", "Первый глоток", "Отсканируйте первую этикетку", 10),
                onClick = {},
                onSwipeAway = {}
            )
            LevelUpSnackbar(
                reward = WinePathReward.LevelUp(level = 2, pointsToNext = 230),
                onClick = {},
                onSwipeAway = {}
            )
        }
    }
}
