package com.example.picencrypt.utils

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import java.io.InputStream

/**
 * Utility for reading and inspecting image EXIF privacy metadata (GPS coordinates, device model, timestamp).
 */
object ExifUtils {

    data class ExifInfo(
        val hasGps: Boolean,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val make: String? = null,
        val model: String? = null,
        val dateTime: String? = null,
        val software: String? = null
    )

    fun readExif(inputStream: InputStream): ExifInfo {
        return try {
            val exif = ExifInterface(inputStream)
            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)

            ExifInfo(
                hasGps = hasGps,
                latitude = if (hasGps) latLong[0].toDouble() else null,
                longitude = if (hasGps) latLong[1].toDouble() else null,
                make = make,
                model = model,
                dateTime = dateTime,
                software = software
            )
        } catch (_: Exception) {
            ExifInfo(hasGps = false)
        }
    }

    fun readExif(context: Context, uri: Uri): ExifInfo {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readExif(input)
            } ?: ExifInfo(hasGps = false)
        } catch (_: Exception) {
            ExifInfo(hasGps = false)
        }
    }
}
