package com.wineapp.presentation.roulette

import com.wineapp.presentation.common.ui.AppIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.RouletteSource
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.CountryFlag
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandBurgundy700
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandOutline
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme

/** Высота бордовой полосы под статус-баром — из макета. */
private val PanelTopInset = 56.dp
private val PanelShape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)
private val PanelBrush = Brush.verticalGradient(
    // Градиент ослаблен против макера: на полностью прозрачном низе
    // бутылки на светлом фоне не читаются.
    colors = listOf(Color(0xE6FFFFFF), Color(0xE6FFFFFF), Color(0x99FFFFFF))
)

@Composable
fun WineRouletteScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: WineRouletteViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    var roll by remember { mutableStateOf<WineRouletteEffect.Roll?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WineRouletteEffect.Roll -> roll = effect
            }
        }
    }

    WineRouletteContent(
        state = state,
        roll = roll,
        onClose = onNavigateBack,
        onSourceChange = {
            roll = null
            viewModel.sendIntent(WineRouletteIntent.SetSource(it))
        },
        onSpin = {
            roll = null
            viewModel.sendIntent(WineRouletteIntent.Spin)
        },
        onSettled = { wine ->
            roll = null
            viewModel.sendIntent(WineRouletteIntent.Settle(wine))
        },
        onOpenDetail = onNavigateToDetail,
        onToggleFavorite = { viewModel.sendIntent(WineRouletteIntent.ToggleFavorite(it)) }
    )
}

@Composable
fun WineRouletteContent(
    state: WineRouletteState,
    roll: WineRouletteEffect.Roll? = null,
    onClose: () -> Unit = {},
    onSourceChange: (RouletteSource) -> Unit = {},
    onSpin: () -> Unit = {},
    onSettled: (Wine) -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Бордовая полоса сверху уходит под статус-бар, поэтому иконки светлые.
    TransparentSystemBars(isAppearanceLightStatusBars = false)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandBurgundy700)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = PanelTopInset)
                .background(PanelBrush, PanelShape)
        ) {
            RouletteDragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))

            when (state) {
                is WineRouletteState.Loading -> Column(modifier = Modifier.fillMaxSize()) {
                    RouletteCloseButton(
                        onClose = onClose,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 36.dp, top = 12.dp)
                    )
                    LoadingBody()
                }
                is WineRouletteState.Error -> Column(modifier = Modifier.fillMaxSize()) {
                    RouletteCloseButton(
                        onClose = onClose,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 36.dp, top = 12.dp)
                    )
                    val message =
                        if (state.message.isBlank()) stringResource(R.string.roulette_error)
                        else state.message
                    ErrorMessage(message = message, modifier = Modifier.padding(top = 32.dp))
                }
                is WineRouletteState.Success -> RouletteBody(
                    state = state,
                    roll = roll,
                    onClose = onClose,
                    onSourceChange = onSourceChange,
                    onSpin = onSpin,
                    onSettled = onSettled,
                    onOpenDetail = onOpenDetail,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun RouletteBody(
    state: WineRouletteState.Success,
    roll: WineRouletteEffect.Roll?,
    onClose: () -> Unit,
    onSourceChange: (RouletteSource) -> Unit,
    onSpin: () -> Unit,
    onSettled: (Wine) -> Unit,
    onOpenDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RouletteHeader(
            title = stringResource(R.string.roulette_title),
            onClose = onClose,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Text(
            text = stringResource(R.string.roulette_subtitle),
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = BrandTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        RouletteSourceTabs(
            source = state.source,
            onSourceChange = onSourceChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedVisibility(
            visible = state.result != null,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            RouletteResultCard(
                wine = state.result,
                isFavorite = state.result?.let { state.likedIds.contains(it.id) } == true,
                onClick = { state.result?.let { onOpenDetail(it.id) } },
                onToggleFavorite = { state.result?.let { onToggleFavorite(it.id) } }
            )
        }
        if (state.result == null) {
            RouletteHintCard(
                poolIsEmpty = state.pool.isEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Барабан прижат к низу экрана и уходит под системные кнопки,
        // а «Крутить» лежит поверх него отдельным слоем.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            WineRouletteWheel(
                pool = state.pool,
                roll = roll,
                canSpin = state.canSpin,
                onSpin = onSpin,
                onSettled = onSettled,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
            RouletteSpinFooter(
                enabled = state.canSpin,
                onSpin = onSpin,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun RouletteDragHandle(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .size(width = 32.dp, height = 4.dp)
            .background(BrandOutline, RoundedCornerShape(percent = 50))
    )
}

@Composable
private fun RouletteHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            color = BrandTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(16.dp))
        RouletteCloseButton(onClose = onClose)
    }
}

@Composable
private fun RouletteCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClose,
        shape = CircleShape,
        color = Color(0x4D000000),
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.roulette_close),
                tint = BrandCream50,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RouletteSourceTabs(
    source: RouletteSource,
    onSourceChange: (RouletteSource) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        RouletteTab(
            label = stringResource(R.string.roulette_tab_collection),
            selected = source == RouletteSource.COLLECTION,
            onClick = { onSourceChange(RouletteSource.COLLECTION) }
        )
        RouletteTab(
            label = stringResource(R.string.roulette_tab_favorites),
            selected = source == RouletteSource.FAVORITES,
            onClick = { onSourceChange(RouletteSource.FAVORITES) }
        )
    }
}

@Composable
private fun RouletteTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) BrandBurgundy600 else BrandCream100,
        border = if (selected) null else BorderStroke(1.dp, BrandBorderDefault),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = label,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = if (selected) BrandCream50 else BrandBurgundy600
            )
        }
    }
}

@Composable
private fun RouletteHintCard(
    poolIsEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier
            .fillMaxWidth()
            .height(202.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            if (poolIsEmpty) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.roulette_empty),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        color = BrandTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.roulette_empty_hint),
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = BrandTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.roulette_hint),
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = BrandTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RouletteResultCard(
    wine: Wine?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = wine != null,
        shape = RoundedCornerShape(28.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier
            .fillMaxWidth()
            .height(202.dp)
    ) {
        val current = wine ?: return@Surface
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                RouletteBottleImage(
                    wine = current,
                    modifier = Modifier.size(width = 80.dp, height = 160.dp)
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    current.winery?.let {
                        Text(
                            text = it,
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = BrandTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = current.name,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        color = BrandTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountryFlag(country = current.country, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = listOfNotNull(current.region, current.vintage?.toString())
                                .joinToString(", "),
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = BrandTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.Star,
                            contentDescription = null,
                            tint = BrandCream500,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = current.rating?.let { "%.2f".format(it) } ?: "—",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = BrandTextPrimary
                        )
                        current.reviewsCount?.let { count ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "($count)",
                                fontFamily = Inter,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = BrandTextSecondary
                            )
                        }
                    }
                }
            }
            FavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            )
        }
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) AppIcons.HeartFilled else AppIcons.Heart,
                    contentDescription = null,
                    tint = if (isFavorite) BrandBurgundy600 else BrandTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
        }
    }
}

@Composable
private fun RouletteSpinFooter(
    enabled: Boolean,
    onSpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Surface(
            onClick = onSpin,
            enabled = enabled,
            shape = RoundedCornerShape(percent = 50),
            color = Color.Transparent,
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Rotate,
                    contentDescription = null,
                    tint = BrandBurgundy600,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.roulette_spin),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandBurgundy600
                )
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(color = BrandBurgundy600)
    }
}

@Preview(showBackground = true)
@Composable
private fun WineRouletteOneWinePreview() {
    val wine = MockDataProvider.wines.first()
    WineAppTheme {
        WineRouletteContent(
            state = WineRouletteState.Success(
                source = RouletteSource.COLLECTION,
                pool = listOf(wine)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WineRouletteFavoritesPreview() {
    val wines = MockDataProvider.wines.take(3)
    WineAppTheme {
        WineRouletteContent(
            state = WineRouletteState.Success(
                source = RouletteSource.FAVORITES,
                pool = wines,
                likedIds = setOf(wines.first().id)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WineRouletteManyWinesPreview() {
    // Больше 17 вин: сектора пересобираются на ходу, пул не заканчивается.
    val wines = List(20) { index ->
        MockDataProvider.wines[index % MockDataProvider.wines.size].copy(id = "pool-$index")
    }
    WineAppTheme {
        WineRouletteContent(
            state = WineRouletteState.Success(
                source = RouletteSource.COLLECTION,
                pool = wines
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WineRouletteEmptyPreview() {
    WineAppTheme {
        WineRouletteContent(
            state = WineRouletteState.Success(
                source = RouletteSource.COLLECTION,
                pool = emptyList()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WineRouletteResultPreview() {
    val wines = MockDataProvider.wines
    WineAppTheme {
        WineRouletteContent(
            state = WineRouletteState.Success(
                source = RouletteSource.COLLECTION,
                pool = wines,
                result = wines.first(),
                likedIds = setOf(wines.first().id)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WineRouletteErrorPreview() {
    WineAppTheme {
        WineRouletteContent(state = WineRouletteState.Error(""))
    }
}
