package com.example.picencrypt

import com.example.picencrypt.utils.CryptoUtils
import com.example.picencrypt.utils.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class FilenameSanitizationTest {

    @Test
    fun testPathTraversalStripping() {
        assertEquals("payload.so", FileUtils.sanitizeFilename("../../../../payload.so"))
        assertEquals("evil.sh", FileUtils.sanitizeFilename("..\\..\\evil.sh"))
        assertEquals("script.py", FileUtils.sanitizeFilename("/storage/emulated/0/Download/script.py"))
        assertEquals("cmd.exe", FileUtils.sanitizeFilename("C:\\Windows\\System32\\cmd.exe"))
    }

    @Test
    fun testControlCharactersStripping() {
        val withNullByte = "malicious\u0000file.png"
        val withAsciiCtrl = "\u0001\u001Fsafe.jpg\u007F"
        assertEquals("maliciousfile.png", FileUtils.sanitizeFilename(withNullByte))
        assertEquals("safe.jpg", FileUtils.sanitizeFilename(withAsciiCtrl))
    }

    @Test
    fun testIllegalCharactersReplaced() {
        val illegalChars = "my:bad*file?with<quotes>\"and|pipe.png"
        val sanitized = FileUtils.sanitizeFilename(illegalChars)
        assertFalse(sanitized.contains(':'))
        assertFalse(sanitized.contains('*'))
        assertFalse(sanitized.contains('?'))
        assertFalse(sanitized.contains('<'))
        assertFalse(sanitized.contains('>'))
        assertFalse(sanitized.contains('"'))
        assertFalse(sanitized.contains('|'))
        assertEquals("my_bad_file_with_quotes__and_pipe.png", sanitized)
    }

    @Test
    fun testLeadingAndTrailingDotsAndSpaces() {
        assertEquals("hidden.txt", FileUtils.sanitizeFilename(".hidden.txt"))
        assertEquals("dots.txt", FileUtils.sanitizeFilename("...dots.txt"))
        assertEquals("spaced.jpg", FileUtils.sanitizeFilename("  spaced.jpg   "))
        assertEquals("trailing.png", FileUtils.sanitizeFilename("trailing.png..."))
    }

    @Test
    fun testReservedWindowsDeviceNames() {
        assertEquals("safe_CON.txt", FileUtils.sanitizeFilename("CON.txt"))
        assertEquals("safe_prn.log", FileUtils.sanitizeFilename("prn.log"))
        assertEquals("safe_AUX", FileUtils.sanitizeFilename("AUX"))
        assertEquals("safe_NUL.dat", FileUtils.sanitizeFilename("NUL.dat"))
        assertEquals("safe_com1.bin", FileUtils.sanitizeFilename("com1.bin"))
        assertEquals("safe_LPT1.pdf", FileUtils.sanitizeFilename("LPT1.pdf"))
    }

    @Test
    fun testBlankAndDotsFallback() {
        assertEquals("file", FileUtils.sanitizeFilename(""))
        assertEquals("file", FileUtils.sanitizeFilename("    "))
        assertEquals("file", FileUtils.sanitizeFilename("..."))
        assertEquals("fallback_custom", FileUtils.sanitizeFilename("..", "fallback_custom"))
    }

    @Test
    fun testExcessiveLengthTruncation() {
        val veryLongBase = "A".repeat(200)
        val veryLongName = "$veryLongBase.jpg"
        val sanitized = FileUtils.sanitizeFilename(veryLongName)

        assertTrue("Sanitized filename must not exceed 128 characters", sanitized.length <= 128)
        assertTrue("Extension must be preserved", sanitized.endsWith(".jpg"))
    }

    @Test
    fun testDecryptionEnforcesSanitizationOnTraversingFilename() {
        val plainBytes = "Sample payload for traversal check".toByteArray(StandardCharsets.UTF_8)
        val password = "StrongPass@2026".toCharArray()
        val maliciousFilename = "../../../../../etc/cron.d/backdoor.sh"

        // Encrypt with malicious name
        val encrypted = CryptoUtils.encryptBytes(plainBytes, password, maliciousFilename)

        // Decrypt must sanitize to a safe filename inside download dir
        val result = CryptoUtils.decryptBytes(encrypted, password)
        assertTrue(result is CryptoUtils.DecryptResult.Success)
        val success = result as CryptoUtils.DecryptResult.Success

        assertEquals("backdoor.sh", success.originalFilename)
        assertFalse(success.originalFilename.contains('/'))
        assertFalse(success.originalFilename.contains('\\'))
        assertFalse(success.originalFilename.contains(".."))
    }
}
