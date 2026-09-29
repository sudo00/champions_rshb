package com.wineapp.presentation.sommelier

import androidx.compose.ui.graphics.graphicsLayer
import com.wineapp.presentation.common.ui.AppIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.ChatHistoryItem
import com.wineapp.domain.model.Wine
import com.wineapp.domain.model.WineContext
import com.wineapp.ui.theme.BrandBorderLight
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream200
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandCream500
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.BrandTextTertiary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SommelierScreen(
    wineId: String? = null,
    wineName: String? = null,
    wineRegion: String? = null,
    wineVariety: String? = null,
    wineVintage: Int? = null,
    wineRating: Float? = null,
    wineStyle: String? = null,
    photoPath: String? = null,
    confidence: Float = 1.0f,
    /** Вопрос, заданный на другом экране (например, из коллекции) — отправляется один раз. */
    initialQuestion: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val viewModel: SommelierViewModel = hiltViewModel()

    var initialQuestionSent by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialQuestion) {
        if (!initialQuestionSent && !initialQuestion.isNullOrBlank()) {
            initialQuestionSent = true
            viewModel.sendIntent(SommelierIntent.SendMessage(initialQuestion))
        }
    }

    LaunchedEffect(wineId) {
        if (wineId != null && wineName != null) {
            viewModel.sendIntent(
                SommelierIntent.SetWineContext(
                    WineContext(
                        wineId = wineId,
                        wineName = wineName,
                        region = wineRegion,
                        variety = wineVariety,
                        vintage = wineVintage,
                        rating = wineRating,
                        style = wineStyle
                    )
                )
            )
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    val history by viewModel.chatHistory.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = BrandCream50) {
                ChatHistoryDrawer(
                    history = history,
                    onOpenChat = { scanId ->
                        viewModel.sendIntent(SommelierIntent.OpenHistory(scanId))
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.sendIntent(SommelierIntent.ClearChat)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        SommelierScreenContent(
            viewModel = viewModel,
            onBurgerClick = { scope.launch { drawerState.open() } },
            onNavigateBack = {
                viewModel.sendIntent(SommelierIntent.SaveAndExit(photoPath, confidence))
                onNavigateBack()
            },
            onNavigateToDetail = onNavigateToDetail
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SommelierScreenContent(
    viewModel: SommelierViewModel,
    onBurgerClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = BrandCream50.toArgb()
            window.navigationBarColor = BrandCream50.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    val messages = when (val s = state) {
        is SommelierState.Idle -> s.messages
        is SommelierState.Loading -> s.messages
        is SommelierState.Error -> s.messages
    }
    val wine = when (val s = state) {
        is SommelierState.Idle -> s.wine
        is SommelierState.Loading -> s.wine
        is SommelierState.Error -> s.wine
    }
    val isWineLoading = when (val s = state) {
        is SommelierState.Idle -> s.isWineLoading
        is SommelierState.Loading -> s.isWineLoading
        is SommelierState.Error -> s.isWineLoading
    }
    val isLoading = state is SommelierState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            SommelierTopBar(
                onBurgerClick = onBurgerClick,
                onCloseClick = onNavigateBack,
                modifier = Modifier.statusBarsPadding()
                    .padding(bottom = 6.dp)
            )
            if (messages.isEmpty()) {
                SommelierEmptyState(
                    wine = wine,
                    isWineLoading = isWineLoading,
                    onQuestionClick = { question ->
                        viewModel.sendIntent(SommelierIntent.SendMessage(question))
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Плашка вина висит поверх сообщений; у списка отступ сверху,
                // чтобы первые сообщения не уезжали под неё.
                val chatWine = wine
                Box(modifier = Modifier.weight(1f)) {
                    SommelierChatContent(
                        messages = messages,
                        isResponseLoading = isLoading,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = if (chatWine != null) 92.dp else 0.dp)
                    )
                    if (chatWine != null) {
                        SommelierWineBanner(
                            wine = chatWine,
                            onClick = { onNavigateToDetail(chatWine.id) },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
            // Запас под висящую поверх пилюлю ввода.
            Spacer(modifier = Modifier.height(84.dp))
        }
        SommelierInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                viewModel.sendIntent(SommelierIntent.SendMessage(inputText))
                inputText = ""
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Верхний блок из макета: бургер меню истории чатов, заголовок со звездой,
 * кнопка-назад с крестиком.
 */
@Composable
private fun SommelierTopBar(
    onBurgerClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onBurgerClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppIcons.Burger,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sommelier_title),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = BrandBurgundy600
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                AppIcons.Star,
                contentDescription = null,
                tint = BrandBurgundy600,
                modifier = Modifier.size(20.dp)
            )
        }
        Surface(
            onClick = onCloseClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppIcons.Close,
                    contentDescription = null,
                    tint = BrandCream50,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Пустое состояние: заголовок + scan-карточка (или скелетон при загрузке вина)
 * по центру, блок дефолтных вопросов снизу над вводом.
 */
@Composable
private fun SommelierEmptyState(
    wine: Wine?,
    isWineLoading: Boolean,
    onQuestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.sommelier_wine_context).trimEnd(':'),
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = BrandTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            when {
                isWineLoading -> SommelierScanCardSkeleton()
                wine != null -> SommelierScanCard(wine = wine)
            }
        }
        SommelierQuestions(
            onQuestionClick = onQuestionClick,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun SommelierScanCard(wine: Wine) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                if (wine.imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.wineapp.util.apiImageUrl(wine.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.sommelier_scan_card_cd),
                        loading = { WinePhotoPlaceholder() },
                        error = { WinePhotoPlaceholder() },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    WinePhotoPlaceholder()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = wine.winery.orEmpty(),
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = BrandTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = wine.name,
                        fontFamily = Playfair,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        color = BrandTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val regionText = listOfNotNull(wine.region, wine.country).joinToString(", ")
                    if (regionText.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = countryFlagEmoji(wine.country), fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = regionText,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
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
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.2f", wine.rating),
                                fontFamily = Inter,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = BrandTextPrimary
                            )
                            wine.reviewsCount?.let {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "($it)",
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = BrandTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Статичный скелетон карточки на время догрузки вина по wineId. */
@Composable
private fun SommelierScanCardSkeleton() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(BrandBorderLight, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                WinePhotoPlaceholder()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(24.dp)
                    .background(BrandBorderLight, RoundedCornerShape(6.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(30.dp)
                    .background(BrandBorderLight, RoundedCornerShape(6.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(24.dp)
                    .background(BrandBorderLight, RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun WinePhotoPlaceholder() {
    Icon(
        AppIcons.WineBottle,
        contentDescription = null,
        tint = BrandTextSecondary.copy(alpha = 0.3f),
        modifier = Modifier.size(72.dp)
    )
}

private fun countryFlagEmoji(country: String?): String = when (country?.lowercase()) {
    "россия", "russia" -> "🇷🇺"
    "франция", "france" -> "🇫🇷"
    "италия", "italy" -> "🇮🇹"
    "испания", "spain" -> "🇪🇸"
    "грузия", "georgia" -> "🇬🇪"
    "чили", "chile" -> "🇨🇱"
    "аргентина", "argentina" -> "🇦🇷"
    else -> "🌍"
}

/**
 * Блок дефолтных вопросов над вводом — только пустое состояние.
 * Короткие подписи для компактных рядов; по тапу уходит полный вопрос.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SommelierQuestions(
    onQuestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val questions = listOf(
        stringResource(R.string.sommelier_chip_pairing) to stringResource(R.string.sommelier_q_pairing),
        stringResource(R.string.sommelier_chip_analogues) to stringResource(R.string.sommelier_q_analogues),
        stringResource(R.string.sommelier_chip_region) to stringResource(R.string.sommelier_q_region),
        stringResource(R.string.sommelier_chip_aging) to stringResource(R.string.sommelier_q_aging),
        stringResource(R.string.sommelier_chip_temp) to stringResource(R.string.sommelier_q_temp)
    )
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        questions.forEach { (short, full) ->
            Surface(
                onClick = { onQuestionClick(full) },
                shape = RoundedCornerShape(percent = 50),
                color = BrandCream50,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, BrandBorderLight)
            ) {
                Text(
                    text = short,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BrandTextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Нижний блок ввода: кремовая пилюля 60dp с тенью, висит поверх контента.
 */
@Composable
private fun SommelierInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = BrandCream50,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                onClick = { },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Plus,
                        contentDescription = null,
                        tint = BrandTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            androidx.compose.foundation.text.BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = BrandTextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandBurgundy600),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                stringResource(R.string.sommelier_input_hint),
                                fontFamily = Inter,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = BrandTextTertiary,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Surface(
                onClick = onSend,
                shape = CircleShape,
                color = BrandBurgundy600,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.ChevronUp,
                        contentDescription = stringResource(R.string.sommelier_send),
                        tint = BrandCream50,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Плашка вина сверху чата из макета: Cream100 r20, фото 43x64, винодельня,
 * название, шеврон. Тап ведёт на карточку вина.
 */
@Composable
private fun SommelierWineBanner(
    wine: Wine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = BrandCream100,
        border = BorderStroke(1.dp, BrandBorderLight),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp)
            ) {
                if (wine.imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.wineapp.util.apiImageUrl(wine.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = wine.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 43.dp, height = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 43.dp, height = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandBorderLight)
                    ) {
                        Icon(
                            AppIcons.WineBottle,
                            contentDescription = null,
                            tint = BrandTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
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
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = BrandTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Центральная часть чата: сообщения + строка загрузки следующего ответа.
 */
@Composable
private fun SommelierChatContent(
    messages: List<ChatMessage>,
    isResponseLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        reverseLayout = true
    ) {
        if (isResponseLoading) {
            item(key = "loading") {
                SommelierLoadingRow(text = stringResource(R.string.sommelier_loading_notes))
            }
        }
        items(messages.reversed(), key = { it.id }) { message ->
            if (message.isUser) {
                SommelierUserMessage(text = message.text)
            } else {
                SommelierAssistantMessage(message = message)
            }
        }
    }
}

@Composable
private fun SommelierAssistantMessage(message: ChatMessage) {
    // Оценка ответа — только визуал, без бэкенда.
    var vote by remember(message.id) { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = message.text,
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = BrandTextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                onClick = { vote = if (vote == 1) 0 else 1 },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // like.xml в ресурсах — копия heart.xml, поэтому «палец вверх»
                    // рисуем отражённым по вертикали dislike.xml.
                    Icon(
                        AppIcons.Dislike,
                        contentDescription = null,
                        tint = if (vote == 1) BrandBurgundy600 else BrandTextPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(scaleY = -1f)
                    )
                }
            }
            Surface(
                onClick = { vote = if (vote == 2) 0 else 2 },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Dislike,
                        contentDescription = null,
                        tint = if (vote == 2) BrandBurgundy600 else BrandTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SommelierUserMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = BrandCream200,
        ) {
            Text(
                text = text,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = BrandTextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SommelierLoadingRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Icon(
            AppIcons.Star,
            contentDescription = null,
            tint = BrandBurgundy600,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = BrandTextSecondary
        )
    }
}

/** Боковое меню истории диалогов сомелье. */
@Composable
private fun ChatHistoryDrawer(
    history: List<ChatHistoryItem>,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.sommelier_history_title),
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                color = BrandTextPrimary
            )
            Surface(
                onClick = onNewChat,
                shape = RoundedCornerShape(percent = 50),
                color = BrandBurgundy600
            ) {
                Text(
                    text = stringResource(R.string.sommelier_history_new_chat),
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BrandCream50,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.sommelier_history_empty),
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = BrandTextSecondary
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.scanId }) { item ->
                    ChatHistoryRow(item = item, onClick = { onOpenChat(item.scanId) })
                    HorizontalDivider(color = BrandBorderLight)
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryRow(item: ChatHistoryItem, onClick: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.wineName,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = BrandTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateFormat.format(Date(item.lastMessageAt)),
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = BrandTextSecondary
                )
            }
            if (item.lastMessage != null) {
                Text(
                    text = item.lastMessage,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = BrandTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SommelierEmptyPreview() {
    WineAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandCream50)
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SommelierTopBar(onBurgerClick = {}, onCloseClick = {})
                SommelierEmptyState(
                    wine = MockDataProvider.wines.first(),
                    isWineLoading = false,
                    onQuestionClick = {},
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(84.dp))
            }
            SommelierInputBar(
                inputText = "",
                onInputChange = {},
                onSend = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SommelierSkeletonPreview() {
    WineAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandCream50)
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SommelierTopBar(onBurgerClick = {}, onCloseClick = {})
                SommelierEmptyState(
                    wine = null,
                    isWineLoading = true,
                    onQuestionClick = {},
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(84.dp))
            }
            SommelierInputBar(
                inputText = "",
                onInputChange = {},
                onSend = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SommelierChatLoadingPreview() {
    WineAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandCream50)
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SommelierTopBar(onBurgerClick = {}, onCloseClick = {})
                SommelierChatContent(
                    messages = listOf(
                        ChatMessage(
                            text = "«Винные краски, Шардоне» — лёгкое и свежее белое вино с мягким фруктово-цветочным ароматом. Хотите, я подскажу, с каким блюдом это вино раскроется лучше всего?",
                            isUser = false
                        ),
                        ChatMessage(text = "Да, подскажите", isUser = true)
                    ),
                    isResponseLoading = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(84.dp))
            }
            SommelierInputBar(
                inputText = "",
                onInputChange = {},
                onSend = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}
