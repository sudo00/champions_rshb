package com.wineapp.presentation.mywines

import com.wineapp.presentation.common.ui.BrandLoader
import com.wineapp.presentation.common.ui.AppIcons
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.presentation.common.ui.BrandSecondaryButton
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.ui.theme.BrandBorderDefault
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme

/**
 * «Мои вина» из фигмы: H1, три пилюли-перехода на разделы, секции
 * «Избранное» / «Коллекция» / «Сканы» с последними винами. Поиска нет.
 */
@Composable
fun MyWinesScreen(
    onNavigateToCellar: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSavedScans: () -> Unit = {},
    onNavigateToRoulette: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val viewModel: MyWinesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    MyWinesContent(
        state = state,
        onNavigateToCellar = onNavigateToCellar,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToSavedScans = onNavigateToSavedScans,
        onNavigateToRoulette = onNavigateToRoulette,
        onNavigateToDetail = onNavigateToDetail,
        onToggleFavorite = { wineId -> viewModel.sendIntent(MyWinesIntent.ToggleFavorite(wineId)) }
    )
}

@Composable
fun MyWinesContent(
    state: MyWinesState,
    onNavigateToCellar: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSavedScans: () -> Unit = {},
    onNavigateToRoulette: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {}
) {
    TransparentSystemBars()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.search_tab_ellipse),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
        Text(
            text = stringResource(R.string.nav_mywines),
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            color = BrandTextPrimary,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                BrandLoader()
            }
        } else {
            // Секции видны всегда: у пустой вместо вин — плашка-подсказка,
            // кнопка-переход на раздел лежит под секцией в любом случае.
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Рулетка всегда доступна: пул берётся из коллекции и избранного.
                BrandButton(
                    text = stringResource(R.string.mywines_roulette),
                    onClick = onNavigateToRoulette,
                    leadingIcon = AppIcons.Play
                )
                MyWinesSection(
                    title = stringResource(R.string.mywines_favorites),
                    subtitle = stringResource(R.string.mywines_favorites_sub),
                    wines = state.favorites,
                    emptyIcon = AppIcons.Heart,
                    emptyHint = stringResource(R.string.favorites_empty_hint),
                    likedIds = state.likedIds,
                    onWineClick = onNavigateToDetail,
                    onToggleFavorite = onToggleFavorite
                )
                BrandSecondaryButton(
                    text = stringResource(R.string.mywines_favorites),
                    onClick = onNavigateToFavorites
                )
                MyWinesSection(
                    title = stringResource(R.string.mywines_collection),
                    subtitle = stringResource(R.string.mywines_collection_sub),
                    wines = state.collection,
                    emptyIcon = AppIcons.WineBottle,
                    emptyHint = stringResource(R.string.cellar_empty_hint),
                    likedIds = state.likedIds,
                    onWineClick = onNavigateToDetail,
                    onToggleFavorite = onToggleFavorite
                )
                BrandSecondaryButton(
                    text = stringResource(R.string.mywines_collection),
                    onClick = onNavigateToCellar
                )
                MyWinesSection(
                    title = stringResource(R.string.mywines_scans),
                    subtitle = stringResource(R.string.mywines_scans_sub),
                    wines = state.scans.map { it.wine },
                    userPhotos = state.scans.map { it.labelPhotoPath },
                    emptyIcon = AppIcons.Camera,
                    emptyHint = stringResource(R.string.mywines_scans_empty_hint),
                    likedIds = state.likedIds,
                    onWineClick = onNavigateToDetail,
                    onToggleFavorite = onToggleFavorite
                )
                BrandSecondaryButton(
                    text = stringResource(R.string.mywines_scans),
                    onClick = onNavigateToSavedScans
                )
            }
        }
        // Место под висящий поверх нижний бар.
        Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun MyWinesSection(
    title: String,
    subtitle: String,
    wines: List<Wine>,
    emptyIcon: ImageVector,
    emptyHint: String,
    likedIds: Set<String>,
    onWineClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    /** Для секции сканов: фото пользователя по индексу вина вместо фото из каталога. */
    userPhotos: List<String?>? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontFamily = Playfair,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            color = BrandTextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = subtitle,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = BrandTextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
        if (wines.isEmpty()) {
            MyWinesEmptySection(icon = emptyIcon, hint = emptyHint)
        } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            wines.forEachIndexed { index, wine ->
                MyWinesScanCard(
                    wine = wine,
                    showUserPhoto = userPhotos != null,
                    userPhotoPath = userPhotos?.getOrNull(index),
                    isFavorite = wine.id in likedIds,
                    onClick = { onWineClick(wine.id) },
                    onFavoriteClick = { onToggleFavorite(wine.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Плашка пустой секции: в стиле scan-карточки (Cream100, r20), но с пунктирной
 * рамкой — место под будущие вина. Иконка раздела + «пока пусто» + как наполнить.
 */
@Composable
private fun MyWinesEmptySection(icon: ImageVector, hint: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BrandCream100)
            .dashedBorder(color = BrandBorderDefault, cornerRadius = 20.dp)
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BrandCream50)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = BrandBurgundy600,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.mywines_section_empty),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = BrandTextPrimary
            )
            Text(
                text = hint,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = BrandTextSecondary
            )
        }
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val strokeWidth = 1.5.dp.toPx()
    val inset = strokeWidth / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        )
    )
}

/**
 * Горизонтальная scan-карточка из макета: Cream100 r20, фото 80x120,
 * винодельня, название, регион + рейтинг, сердечко справа сверху.
 */
@Composable
private fun MyWinesScanCard(
    wine: Wine,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    showUserPhoto: Boolean = false,
    userPhotoPath: String? = null
) {
    val userPhoto = remember(userPhotoPath) {
        userPhotoPath?.let { java.io.File(it) }?.takeIf { it.exists() }
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier.width(280.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp)
            ) {
                if (showUserPhoto && userPhoto != null) {
                    // Скан: снимок этикетки, сделанный пользователем.
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(userPhoto)
                            .crossfade(true)
                            .build(),
                        contentDescription = wine.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandBorderLight)
                    )
                } else if (!showUserPhoto && wine.imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.wineapp.util.apiImageUrl(wine.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = wine.name,
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BrandBorderLight)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = BrandBurgundy600,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        error = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BrandBorderLight)
                            ) {
                                Icon(
                                    AppIcons.WineBottle,
                                    contentDescription = null,
                                    tint = BrandTextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandBorderLight)
                    ) {
                        Icon(
                            AppIcons.WineBottle,
                            contentDescription = null,
                            tint = BrandTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = wine.winery.orEmpty(),
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = wine.name,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        color = BrandTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val regionText = listOfNotNull(wine.region, wine.country)
                            .joinToString(", ")
                        if (regionText.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.wineapp.presentation.common.ui.CountryFlag()
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = regionText,
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
                        if (wine.rating != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Star,
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
                    }
                }
            }
            Surface(
                onClick = onFavoriteClick,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isFavorite) AppIcons.HeartFilled else AppIcons.Heart,
                        contentDescription = null,
                        tint = if (isFavorite) BrandBurgundy600 else BrandTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun MyWinesPreview() {
    WineAppTheme {
        val wines = MockDataProvider.wines
        MyWinesContent(
            state = MyWinesState(
                favorites = wines.take(3),
                collection = wines.take(3),
                scans = wines.take(3).mapIndexed { i, wine ->
                    SavedScan(
                        id = "scan-$i", wine = wine, labelPhotoPath = null, confidence = 0.9f,
                        conversation = emptyList(), scannedAt = 0L
                    )
                },
                likedIds = setOf(wines.first().id),
                isLoading = false
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun MyWinesEmptyPreview() {
    WineAppTheme {
        MyWinesContent(state = MyWinesState(isLoading = false))
    }
}
