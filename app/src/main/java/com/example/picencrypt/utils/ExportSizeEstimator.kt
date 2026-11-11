package com.example.picencrypt.utils

import com.example.picencrypt.model.ImageExportFormat
import java.util.Locale
import kotlin.math.pow

/**
 * Utility for estimating compressed file sizes and formatting size metrics
 * before exporting scrambled or processed images.
 */
object ExportSizeEstimator {

    /**
     * Estimates the exported file size in bytes based on image dimensions,
     * export format, and compression quality.
     */
    fun estimateBytes(
        width: Int,
        height: Int,
        format: ImageExportFormat,
        quality: Int
    ): Long {
        if (width <= 0 || height <= 0) return 0L

        val rawBytes = width.toLong() * height.toLong() * 4L
        val clampedQuality = quality.coerceIn(1, 100)

        val estimated = when (format) {
            ImageExportFormat.PNG -> {
                // PNG uses lossless Deflate. For scrambled high-entropy images,
                // compression ratio typically converges around ~65% of raw ARGB.
                (rawBytes * 0.65).toLong()
            }
            ImageExportFormat.JPEG -> {
                // Non-linear JPEG compression curve
                val qFactor = (clampedQuality / 100.0).pow(1.8)
                val ratio = 0.05 + 0.35 * qFactor
                (rawBytes * ratio).toLong()
            }
            ImageExportFormat.WEBP -> {
                if (clampedQuality >= 100) {
                    // Lossless WebP: ~25%-30% smaller than PNG
                    (rawBytes * 0.45).toLong()
                } else {
                    // Lossy WebP: significantly more compact than JPEG at same quality
                    val qFactor = (clampedQuality / 100.0).pow(1.8)
                    val ratio = 0.04 + 0.25 * qFactor
                    (rawBytes * ratio).toLong()
                }
            }
        }

        return estimated.coerceAtLeast(1024L)
    }

    /**
     * Formats bytes into a human-readable string (e.g. "450 KB", "1.8 MB").
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1.0) return "1 KB"
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    /**
     * Calculates the estimated size ratio percentage relative to PNG (PNG = 100).
     */
    fun relativeRatioPercent(format: ImageExportFormat, quality: Int): Int {
        val pngEst = estimateBytes(1000, 1000, ImageExportFormat.PNG, 100)
        val targetEst = estimateBytes(1000, 1000, format, quality)
        return if (pngEst > 0L) {
            ((targetEst.toDouble() / pngEst.toDouble()) * 100).toInt().coerceIn(1, 100)
        } else {
            100
        }
    }
}
