package com.wineapp.presentation.agegate

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.R
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair

import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream300
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextSecondary

/**
 * Возрастной гейт как полноэкранный блокирующий оверлей, а не BottomSheet:
 * свайкать нечего, системная кнопка «назад» закрывает приложение.
 * Показывается только при первом открытии (см. AgeGatePrefs).
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeGateScreen(
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val exitApp = {
        (context as? Activity)?.finishAffinity()
        System.exit(0)
    }

    // Перехватываем системный «назад» раньше всех: выход из приложения.
    BackHandler(enabled = true) {
        onDeny()
        exitApp()
    }

    // Прозрачные системные панели: фон тянется под кнопки навигации,
    // иконки тёмные (фон светлый), контент упирается в инсеты, а не в край.
    TransparentSystemBars()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        // Фоновый эллипс из ресурсов: вся ширина, высота 750, прижат к низу.
        Image(
            painter = painterResource(
                id = R.drawable.ellipse_bg
            ),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp)
                .align(Alignment.BottomCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .clipToBounds()
        ) {
            // Шапка: логотип-заглушка слева, бейдж 18+ справа (y56).
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = 16.dp
                    )
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    // Логотип из ресурсов (160x40 по макету).
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = com.wineapp.R.drawable.svoe_vino_logo
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(width = 160.dp, height = 40.dp)
                    )
                    Surface(
                        onClick = {},
                        shape = CircleShape,
                        color = BrandBurgundy600,
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.CenterEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "18+",
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Круги в одном контейнере без мёртвой зоны: фото сверху,
                // бордовый снизу с наездом. Высота = 2 диаметра минус наезд.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val diameter = maxWidth
                    val overlap = 128.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(diameter * 2 - overlap)
                            .clipToBounds()
                    ) {
                        // Фото вина из ресурсов.
                        Box(
                            modifier = Modifier
                                .size(diameter)
                                .align(Alignment.TopCenter)
                                .clip(CircleShape)
                                .background(BrandCream300),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = com.wineapp.R.drawable.cool_wine
                                ),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Бордовый круг на всю ширину.
                        // Заголовок строго по центру (кегль 37 вместо 48 из макета:
                        // системный Serif шире Playfair, иначе рвёт по словам).
                        Box(
                            modifier = Modifier
                                .size(diameter)
                                .align(Alignment.BottomCenter)
                                .clip(CircleShape)
                                .background(BrandBurgundy600),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Своё вино,\nСвой вкус",
                                fontFamily = Playfair,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 48.sp,
                                lineHeight = 54.sp,
                                textAlign = TextAlign.Center,
                                color = BrandCream50,
                                modifier = Modifier.width(280.dp)
                            )
                        }
                    }
                }
                // Воздух между кругами и низом; на низких экранах сожмётся, дальше — скролл.
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = ageGateAnnotatedMessage(),
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        color = BrandTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BrandButton(
                        text = "Начать",
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}



@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgeGateBottomSheetPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        AgeGateScreen(onConfirm = {}, onDeny = {})
    }
}

/**
 * Текст age_gate_message с жирным выделением «что Вам исполнилось 18 лет».
 * Парсинг устойчив к изменениям строки: если фраза не найдена — вернётся обычный текст.
 */
@Composable
fun ageGateAnnotatedMessage(): AnnotatedString {
    val full = stringResource(com.wineapp.R.string.age_gate_message)
    val boldPart = "что Вам исполнилось 18 лет"
    val start = full.indexOf(boldPart)
    if (start < 0) return AnnotatedString(full)
    return buildAnnotatedString {
        append(full.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(boldPart)
        }
        append(full.substring(start + boldPart.length))
    }
}
