package com.wineapp.presentation.savedscans

import com.wineapp.presentation.common.ui.BrandLoader
import com.wineapp.presentation.common.ui.AppIcons
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.ui.WinePhotoViewer
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandDivider
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme
import com.wineapp.util.ShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedScanDetailScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val viewModel: SavedScanDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    SavedScanDetailScreenContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onNavigateToWineDetail = onNavigateToDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScanDetailScreenContent(
    state: SavedScanDetailState,
    onNavigateBack: () -> Unit,
    onNavigateToWineDetail: (String) -> Unit
) {
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = BrandCream50.toArgb()
            window.navigationBarColor = BrandCream50.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }
    var photoViewer by remember { mutableStateOf(false) }
    val successScan = (state as? SavedScanDetailState.Success)?.scan

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                com.wineapp.presentation.common.ui.BackCircleButton(onClick = onNavigateBack)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.saved_scan_detail_title),
                    fontFamily = Playfair,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    color = BrandTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (successScan != null) {
                    val context = LocalContext.current
                    Surface(
                        onClick = {
                            ShareHelper.shareConversation(context, successScan.wine, successScan.conversation)
                        },
                        shape = CircleShape,
                        color = BrandBurgundy600,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.detail_share),
                                tint = BrandCream50,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
            when (state) {
                is SavedScanDetailState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        BrandLoader()
                    }
                }
                is SavedScanDetailState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is SavedScanDetailState.Success -> {
                    val scan = state.scan
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            LabelPhotoCard(
                                scan = scan,
                                onPhotoClick = { photoViewer = true }
                            )
                        }
                        item {
                            ScanResultCard(
                                scan = scan,
                                onWineClick = { onNavigateToWineDetail(scan.wine.id) }
                            )
                        }
                        if (scan.conversation.isNotEmpty()) {
                            item {
                                ConversationSection(conversation = scan.conversation)
                            }
                        }
                    }
                }
            }
        }
        // Оверлей просмотра фото этикетки с зумом (тот же, что для вина).
        if (photoViewer && successScan?.labelPhotoPath != null) {
            WinePhotoViewer(
                imageUrl = successScan.labelPhotoPath,
                contentDescription = stringResource(R.string.saved_scan_detail_label_photo),
                onClose = { photoViewer = false },
                isLocalFile = true
            )
        }
    }
}

@Composable
private fun LabelPhotoCard(
    scan: SavedScan,
    onPhotoClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        scan.labelPhotoPath?.let { path ->
            if (File(path).exists()) {
                Surface(
                    onClick = onPhotoClick,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(path))
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.saved_scan_detail_label_photo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                EmptyPhotoBox(text = stringResource(R.string.saved_scan_detail_photo_unavailable))
            }
        } ?: run {
            EmptyPhotoBox(text = stringResource(R.string.saved_scan_detail_no_photo))
        }
    }
}

@Composable
private fun EmptyPhotoBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Inter,
            fontSize = 14.sp,
            color = BrandTextSecondary
        )
    }
}

@Composable
private fun ScanResultCard(
    scan: SavedScan,
    onWineClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }

    Surface(
        onClick = onWineClick,
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.saved_scan_detail_recognition_result),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = BrandTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = scan.wine.name,
                fontFamily = Playfair,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                color = BrandTextPrimary
            )
            scan.recognitionStatus.takeUnless { it == "legacy" }?.let { status ->
                Text(
                    text = when (status) {
                        "user_confirmed" -> "Подтверждено вами"
                        "score_confirmed" -> "Распознано автоматически"
                        "not_in_catalog" -> "Нет в каталоге"
                        SavedScan.STATUS_SOMMELIER_CHAT -> "Диалог с сомелье"
                        else -> "Не подтверждено · первый кандидат"
                    },
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BrandTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.height(12.dp))

            Column {
                scan.wine.variety?.let {
                    ScanSpecRow(label = stringResource(R.string.detail_variety), value = it)
                }
                scan.wine.style?.let {
                    ScanSpecRow(label = stringResource(R.string.detail_category), value = it)
                }
                scan.wine.alcoholPercentage?.let {
                    ScanSpecRow(
                        label = stringResource(R.string.detail_alcohol),
                        value = "${String.format("%.0f", it)}%"
                    )
                }
                listOfNotNull(scan.wine.region, scan.wine.country)
                    .joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                        ScanSpecRow(label = stringResource(R.string.detail_region), value = it)
                    }
                scan.wine.price?.let {
                    ScanSpecRow(
                        label = stringResource(R.string.detail_price),
                        value = "${scan.wine.currency ?: "$"} ${String.format("%.2f", it)}"
                    )
                }
                if (scan.wine.rating != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ScanSpecRowPadding)
                    ) {
                        Text(
                            text = stringResource(R.string.detail_rating_title),
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = BrandTextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                AppIcons.Star,
                                contentDescription = null,
                                tint = BrandCream500,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = com.wineapp.util.formatRating(scan.wine.rating),
                                fontFamily = Playfair,
                                fontWeight = FontWeight.Medium,
                                fontSize = 24.sp,
                                lineHeight = 30.sp,
                                color = BrandTextPrimary
                            )
                            scan.wine.reviewsCount?.let {
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
                ScanSpecRow(
                    label = stringResource(R.string.saved_scan_detail_confidence),
                    value = "${(scan.confidence * 100).toInt()}%"
                )
                ScanSpecRow(
                    label = stringResource(R.string.saved_scan_detail_scan_date),
                    value = dateFormat.format(Date(scan.scannedAt)),
                    hideDivider = true
                )
            }

            scan.wine.description?.let {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BrandDivider)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.detail_description),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = BrandTextSecondary
                )
            }
        }
    }
}

/** Вертикальный отступ строки таблицы (как в карточке вина) — иначе таблица сплющена. */
private val ScanSpecRowPadding = 12.dp

@Composable
private fun ScanSpecRow(
    label: String,
    value: String,
    hideDivider: Boolean = false
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanSpecRowPadding)
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
        if (!hideDivider) {
            HorizontalDivider(color = BrandDivider)
        }
    }
}

@Composable
private fun ConversationSection(conversation: List<SommelierMessage>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BrandBurgundy600),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.WineBottle,
                        contentDescription = null,
                        tint = BrandCream50,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.saved_scan_detail_conversation),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        color = BrandTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.saved_scan_detail_conversation_subtitle, conversation.size),
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = BrandTextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BrandDivider)
            Spacer(modifier = Modifier.height(14.dp))

            conversation.forEachIndexed { index, message ->
                ConversationBubble(message = message)
                if (index < conversation.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversationBubble(message: SommelierMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) BrandBurgundy600 else BrandCream50
    val textColor = if (isUser) BrandCream50 else BrandTextPrimary

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(BrandBurgundy600),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.WineBottle,
                            contentDescription = null,
                            tint = BrandCream50,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isUser) stringResource(R.string.saved_scan_detail_chat_you) else stringResource(R.string.saved_scan_detail_chat_sommelier),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (isUser) BrandBurgundy600 else BrandTextSecondary
                )
                if (isUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(BrandCream50)
                            .border(1.dp, BrandBorderLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = BrandBurgundy600,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .widthIn(min = 64.dp, max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(color = bubbleColor)
                    .border(
                        1.dp,
                        if (isUser) BrandBurgundy600 else BrandBorderLight,
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = textColor,
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
private fun SavedScanDetailPreview() {
    val previewScan = SavedScan(
        id = "1",
        wine = Wine(
            id = "w1",
            name = "Chateau Margaux 2018",
            vintage = 2018,
            rating = 4.7f,
            reviewsCount = 2341,
            price = 450.0,
            currency = "$",
            region = "Bordeaux",
            country = "France",
            variety = "Cabernet Sauvignon",
            style = "Dry Red",
            alcoholPercentage = 13.5f,
            imageUrl = null,
            description = "A majestic wine with rich flavors.",
            foodPairing = listOf("Lamb", "Cheese"),
            winery = "Chateau Margaux"
        ),
        labelPhotoPath = null,
        confidence = 0.92f,
        conversation = listOf(
            SommelierMessage("user", "С чем подавать?"),
            SommelierMessage("assistant", "Отлично сочетается с мясными блюдами.")
        ),
        scannedAt = System.currentTimeMillis()
    )
    WineAppTheme {
        SavedScanDetailScreenContent(
            state = SavedScanDetailState.Success(previewScan),
            onNavigateBack = {},
            onNavigateToWineDetail = {}
        )
    }
}
