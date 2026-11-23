package com.example.picencrypt

import com.example.picencrypt.utils.MirageAutoOptimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirageAutoOptimizerTest {

    @Test
    fun testCalculateLuminanceStatsOnBlackAndWhite() {
        val blackPixels = IntArray(100) { 0xFF000000.toInt() } // ARGB black
        val blackStats = MirageAutoOptimizer.calculateLuminanceStats(blackPixels)
        assertEquals(0f, blackStats.mean, 0.01f)
        assertEquals(0f, blackStats.stdDev, 0.01f)
        assertEquals(1.0f, blackStats.darkRatio, 0.01f)
        assertEquals(0.0f, blackStats.lightRatio, 0.01f)

        val whitePixels = IntArray(100) { 0xFFFFFFFF.toInt() } // ARGB white
        val whiteStats = MirageAutoOptimizer.calculateLuminanceStats(whitePixels)
        assertEquals(255f, whiteStats.mean, 0.01f)
        assertEquals(0f, whiteStats.stdDev, 0.01f)
        assertEquals(0.0f, whiteStats.darkRatio, 0.01f)
        assertEquals(1.0f, whiteStats.lightRatio, 0.01f)
    }

    @Test
    fun testDarkCoverGetsBrightnessBoost() {
        // Create dark cover image with luminance ~80
        val darkCoverPixels = IntArray(200) {
            val gray = 80
            (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        // Create normal hidden image with luminance ~80
        val normalHiddenPixels = IntArray(200) {
            val gray = 80
            (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        val params = MirageAutoOptimizer.optimizeParams(darkCoverPixels, normalHiddenPixels)

        // Cover brightness must be positive to compensate for dark cover on white background
        assertTrue("Dark cover must receive positive brightness adjustment", params.coverBrightness > 15f)
        assertTrue("Cover contrast must be >= 1.0f", params.coverContrast >= 1.0f)
    }

    @Test
    fun testBrightHiddenGetsBrightnessSuppression() {
        // Create normal cover image with luminance ~190
        val normalCoverPixels = IntArray(200) {
            val gray = 190
            (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        // Create bright hidden image with luminance ~180 (too bright for black background)
        val brightHiddenPixels = IntArray(200) {
            val gray = 180
            (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        val params = MirageAutoOptimizer.optimizeParams(normalCoverPixels, brightHiddenPixels)

        // Hidden brightness must be negative to prevent show-through on white background
        assertTrue("Bright hidden must receive negative brightness adjustment", params.hiddenBrightness < -15f)
    }

    @Test
    fun testLowContrastCoverGetsContrastEnhancement() {
        // Low contrast (stdDev = 0)
        val flatCover = IntArray(100) { 0xFF999999.toInt() }
        val flatHidden = IntArray(100) { 0xFF444444.toInt() }

        val params = MirageAutoOptimizer.optimizeParams(flatCover, flatHidden)
        assertTrue("Flat cover should have contrast boosted", params.coverContrast > 1.0f)
        assertTrue("Flat hidden should have contrast boosted", params.hiddenContrast > 1.0f)
    }
}
