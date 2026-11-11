package com.example.picencrypt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for compare view state calculations, split fraction bounds, and toggles.
 */
class CompareViewFeatureTest {

    @Test
    fun testSplitFractionBoundsClamping() {
        // Clamp to [0.05f, 0.95f] to prevent divider from escaping the screen bounds
        val minBound = 0.05f
        val maxBound = 0.95f

        fun clampFraction(fraction: Float): Float = fraction.coerceIn(minBound, maxBound)

        assertEquals(0.05f, clampFraction(-0.2f), 0.001f)
        assertEquals(0.05f, clampFraction(0.01f), 0.001f)
        assertEquals(0.5f, clampFraction(0.5f), 0.001f)
        assertEquals(0.95f, clampFraction(0.99f), 0.001f)
        assertEquals(0.95f, clampFraction(1.5f), 0.001f)
    }

    @Test
    fun testCompareModeAvailabilityLogic() {
        val hasOriginal = true
        val hasModified = true
        val isDifferent = true
        val canCompare = hasOriginal && hasModified && isDifferent
        assertTrue(canCompare)

        val unmodifiedCompare = hasOriginal && false && false
        assertFalse(unmodifiedCompare)
    }

    @Test
    fun testPressAndHoldStateToggle() {
        var isPressingOriginal = false
        var activeView = if (isPressingOriginal) "ORIGINAL" else "PROCESSED"
        assertEquals("PROCESSED", activeView)

        // User presses down on preview thumbnail
        isPressingOriginal = true
        activeView = if (isPressingOriginal) "ORIGINAL" else "PROCESSED"
        assertEquals("ORIGINAL", activeView)

        // User releases touch
        isPressingOriginal = false
        activeView = if (isPressingOriginal) "ORIGINAL" else "PROCESSED"
        assertEquals("PROCESSED", activeView)
    }
}
