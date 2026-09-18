package com.wineapp.presentation.sommelier

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SommelierScreen() {
    val viewModel: SommelierViewModel = hiltViewModel()
    SommelierScreenContent(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SommelierScreenContent(viewModel: SommelierViewModel) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val view = LocalView.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = surfaceColor.toArgb()
        window.navigationBarColor = surfaceColor.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
    }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        // Chat Messages
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            val messages = when (val current = state) {
                is SommelierState.Idle -> current.messages
                is SommelierState.Loading -> current.messages
                is SommelierState.Error -> current.messages
            }
            val isLoading = state is SommelierState.Loading

            if (messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = stringResource(com.wineapp.R.string.sommelier_title),
                    message = stringResource(com.wineapp.R.string.sommelier_empty)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    reverseLayout = true
                ) {
                    items(messages.reversed()) { message ->
                        ChatBubble(message = message)
                    }
                    if (isLoading) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
            }
        }

        // Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    placeholder = { Text(stringResource(com.wineapp.R.string.sommelier_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    singleLine = true
                )
                Button(
                    onClick = {
                        viewModel.sendIntent(SommelierIntent.SendMessage(inputText))
                        inputText = ""
                    },
                    enabled = inputText.trim().isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = stringResource(com.wineapp.R.string.sommelier_send), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 0.dp, max = 280.dp)
                .padding(12.dp, 16.dp)
                .background(
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.isUser) 4.dp else 20.dp,
                        bottomEnd = if (message.isUser) 20.dp else 4.dp
                    )
                )
        ) {
            Text(
                message.text,
                color = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition()
    val scale1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )
    val scale2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )
    val scale3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(12.dp, 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { scaleX = scale1; scaleY = scale1 }
                        .background(Color.Gray, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { scaleX = scale2; scaleY = scale2 }
                        .background(Color.Gray, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { scaleX = scale3; scaleY = scale3 }
                        .background(Color.Gray, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SommelierScreenPreview() {
    val sampleMessages = listOf(
        ChatMessage(text = "Какое вино подходит к пасте?", isUser = true),
        ChatMessage(text = "Для пасты с томатным соусом рекомендую Кианти или Монтепульчано. Кислотность томатов прекрасно сочетается с яркими вишнёвыми нотами Саньовезе.", isUser = false),
        ChatMessage(text = "А что насчет морепродуктов?", isUser = true),
        ChatMessage(text = "С морепродуктами отлично подойдёт освежающий Совиньон Блан или Пино Гриджио. Для более сытных блюд, таких каклобстер, попробуйте насыщенный Шардонне.", isUser = false),
    )
    com.wineapp.ui.theme.WineAppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    reverseLayout = true
                ) {
                    items(sampleMessages.reversed()) { message ->
                        ChatBubble(message = message)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextField(value = "Расскажите ещё о винах Бордо", onValueChange = {}, modifier = Modifier.weight(1f).padding(end = 8.dp), singleLine = true)
                    Button(onClick = {}, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Send, contentDescription = stringResource(com.wineapp.R.string.sommelier_send), tint = Color.White)
                    }
                }
            }
        }
    }
}