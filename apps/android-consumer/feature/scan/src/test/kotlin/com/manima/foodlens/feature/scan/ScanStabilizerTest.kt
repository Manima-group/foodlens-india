package com.manima.foodlens.feature.scan

import com.manima.foodlens.core.model.Symbology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanStabilizerTest {
    private var now = 0L
    private val stabilizer = ScanStabilizer(requiredFrames = 2, debounceMs = 2_000, now = { now })

    private fun d(value: String, x: Float = 640f, y: Float = 360f) = Detection(value, Symbology.EAN_13, x, y)

    @Test
    fun acceptsOnlyAfterTwoConsecutiveMatchingFrames() {
        assertNull(stabilizer.onFrame(listOf(d("A")), 1280, 720))
        assertEquals("A", stabilizer.onFrame(listOf(d("A")), 1280, 720)?.rawValue)
    }

    @Test
    fun aDifferentValueResetsTheStreak() {
        stabilizer.onFrame(listOf(d("A")), 1280, 720)
        assertNull(stabilizer.onFrame(listOf(d("B")), 1280, 720))
        assertEquals("B", stabilizer.onFrame(listOf(d("B")), 1280, 720)?.rawValue)
    }

    @Test
    fun anEmptyFrameResetsTheStreak() {
        stabilizer.onFrame(listOf(d("A")), 1280, 720)
        stabilizer.onFrame(emptyList(), 1280, 720)
        assertNull(stabilizer.onFrame(listOf(d("A")), 1280, 720))
    }

    @Test
    fun sameValueIsDebouncedFor2Seconds() {
        repeat(2) { stabilizer.onFrame(listOf(d("A")), 1280, 720) }
        now = 1_999
        repeat(2) { assertNull(stabilizer.onFrame(listOf(d("A")), 1280, 720)) }
        now = 2_000
        stabilizer.onFrame(listOf(d("A")), 1280, 720)
        assertEquals("A", stabilizer.onFrame(listOf(d("A")), 1280, 720)?.rawValue)
    }

    @Test
    fun aDifferentValueIsNotDebounced() {
        repeat(2) { stabilizer.onFrame(listOf(d("A")), 1280, 720) }
        stabilizer.onFrame(listOf(d("B")), 1280, 720)
        assertEquals("B", stabilizer.onFrame(listOf(d("B")), 1280, 720)?.rawValue)
    }

    @Test
    fun picksTheBarcodeNearestTheFrameCentre() {
        val frame = listOf(d("EDGE", x = 50f, y = 50f), d("CENTRE", x = 600f, y = 380f), d("FAR", x = 1200f, y = 700f))
        assertEquals("CENTRE", ScanStabilizer.nearestToCentre(frame, 1280, 720)?.rawValue)
        stabilizer.onFrame(frame, 1280, 720)
        assertEquals("CENTRE", stabilizer.onFrame(frame.reversed(), 1280, 720)?.rawValue)
    }

    @Test
    fun resetAllowsImmediateRescan() {
        repeat(2) { stabilizer.onFrame(listOf(d("A")), 1280, 720) }
        stabilizer.reset()
        stabilizer.onFrame(listOf(d("A")), 1280, 720)
        assertEquals("A", stabilizer.onFrame(listOf(d("A")), 1280, 720)?.rawValue)
    }
}
