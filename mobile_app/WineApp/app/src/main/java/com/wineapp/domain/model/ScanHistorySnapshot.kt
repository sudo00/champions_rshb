package com.wineapp.domain.model

/** History records the scan; saving is not a claim that the first candidate is correct. */
fun ScanResult.historySnapshot(imagePath: String, scannedAt: Long): SavedScan? {
    val id = scanId ?: return null
    if (recognitionStatus == "no_target") return null
    val confirmation = confirmation()
    val selected = confirmation?.candidate
    val absent = recognitionStatus == "not_in_catalog"
    val candidate = if (absent) null else selected?.wine ?: wine ?: scoredCandidates.firstOrNull()?.wine
    if (!absent && candidate == null) return null
    val displayWine = candidate ?: Wine(
        id = "", name = "Вино не найдено в каталоге", vintage = null, rating = null,
        reviewsCount = null, price = null, currency = null, region = null, country = null,
        variety = null, style = null, alcoholPercentage = null, imageUrl = null,
        description = message, winery = null
    )
    return SavedScan(
        id = id, wine = displayWine, labelPhotoPath = imagePath,
        confidence = if (absent) 0f else selected?.matchScore
            ?: scoredCandidates.firstOrNull { it.wine.id == displayWine.id }?.matchScore ?: confidence,
        conversation = emptyList(), scannedAt = scannedAt,
        recognitionStatus = when {
            absent -> "not_in_catalog"
            confirmation != null -> confirmation.source
            else -> "candidates_unverified"
        }
    )
}
