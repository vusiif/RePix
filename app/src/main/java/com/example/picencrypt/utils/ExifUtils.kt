package com.example.picencrypt.utils

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Utility for reading, inspecting, and stripping image EXIF privacy metadata (GPS coordinates, device model, timestamp).
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
    ) {
        val isClean: Boolean
            get() = !hasGps && make.isNullOrBlank() && model.isNullOrBlank() && dateTime.isNullOrBlank()

        fun formatCoordinates(): String? {
            if (latitude == null || longitude == null) return null
            val latDir = if (latitude >= 0) "N" else "S"
            val lngDir = if (longitude >= 0) "E" else "W"
            return String.format(
                Locale.US,
                "%.4f°%s, %.4f°%s",
                Math.abs(latitude),
                latDir,
                Math.abs(longitude),
                lngDir
            )
        }

        fun getDeviceDescription(): String? {
            val parts = listOfNotNull(make, model).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            // De-duplicate if model already contains make
            return if (parts.size == 2 && parts[1].contains(parts[0], ignoreCase = true)) {
                parts[1]
            } else {
                parts.joinToString(" ")
            }
        }
    }

    fun cleanExif(): ExifInfo = ExifInfo(hasGps = false)

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
            cleanExif()
        }
    }

    fun readExif(context: Context, uri: Uri): ExifInfo {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readExif(input)
            } ?: cleanExif()
        } catch (_: Exception) {
            cleanExif()
        }
    }

    /**
     * Strips sensitive location, device, and timestamp attributes from an [ExifInterface] instance.
     */
    fun stripSensitiveAttributes(exif: ExifInterface) {
        val sensitiveTags = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_SOFTWARE
        )

        for (tag in sensitiveTags) {
            try {
                exif.setAttribute(tag, null)
            } catch (_: Exception) {}
        }
    }

    /**
     * Physically strips all sensitive EXIF privacy metadata from a target file on disk.
     */
    fun stripExifFile(file: File): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        return try {
            val exif = ExifInterface(file.absolutePath)
            stripSensitiveAttributes(exif)
            exif.saveAttributes()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Physically strips all sensitive EXIF privacy metadata from a target URI in MediaStore.
     */
    fun stripExifUri(context: Context, uri: Uri): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    stripSensitiveAttributes(exif)
                    exif.saveAttributes()
                    true
                } ?: false
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
