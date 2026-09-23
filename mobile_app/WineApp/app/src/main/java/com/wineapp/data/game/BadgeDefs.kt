package com.wineapp.data.game

/**
 * Определения бейджей «Винного пути». Тексты захардкожены русскими строками осознанно:
 * это контент-пак геймификации (как уровни в игре), а не UI-строки.
 */
data class BadgeDef(
    val code: String,
    val title: String,
    val description: String,
    val points: Int
)

object BadgeDefs {

    const val FIRST_SCAN = "first_scan"
    const val EXPLORER_3 = "explorer_3"
    const val EXPLORER_5 = "explorer_5"
    const val EXPLORER_8 = "explorer_8"
    const val TASTER_10 = "taster_10"
    const val TASTER_50 = "taster_50"

    fun pioneerCode(territoryId: String) = "pioneer_$territoryId"

    const val POINTS_SCAN = 10

    val all: List<BadgeDef> = buildList {
        add(BadgeDef(FIRST_SCAN, "Первый глоток", "Отсканируйте первую этикетку", 10))
        add(BadgeDef("pioneer_kuban", "Первопроходец Кубани", "Попробуйте вино Кубани", 50))
        add(BadgeDef("pioneer_crimea", "Крымский эксперт", "Попробуйте вино Крыма", 50))
        add(BadgeDef("pioneer_dagestan", "Первопроходец Дагестана", "Попробуйте вино Дагестана", 50))
        add(BadgeDef("pioneer_don", "Первопроходец Дона", "Попробуйте вино Долины Дона", 50))
        add(BadgeDef("pioneer_stavropol", "Первопроходец Ставрополья", "Попробуйте вино Ставрополья", 50))
        add(BadgeDef("pioneer_volga", "Первопроходец Волги", "Попробуйте вино Нижней Волги", 50))
        add(BadgeDef("pioneer_samara", "Первопроходец Самары", "Попробуйте вино Самары", 50))
        add(BadgeDef("pioneer_ossetia", "Первопроходец Осетии", "Попробуйте вино Осетии", 50))
        add(BadgeDef(EXPLORER_3, "Исследователь", "Откройте 3 территории", 30))
        add(BadgeDef(EXPLORER_5, "Путешественник", "Откройте 5 территорий", 100))
        add(BadgeDef(EXPLORER_8, "Легенда пути", "Откройте все 8 территорий", 200))
        add(BadgeDef(TASTER_10, "Дегустатор", "Отсканируйте 10 вин", 20))
        add(BadgeDef(TASTER_50, "Сомелье", "Отсканируйте 50 вин", 100))
    }

    val byCode: Map<String, BadgeDef> = all.associateBy { it.code }

    /** Пороги уровней по суммарным очкам. */
    val levelThresholds: List<Int> = listOf(0, 100, 300, 600, 1000, 1600, 2400)

    fun levelFor(points: Int): Pair<Int, Float> {
        var level = 1
        for (i in levelThresholds.indices) {
            if (points >= levelThresholds[i]) level = i + 1
        }
        val current = levelThresholds.getOrElse(level - 1) { points }
        val next = levelThresholds.getOrElse(level) { current + 1 }
        val progress = if (next > current) {
            ((points - current).toFloat() / (next - current)).coerceIn(0f, 1f)
        } else 1f
        return level to progress
    }
}
