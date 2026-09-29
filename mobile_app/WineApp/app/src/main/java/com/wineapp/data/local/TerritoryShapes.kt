package com.wineapp.data.local

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.wineapp.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Границы для карты «Винного пути»: все субъекты РФ фоном + винодельческие территории.
 *
 * res/raw/territories.json собран из OSM (© участники OpenStreetMap, ODbL):
 * субъекты — timurkanaz/Russia_geojson_OSM; ДНР, ЛНР, Запорожская и Херсонская области —
 * geoBoundaries ADM1 (тоже OSM), их стыки притянуты к границам соседних субъектов.
 * Упрощение топологическое: общая граница соседей — одна дуга, упрощённая один раз,
 * поэтому у соседей она совпадает точно (без щелей и двойных линий).
 * Проекция: x = (lon − 19) / 174 (Чукотка за 180° — lon + 360), y = (82 − lat) / 41.
 *
 * Кольца фона отсортированы по площади по убыванию: анклавы (Адыгея, Москва) идут после
 * объемлющих регионов и рисуются поверх. Контуры винных территорий — точные копии
 * колец фона их субъектов.
 * API: [backgroundRings], [backgroundTerritories], [outlines], [territoryAt].
 */
object TerritoryShapes {

    @Volatile
    private var holder: Holder? = null

    val isLoaded: Boolean
        get() = holder != null

    /** Фон: все субъекты РФ, большие первыми. До загрузки — пусто. */
    val backgroundRings: List<List<Offset>>
        get() = holder?.background ?: emptyList()

    /** Территория каждого кольца [backgroundRings] (null — не винный регион). */
    val backgroundTerritories: List<String?>
        get() = holder?.backgroundTerritories ?: emptyList()

    /** Контуры винных территорий по id. До загрузки — пусто. */
    val outlines: Map<String, List<List<Offset>>>
        get() = holder?.outlines ?: emptyMap()

    /**
     * Загрузить и распарсить JSON. Идемпотентно и потокобезопасно.
     * Вызывать с фонового потока — парсинг ~270 КБ занимает десятки миллисекунд.
     */
    fun ensureLoaded(context: Context) {
        if (holder != null) return
        synchronized(this) {
            if (holder != null) return
            holder = try {
                parse(context.resources.openRawResource(R.raw.territories).bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.e("TerritoryShapes", "Failed to load territories.json", e)
                Holder(emptyList(), emptyList(), emptyMap())
            }
        }
    }

    private fun parse(json: String): Holder {
        val root = JSONObject(json)
        fun readRing(arr: JSONArray): List<Offset> {
            val ring = ArrayList<Offset>(arr.length())
            for (i in 0 until arr.length()) {
                val pt = arr.getJSONArray(i)
                ring.add(Offset(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat()))
            }
            return ring
        }
        val bgJson = root.getJSONArray("background")
        val background = ArrayList<List<Offset>>(bgJson.length())
        for (i in 0 until bgJson.length()) {
            background.add(readRing(bgJson.getJSONArray(i)))
        }
        val outlinesJson = root.getJSONObject("outlines")
        val outlines = HashMap<String, List<List<Offset>>>(outlinesJson.length())
        val keys = outlinesJson.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val regionJson = outlinesJson.getJSONArray(id)
            val rings = ArrayList<List<Offset>>(regionJson.length())
            for (i in 0 until regionJson.length()) {
                rings.add(readRing(regionJson.getJSONArray(i)))
            }
            outlines[id] = rings
        }
        // Контуры — точные копии колец фона: сопоставляем по совпадению колец.
        val territories = arrayOfNulls<String>(background.size)
        outlines.forEach { (id, rings) ->
            rings.forEach { ring ->
                val index = background.indexOf(ring)
                if (index >= 0) territories[index] = id
                else Log.w("TerritoryShapes", "Outline ring of $id not found in background")
            }
        }
        return Holder(background, territories.toList(), outlines)
    }

    /** Ray casting: точка внутри полигона или нет. Координаты — в одном пространстве. */
    fun contains(point: Offset, polygon: List<Offset>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            if ((yi > point.y) != (yj > point.y) &&
                point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Территория под точкой (дроби 0..1). Ищем с конца фона — там меньшие кольца,
     * поэтому анклав побеждает объемлющий регион: тап по Адыгее не выбирает Кубань.
     */
    fun territoryAt(point: Offset): String? {
        val current = holder ?: return null
        for (i in current.background.indices.reversed()) {
            if (contains(point, current.background[i])) return current.backgroundTerritories[i]
        }
        return null
    }

    private data class Holder(
        val background: List<List<Offset>>,
        val backgroundTerritories: List<String?>,
        val outlines: Map<String, List<List<Offset>>>
    )
}
