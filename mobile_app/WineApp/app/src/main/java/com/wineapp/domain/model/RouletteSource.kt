package com.wineapp.domain.model

/**
 * Источник пула винной рулетки.
 *
 * COLLECTION — коллекция пользователя (записи погреба, статус наличия не важен);
 * FAVORITES — избранное.
 */
enum class RouletteSource {
    COLLECTION,
    FAVORITES
}
