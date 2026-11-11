package com.example.picencrypt

import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.utils.ExportSizeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for file size estimation and size formatting logic.
 */
class ExportSizeEstimatorTest {

    @Test
    fun testEstimateZeroOrNegativeDimensions() {
        assertEquals(0L, ExportSizeEstimator.estimateBytes(0, 1080, ImageExportFormat.PNG, 100))
        assertEquals(0L, ExportSizeEstimator.estimateBytes(1920, 0, ImageExportFormat.JPEG, 90))
        assertEquals(0L, ExportSizeEstimator.estimateBytes(-100, -100, ImageExportFormat.WEBP, 90))
    }

    @Test
    fun testSizeEstimatesRelativeScale() {
        val w = 1920
        val h = 1080
        val pngSize = ExportSizeEstimator.estimateBytes(w, h, ImageExportFormat.PNG, 100)
        val webpLosslessSize = ExportSizeEstimator.estimateBytes(w, h, ImageExportFormat.WEBP, 100)
        val webpLossySize = ExportSizeEstimator.estimateBytes(w, h, ImageExportFormat.WEBP, 85)
        val jpegSize = ExportSizeEstimator.estimateBytes(w, h, ImageExportFormat.JPEG, 85)

        // Lossless WebP should be smaller than PNG
        assertTrue(webpLosslessSize < pngSize)
        // Lossy WebP should be smaller than lossless WebP
        assertTrue(webpLossySize < webpLosslessSize)
        // Lossy WebP should be smaller than JPEG at equal quality
        assertTrue(webpLossySize < jpegSize)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", ExportSizeEstimator.formatFileSize(0L))
        assertEquals("1 KB", ExportSizeEstimator.formatFileSize(500L))
        assertEquals("500 KB", ExportSizeEstimator.formatFileSize(500L * 1024L))
        assertEquals("1.5 MB", ExportSizeEstimator.formatFileSize((1.5 * 1024L * 1024L).toLong()))
    }

    @Test
    fun testRelativeRatioPercent() {
        val pngRatio = ExportSizeEstimator.relativeRatioPercent(ImageExportFormat.PNG, 100)
        assertEquals(100, pngRatio)

        val webpRatio = ExportSizeEstimator.relativeRatioPercent(ImageExportFormat.WEBP, 85)
        assertTrue("WebP ratio should be less than 100%, was $webpRatio", webpRatio < 100)
        assertTrue("WebP ratio should be positive, was $webpRatio", webpRatio > 0)
    }
}
