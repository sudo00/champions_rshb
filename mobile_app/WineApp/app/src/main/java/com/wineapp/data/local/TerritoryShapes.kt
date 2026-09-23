package com.wineapp.data.local

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.wineapp.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Границы для карты «Винного пути»: вся Россия фоном + детальные винодельческие регионы.
 * Данные лежат в res/raw/territories.json (GeoJSON после генерализации Douglas-Peucker;
 * Крым — по реальным координатам побережья), грузятся лениво один раз.
 * API: [backgroundRings], [outlines], [territoryAt]. Координаты — доли карты 0..1
 * (вьюпорт lon 19–193 с заворотом Чукотки, lat 41–82).
 */
object TerritoryShapes {

    @Volatile
    private var holder: Holder? = null

    val isLoaded: Boolean
        get() = holder != null

    /** Фон: все субъекты РФ. До загрузки — пусто. */
    val backgroundRings: List<List<Offset>>
        get() = holder?.background ?: emptyList()

    /** Контуры винных территорий по id. До загрузки — пусто. */
    val outlines: Map<String, List<List<Offset>>>
        get() = holder?.outlines ?: emptyMap()

    /**
     * Загрузить и распарсить JSON. Идемпотентно и потокобезопасно.
     * Вызывать с фонового потока — парсинг ~100 КБ занимает десятки миллисекунд.
     */
    fun ensureLoaded(context: Context) {
        if (holder != null) return
        synchronized(this) {
            if (holder != null) return
            holder = try {
                parse(context.resources.openRawResource(R.raw.territories).bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.e("TerritoryShapes", "Failed to load territories.json", e)
                Holder(emptyList(), emptyMap())
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
        return Holder(background, outlines)
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

    /** Территория под точкой (дроби 0..1). Маленькие полигоны проверяются первыми. */
    fun territoryAt(point: Offset): String? {
        return outlines.entries
            .sortedBy { (_, rings) -> rings.maxOfOrNull { polygonArea(it) } ?: 0f }
            .firstOrNull { (_, rings) -> rings.any { contains(point, it) } }
            ?.key
    }

    private fun polygonArea(poly: List<Offset>): Float {
        var area = 0f
        var j = poly.size - 1
        for (i in poly.indices) {
            area += (poly[j].x + poly[i].x) * (poly[j].y - poly[i].y)
            j = i
        }
        return kotlin.math.abs(area / 2f)
    }

    private data class Holder(
        val background: List<List<Offset>>,
        val outlines: Map<String, List<List<Offset>>>
    )
}
