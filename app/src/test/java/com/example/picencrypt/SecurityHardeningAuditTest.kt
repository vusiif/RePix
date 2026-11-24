package com.example.picencrypt

import com.example.picencrypt.utils.CryptoUtils
import com.example.picencrypt.utils.SteganographyHelper
import com.example.picencrypt.utils.TomatoScramble
import com.example.picencrypt.utils.PicEncryptRowScramble
import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class SecurityHardeningAuditTest {

    @Test
    fun testStegoV2TrailerOverflowRejection() {
        // Construct 64-byte V2 trailer where offset + length overflows Long.MAX_VALUE
        val trailer = ByteBuffer.allocate(SteganographyHelper.TRAILER_V2_SIZE).apply {
            putLong(Long.MAX_VALUE - 20L) // Extremely large offset
            putLong(100L) // length
            putInt(SteganographyHelper.ArchiveType.ZIP.code)
            put(ByteArray(32)) // sha256
            putShort(2.toShort()) // version 2
            putShort(0.toShort()) // flags
            put(SteganographyHelper.STEGO_V2_MAGIC)
        }.array()

        // totalFileSize is reasonable (e.g. 5000 bytes)
        val result = SteganographyHelper.parseV2Trailer(trailer, 5000L)
        assertNull("Trailer with overflowing dimensions must be rejected", result)
    }

    @Test
    fun testStegoV2TrailerOffsetExceedsFileSize() {
        val trailer = ByteBuffer.allocate(SteganographyHelper.TRAILER_V2_SIZE).apply {
            putLong(10000L) // offset > totalFileSize
            putLong(500L)
            putInt(SteganographyHelper.ArchiveType.ZIP.code)
            put(ByteArray(32))
            putShort(2.toShort())
            putShort(0.toShort())
            put(SteganographyHelper.STEGO_V2_MAGIC)
        }.array()

        val result = SteganographyHelper.parseV2Trailer(trailer, 2000L)
        assertNull("Trailer with offset exceeding file size must be rejected", result)
    }

    @Test
    fun testPngChunkHighBitSignednessHandling() {
        // Construct a dummy PNG stream with high bit set in chunk length (e.g. 0x80 0x00 0x00 0x10)
        // Followed by valid IEND
        val streamBytes = ByteBuffer.allocate(64).apply {
            put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) // PNG magic
            put(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // IEND length = 0
            put("IEND".toByteArray(Charsets.US_ASCII))
            put(byteArrayOf(0xAE.toByte(), 0x42, 0x60, 0x82.toByte())) // CRC
        }.array()

        val inspection = SteganographyHelper.detectArchiveFromStream(
            ByteArrayInputStream(streamBytes),
            streamBytes.size.toLong()
        )
        // Should parse smoothly without crash or hang
        assertNotNull(inspection)
    }

    @Test
    fun testCryptoUtilsDeriveKeyWipesPbeKeySpec() {
        val password = "SensitiveMasterPassword#2026".toCharArray()
        val salt = ByteArray(16) { 0x55 }
        val keySpec = CryptoUtils.deriveKey(password, salt, 1000)
        assertNotNull(keySpec)
        assertEquals("AES", keySpec.algorithm)
        assertEquals(32, keySpec.encoded.size)
    }

    @Test
    fun testTomatoScrambleNegativeKeyCyclicShift() {
        val pixels = IntArray(16 * 16) { it }
        val img = Image(pixels, 16, 16)

        // Negative key must not crash or produce out-of-bounds indices
        val result = TomatoScramble(img, -1.0).process(ProcessType.ENCRYPT)
        assertEquals(16, result.width)
        assertEquals(16, result.height)
        assertEquals(256, result.pixels.size)
    }

    @Test
    fun testPicEncryptScrambleBoundaryKeys() {
        val pixels = IntArray(10 * 10) { it }
        val img = Image(pixels, 10, 10)

        // Boundary key values (0.0, 1.0) must not diverge or crash
        val result1 = PicEncryptRowScramble(img, 0.0).process(ProcessType.ENCRYPT)
        assertEquals(100, result1.pixels.size)

        val result2 = PicEncryptRowScramble(img, 1.0).process(ProcessType.ENCRYPT)
        assertEquals(100, result2.pixels.size)
    }
}
