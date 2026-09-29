package com.wineapp.presentation.scanner

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.RecommendedWine
import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.ScoredWine
import com.wineapp.domain.model.confirmation
import com.wineapp.presentation.common.ui.BadgeType
import com.wineapp.presentation.common.ui.BrandSecondaryButton
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.presentation.common.ui.WineBadge
import com.wineapp.presentation.scanresult.ScanMatchCard
import com.wineapp.presentation.scanresult.ScanResultErrorColor
import com.wineapp.presentation.scanresult.ScanResultIntro
import com.wineapp.presentation.scanresult.ScanResultNote
import com.wineapp.presentation.scanresult.ScanResultSectionTitle
import com.wineapp.presentation.scanresult.ScanResultTopBar
import com.wineapp.presentation.scanresult.ScanResultWineRow
import com.wineapp.ui.theme.BrandBurgundy600
import com.wineapp.ui.theme.BrandCream200
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.WineAppTheme

/** Keeps identification candidates, user confirmation and recommendations separate. */
@Composable
fun ScanSummaryScreen(
    state: ScannerState.Success,
    alreadyTried: Boolean = false,
    onConfirm: (String) -> Unit,
    onOpenWine: (String, Boolean) -> Unit,
    /** «Назад» — к камере сканера (новый снимок), а не выход из сканера. */
    onBack: () -> Unit
) {
    val result = state.result
    val absent = result.recognitionStatus == "not_in_catalog"
    val confirmation = result.confirmation()
    val confirmed = confirmation?.candidate
    var choosingCandidate by remember(result.scanId, result.userConfirmedSlug) { mutableStateOf(false) }
    val hasRecs = result.recommendations.isNotEmpty()

    TransparentSystemBars()
    Box(
        modifier = Modifier
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
            item {
                ScanResultTopBar(onBack = onBack)
            }
            item {
                ScanResultIntro(
                    title = stringResource(
                        when {
                            absent -> R.string.scan_result_title_absent
                            choosingCandidate -> R.string.scan_result_title_choosing
                            confirmed != null -> R.string.scan_result_title_found
                            else -> R.string.scan_result_title_likely
                        }
                    ),
                    subtitle = stringResource(
                        when {
                            absent -> if (hasRecs) R.string.scan_result_sub_absent_recs else R.string.scan_result_sub_retry
                            choosingCandidate -> R.string.scan_result_sub_choosing
                            confirmed != null -> R.string.scan_result_sub_found
                            else -> if (hasRecs) R.string.scan_result_sub_likely_recs else R.string.scan_result_sub_retry
                        }
                    ),
                    photoPath = state.imagePath
                ) {
                    when {
                        absent -> WineBadge(
                            type = BadgeType.ERROR,
                            text = stringResource(R.string.scan_result_not_in_catalog)
                        )
                        confirmed == null && !choosingCandidate -> WineBadge(
                            type = BadgeType.WARNING,
                            text = stringResource(R.string.scan_result_needs_check)
                        )
                        confirmed != null && !choosingCandidate && alreadyTried -> WineBadge(
                            type = BadgeType.SUCCESS,
                            text = stringResource(R.string.scan_result_already_tried)
                        )
                    }
                }
            }
            if (confirmed != null && !choosingCandidate) {
                item {
                    ScanMatchCard(
                        wine = confirmed.wine,
                        matchScore = confirmed.matchScore,
                        onClick = { onOpenWine(confirmed.wine.id, true) }
                    )
                }
            } else if (!absent && choosingCandidate) {
                items(result.scoredCandidates, key = { "candidate:${it.slug}" }) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        canConfirm = result.scanId != null,
                        busy = state.confirming,
                        onConfirm = { onConfirm(candidate.slug) },
                        onOpen = { onOpenWine(candidate.wine.id, true) }
                    )
                }
            }
            if (confirmed != null && result.scoredCandidates.size > 1) {
                item {
                    BrandSecondaryButton(
                        text = stringResource(
                            if (choosingCandidate) R.string.scan_result_back_to_result
                            else R.string.scan_result_choose_other
                        ),
                        onClick = { if (!state.confirming) choosingCandidate = !choosingCandidate }
                    )
                }
            }
            if (state.confirming) {
                item {
                    LinearProgressIndicator(
                        color = BrandBurgundy600,
                        trackColor = BrandCream200,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                    )
                }
            }
            state.confirmationError?.let { error ->
                item {
                    Text(
                        error,
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = ScanResultErrorColor
                    )
                }
            }
            item {
                ScanResultSectionTitle(
                    stringResource(
                        if (confirmed != null) R.string.scan_result_recommend_also
                        else R.string.scan_result_similar
                    )
                )
            }
            items(
                result.recommendations.chunked(2),
                key = { row -> "recommendation:${row.first().wine.id}" }
            ) { row ->
                ScanResultWineRow(
                    wines = row.map { it.wine },
                    onClick = { wine -> onOpenWine(wine.id, false) }
                )
            }
            if (!hasRecs) {
                item {
                    ScanResultNote(
                        stringResource(
                            when (result.recommendationStatus) {
                                "ambiguous_evidence" -> R.string.scan_result_recs_ambiguous
                                "no_suitable_analogs" -> R.string.scan_result_recs_none
                                else -> R.string.scan_result_recs_failed
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: ScoredWine,
    canConfirm: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onOpen: () -> Unit
) {
    val view = LocalView.current
    ScanMatchCard(
        wine = candidate.wine,
        matchScore = candidate.matchScore,
        onClick = onOpen,
        onConfirm = if (canConfirm) {
            {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onConfirm()
            }
        } else null,
        confirmEnabled = !busy
    )
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun ScanSummaryScreenPreview() {
    WineAppTheme {
        val wines = MockDataProvider.wines
        ScanSummaryScreen(
            state = ScannerState.Success(
                result = ScanResult(
                    wine = wines.first(),
                    confidence = 0.92f,
                    scanId = "preview",
                    scoredCandidates = listOf(
                        ScoredWine(wines.first(), "first", 1, 0.92f),
                        ScoredWine(wines[1], "second", 2, 0.41f)
                    ),
                    recommendations = wines.drop(2).take(3).map { RecommendedWine(it, emptyList()) }
                ),
                imagePath = ""
            ),
            alreadyTried = true,
            onConfirm = {},
            onOpenWine = { _, _ -> },
            onBack = {}
        )
    }
}
