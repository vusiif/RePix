package com.example.picencrypt

import com.example.picencrypt.utils.ExportFilenameGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for custom export filename generation, token replacement, and sanitization.
 */
class ExportFilenameGeneratorTest {

    @Test
    fun testOriginalBaseNameExtraction() {
        assertEquals("vacation_photo", ExportFilenameGenerator.getOriginalBaseName("vacation_photo.jpg"))
        assertEquals("archive.tar", ExportFilenameGenerator.getOriginalBaseName("archive.tar.gz"))
        assertEquals("photo", ExportFilenameGenerator.getOriginalBaseName("photo"))
        assertEquals("image", ExportFilenameGenerator.getOriginalBaseName(""))
    }

    @Test
    fun testFilenameSanitization() {
        val unsafe = "file/with:illegal*chars?and\"quotes|here<>.png"
        val sanitized = ExportFilenameGenerator.sanitizeFilename(unsafe)
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("*"))
        assertFalse(sanitized.contains("?"))
        assertFalse(sanitized.contains("\""))
        assertFalse(sanitized.contains("|"))
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
    }

    @Test
    fun testTimestampPatternResolution() {
        val name = ExportFilenameGenerator.generateFilename(
            template = ExportFilenameGenerator.PATTERN_TIMESTAMP,
            prefix = "RePix",
            originalFilename = "test.png",
            index = 0,
            algorithmName = "Tomato",
            extension = "png",
            timestamp = 1700000000000L
        )
        assertTrue(name.startsWith("RePix_"))
        assertTrue(name.endsWith("_001.png"))
    }

    @Test
    fun testKeepOriginalPatternResolution() {
        val name = ExportFilenameGenerator.generateFilename(
            template = ExportFilenameGenerator.PATTERN_ORIGINAL_PREFIX,
            prefix = "Secret",
            originalFilename = "my_doc.jpg",
            index = 2,
            algorithmName = "Tomato",
            extension = "webp"
        )
        assertEquals("Secret_my_doc.webp", name)
    }

    @Test
    fun testAlgorithmTaggedPatternResolution() {
        val name = ExportFilenameGenerator.generateFilename(
            template = ExportFilenameGenerator.PATTERN_ALGORITHM_TAGGED,
            prefix = "RePix",
            originalFilename = "landscape.jpeg",
            index = 0,
            algorithmName = "Tomato",
            extension = "jpg"
        )
        assertEquals("RePix_Tomato_landscape.jpg", name)
    }

    @Test
    fun testSequentialPatternResolution() {
        val name = ExportFilenameGenerator.generateFilename(
            template = ExportFilenameGenerator.PATTERN_SEQUENTIAL,
            prefix = "Batch",
            originalFilename = "any.png",
            index = 8,
            algorithmName = "Block",
            extension = "png"
        )
        assertEquals("Batch_009.png", name)
    }
}
