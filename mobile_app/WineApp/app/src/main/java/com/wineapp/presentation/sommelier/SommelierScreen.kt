package com.wineapp.presentation.sommelier

import com.wineapp.domain.model.WineContext
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.presentation.common.WineAppTopAppBar
import com.wineapp.util.ShareHelper

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
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: SommelierViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(wineId) {
        if (wineId != null && wineName != null) {
            viewModel.sendIntent(SommelierIntent.SetWineContext(
                WineContext(
                    wineId = wineId,
                    wineName = wineName,
                    region = wineRegion,
                    variety = wineVariety,
                    vintage = wineVintage,
                    rating = wineRating,
                    style = wineStyle
                )
            ))
        }
    }

    SommelierScreenContent(
        viewModel = viewModel,
        onNavigateBack = {
            viewModel.sendIntent(SommelierIntent.SaveAndExit(photoPath, confidence))
            onNavigateBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SommelierScreenContent(
    viewModel: SommelierViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            val window = activity.window
            window.statusBarColor = surfaceColor.toArgb()
            window.navigationBarColor = surfaceColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    val showBackButton = when (val s = state) {
        is SommelierState.Idle -> s.wineContext != null
        is SommelierState.Loading -> s.wineContext != null
        is SommelierState.Error -> s.wineContext != null
    }

    Scaffold(
        topBar = {
            WineAppTopAppBar(
                title = stringResource(R.string.sommelier_title),
                showBack = showBackButton,
                onBack = onNavigateBack,
                actions = {
                    val messages = when (val current = state) {
                        is SommelierState.Idle -> current.messages
                        is SommelierState.Loading -> current.messages
                        is SommelierState.Error -> current.messages
                    }
                    val wineContext = when (val current = state) {
                        is SommelierState.Idle -> current.wineContext
                        is SommelierState.Loading -> current.wineContext
                        is SommelierState.Error -> current.wineContext
                    }
                    if (messages.isNotEmpty() && wineContext != null) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            ShareHelper.shareConversationFromChat(
                                context,
                                wineContext.wineName,
                                messages
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.detail_share)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            SommelierInput(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    viewModel.sendIntent(SommelierIntent.SendMessage(inputText))
                    inputText = ""
                }
            )
        }
    ) { paddingValues ->
        val wineContext = when (val s = state) {
            is SommelierState.Idle -> s.wineContext
            is SommelierState.Loading -> s.wineContext
            is SommelierState.Error -> s.wineContext
        }

        val messages = when (val current = state) {
            is SommelierState.Idle -> current.messages
            is SommelierState.Loading -> current.messages
            is SommelierState.Error -> current.messages
        }
        val isLoading = state is SommelierState.Loading

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }

        if (messages.isEmpty()) {
            SommelierEmptyState(
                wineContext = wineContext,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onQuestionClick = { question ->
                    inputText = question
                    viewModel.sendIntent(SommelierIntent.SendMessage(question))
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (wineContext != null) {
                    WineContextInline(
                        wineContext = wineContext,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true
                ) {
                    if (isLoading) {
                        item(key = "typing") {
                            TypingIndicator()
                        }
                    }
                    items(messages.reversed(), key = { it.id }) { message ->
                        ChatBubble(message = message)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SommelierEmptyState(
    wineContext: WineContext?,
    modifier: Modifier = Modifier,
    onQuestionClick: (String) -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            stringResource(R.string.sommelier_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.sommelier_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (wineContext != null) {
            Spacer(modifier = Modifier.height(32.dp))
            WineContextCard(wineContext = wineContext)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.sommelier_suggested_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestedQuestion(text = stringResource(R.string.sommelier_q_pairing), onClick = onQuestionClick)
                SuggestedQuestion(text = stringResource(R.string.sommelier_q_analogues), onClick = onQuestionClick)
                SuggestedQuestion(text = stringResource(R.string.sommelier_q_region), onClick = onQuestionClick)
                SuggestedQuestion(text = stringResource(R.string.sommelier_q_aging), onClick = onQuestionClick)
                SuggestedQuestion(text = stringResource(R.string.sommelier_q_temp), onClick = onQuestionClick)
            }
        }
    }
}

@Composable
private fun SommelierInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.sommelier_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Surface(
                onClick = onSend,
                enabled = inputText.trim().isNotBlank(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.sommelier_send),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WineContextCard(wineContext: WineContext) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.WineBar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.sommelier_wine_context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    wineContext.wineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOfNotNull(
                        wineContext.vintage?.toString(),
                        wineContext.region,
                        wineContext.variety
                    ).forEachIndexed { index, value ->
                        if (index > 0) {
                            Text(
                                "\u00b7",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                            )
                        }
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WineContextInline(wineContext: WineContext, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WineBar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    wineContext.wineName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                val subtitle = listOfNotNull(
                    wineContext.vintage?.toString(),
                    wineContext.region,
                    wineContext.variety
                ).joinToString(" \u00b7 ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedQuestion(text: String, onClick: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick(text) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 48.dp, max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(
                    color = if (isUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition()
    val dots = listOf(0, 200, 400).map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, delayMillis = delay),
                repeatMode = RepeatMode.Reverse
            ),
            label = ""
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                dots.forEach { anim ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer {
                                val value by anim
                                alpha = value
                                scaleX = value
                                scaleY = value
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SommelierScreenWithWinePreview() {
    com.wineapp.ui.theme.WineAppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                SommelierEmptyState(
                    wineContext = WineContext(
                        wineId = "1",
                        wineName = "Chateau Margaux 2018",
                        region = "Bordeaux",
                        variety = "Cabernet Sauvignon",
                        vintage = 2018,
                        rating = 4.7f,
                        style = "Dry Red"
                    ),
                    modifier = Modifier.fillMaxSize(),
                    onQuestionClick = {}
                )
            }
            SommelierInput(inputText = "", onInputChange = {}, onSend = {})
        }
    }
}
