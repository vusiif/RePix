package com.example.picencrypt

import com.example.picencrypt.utils.CryptoUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class CryptoUtilsTest {

    @Test
    fun testAesGcmEncryptionAndDecryptionRoundTrip() {
        val plainText = "Hello RePix Security! 这是一个高强度 AES-256-GCM 加密测试。"
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val password = "StrongPassword@2026!".toCharArray()
        val filename = "secret_image.png"

        // Encrypt
        val encrypted = CryptoUtils.encryptBytes(plainBytes, password, filename)
        assertTrue(encrypted.size > plainBytes.size)

        // Inspect header
        val header = CryptoUtils.inspectHeader(encrypted.inputStream())
        assertEquals(filename, header?.originalFilename)
        assertEquals(plainBytes.size.toLong(), header?.plainLength)
        assertEquals(CryptoUtils.CURRENT_VERSION, header?.version)

        // Decrypt with correct password
        val result = CryptoUtils.decryptBytes(encrypted, password)
        assertTrue("Decryption must succeed with correct password", result is CryptoUtils.DecryptResult.Success)

        val success = result as CryptoUtils.DecryptResult.Success
        assertEquals(filename, success.originalFilename)
        assertArrayEquals(plainBytes, success.plainBytes)
        assertEquals(plainText, String(success.plainBytes, StandardCharsets.UTF_8))
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
