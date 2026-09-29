package com.wineapp.presentation.scanresult

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.BadgeType
import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.presentation.common.ui.WineBadge
import com.wineapp.presentation.common.ui.WineCard
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream200
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

/**
 * Шапка: круглая «назад» слева. Отдельной кнопки «Новое фото» нет —
 * на экране итога скана «назад» и так возвращает к камере.
 */
@Composable
fun ScanResultTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        com.wineapp.presentation.common.ui.BackCircleButton(onClick = onBack)
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
    // Процент совпадения — не предупреждение: и в жёлтом, и в зелёном варианте
    // контурная галочка, цвет — по типу плашки.
    WineBadge(
        type = matchBadgeType(score),
        text = stringResource(R.string.scan_result_match, (score * 100).roundToInt()),
        modifier = modifier,
        icon = androidx.compose.material.icons.Icons.Outlined.CheckCircle
    )
}

/**
 * Карточка найденного вина — в стиле карточек «Избранного». Опционально —
 * бейдж совпадения [matchScore] и CTA «Это моё вино» под карточкой.
 */
@Composable
fun ScanMatchCard(
    wine: Wine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    matchScore: Float? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true
) {
    // Тот же каркас, что у карточек «Избранного» (WineListCard + WineListHeadline):
    // фото 80×120, винодельня, название, регион с флагом, рейтинг. Вместо чипа
    // «Хочу/Понравилось» внизу — бейдж совпадения. CTA кандидата — под карточкой.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        com.wineapp.presentation.common.ui.WineListCard(
            imageModel = wine.imageUrl?.let { com.wineapp.util.apiImageUrl(it) },
            imageDescription = wine.name,
            onClick = onClick,
            headline = { com.wineapp.presentation.common.ui.WineListHeadline(wine) },
            tagContent = matchScore?.let { score -> { MatchBadge(score = score) } }
        )
        if (onConfirm != null) {
            BrandButton(
                text = stringResource(R.string.scan_result_confirm),
                onClick = { if (confirmEnabled) onConfirm() },
                modifier = Modifier.height(44.dp)
            )
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
            ScanResultTopBar(onBack = {})
            ScanMatchCard(
                wine = MockDataProvider.wines.first(),
                onClick = {},
                matchScore = 0.92f,
                onConfirm = {}
            )
        }
    }
}
