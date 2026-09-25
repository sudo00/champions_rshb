package com.wineapp.presentation.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.wineapp.domain.model.ScoredWine
import com.wineapp.presentation.common.ui.WineCard
import kotlin.math.roundToInt

/** Keeps identification candidates, user confirmation and recommendations separate. */
@Composable
fun ScanSummaryScreen(
    state: ScannerState.Success,
    onConfirm: (String) -> Unit,
    onOpenWine: (String, Boolean) -> Unit,
    onNewPhoto: () -> Unit,
    onBack: () -> Unit
) {
    val result = state.result
    val absent = result.recognitionStatus == "not_in_catalog"
    val confirmed = result.scoredCandidates.firstOrNull { it.slug == result.userConfirmedSlug }
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text("Назад") }
                    TextButton(onClick = onNewPhoto, enabled = !state.confirming) { Text("Новое фото") }
                }
                Text(
                    when {
                        absent -> "Вино не найдено в каталоге"
                        confirmed != null -> "Ваше вино"
                        else -> "Похожие варианты из каталога"
                    }, style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        absent -> "Не удалось найти это вино. Ниже — рекомендации по характеристикам, прочитанным на этикетке."
                        confirmed != null -> "Вы подтвердили совпадение."
                        else -> "Совпадение пока не подтверждено. Выберите своё вино среди результатов поиска."
                    }, style = MaterialTheme.typography.bodyMedium
                )
            }
            if (confirmed != null) {
                item { CandidateCard(confirmed, false, false, {}, { onOpenWine(confirmed.wine.id, true) }) }
            } else if (!absent) {
                items(result.scoredCandidates, key = { "candidate:${it.slug}" }) { candidate ->
                    CandidateCard(candidate, result.scanId != null, state.confirming,
                        { onConfirm(candidate.slug) }, { onOpenWine(candidate.wine.id, true) })
                }
                if (result.scoredCandidates.isNotEmpty()) {
                    item { Text("Совпадение — оценка сходства от 0 до 100, а не вероятность правильного ответа.",
                        style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (state.confirming) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.confirmationError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(if (!absent && confirmed == null) "Рекомендации по первому кандидату" else "Рекомендуем также",
                    style = MaterialTheme.typography.titleLarge)
                if (!absent && confirmed == null) {
                    Text("Подбор ориентируется на первую карточку поиска; она может отличаться от вина на фото.",
                        style = MaterialTheme.typography.bodySmall)
                }
                val labels = mapOf("color" to "Цвет", "sweetness" to "Сахар", "grapes" to "Виноград", "producer" to "Производитель")
                result.recommendationCriteria.forEach { (key, value) ->
                    labels[key]?.let { label -> Text("$label: $value", style = MaterialTheme.typography.bodySmall) }
                }
            }
            items(result.recommendations, key = { "recommendation:${it.wine.id}" }) { recommendation ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WineCard(recommendation.wine, modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenWine(recommendation.wine.id, false) })
                    recommendation.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (result.recommendations.isEmpty()) {
                item {
                    Text(when (result.recommendationStatus) {
                        "ambiguous_evidence" -> "Прочитанные характеристики противоречат друг другу. Попробуйте более чёткое фото."
                        "no_suitable_analogs" -> "В каталоге пока нет рекомендаций с такими характеристиками."
                        else -> "Недостаточно данных для подбора рекомендаций. Попробуйте сфотографировать этикетку ближе."
                    })
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: ScoredWine, canConfirm: Boolean, busy: Boolean,
                          onConfirm: () -> Unit, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${candidate.rank}. ${candidate.wine.winery.orEmpty()}", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            candidate.matchScore?.let { Text("Совпадение ${(it * 100).roundToInt()}/100", style = MaterialTheme.typography.labelLarge) }
        }
        WineCard(candidate.wine, modifier = Modifier.fillMaxWidth(), onClick = onOpen)
        if (canConfirm) OutlinedButton(onClick = onConfirm, enabled = !busy) { Text("Это моё вино") }
    }
}
