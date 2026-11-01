package com.example.picencrypt.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.picencrypt.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun getUriFilename(context: Context, uri: Uri): String {
        var name = ""
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx != -1) {
                    name = it.getString(idx) ?: ""
                }
            }
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }

    fun saveStegoImage(context: Context, coverUri: Uri, archiveUri: Uri): Boolean {
        val appName = context.getString(R.string.app_name)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val coverName = getUriFilename(context, coverUri)
        val isJpg = coverName.endsWith(".jpg", ignoreCase = true) || coverName.endsWith(".jpeg", ignoreCase = true)
        val ext = if (isJpg) "jpg" else "png"
        val mimeType = if (isJpg) "image/jpeg" else "image/png"
        val fileName = "Stego_${timeStamp}.$ext"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$appName")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val coverIn = context.contentResolver.openInputStream(coverUri) ?: return false
                    val archIn = context.contentResolver.openInputStream(archiveUri) ?: return false
                    coverIn.use { cIn ->
                        archIn.use { aIn ->
                            SteganographyHelper.createStegoImage(cIn, aIn, out)
                        }
                    }
                } ?: return false
            } else {
                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName)
                if (!saveDir.exists()) saveDir.mkdirs()
                val outFile = File(saveDir, fileName)
                FileOutputStream(outFile).use { out ->
                    val coverIn = context.contentResolver.openInputStream(coverUri) ?: return false
                    val archIn = context.contentResolver.openInputStream(archiveUri) ?: return false
                    coverIn.use { cIn ->
                        archIn.use { aIn ->
                            SteganographyHelper.createStegoImage(cIn, aIn, out)
                        }
                    }
                }
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://${outFile.absolutePath}")))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun extractArchiveFromStego(context: Context, stegoUri: Uri): String? {
        val appName = context.getString(R.string.app_name)
        val cacheFile = File(context.cacheDir, "temp_stego_${System.currentTimeMillis()}.bin")
        return try {
            context.contentResolver.openInputStream(stegoUri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val detection = SteganographyHelper.detectArchiveOffset(cacheFile) ?: return null
            val ext = detection.type.extension
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outFileName = "Extracted_${timeStamp}.$ext"

            val extractedTempFile = File(context.cacheDir, "temp_extracted_${System.currentTimeMillis()}.$ext")
            val success = SteganographyHelper.extractArchive(cacheFile, extractedTempFile)
            if (!success || !extractedTempFile.exists() || extractedTempFile.length() == 0L) {
                extractedTempFile.delete()
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$appName")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(extractedTempFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: return null
            } else {
                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), appName)
                if (!saveDir.exists()) saveDir.mkdirs()
                val outFile = File(saveDir, outFileName)
                FileInputStream(extractedTempFile).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            extractedTempFile.delete()
            outFileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (cacheFile.exists()) cacheFile.delete()
        }
    }
}
