package com.wineapp.domain.model

data class ScanConfirmation(val candidate: ScoredWine, val source: String)

/** UI policy, not a calibrated probability or a replacement for the server's refusal. */
fun ScanResult.confirmation(): ScanConfirmation? {
    if (recognitionStatus in setOf("not_in_catalog", "no_target")) return null
    scoredCandidates.firstOrNull { it.slug == userConfirmedSlug }?.let {
        return ScanConfirmation(it, "user_confirmed")
    }
    val first = scoredCandidates.firstOrNull() ?: return null
    val score = first.matchScore ?: return null
    return if (first.rank == 1 && score.isFinite() && score > .50f && score <= 1f)
        ScanConfirmation(first, "score_confirmed") else null
}

/** Keep the detail screen consistent with the selected card on scan results. */
fun ScanResult.detailRecognitionStatus(slug: String): String? {
    val selected = confirmation()?.takeIf { it.candidate.slug == slug }
    return when (selected?.source) {
        "user_confirmed" -> "confirmed_by_user"
        "score_confirmed" -> "score_confirmed"
        else -> recognitionStatus
    }
}
