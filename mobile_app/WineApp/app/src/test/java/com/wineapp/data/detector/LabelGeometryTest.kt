package com.wineapp.data.detector

import org.junit.Assert.*
import org.junit.Test

class LabelGeometryTest {
    @Test fun decodesNchwChannelsAndRemovesPortraitPadding() {
        val values = FloatArray(5 * 2100)
        values[0] = .5f; values[2100] = .5f; values[4200] = .25f; values[6300] = .5f; values[8400] = .9f
        val box = LabelLetterbox(100, 200).decode(values).single()
        assertEquals(.25f, box.left, .001f); assertEquals(.75f, box.right, .001f)
        assertEquals(.25f, box.top, .001f); assertEquals(.75f, box.bottom, .001f)
    }

    @Test fun nmsKeepsBestDuplicateAndRejectsLowOrNonfiniteScores() {
        val values = FloatArray(5 * 2100)
        for (i in 0..3) {
            values[i] = .5f; values[2100+i] = .5f; values[4200+i] = .5f; values[6300+i] = .5f
        }
        values[8400] = .8f; values[8401] = .9f; values[8402] = .3f; values[8403] = Float.NaN
        assertEquals(.9f, LabelLetterbox(320, 320).decode(values).single().score, .001f)
    }

    @Test fun captureWaitsForStabilityAndFiresOnlyOnceUntilTargetLeaves() {
        val gate = LabelCaptureGate()
        val target = listOf(LabelBox(.25f, .25f, .75f, .75f, .9f))
        for (time in 0L..800L step 100) assertFalse(gate.update(target, time))
        assertTrue(gate.update(target, 900))
        for (time in 1000L..2000L step 100) assertFalse(gate.update(target, time))
        gate.update(emptyList(), 4000)
        for (time in 4100L..4900L step 100) assertFalse(gate.update(target, time))
        assertTrue(gate.update(target, 5000))
    }

    @Test fun severalTargetsOrGapsCannotTriggerCapture() {
        val gate = LabelCaptureGate()
        val one = LabelBox(.2f, .2f, .5f, .8f, .9f)
        val two = LabelBox(.5f, .2f, .8f, .8f, .9f)
        for (time in 0L..2000L step 100) assertFalse(gate.update(listOf(one, two), time))
        for (time in 3000L..9000L step 500) assertFalse(gate.update(listOf(one), time))
    }
}
