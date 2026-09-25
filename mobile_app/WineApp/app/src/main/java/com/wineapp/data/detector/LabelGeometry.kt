package com.wineapp.data.detector

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class LabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float) {
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    fun iou(other: LabelBox): Float {
        val intersection = (min(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f) *
            (min(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        return intersection / (area + other.area - intersection).coerceAtLeast(1e-8f)
    }
    fun eligible(): Boolean = abs((left + right) / 2 - .5f) <= .25f &&
        (top + bottom) / 2 in .15f.. .85f && right - left >= .12f && bottom - top >= .1f &&
        area >= .03f && left > .01f && top > .01f && right < .99f && bottom < .99f
}

data class LabelLetterbox(val width: Int, val height: Int, val side: Int = 320) {
    val scale = min(side.toFloat() / width, side.toFloat() / height)
    val resizedWidth = (width * scale).roundToInt()
    val resizedHeight = (height * scale).roundToInt()
    val left = (side - resizedWidth) / 2
    val top = (side - resizedHeight) / 2
    fun decode(values: FloatArray, threshold: Float = .7f): List<LabelBox> {
        require(values.size == 5 * 2100)
        val proposals = (0 until 2100).mapNotNull { i ->
            val score = values[4 * 2100 + i]
            if (!score.isFinite() || score < threshold) return@mapNotNull null
            val cx = values[i] * side
            val cy = values[2100 + i] * side
            val w = values[4200 + i] * side
            val h = values[6300 + i] * side
            if (!listOf(cx, cy, w, h).all { it.isFinite() } || w <= 0 || h <= 0) return@mapNotNull null
            LabelBox(((cx - w / 2 - left) / scale / width).coerceIn(0f, 1f),
                ((cy - h / 2 - top) / scale / height).coerceIn(0f, 1f),
                ((cx + w / 2 - left) / scale / width).coerceIn(0f, 1f),
                ((cy + h / 2 - top) / scale / height).coerceIn(0f, 1f), score)
                .takeIf { it.area > 0 }
        }.sortedByDescending { it.score }
        val kept = mutableListOf<LabelBox>()
        for (box in proposals) {
            if (kept.none { it.iou(box) > .7f }) kept.add(box)
            if (kept.size == 100) break
        }
        return kept
    }
}

/** A stable single central target can fire once; absence is required to rearm. */
class LabelCaptureGate {
    private var previous: LabelBox? = null
    private var since = 0L
    private var lastSeen = 0L
    private var frames = 0
    private var fired = false

    fun reset() { previous = null; since = 0L; lastSeen = 0L; frames = 0; fired = false }

    fun update(boxes: List<LabelBox>, now: Long): Boolean {
        val eligible = boxes.filter { it.eligible() }
        if (eligible.size != 1) {
            previous = null; frames = 0
            if (now - lastSeen > 1500) fired = false
            return false
        }
        val target = eligible.single()
        if (previous?.iou(target)?.let { it >= .75f } != true || now - lastSeen > 400) {
            since = now; frames = 0
        }
        previous = target; lastSeen = now; frames++
        if (!fired && frames >= 6 && now - since >= 900) { fired = true; return true }
        return false
    }
}
