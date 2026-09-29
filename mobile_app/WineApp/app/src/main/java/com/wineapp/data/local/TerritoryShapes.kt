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
     * «Швы» фона: для каждого кольца [backgroundRings] — ломаные из рёбер, за которыми
     * (по внешней нормали) ближе [SEAM_EPS] лежит соседний регион. Контуры упрощали
     * по отдельности, поэтому общие границы соседей не совпадают и между ними видна
     * тёмная подложка. Карта обводит швы цветом заливки под самими заливками —
     * щели закрываются, а береговая линия (соседа за ней нет) остаётся как есть.
     */
    val backgroundSeams: List<List<List<Offset>>>
        get() = holder?.backgroundSeams ?: emptyList()

    /** Швы винных территорий по id — в цвете их заливки. */
    val outlineSeams: Map<String, List<List<Offset>>>
        get() = holder?.outlineSeams ?: emptyMap()

    /** Максимальная ширина щели, которую закрывают швы, в долях ширины карты. */
    const val SEAM_EPS = 0.005f

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
                Holder(emptyList(), emptyMap(), emptyList(), emptyMap())
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
        val backgroundSeams = computeSeams(background)
        // Винные контуры в JSON совпадают с кольцами фона — берём их швы по совпадению колец.
        val outlineSeams = outlines.mapValues { (_, rings) ->
            rings.flatMap { ring ->
                val index = background.indexOf(ring)
                if (index >= 0) backgroundSeams[index] else emptyList()
            }
        }
        return Holder(background, outlines, backgroundSeams, outlineSeams)
    }

    private fun computeSeams(rings: List<List<Offset>>): List<List<List<Offset>>> {
        val eps = SEAM_EPS
        val boxes = rings.map { ring ->
            floatArrayOf(
                ring.minOf { it.x } - eps, ring.minOf { it.y } - eps,
                ring.maxOf { it.x } + eps, ring.maxOf { it.y } + eps
            )
        }
        fun FloatArray.has(x: Float, y: Float) = x >= this[0] && x <= this[2] && y >= this[1] && y <= this[3]
        return rings.mapIndexed { i, ring ->
            val box = boxes[i]
            val neighbours = rings.indices.filter { k ->
                val other = boxes[k]
                k != i && other[2] >= box[0] && other[0] <= box[2] && other[3] >= box[1] && other[1] <= box[3]
            }
            val lines = ArrayList<List<Offset>>()
            var current: ArrayList<Offset>? = null
            for (s in 0 until ring.size - 1) {
                val a = ring[s]
                val b = ring[s + 1]
                if (isSeam(ring, a, b, neighbours.filter { boxes[it].has((a.x + b.x) / 2f, (a.y + b.y) / 2f) }.map { rings[it] })) {
                    if (current == null) {
                        current = arrayListOf(a)
                        lines.add(current)
                    }
                    current.add(b)
                } else {
                    current = null
                }
            }
            lines
        }
    }

    /** Ребро [a]–[b] кольца [ring] — шов, если снаружи от него (не внутрь) рядом чужое кольцо. */
    private fun isSeam(ring: List<Offset>, a: Offset, b: Offset, others: List<List<Offset>>): Boolean {
        val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val d = b - a
        val len = d.getDistance()
        if (len == 0f || others.isEmpty()) return false
        var normal = Offset(-d.y / len, d.x / len)
        if (contains(mid + normal * 1e-5f, ring)) normal = -normal
        for (other in others) {
            for (t in 0 until other.size - 1) {
                val toOther = nearestOnSegment(mid, other[t], other[t + 1]) - mid
                val dist = toOther.getDistance()
                // Совпадающая граница или сосед за ребром (а не вдоль берега у стыка).
                if (dist < 1e-6f) return true
                if (dist < SEAM_EPS && toOther.x * normal.x + toOther.y * normal.y > 0.7f * dist) return true
            }
        }
        return false
    }

    private fun nearestOnSegment(p: Offset, a: Offset, b: Offset): Offset {
        val d = b - a
        val lenSq = d.x * d.x + d.y * d.y
        val t = if (lenSq == 0f) 0f else (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / lenSq).coerceIn(0f, 1f)
        return a + d * t
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
        val outlines: Map<String, List<List<Offset>>>,
        val backgroundSeams: List<List<List<Offset>>>,
        val outlineSeams: Map<String, List<List<Offset>>>
    )
}
