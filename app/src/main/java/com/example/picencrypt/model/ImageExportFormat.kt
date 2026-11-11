package com.example.picencrypt.model

import android.graphics.Bitmap
import android.os.Build
import com.example.picencrypt.R

/**
 * Image export formats supported by RePix (PNG, lossless/lossy WebP, and JPEG).
 */
enum class ImageExportFormat(
    val extension: String,
    val mimeType: String,
    val stringResId: Int,
    val defaultQuality: Int
) {
    PNG("png", "image/png", R.string.export_format_png, 100),
    WEBP("webp", "image/webp", R.string.export_format_webp, 90),
    JPEG("jpg", "image/jpeg", R.string.export_format_jpeg, 90);

    /**
     * Whether this export format allows user adjustment of compression quality.
     */
    val supportsQualityAdjustment: Boolean
        get() = this != PNG

    /**
     * Indicates whether the exported image will undergo lossy compression at the specified quality.
     * Crucial for warning the user if irreversible pixel degradation will break encryption reversibility.
     */
    fun isLossy(quality: Int): Boolean = when (this) {
        PNG -> false
        WEBP -> quality < 100
        JPEG -> true
    }

    /**
     * Resolves the appropriate Bitmap.CompressFormat across different Android API versions.
     */
    fun getCompressFormat(quality: Int = defaultQuality): Bitmap.CompressFormat {
        return when (this) {
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (quality >= 100) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    }
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            JPEG -> Bitmap.CompressFormat.JPEG
        }
    }
}
