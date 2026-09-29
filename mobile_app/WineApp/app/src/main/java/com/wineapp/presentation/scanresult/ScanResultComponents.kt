package com.wineapp.presentation.scanresult

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.BadgeType
import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.presentation.common.ui.WineBadge
import com.wineapp.presentation.common.ui.WineCard
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream200
import com.wineapp.ui.theme.BrandCream400
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme
import kotlin.math.roundToInt

/**
 * Общие блоки экранов результата скана (ScanSummaryScreen и ScanResultScreen)
 * в фирменном стиле: Cream-фон, Playfair-заголовки, бордовые CTA.
 */

/** Шапка: бордовая круглая «назад» слева, пилюля «Новое фото» справа. */
@Composable
fun ScanResultTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNewPhoto: (() -> Unit)? = null,
    newPhotoEnabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = BrandBurgundy600,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppIcons.ChevronLeft,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (onNewPhoto != null) {
            Surface(
                onClick = { if (newPhotoEnabled) onNewPhoto() },
                shape = RoundedCornerShape(percent = 50),
                color = BrandCream100,
                border = BorderStroke(1.dp, BrandCream400),
                modifier = Modifier.height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        AppIcons.Camera,
                        contentDescription = null,
                        tint = if (newPhotoEnabled) BrandTextPrimary else BrandTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.scan_result_new_photo),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = if (newPhotoEnabled) BrandTextPrimary else BrandTextSecondary
                    )
                }
            }
        }
    }
}

/** Заголовок результата + подзаголовок, справа миниатюра снимка пользователя. */
@Composable
fun ScanResultIntro(
    title: String,
    subtitle: String?,
    photoPath: String?,
    modifier: Modifier = Modifier,
    badges: @Composable () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = Playfair,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    color = BrandTextPrimary
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = BrandTextSecondary
                    )
                }
            }
            if (!photoPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.scan_result_your_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 72.dp, height = 96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BrandCream200)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { badges() }
    }
}

/** Тип плашки по скору: уверенное совпадение — зелёная, пограничное — жёлтая. */
fun matchBadgeType(score: Float): BadgeType = when {
    score >= 0.8f -> BadgeType.SUCCESS
    score > 0.5f -> BadgeType.WARNING
    else -> BadgeType.NEUTRAL
}

@Composable
fun MatchBadge(score: Float, modifier: Modifier = Modifier) {
    WineBadge(
        type = matchBadgeType(score),
        text = stringResource(R.string.scan_result_match, (score * 100).roundToInt()),
        modifier = modifier
    )
}

/**
 * Горизонтальная карточка найденного вина: фото, скор, название, винодельня,
 * год/регион, рейтинг и цена. Опционально — CTA «Это моё вино» снизу.
 */
@Composable
fun ScanMatchCard(
    wine: Wine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    matchScore: Float? = null,
    highlighted: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandCream100),
        border = BorderStroke(
            if (highlighted) 1.5.dp else 1.dp,
            if (highlighted) BrandCream400 else BrandBorderLight
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 88.dp, height = 112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandCream200),
                    contentAlignment = Alignment.Center
                ) {
                    wine.imageUrl?.let { url ->
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(com.wineapp.util.apiImageUrl(url))
                                .crossfade(true)
                                .build(),
                            contentDescription = wine.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    matchScore?.let {
                        MatchBadge(score = it)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        wine.name,
                        fontFamily = Playfair,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        color = BrandTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    wine.winery?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            it,
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = BrandTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (wine.vintage != null || wine.region != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            wine.vintage?.let {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandCream50,
                                    border = BorderStroke(1.dp, BrandBorderLight)
                                ) {
                                    Text(
                                        "$it",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontFamily = Inter,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = BrandTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            wine.region?.let {
                                Text(
                                    it,
                                    fontFamily = Inter,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = BrandTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (wine.rating != null || wine.price != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            wine.rating?.let {
                                Icon(
                                    AppIcons.Star,
                                    contentDescription = null,
                                    tint = BrandCream500,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    String.format("%.1f", it),
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    color = BrandTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            wine.price?.let {
                                Text(
                                    "${wine.currency ?: "$"} ${String.format("%.0f", it)}",
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp,
                                    color = BrandBurgundy600
                                )
                            }
                        }
                    }
                }
                Icon(
                    AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = BrandTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (onConfirm != null) {
                Spacer(modifier = Modifier.height(12.dp))
                BrandButton(
                    text = stringResource(R.string.scan_result_confirm),
                    onClick = { if (confirmEnabled) onConfirm() },
                    modifier = Modifier.height(44.dp)
                )
            }
        }
    }
}

@Composable
fun ScanResultSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = Playfair,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = BrandTextPrimary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

/** Ряд из двух фирменных карточек WineCard (сетка рекомендаций). */
@Composable
fun ScanResultWineRow(
    wines: List<Wine>,
    onClick: (Wine) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        wines.forEach { wine ->
            WineCard(
                wine = wine,
                modifier = Modifier.weight(1f),
                height = 260.dp,
                onClick = { onClick(wine) }
            )
        }
        if (wines.size == 1) Spacer(modifier = Modifier.weight(1f))
    }
}

/** Информационная плашка для пустых состояний внутри списка. */
@Composable
fun ScanResultNote(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text,
            fontFamily = Inter,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = BrandTextSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

val ScanResultErrorColor = Color(0xFFB65349)

@Preview(showBackground = true, backgroundColor = 0xFFFEFDF9)
@Composable
private fun ScanMatchCardPreview() {
    WineAppTheme {
        Column(Modifier.padding(16.dp)) {
            ScanResultTopBar(onBack = {}, onNewPhoto = {})
            ScanMatchCard(
                wine = MockDataProvider.wines.first(),
                onClick = {},
                matchScore = 0.92f,
                highlighted = true,
                onConfirm = {}
            )
        }
    }
}
