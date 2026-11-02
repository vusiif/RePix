package com.example.picencrypt

import com.example.picencrypt.model.ImageExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying export format metadata and defaults.
 */
class ImageExportFormatTest {

    @Test
    fun testExportFormatMetadata() {
        val png = ImageExportFormat.PNG
        assertEquals("png", png.extension)
        assertEquals("image/png", png.mimeType)
        assertEquals(100, png.defaultQuality)

        val webp = ImageExportFormat.WEBP
        assertEquals("webp", webp.extension)
        assertEquals("image/webp", webp.mimeType)
        assertEquals(100, webp.defaultQuality)

        val jpeg = ImageExportFormat.JPEG
        assertEquals("jpg", jpeg.extension)
        assertEquals("image/jpeg", jpeg.mimeType)
        assertTrue(jpeg.defaultQuality in 80..100)
    }
}
