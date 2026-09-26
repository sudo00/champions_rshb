package com.wineapp.presentation.detail

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.BadgeType
import com.wineapp.presentation.common.ui.ErrorMessage
import com.wineapp.presentation.common.ui.LoadingOverlay
import com.wineapp.presentation.common.ui.WineBadge
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandBurgundy700
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream300
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandDivider
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair

@Composable
fun DetailScreen(
    wineId: String,
    photoPath: String? = null,
    confidence: Float = 1.0f,
    recognitionStatus: String? = null,
    navController: NavHostController
) {
    val viewModel = hiltViewModel<DetailViewModel>()
    DetailScreenContent(
        viewModel = viewModel,
        wineId = wineId,
        photoPath = photoPath,
        confidence = confidence,
        recognitionStatus = recognitionStatus,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToWine = { id -> navController.navigate("detail/$id") },
        onNavigateToSommelier = { wine ->
            val uriWineName = android.net.Uri.encode(wine.name)
            val uriRegion = android.net.Uri.encode(wine.region ?: "")
            val uriVariety = android.net.Uri.encode(wine.variety ?: "")
            val uriStyle = android.net.Uri.encode(wine.style ?: "")
            val encodedPhoto = photoPath?.let { android.net.Uri.encode(it) } ?: ""
            navController.navigate(
                "sommelier/${wine.id}/$uriWineName/$uriRegion/$uriVariety/${wine.vintage ?: 0}/${wine.rating ?: 0f}/$uriStyle?photoPath=$encodedPhoto&confidence=$confidence"
            )
        }
    )
}

@Composable
fun DetailScreenContent(
    viewModel: DetailViewModel,
    wineId: String,
    photoPath: String? = null,
    confidence: Float = 1.0f,
    recognitionStatus: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToWine: (String) -> Unit = {},
    onNavigateToSommelier: (Wine) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val candidatesUnverifiedStr = stringResource(R.string.scan_status_candidates_unverified)

    LaunchedEffect(recognitionStatus) {
        if (recognitionStatus == "candidates_unverified") {
            snackbarHostState.showSnackbar(
                candidatesUnverifiedStr,
                duration = SnackbarDuration.Short
            )
        }
    }

    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = Color.Black.copy(alpha = 0.5f).toArgb()
            window.navigationBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    LaunchedEffect(wineId) {
        viewModel.sendIntent(DetailIntent.LoadDetail(wineId, photoPath, confidence, recognitionStatus))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is DetailState.Loading -> LoadingOverlay()
            is DetailState.Error -> ErrorMessage(
                message = current.message,
                onRetry = { viewModel.sendIntent(DetailIntent.Retry) }
            )
            is DetailState.Success -> DetailContent(
                state = current,
                onNavigateBack = onNavigateBack,
                onNavigateToWine = onNavigateToWine,
                onNavigateToSommelier = onNavigateToSommelier,
                onToggleFavorite = { viewModel.sendIntent(DetailIntent.ToggleFavorite) },
                onToggleWish = { viewModel.sendIntent(DetailIntent.ToggleWish) },
                onToggleCellar = { viewModel.sendIntent(DetailIntent.ToggleCellar) }
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContent(
    state: DetailState.Success,
    onNavigateBack: () -> Unit = {},
    onNavigateToWine: (String) -> Unit = {},
    onNavigateToSommelier: (Wine) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleWish: () -> Unit = {},
    onToggleCellar: () -> Unit = {}
) {
    val wine = state.wine
    val scaffoldState = rememberBottomSheetScaffoldState()
    // Шит в покое встаёт ровно под hero (519dp): угловые кнопки всегда видны.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val peekHeight = (screenHeight - 519.dp).coerceAtLeast(200.dp)
    // Непрерывный коллапс из смещения шита: фото и кнопки гаснут по мере
    // скролла, а не по порогу. offset: peek (покой) -> ~0 (раскрыт).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val peekPx = with(density) { peekHeight.toPx() }
    val sheetOffset = try {
        scaffoldState.bottomSheetState.requireOffset()
    } catch (e: Exception) {
        peekPx
    }
    val collapse = (1f - sheetOffset / peekPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetShape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        sheetContainerColor = Color.White,
        sheetDragHandle = null,
        sheetContent = {
            DetailSheetContent(
                state = state,
                onToggleCellar = onToggleCellar,
                onFilterClick = { },
                onSimilarClick = onNavigateToWine
            )
        },
        containerColor = BrandCream300
    ) {
        DetailHero(
            wine = wine,
            collapse = collapse,
            isFavorite = state.isFavorite,
            isWished = state.isWished,
            onBackClick = onNavigateBack,
            onFavoriteClick = onToggleFavorite,
            onSommelierClick = { onNavigateToSommelier(wine) },
            onWishClick = onToggleWish
        )
    }
}

/**
 * Цвет эллипса hero по категории вина (поле style: «Красное сухое» и т.п.).
 * Только одно поле — сорт/название больше не участвуют и не перетягивают
 * (розе из гренаша уходило в красное). Дефолт — бургунди.
 */
private fun wineEllipseColor(wine: Wine): Color {
    val text = (wine.style ?: "").lowercase()
    return when {
        text.contains("розов") || text.contains("rose") || text.contains("rosé") ->
            Color(0xFFE3A68F)
        text.contains("бел") || text.contains("white") ||
            text.contains("blanc") || text.contains("bianco") ->
            Color(0xFFEAD9AE)
        else -> BrandBurgundy700
    }
}

@Composable
private fun DetailHero(
    wine: Wine,
    collapse: Float,
    isFavorite: Boolean,
    isWished: Boolean,
    onBackClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSommelierClick: () -> Unit,
    onWishClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(519.dp)
            .clip(RoundedCornerShape(bottomEnd = 44.dp))
            .background(BrandCream300)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.wine_details_ellipse),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            colorFilter = ColorFilter.tint(
                wineEllipseColor(wine),
                androidx.compose.ui.graphics.BlendMode.SrcIn
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 56.dp)
        ) {
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.3f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = BrandCream50,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = wine.winery.orEmpty(),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandCream50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(20.dp)
                )
            }
            Surface(
                onClick = onFavoriteClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.3f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = BrandCream50,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f - collapse)
                .padding(bottom = 23.dp)
        ) {
            if (wine.imageUrl != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(com.wineapp.util.apiImageUrl(wine.imageUrl))
                        .crossfade(true)
                        .build(),
                    contentDescription = wine.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 316.dp, height = 380.dp)
                )
            } else {
                Icon(
                    Icons.Default.WineBar,
                    contentDescription = null,
                    tint = BrandCream50.copy(alpha = 0.5f),
                    modifier = Modifier.size(120.dp)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .alpha(1f - collapse)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Звезда — вход в сомелье.
            Surface(
                onClick = onSommelierClick,
                shape = CircleShape,
                color = BrandBurgundy600,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.detail_ask_sommelier),
                        tint = BrandCream50,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            // Галочка — «Хочу попробовать».
            Surface(
                onClick = onWishClick,
                shape = CircleShape,
                color = if (isWished) BrandCream50 else BrandBurgundy600,
                border = if (isWished) BorderStroke(2.dp, BrandBurgundy600) else null,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isWished) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(R.string.detail_wish),
                        tint = if (isWished) BrandBurgundy600 else BrandCream50,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun DetailSheetContent(
    state: DetailState.Success,
    onToggleCellar: () -> Unit,
    onFilterClick: () -> Unit,
    onSimilarClick: (String) -> Unit
) {
    val wine = state.wine
    // Шит останавливается под строкой верхних кнопок hero, дальше скролл внутри.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = screenHeight - 100.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp)
            .padding(bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFF79747E))
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.recognitionStatus in setOf("user_confirmed", "score_confirmed", "legacy")) {
                    WineBadge(
                        type = BadgeType.SUCCESS,
                        text = stringResource(R.string.detail_success_badge)
                    )
                }
                // Нейтральный бейдж показывает год урожая вместо «Инфо».
                wine.vintage?.let {
                    WineBadge(
                        type = BadgeType.NEUTRAL,
                        text = it.toString()
                    )
                }
            }
            Text(
                text = wine.name,
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                color = BrandTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column {
            wine.variety?.let { DetailSpecRow(label = stringResource(R.string.detail_variety), value = it) }
            wine.style?.let { DetailSpecRow(label = stringResource(R.string.detail_category), value = it) }
            wine.alcoholPercentage?.let {
                DetailSpecRow(
                    label = stringResource(R.string.detail_alcohol),
                    value = "${String.format("%.0f", it)}%"
                )
            }
            listOfNotNull(wine.region, wine.country).joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                DetailSpecRow(label = stringResource(R.string.detail_region), value = it)
            }
            wine.price?.let {
                DetailSpecRow(
                    label = stringResource(R.string.detail_price),
                    value = "${wine.currency ?: "$"} ${String.format("%.2f", it)}"
                )
            }
            if (wine.rating != null) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.detail_rating_title),
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = BrandTextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format("%.2f", wine.rating),
                            fontFamily = Playfair,
                            fontWeight = FontWeight.Medium,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            color = BrandTextPrimary
                        )
                        wine.reviewsCount?.let {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($it)",
                                fontFamily = Playfair,
                                fontWeight = FontWeight.Medium,
                                fontSize = 24.sp,
                                lineHeight = 30.sp,
                                color = BrandTextSecondary
                            )
                        }
                    }
                }
                HorizontalDivider(color = BrandDivider)
            }
        }
        wine.description?.let { DetailDescriptionBlock(text = it) }
        wine.foodPairing.takeIf { it.isNotEmpty() }?.let { pairings ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.detail_pairing),
                    fontFamily = Playfair,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    color = BrandTextPrimary
                )
                pairings.forEach { pairing ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandCream100
                    ) {
                        Text(
                            pairing,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = BrandTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        // Погреб: CTA-кнопка (места в hero не хватило — живёт в шите).
        Surface(
            onClick = onToggleCellar,
            shape = RoundedCornerShape(percent = 50),
            color = if (state.isInCellar) BrandBurgundy600 else Color.Transparent,
            border = if (state.isInCellar) null else BorderStroke(1.dp, BrandBurgundy600),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 14.dp)
            ) {
                Text(
                    text = if (state.isInCellar) {
                        stringResource(R.string.detail_cellar_added, state.cellarQuantity)
                    } else {
                        stringResource(R.string.detail_cellar)
                    },
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = if (state.isInCellar) BrandCream50 else BrandBurgundy600
                )
            }
        }
        DetailRateBlock()
        if (state.similar.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.detail_similar),
                        fontFamily = Playfair,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        color = BrandTextPrimary
                    )
                    Surface(onClick = onFilterClick, color = Color.Transparent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = BrandBurgundy600,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.detail_filter),
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                color = BrandBurgundy600
                            )
                        }
                    }
                }
                state.similar.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { item ->
                            DetailSimilarCard(
                                wine = item,
                                onClick = { onSimilarClick(item.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSpecRow(label: String, value: String) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = BrandTextSecondary
            )
            Text(
                text = value,
                fontFamily = Playfair,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                color = BrandTextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = BrandDivider)
    }
}

@Composable
private fun DetailDescriptionBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = text,
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = BrandTextSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.detail_less else R.string.detail_more
                    ),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandBurgundy600
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = BrandBurgundy600,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRateBlock() {
    var rating by remember { mutableIntStateOf(0) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { index ->
                Surface(
                    onClick = { rating = index },
                    shape = CircleShape,
                    color = BrandBurgundy600,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = BrandCream50.copy(alpha = if (rating >= index) 1f else 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.detail_rate_wine),
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = BrandTextSecondary
        )
    }
}

@Composable
private fun DetailSimilarCard(
    wine: Wine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier
    ) {
        Box {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (wine.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = BrandCream500,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.2f", wine.rating),
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = BrandTextPrimary
                        )
                        wine.reviewsCount?.let {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($it)",
                                fontFamily = Inter,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = BrandTextSecondary
                            )
                        }
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    if (wine.imageUrl != null) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(com.wineapp.util.apiImageUrl(wine.imageUrl))
                                .crossfade(true)
                                .build(),
                            contentDescription = wine.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.WineBar,
                            contentDescription = null,
                            tint = BrandTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = wine.name,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        color = BrandTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = wine.winery.orEmpty(),
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun DetailScreenNewPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wine = MockDataProvider.wines.first()
        DetailContent(
            state = DetailState.Success(
                wine = wine,
                similar = MockDataProvider.wines.drop(1).take(4),
                recognitionStatus = "score_confirmed",
                isFavorite = true,
                isWished = true,
                isInCellar = true,
                cellarQuantity = 2
            )
        )
    }
}
