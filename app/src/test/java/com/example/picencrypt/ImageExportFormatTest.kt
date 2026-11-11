package com.example.picencrypt

import com.example.picencrypt.model.ImageExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying export format metadata, quality adjustment capabilities, and lossiness detection.
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
        assertEquals(90, webp.defaultQuality)

        val jpeg = ImageExportFormat.JPEG
        assertEquals("jpg", jpeg.extension)
        assertEquals("image/jpeg", jpeg.mimeType)
        assertEquals(90, jpeg.defaultQuality)
    }

    @Test
    fun testQualityAdjustmentSupport() {
        assertFalse(ImageExportFormat.PNG.supportsQualityAdjustment)
        assertTrue(ImageExportFormat.WEBP.supportsQualityAdjustment)
        assertTrue(ImageExportFormat.JPEG.supportsQualityAdjustment)
    }

    @Test
    fun testLossinessDetection() {
        // PNG is always lossless
        assertFalse(ImageExportFormat.PNG.isLossy(100))
        assertFalse(ImageExportFormat.PNG.isLossy(50))

        // WebP is lossless only at 100% quality
        assertFalse(ImageExportFormat.WEBP.isLossy(100))
        assertTrue(ImageExportFormat.WEBP.isLossy(90))
        assertTrue(ImageExportFormat.WEBP.isLossy(70))

        // JPEG is always lossy
        assertTrue(ImageExportFormat.JPEG.isLossy(100))
        assertTrue(ImageExportFormat.JPEG.isLossy(90))
        assertTrue(ImageExportFormat.JPEG.isLossy(50))
    }

    @Test
    fun testCompressFormatResolutions() {
        assertNotNull(ImageExportFormat.PNG.getCompressFormat())
        assertNotNull(ImageExportFormat.WEBP.getCompressFormat(100))
        assertNotNull(ImageExportFormat.WEBP.getCompressFormat(90))
        assertNotNull(ImageExportFormat.JPEG.getCompressFormat())
    }
}
