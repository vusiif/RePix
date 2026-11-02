package com.example.picencrypt.utils

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                    context.contentResolver.openInputStream(coverUri)?.use { cIn ->
                        context.contentResolver.openInputStream(archiveUri)?.use { aIn ->
                            SteganographyHelper.createStegoImage(cIn, aIn, out)
                        }
                    }
                } ?: return false
            } else {
                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName)
                if (!saveDir.exists()) saveDir.mkdirs()
                val outFile = File(saveDir, fileName)
                FileOutputStream(outFile).use { out ->
                    context.contentResolver.openInputStream(coverUri)?.use { cIn ->
                        context.contentResolver.openInputStream(archiveUri)?.use { aIn ->
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

    /**
     * 保存字节数据至系统公共 Download/RePix 目录
     */
    fun saveBytesToDownload(context: Context, fileName: String, bytes: ByteArray): Boolean {
        val appName = context.getString(R.string.app_name)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$appName")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: return false
            } else {
                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), appName)
                if (!saveDir.exists()) saveDir.mkdirs()
                val outFile = File(saveDir, fileName)
                FileOutputStream(outFile).use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 通用安全缩略图加载：利用 inJustDecodeBounds 下采样，防止大图解码 OOM
     */
    fun loadThumbnail(context: Context, uri: Uri, maxSize: Int = 300): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            val outW = boundsOptions.outWidth
            val outH = boundsOptions.outHeight
            if (outW <= 0 || outH <= 0) return null

            var sampleSize = 1
            val maxDim = maxOf(outW, outH)
            while (maxDim / (sampleSize * 2) >= maxSize) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 准确读取 Uri 对应底层文件的字节长度（优先查询 MediaStore.SIZE，兜底 FileDescriptor）
     */
    fun getUriFileSize(context: Context, uri: Uri): Long {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (idx != -1) {
                        size = it.getLong(idx)
                    }
                }
            }
            if (size <= 0L) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    size = it.statSize
                }
            }
            size.coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 人类可读的文件大小格式化
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        digitGroups = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
