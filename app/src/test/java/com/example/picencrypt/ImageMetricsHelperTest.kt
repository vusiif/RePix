package com.example.picencrypt

import com.example.picencrypt.utils.ImageMetricsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ImageMetricsHelperTest {

    @Test
    fun testMonochromeImageMetrics() {
        val w = 64
        val h = 64
        // All white pixels
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val metrics = ImageMetricsHelper.calculateMetrics(pixels, w, h)

        // Monochromatic image has 0 entropy
        assertEquals(0.0, metrics.entropy, 0.001)
        // No variance, correlation is 0
        assertEquals(0.0, metrics.correlation, 0.001)
        // Highest bin should be 1.0f
        assertEquals(1.0f, metrics.rHistogram[31], 0.001f)
    }

    @Test
    fun testGradientHighCorrelationLowScramble() {
        val w = 100
        val h = 100
        val pixels = IntArray(w * h)
        // Smooth horizontal gradient: adjacent pixels differ by only 1~2
        for (y in 0 until h) {
            for (x in 0 until w) {
                val gray = (x * 255 / w).coerceIn(0, 255)
                val color = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                pixels[y * w + x] = color
            }
        }

        val metrics = ImageMetricsHelper.calculateMetrics(pixels, w, h)
        // Gradient has high adjacent pixel correlation (near 1.0)
        assertTrue("Correlation should be high for smooth gradient: ${metrics.correlation}", metrics.correlation > 0.95)
        // Scramble score should be low because it's an orderly natural pattern
        assertTrue("Scramble score should be low for gradient: ${metrics.scrambleScore}", metrics.scrambleScore < 60f)
    }

    @Test
    fun testRandomNoiseHighEntropyHighScramble() {
        val w = 120
        val h = 120
        val pixels = IntArray(w * h)
        val random = Random(42)
        // Uniform random noise simulating scrambled image
        for (i in pixels.indices) {
            val r = random.nextInt(256)
            val g = random.nextInt(256)
            val b = random.nextInt(256)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val metrics = ImageMetricsHelper.calculateMetrics(pixels, w, h)
        // Three independent uniform channels sum to a bell-shaped gray distribution (~7.3-7.6 bits)
        assertTrue("Entropy should be high for noise: ${metrics.entropy}", metrics.entropy > 7.2)
        // Correlation between independent random pixels should be near 0.0
        assertTrue("Correlation should be near 0: ${metrics.correlation}", Math.abs(metrics.correlation) < 0.05)
        // Scramble score should be high (> 80%)
        assertTrue("Scramble score should be > 80%: ${metrics.scrambleScore}", metrics.scrambleScore > 80f)
    }

    @Test
    fun testPixelPermutationDrasticallyReducesCorrelation() {
        val w = 80
        val h = 80
        val pixels = IntArray(w * h)
        // Create an image with strong horizontal striping / spatial correlation
        for (y in 0 until h) {
            val rowBase = (y * 3) % 256
            for (x in 0 until w) {
                val color = (0xFF shl 24) or (rowBase shl 16) or (rowBase shl 8) or rowBase
                pixels[y * w + x] = color
            }
        }

        val beforeMetrics = ImageMetricsHelper.calculateMetrics(pixels, w, h)
        assertEquals("Adjacent horizontal pixels in same row have correlation 1.0", 1.0, beforeMetrics.correlation, 0.001)

        // Shuffle pixel positions (simulating scrambling)
        val scrambled = pixels.clone()
        val random = Random(1337)
        for (i in scrambled.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val temp = scrambled[i]
            scrambled[i] = scrambled[j]
            scrambled[j] = temp
        }

        val afterMetrics = ImageMetricsHelper.calculateMetrics(scrambled, w, h)
        // Entropy remains identical (same histogram)
        assertEquals("Permutation preserves histogram entropy", beforeMetrics.entropy, afterMetrics.entropy, 0.001)
        // But spatial correlation crashes to near 0!
        assertTrue("Shuffling should collapse correlation: ${afterMetrics.correlation}", Math.abs(afterMetrics.correlation) < 0.05)
        // Scramble score increases dramatically
        assertTrue("Scramble score should rise after shuffle", afterMetrics.scrambleScore > beforeMetrics.scrambleScore + 40f)
    }
}
