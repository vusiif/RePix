package com.example.picencrypt.utils

import android.graphics.Bitmap
import kotlin.math.round
import kotlin.math.sqrt

object MirageAutoOptimizer {

    data class LuminanceStats(
        val mean: Float,
        val stdDev: Float,
        val darkRatio: Float,
        val lightRatio: Float
    )

    data class MirageTuneParams(
        val coverBrightness: Float,
        val coverContrast: Float,
        val hiddenBrightness: Float,
        val hiddenContrast: Float
    )

    /**
     * Calculate BT.601 luminance statistics (mean, std dev, dark/light pixel ratios)
     * using a single O(N) pass.
     */
    fun calculateLuminanceStats(pixels: IntArray): LuminanceStats {
        if (pixels.isEmpty()) {
            return LuminanceStats(128f, 0f, 0f, 0f)
        }

        var sumY = 0.0
        var darkCount = 0
        var lightCount = 0

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            sumY += y
            if (y < 64f) darkCount++
            else if (y > 192f) lightCount++
        }

        val total = pixels.size.toDouble()
        val mean = (sumY / total).toFloat()

        var varianceSum = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val diff = y - mean
            varianceSum += diff * diff
        }

        val stdDev = sqrt(varianceSum / total).toFloat()
        val darkRatio = (darkCount / total).toFloat()
        val lightRatio = (lightCount / total).toFloat()

        return LuminanceStats(mean, stdDev, darkRatio, lightRatio)
    }

    /**
     * Computes the recommended brightness and contrast adjustments for Cover and Hidden images
     * to maximize the optical visibility under white and black backgrounds while preventing leak-through.
     */
    fun optimizeParams(coverPixels: IntArray, hiddenPixels: IntArray): MirageTuneParams {
        val coverStats = calculateLuminanceStats(coverPixels)
        val hiddenStats = calculateLuminanceStats(hiddenPixels)

        // 1. Cover tuning (Target mean ~190f on white background)
        val rawCoverBrightness = if (coverStats.mean < 190f) {
            ((190f - coverStats.mean) * 0.45f).coerceIn(0f, 40f)
        } else {
            ((190f - coverStats.mean) * 0.2f).coerceIn(-15f, 0f)
        }
        val coverBrightness = round(rawCoverBrightness * 2f) / 2f // Round to 0.5 precision

        val rawCoverContrast = when {
            coverStats.stdDev < 35f -> (1.0f + (35f - coverStats.stdDev) / 70f).coerceIn(1.0f, 1.35f)
            coverStats.stdDev > 65f -> (1.0f - (coverStats.stdDev - 65f) / 150f).coerceIn(0.85f, 1.0f)
            else -> 1.0f
        }
        val coverContrast = round(rawCoverContrast * 100f) / 100f

        // 2. Hidden tuning (Target mean ~75f on black background)
        val rawHiddenBrightness = if (hiddenStats.mean > 75f) {
            ((75f - hiddenStats.mean) * 0.45f).coerceIn(-40f, 0f)
        } else {
            ((75f - hiddenStats.mean) * 0.25f).coerceIn(0f, 25f)
        }
        val hiddenBrightness = round(rawHiddenBrightness * 2f) / 2f

        val rawHiddenContrast = when {
            hiddenStats.stdDev < 40f -> 1.15f
            else -> 1.05f
        }
        val hiddenContrast = round(rawHiddenContrast * 100f) / 100f

        return MirageTuneParams(
            coverBrightness = coverBrightness,
            coverContrast = coverContrast,
            hiddenBrightness = hiddenBrightness,
            hiddenContrast = hiddenContrast
        )
    }

    /**
     * Extract pixels from cover and hidden bitmaps and compute optimal tuning parameters.
     */
    fun optimize(coverBitmap: Bitmap, hiddenBitmap: Bitmap): MirageTuneParams {
        val cPixels = IntArray(coverBitmap.width * coverBitmap.height)
        coverBitmap.getPixels(cPixels, 0, coverBitmap.width, 0, 0, coverBitmap.width, coverBitmap.height)

        val hPixels = IntArray(hiddenBitmap.width * hiddenBitmap.height)
        hiddenBitmap.getPixels(hPixels, 0, hiddenBitmap.width, 0, 0, hiddenBitmap.width, hiddenBitmap.height)

        return optimizeParams(cPixels, hPixels)
    }
}
