package com.wineapp.data.game

import com.wineapp.data.local.Territory
import com.wineapp.data.local.TerritoryRegistry
import kotlin.math.ceil

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

/**
 * Ступени освоения одной территории. Название ступени идёт перед названием региона
 * в родительном падеже: «Знаток Кубани», «Легенда Нижней Волги».
 */
enum class TerritoryTier(
    val codePrefix: String,
    val title: String,
    val description: String,
    val points: Int,
    /** Доля каталога территории для ступени; 0 = достаточно первого вина. */
    val share: Double
) {
    PIONEER("pioneer", "Первопроходец", "Отсканируйте первое вино региона", 50, 0.0),
    EXPERT("expert", "Знаток", "Отсканируйте 50% вин региона", 100, 0.5),
    LEGEND("legend", "Легенда", "Отсканируйте все вина региона", 200, 1.0);

    /** Сколько вин территории нужно отсканировать, чтобы получить ступень. */
    fun requiredWines(totalWines: Int): Int = when (this) {
        PIONEER -> 1
        EXPERT -> ceil(totalWines * share).toInt()
        LEGEND -> totalWines
    }
}

object BadgeDefs {

    const val FIRST_SCAN = "first_scan"
    const val EXPLORER_3 = "explorer_3"
    const val EXPLORER_5 = "explorer_5"
    const val EXPLORER_8 = "explorer_8"
    const val TASTER_10 = "taster_10"
    const val TASTER_50 = "taster_50"

    const val POINTS_SCAN = 10

    /** Сколько территорий можно открыть сканированием — порог «Легенды пути». */
    val OPENABLE_TERRITORIES: Int = TerritoryRegistry.territories.count { !it.locked }

    fun codeFor(tier: TerritoryTier, territoryId: String) = "${tier.codePrefix}_$territoryId"

    fun territoryBadge(tier: TerritoryTier, territory: Territory): BadgeDef = BadgeDef(
        code = codeFor(tier, territory.id),
        title = "${tier.title} ${territory.nameGenitive}",
        description = tier.description,
        points = tier.points
    )

    /**
     * Порядок вывода в шторке: сначала ступень, внутри ступени — от крупной территории
     * к мелкой, чтобы «Первопроходец Кубани» шёл первым.
     */
    val territoryBadges: List<BadgeDef> = TerritoryTier.entries.flatMap { tier ->
        TerritoryRegistry.territories
            .filterNot { it.locked }
            .sortedByDescending { it.totalWines }
            .map { territoryBadge(tier, it) }
    }

    val generalBadges: List<BadgeDef> = listOf(
        BadgeDef(FIRST_SCAN, "Первый глоток", "Отсканируйте первую этикетку", 10),
        BadgeDef(EXPLORER_3, "Исследователь", "Откройте 3 территории", 30),
        BadgeDef(EXPLORER_5, "Путешественник", "Откройте 5 территорий", 100),
        // Код explorer_8 историческое имя (хранится в БД); порог — все открываемые территории.
        BadgeDef(EXPLORER_8, "Легенда пути", "Откройте все $OPENABLE_TERRITORIES территорий", 200),
        BadgeDef(TASTER_10, "Дегустатор", "Отсканируйте 10 вин", 20),
        BadgeDef(TASTER_50, "Сомелье", "Отсканируйте 50 вин", 100)
    )

    val all: List<BadgeDef> = generalBadges + territoryBadges

    val byCode: Map<String, BadgeDef> = all.associateBy { it.code }

    /** Пороги уровней по суммарным очкам. */
    val levelThresholds: List<Int> = listOf(0, 100, 300, 600, 1000, 1600, 2400)

    /**
     * Полное описание уровня по суммарным очкам: номер, прогресс внутри уровня
     * и границы по очкам — нужны шторке «Винного пути» для шкалы уровня.
     */
    data class LevelInfo(
        val level: Int,
        val progress: Float,
        val pointsFrom: Int,
        val pointsTo: Int
    )

    fun levelFor(points: Int): LevelInfo {
        var level = 1
        for (i in levelThresholds.indices) {
            if (points >= levelThresholds[i]) level = i + 1
        }
        val from = levelThresholds.getOrElse(level - 1) { 0 }
        val to = levelThresholds.getOrElse(level) { from }
        val progress = if (to > from) {
            ((points - from).toFloat() / (to - from)).coerceIn(0f, 1f)
        } else 1f
        return LevelInfo(level = level, progress = progress, pointsFrom = from, pointsTo = to)
    }
}
