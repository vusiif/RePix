package com.example.picencrypt

import com.example.picencrypt.utils.CryptoUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class CryptoUtilsTest {

    @Test
    fun testAesGcmEncryptionAndDecryptionRoundTrip() {
        val plainText = "Hello RePix Security! 这是一个高强度 AES-256-GCM 加密测试。"
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val password = "StrongPassword@2026!".toCharArray()
        val filename = "secret_image.png"

        // Encrypt (RPX V2)
        val encrypted = CryptoUtils.encryptBytes(plainBytes, password, filename)
        assertTrue(encrypted.size > plainBytes.size)

        // Inspect header
        val header = CryptoUtils.inspectHeader(encrypted.inputStream())
        assertEquals(filename, header?.originalFilename)
        assertEquals(plainBytes.size.toLong(), header?.plainLength)
        assertEquals(CryptoUtils.CURRENT_VERSION, header?.version)
        assertEquals(CryptoUtils.PBKDF2_ITERATIONS_V2, header?.iterations)
        assertTrue(header?.aadBytes != null)

        // Decrypt with correct password
        val result = CryptoUtils.decryptBytes(encrypted, password)
        assertTrue("Decryption must succeed with correct password", result is CryptoUtils.DecryptResult.Success)

        val success = result as CryptoUtils.DecryptResult.Success
        assertEquals(filename, success.originalFilename)
        assertArrayEquals(plainBytes, success.plainBytes)
        assertEquals(plainText, String(success.plainBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun testV1BackwardCompatibilityDecryption() {
        val plainText = "Legacy RePix V1 Payload - 保留旧版只读解密兼容"
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val password = "OldPassword123".toCharArray()
        val filename = "legacy_v1.jpg"

        val inStream = ByteArrayInputStream(plainBytes)
        val outStream = ByteArrayOutputStream()
        CryptoUtils.encryptStreamV1ForTesting(inStream, outStream, password, filename, plainBytes.size.toLong())
        val v1Encrypted = outStream.toByteArray()

        // Inspect V1 header
        val header = CryptoUtils.inspectHeader(v1Encrypted.inputStream())
        assertEquals(CryptoUtils.VERSION_1, header?.version)
        assertEquals(CryptoUtils.PBKDF2_ITERATIONS_V1, header?.iterations)
        assertEquals(filename, header?.originalFilename)

        // Decrypt V1 file with new decryptBytes
        val result = CryptoUtils.decryptBytes(v1Encrypted, password)
        assertTrue("V1 format must be transparently decrypted", result is CryptoUtils.DecryptResult.Success)

        val success = result as CryptoUtils.DecryptResult.Success
        assertEquals(filename, success.originalFilename)
        assertArrayEquals(plainBytes, success.plainBytes)
    }

    @Test
    fun testV2HeaderTamperingIsRejectedByAad() {
        val plainBytes = "Super confidential data".toByteArray(StandardCharsets.UTF_8)
        val password = "StrongPassword#2026".toCharArray()
        val filename = "financial_report.pdf"

        val encrypted = CryptoUtils.encryptBytes(plainBytes, password, filename)

        // Tamper with one byte in the header filename (e.g. change 'f' to 'g')
        // In V2, header starts with: Magic(4) + Ver(1) + Algo(1) + Iters(4) + Salt(16) + IV(12) + NameLen(2)
        // Filename begins at index 4 + 1 + 1 + 4 + 16 + 12 + 2 = 40
        val tampered = encrypted.clone()
        val filenameOffset = 40
        tampered[filenameOffset] = (tampered[filenameOffset].toInt() xor 0x01).toByte()

        val result = CryptoUtils.decryptBytes(tampered, password)
        assertTrue(
            "Tampering with header metadata must cause GCM AAD verification failure",
            result is CryptoUtils.DecryptResult.WrongPasswordOrCorrupted
        )
    }

    @Test
    fun testStreamingLargeDataRoundTrip() {
        // Generate a 200 KiB test payload to verify multiple chunks
        val largeData = ByteArray(200 * 1024) { (it % 127).toByte() }
        val password = "LargeStreamPassword".toCharArray()
        val filename = "large_image.raw"

        val inStream = ByteArrayInputStream(largeData)
        val encryptedOut = ByteArrayOutputStream()
        CryptoUtils.encryptStream(
            input = inStream,
            output = encryptedOut,
            password = password,
            originalFilename = filename,
            plainLength = largeData.size.toLong(),
            bufferSize = 32 * 1024
        )

        val encryptedBytes = encryptedOut.toByteArray()
        val decryptIn = ByteArrayInputStream(encryptedBytes)
        val decryptedOut = ByteArrayOutputStream()

        val decryptResult = CryptoUtils.decryptStream(
            input = decryptIn,
            output = decryptedOut,
            password = password,
            bufferSize = 32 * 1024
        )

        assertTrue(decryptResult is CryptoUtils.DecryptResult.Success)
        val success = decryptResult as CryptoUtils.DecryptResult.Success
        assertEquals(filename, success.originalFilename)
        assertArrayEquals(largeData, decryptedOut.toByteArray())
    }

    @Test
    fun testWrongPasswordFailsGracefully() {
        val plainBytes = "Confidential Payload".toByteArray(StandardCharsets.UTF_8)
        val correctPassword = "CorrectKey123".toCharArray()
        val wrongPassword = "WrongKey456".toCharArray()

        val encrypted = CryptoUtils.encryptBytes(plainBytes, correctPassword, "data.bin")

        val result = CryptoUtils.decryptBytes(encrypted, wrongPassword)
        assertTrue(
            "Wrong password must yield WrongPasswordOrCorrupted",
            result is CryptoUtils.DecryptResult.WrongPasswordOrCorrupted
        )
    }

    @Test
    fun testTamperedCiphertextIsRejected() {
        val plainBytes = "Important bank statement".toByteArray(StandardCharsets.UTF_8)
        val password = "BankPassword#99".toCharArray()
        val encrypted = CryptoUtils.encryptBytes(plainBytes, password, "statement.pdf")

        // Tamper with one byte in the payload
        val tampered = encrypted.clone()
        val tamperIndex = tampered.size - 5
        tampered[tamperIndex] = (tampered[tamperIndex].toInt() xor 0x01).toByte()

        val result = CryptoUtils.decryptBytes(tampered, password)
        assertTrue(
            "Tampered ciphertext must be rejected by GCM tag verification",
            result is CryptoUtils.DecryptResult.WrongPasswordOrCorrupted
        )
    }

    @Test
    fun testRandomSaltAndIvUniqueness() {
        val plainBytes = "Repeated data test".toByteArray(StandardCharsets.UTF_8)
        val password = "SamePassword".toCharArray()

        val enc1 = CryptoUtils.encryptBytes(plainBytes, password, "file.jpg")
        val enc2 = CryptoUtils.encryptBytes(plainBytes, password, "file.jpg")

        // Even with the same password and content, ciphertexts must differ due to random Salt and IV
        assertFalse(
            "Ciphertexts of identical data must differ across runs",
            enc1.contentEquals(enc2)
        )
    }

    @Test
    fun testInvalidFormatRejection() {
        val randomGarbage = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55)
        val result = CryptoUtils.decryptBytes(randomGarbage, "any".toCharArray())
        assertTrue(result is CryptoUtils.DecryptResult.InvalidFormat)
    }
}
