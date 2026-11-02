package com.example.picencrypt

import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.utils.BlockScramble
import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.PerPixelScramble
import com.example.picencrypt.utils.PicEncryptRowColumnScramble
import com.example.picencrypt.utils.PicEncryptRowScramble
import com.example.picencrypt.utils.RowPixelScramble
import com.example.picencrypt.utils.TomatoScramble
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Comprehensive round-trip reversibility and boundary test suite for all scramble algorithms.
 */
class AlgorithmRoundTripTest {

    private fun createGradientImage(width: Int, height: Int): Image {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width.coerceAtLeast(1)) and 0xFF
                val g = (y * 255 / height.coerceAtLeast(1)) and 0xFF
                val b = ((x + y) * 128 / (width + height).coerceAtLeast(1)) and 0xFF
                val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                pixels[y * width + x] = color
            }
        }
        return Image(pixels, width, height)
    }

    private fun createRandomImage(width: Int, height: Int, seed: Long = 42L): Image {
        val rng = Random(seed)
        val pixels = IntArray(width * height) {
            (0xFF shl 24) or (rng.nextInt(256) shl 16) or (rng.nextInt(256) shl 8) or rng.nextInt(256)
        }
        return Image(pixels, width, height)
    }

    @Test
    fun testTomatoScrambleRoundTrip() {
        val w = 32
        val h = 32
        val original = createGradientImage(w, h)
        val copyOfPixels = original.pixels.clone()

        val encrypted = TomatoScramble(Image(original.pixels.clone(), w, h), 1.0).process(ProcessType.ENCRYPT)
        // Ensure image was actually altered
        assertFalse("Pixels should be scrambled", copyOfPixels.contentEquals(encrypted.pixels))

        val decrypted = TomatoScramble(Image(encrypted.pixels.clone(), w, h), 1.0).process(ProcessType.DECRYPT)
        assertArrayEquals("Decrypted pixels must 100% match original", copyOfPixels, decrypted.pixels)
    }

    @Test
    fun testBlockScrambleRoundTripWithBlockMultiple() {
        // BlockScramble defaults to 32x32 block grid. Test exact block multiples (e.g. 64x64)
        val w = 64
        val h = 64
        val original = createGradientImage(w, h)
        val copyOfPixels = original.pixels.clone()

        val encrypted = BlockScramble(Image(original.pixels.clone(), w, h), "testKey123").process(ProcessType.ENCRYPT)
        assertFalse("Pixels should be scrambled", copyOfPixels.contentEquals(encrypted.pixels))
        assertEquals(64, encrypted.width)
        assertEquals(64, encrypted.height)

        val decrypted = BlockScramble(Image(encrypted.pixels.clone(), w, h), "testKey123").process(ProcessType.DECRYPT)
        assertArrayEquals("Decrypted pixels must 100% match original", copyOfPixels, decrypted.pixels)
    }

    @Test
    fun testBlockScramblePaddingOnNonMultiples() {
        // Non-multiple dimensions (e.g. 40x50) get rounded up to the nearest multiple of 32
        val w = 40
        val h = 50
        val original = createGradientImage(w, h)

        val encrypted = BlockScramble(Image(original.pixels.clone(), w, h), "key").process(ProcessType.ENCRYPT)
        assertEquals("Width must be padded to multiple of 32", 64, encrypted.width)
        assertEquals("Height must be padded to multiple of 32", 64, encrypted.height)
    }

    @Test
    fun testRowPixelScrambleRoundTrip() {
        val w = 35
        val h = 45
        val original = createRandomImage(w, h)
        val copyOfPixels = original.pixels.clone()

        val encrypted = RowPixelScramble(Image(original.pixels.clone(), w, h), "0.75").process(ProcessType.ENCRYPT)
        assertFalse("Pixels should be scrambled", copyOfPixels.contentEquals(encrypted.pixels))

        val decrypted = RowPixelScramble(Image(encrypted.pixels.clone(), w, h), "0.75").process(ProcessType.DECRYPT)
        assertArrayEquals("Decrypted pixels must 100% match original", copyOfPixels, decrypted.pixels)
    }

    @Test
    fun testPerPixelScrambleRoundTrip() {
        val w = 24
        val h = 30
        val original = createGradientImage(w, h)
        val copyOfPixels = original.pixels.clone()

        val encrypted = PerPixelScramble(Image(original.pixels.clone(), w, h), "SecretKey").process(ProcessType.ENCRYPT)
        assertFalse("Pixels should be scrambled", copyOfPixels.contentEquals(encrypted.pixels))

        val decrypted = PerPixelScramble(Image(encrypted.pixels.clone(), w, h), "SecretKey").process(ProcessType.DECRYPT)
        assertArrayEquals("Decrypted pixels must 100% match original", copyOfPixels, decrypted.pixels)
    }

    @Test
    fun testPicEncryptRowScrambleRoundTrip() {
        val w = 50
        val h = 50
        val original = createGradientImage(w, h)
        val copyOfPixels = original.pixels.clone()

        val encrypted = PicEncryptRowScramble(Image(original.pixels.clone(), w, h), 0.618).process(ProcessType.ENCRYPT)
        assertFalse("Pixels should be scrambled", copyOfPixels.contentEquals(encrypted.pixels))

        val decrypted = PicEncryptRowScramble(Image(encrypted.pixels.clone(), w, h), 0.618).process(ProcessType.DECRYPT)
        assertArrayEquals("Decrypted pixels must 100% match original", copyOfPixels, decrypted.pixels)
    }

    @Test
    fun testPicEncryptRowColumnScrambleRoundTrip() {
        val w = 48
        val h = 48
        val original = createGradientImage(w, h)
        val copyOfPixels = original.pixels.clone()

        val encrypted = PicEncryptRowColumnScramble(Image(original.pixels.clone(), w, h), 0.45).process(ProcessType.ENCRYPT)
        assertFalse("Pixels should be scrambled", copyOfPixels.contentEquals(encrypted.pixels))

        val decrypted = PicEncryptRowColumnScramble(Image(encrypted.pixels.clone(), w, h), 0.45).process(ProcessType.DECRYPT)
        assertArrayEquals("Decrypted pixels must 100% match original", copyOfPixels, decrypted.pixels)
    }

    @Test
    fun testAlgorithmKeyValidityRules() {
        // Tomato key validity: (0.0, 1.618]
        assertTrue(Algorithm.checkTomatoKeyValidity("1"))
        assertTrue(Algorithm.checkTomatoKeyValidity("1.618"))
        assertTrue(Algorithm.checkTomatoKeyValidity("0.001"))
        assertFalse(Algorithm.checkTomatoKeyValidity("0"))
        assertFalse(Algorithm.checkTomatoKeyValidity("0.0"))
        assertFalse(Algorithm.checkTomatoKeyValidity("1.619"))
        assertFalse(Algorithm.checkTomatoKeyValidity("-0.5"))
        assertFalse(Algorithm.checkTomatoKeyValidity("not_a_number"))

        // PicEncrypt key validity: (0.0, 1.0)
        assertTrue(Algorithm.checkPicEncryptKeyValidity("0.5"))
        assertTrue(Algorithm.checkPicEncryptKeyValidity("0.001"))
        assertTrue(Algorithm.checkPicEncryptKeyValidity("0.999"))
        assertFalse(Algorithm.checkPicEncryptKeyValidity("0"))
        assertFalse(Algorithm.checkPicEncryptKeyValidity("1.0"))
        assertFalse(Algorithm.checkPicEncryptKeyValidity("1.5"))
        assertFalse(Algorithm.checkPicEncryptKeyValidity("-0.1"))
        assertFalse(Algorithm.checkPicEncryptKeyValidity("abc"))
    }
}
