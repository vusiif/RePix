package com.example.picencrypt.model

import android.graphics.Bitmap
import android.os.Build
import com.example.picencrypt.R

/**
 * Image export formats supported by RePix (PNG, lossless WebP, and JPEG).
 */
enum class ImageExportFormat(
    val extension: String,
    val mimeType: String,
    val stringResId: Int,
    val defaultQuality: Int
) {
    PNG("png", "image/png", R.string.export_format_png, 100),
    WEBP("webp", "image/webp", R.string.export_format_webp, 100),
    JPEG("jpg", "image/jpeg", R.string.export_format_jpeg, 95);

    fun getCompressFormat(): Bitmap.CompressFormat {
        return when (this) {
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            JPEG -> Bitmap.CompressFormat.JPEG
        }
    }
}
