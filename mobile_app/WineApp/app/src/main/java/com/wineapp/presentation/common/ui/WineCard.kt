package com.wineapp.presentation.common.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.Wine
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.WineAppTheme

/**
 * Фирменная карточка вина (Cream 100, radius 28): рейтинг, фото, название,
 * винодельня, сердечко. Дизайн из фигмы главного экрана.
 */
@Composable
fun WineCard(
    wine: Wine,
    modifier: Modifier = Modifier,
    height: Dp = 284.dp,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BrandCream100),
        onClick = onClick,
        modifier = modifier
            .height(height)
            .then(
                if (onLongClick != null) Modifier.notifyLongPress(onLongClick)
                else Modifier
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Рейтинг.
                if (wine.rating != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Star,
                        contentDescription = null,
                        tint = BrandCream500,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        String.format("%.2f", wine.rating),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextPrimary
                    )
                    wine.reviewsCount?.let {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "($it)",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = BrandTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Фото бутылки.
                if (wine.imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.wineapp.util.apiImageUrl(wine.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = wine.name,
                        loading = { CataloguePhotoMessage("Загрузка фото…") },
                        error = { CataloguePhotoMessage("Фото недоступно") },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    CataloguePhotoMessage("Фото недоступно", modifier = Modifier.fillMaxWidth().weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    wine.name,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    wine.winery ?: "",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BrandTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Сердечко.
            if (onFavoriteClick != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, end = 13.5.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isFavorite) AppIcons.HeartFilled else AppIcons.Heart,
                            contentDescription = null,
                            tint = if (isFavorite) com.wineapp.ui.theme.BrandBurgundy600 else BrandTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ручной детект лонг-пресса поверх нативного onClick карточки.
 * Голые clickable/combinedClickable в проекте запрещены: foundation в сборке
 * новее material3 1.2.1 и требует IndicationNodeFactory, а тема отдаёт старый
 * ripple (краш "clickable only supports IndicationNodeFactory").
 * Таймаут без отрыва пальца — лонг-пресс (down consume'им, чтобы Card
 * не докликнул), отрыв раньше — обычный тап Card.
 */
private fun Modifier.notifyLongPress(onLongClick: () -> Unit): Modifier =
    pointerInput(onLongClick) {
        val timeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val releasedInTime = withTimeoutOrNull(timeout) {
                waitForUpOrCancellation()
            }
            if (releasedInTime == null) {
                down.consume()
                onLongClick()
            }
        }
    }

@Composable
private fun CataloguePhotoMessage(message: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(message, color = BrandTextSecondary, fontFamily = Inter, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFEFDF9)
@Composable
private fun WineCardPreview() {
    WineAppTheme {
        WineCard(
            wine = MockDataProvider.wines.first(),
            onClick = {},
            onFavoriteClick = {}
        )
    }
}
