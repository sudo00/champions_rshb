package com.wineapp.data.local

/**
 * Реестр винодельческих территорий России для «Винного пути».
 * Сырые строки региона из каталога грязные — нормализация идёт по алиасам.
 * totalWines — снэпшот каталога (заменить counts с бэка в фазе 2).
 * Координаты anchor — относительные (0..1) точки на схематичной карте.
 * locked = территорию нельзя открыть сканированием (вин нет в каталоге).
 */
data class Territory(
    val id: String,
    val name: String,
    /**
     * Родительный падеж названия — для званий вида «Знаток Кубани».
     * Хранится явно, потому что падежи не выводятся из имени строковой арифметикой.
     */
    val nameGenitive: String,
    val aliases: List<String>,
    val anchorX: Float,
    val anchorY: Float,
    val totalWines: Int,
    val locked: Boolean = false
)

object TerritoryRegistry {

    const val KUBAN = "kuban"
    const val CRIMEA = "crimea"
    const val DAGESTAN = "dagestan"
    const val DON = "don"
    const val STAVROPOL = "stavropol"
    const val VOLGA = "volga"
    const val SAMARA = "samara"
    const val OSSETIA = "ossetia"
    const val MOSCOW = "moscow"
    const val BASHKIRIA = "bashkiria"

    val territories: List<Territory> = listOf(
        Territory(KUBAN, "Кубань", "Кубани", listOf("кубань", "тамань", "краснодар"), 0.117f, 0.889f, 1067),
        Territory(CRIMEA, "Крым", "Крыма", listOf("крым", "севастополь", "ялта"), 0.088f, 0.895f, 769),
        Territory(DON, "Долина Дона", "Долины Дона", listOf("долина дона", "дона", "ростов"), 0.128f, 0.838f, 78),
        Territory(STAVROPOL, "Ставрополье", "Ставрополья", listOf("ставрополье", "ставрополь", "кавминводы", "кмв"), 0.139f, 0.909f, 43),
        Territory(OSSETIA, "Осетия", "Осетии", listOf("осетия", "алания"), 0.145f, 0.947f, 5),
        Territory(DAGESTAN, "Дагестан", "Дагестана", listOf("дагестан", "дербент"), 0.158f, 0.942f, 97),
        Territory(VOLGA, "Нижняя Волга", "Нижней Волги", listOf("нижняя волга", "волгоград", "астрахань"), 0.154f, 0.825f, 22),
        Territory(SAMARA, "Самара", "Самары", listOf("самара"), 0.181f, 0.698f, 21),
        Territory(BASHKIRIA, "Башкирия", "Башкирии", listOf("башкирия", "башкортостан", "уфа"), 0.220f, 0.681f, 0, locked = true),
        Territory(MOSCOW, "Подмосковье", "Подмосковья", listOf("подмосковье", "московская", "москва"), 0.107f, 0.643f, 0, locked = true)
    )

    val byId: Map<String, Territory> = territories.associateBy { it.id }

    /**
     * Нормализация сырого региона в territoryId. Возвращает null если не распознано.
     * Совпадение — вхождение алиаса (длинные алиасы проверяются первыми).
     */
    fun normalize(rawRegion: String?): String? {
        if (rawRegion.isNullOrBlank()) return null
        val normalized = rawRegion.trim().lowercase().replace('ё', 'е')
        // Более длинные алиасы первыми, чтобы «долина дона» не проиграла коротким.
        val all = territories.flatMap { t -> t.aliases.map { alias -> alias to t.id } }
            .sortedByDescending { it.first.length }
        for ((alias, id) in all) {
            if (normalized.contains(alias)) return id
        }
        return null
    }
}
