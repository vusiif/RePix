package com.example.picencrypt

import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.LcgShuffleScramble
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Arrays

class LcgShuffleScrambleTest {

    @Test
    fun testRoundTripReversibility() {
        val width = 64
        val height = 48
        val total = width * height
        val originalPixels = IntArray(total) { it * 31 + 7 }

        val image = Image(originalPixels, width, height)
        val key = "RePixStrongKey2026"

        val encrypter = LcgShuffleScramble(image, key)
        val encryptedImage = encrypter.process(ProcessType.ENCRYPT)
        val encryptedPixels = encryptedImage.pixels

        // Verify the image was thoroughly shuffled and not identical to original
        assertFalse("Encrypted pixels must not equal original pixels", Arrays.equals(originalPixels, encryptedPixels))

        // Decrypt the encrypted image using the exact same key
        val decrypter = LcgShuffleScramble(encryptedImage, key)
        val decryptedImage = decrypter.process(ProcessType.DECRYPT)
        val decryptedPixels = decryptedImage.pixels

        // Verify 100% bit-exact restoration
        assertArrayEquals("Decrypted pixels must exactly match original pixels", originalPixels, decryptedPixels)
    }

    @Test
    fun testOddAndNonPowerOfTwoDimensions() {
        val width = 37
        val height = 29
        val total = width * height
        val originalPixels = IntArray(total) { (it * 104729) xor 0x55AA55AA }

        val image = Image(originalPixels, width, height)
        val key = "NonPowerOfTwoSeed"

        val encrypted = LcgShuffleScramble(image, key).process(ProcessType.ENCRYPT)
        assertFalse(Arrays.equals(originalPixels, encrypted.pixels))

        val decrypted = LcgShuffleScramble(encrypted, key).process(ProcessType.DECRYPT)
        assertArrayEquals(originalPixels, decrypted.pixels)
    }

    @Test
    fun testKeySensitivity() {
        val width = 32
        val height = 32
        val total = width * height
        val originalPixels = IntArray(total) { it + 1 }
        val image = Image(originalPixels, width, height)

        val encA = LcgShuffleScramble(image, "SecretKey1").process(ProcessType.ENCRYPT)
        val encB = LcgShuffleScramble(image, "SecretKey2").process(ProcessType.ENCRYPT)

        assertFalse("Different keys must produce different shuffles", Arrays.equals(encA.pixels, encB.pixels))
    }

    @Test
    fun testEdgeCases() {
        // Single pixel
        val singlePixel = intArrayOf(0x12345678)
        val singleImg = Image(singlePixel, 1, 1)
        val encSingle = LcgShuffleScramble(singleImg, "key").process(ProcessType.ENCRYPT)
        assertArrayEquals(singlePixel, encSingle.pixels)

        val decSingle = LcgShuffleScramble(encSingle, "key").process(ProcessType.DECRYPT)
        assertArrayEquals(singlePixel, decSingle.pixels)

        // Empty image
        val emptyPixels = IntArray(0)
        val emptyImg = Image(emptyPixels, 0, 0)
        val encEmpty = LcgShuffleScramble(emptyImg, "key").process(ProcessType.ENCRYPT)
        assertEquals(0, encEmpty.pixels.size)
    }

    @Test
    fun testJavaRandomSteppingSymmetry() {
        val rng = LcgShuffleScramble.JavaRandom(123456789L)
        val bounds = intArrayOf(2, 4, 8, 16, 32, 64, 100, 1000, 7777, 65536)

        for (bound in bounds) {
            val startSeed = rng.getSeed()
            val forwardVal = rng.nextInt(bound)
            val backwardVal = rng.previousInt(bound)

            assertEquals("Stepping back must produce the same random value", forwardVal, backwardVal)
            assertEquals("Stepping back must restore the exact internal seed", startSeed, rng.getSeed())
        }
    }
}
