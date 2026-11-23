package com.manima.foodlens.core.data

import com.manima.foodlens.core.data.image.ImageMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageMathTest {
    @Test
    fun largePhotoIsScaledToLongestEdge2000() {
        assertEquals(2000 to 1500, ImageMath.targetSize(4000, 3000))
        assertEquals(1125 to 2000, ImageMath.targetSize(2250, 4000))
    }

    @Test
    fun smallPhotoIsNeverUpscaled() {
        assertEquals(800 to 600, ImageMath.targetSize(800, 600))
    }

    @Test
    fun sampleSizeKeepsAtLeastTargetResolution() {
        assertEquals(2, ImageMath.sampleSize(4000, 3000))
        assertEquals(4, ImageMath.sampleSize(8000, 6000))
        assertEquals(1, ImageMath.sampleSize(3999, 3000))
        assertEquals(1, ImageMath.sampleSize(1000, 800))
    }

    @Test
    fun rotationIsNormalised() {
        assertEquals(270, ImageMath.normalizeDegrees(-90))
        assertEquals(0, ImageMath.normalizeDegrees(360))
        assertEquals(180, ImageMath.normalizeDegrees(540))
    }
}
