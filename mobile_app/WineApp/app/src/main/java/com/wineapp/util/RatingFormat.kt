package com.wineapp.util

import java.util.Locale

/**
 * Рейтинг вина — всегда через точку («4.70»), независимо от языка устройства:
 * String.format без локали на русской системе ставит запятую.
 */
fun formatRating(rating: Float, decimals: Int = 2): String =
    String.format(Locale.US, "%.${decimals}f", rating)
