package com.wineapp.presentation.scanresult

import com.wineapp.presentation.common.ui.BrandLoader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.presentation.common.ui.BadgeType
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WineBadge
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.Inter

@Composable
fun ScanResultScreen(
    confidence: Float,
    mainWineId: String,
    altIds: String,
    photoPath: String? = null,
    recognitionStatus: String? = null,
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: ScanResultViewModel = hiltViewModel()
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

    LaunchedEffect(mainWineId) {
        viewModel.sendIntent(ScanResultIntent.LoadWines(mainWineId, altIds))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScanResultContent(
            state = state,
            confidence = confidence,
            photoPath = photoPath,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateBack = onNavigateBack
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
fun ScanResultContent(
    state: ScanResultState,
    confidence: Float,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    photoPath: String? = null,
    onNavigateBack: () -> Unit = {}
) {
    TransparentSystemBars()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        Image(
            painter = painterResource(R.drawable.search_tab_ellipse),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ScanResultTopBar(onBack = onNavigateBack) }
            when (state) {
                is ScanResultState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BrandLoader()
                    }
                }
                is ScanResultState.Error -> item {
                    Text(
                        state.message,
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = ScanResultErrorColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp)
                    )
                }
                is ScanResultState.Success -> {
                    item {
                        ScanResultIntro(
                            title = stringResource(R.string.scan_result_title),
                            subtitle = stringResource(R.string.scan_result_sub_found),
                            photoPath = photoPath
                        ) {
                            MatchBadge(score = confidence)
                            if (state.alreadyTried) {
                                WineBadge(
                                    type = BadgeType.SUCCESS,
                                    text = stringResource(R.string.scan_result_already_tried)
                                )
                            }
                        }
                    }
                    item { ScanResultSectionTitle(stringResource(R.string.scan_result_best_match)) }
                    item {
                        ScanMatchCard(
                            wine = state.mainWine,
                            highlighted = true,
                            onClick = { onNavigateToDetail(state.mainWine.id) }
                        )
                    }
                    if (state.alternatives.isNotEmpty()) {
                        item { ScanResultSectionTitle(stringResource(R.string.scan_result_alternatives)) }
                        items(state.alternatives, key = { "alt:${it.id}" }) { wine ->
                            ScanMatchCard(
                                wine = wine,
                                onClick = { onNavigateToDetail(wine.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ScanResultScreenPreview() {
    com.wineapp.ui.theme.WineAppTheme {
        val wines = MockDataProvider.wines
        ScanResultContent(
            state = ScanResultState.Success(
                mainWine = wines.first(),
                alternatives = wines.drop(1).take(3),
                alreadyTried = true
            ),
            confidence = 0.92f,
            onNavigateToDetail = {}
        )
    }
}
