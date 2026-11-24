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
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    /**
     * 跨版本通知系统 MediaStore 刷新或使用 MediaScannerConnection 索引新生成的图片
     * 彻底解决 ACTION_MEDIA_SCANNER_SCAN_FILE 过时警告，并确保相册立刻显示
     */
    fun notifyMediaScanner(context: Context, file: File, mimeType: String? = null) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                if (mimeType != null) arrayOf(mimeType) else null,
                null
            )
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
                )
            } catch (_: Exception) {}
        }
    }

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

    /**
     * Sanitizes raw filenames to prevent path traversal, control character injection,
     * reserved name collision, and filesystem length limits.
     */
    fun sanitizeFilename(rawFilename: String, fallback: String = "file"): String {
        if (rawFilename.isBlank()) return fallback

        // 1. Strip any directory components (both / and \)
        var name = rawFilename.substringAfterLast('/').substringAfterLast('\\').trim()

        // 2. Remove control characters (0x00..0x1F, 0x7F)
        name = name.replace(Regex("[\\x00-\\x1F\\x7F]"), "")

        // 3. Remove illegal filesystem characters : * ? " < > |
        name = name.replace(Regex("[:*?\"<>|]"), "_")

        // 4. Remove leading and trailing dots and spaces
        name = name.trimStart('.', ' ')
        name = name.trimEnd('.', ' ')

        // 5. Prevent Windows / FAT reserved device names
        val baseName = name.substringBefore('.')
        val reserved = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
        if (reserved.contains(baseName.uppercase(Locale.US))) {
            name = "safe_$name"
        }

        // 6. Enforce max length (128 chars), preserving extension if present
        if (name.length > 128) {
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex in 1 until name.length - 1 && name.length - dotIndex <= 16) {
                val ext = name.substring(dotIndex)
                val base = name.substring(0, 128 - ext.length)
                name = base + ext
            } else {
                name = name.take(128)
            }
        }

        return if (name.isBlank()) fallback else name
    }

    /**
     * Inspects a stego image from a Uri, utilizing ParcelFileDescriptor for O(1) random access
     * when supported (SAF, Downloads, MediaStore), with safe stream fallback.
     */
    fun inspectStegoUri(context: Context, stegoUri: Uri): SteganographyHelper.StegoInspection {
        val totalSize = getUriFileSize(context, stegoUri)

        // 1. Try ParcelFileDescriptor for direct seekable FileChannel
        try {
            context.contentResolver.openFileDescriptor(stegoUri, "r")?.use { pfd ->
                val size = if (pfd.statSize > 0L) pfd.statSize else totalSize
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val inspection = SteganographyHelper.detectArchiveFromStream(fis, size)
                    if (inspection.hasArchive) {
                        return inspection
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Stream fallback
        return try {
            context.contentResolver.openInputStream(stegoUri)?.use { stream ->
                SteganographyHelper.detectArchiveFromStream(stream, totalSize)
            } ?: SteganographyHelper.StegoInspection(hasArchive = false, totalSize = totalSize)
        } catch (e: Exception) {
            SteganographyHelper.StegoInspection(hasArchive = false, totalSize = totalSize)
        }
    }

    fun saveStegoImage(context: Context, coverUri: Uri, archiveUri: Uri): Boolean {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val coverName = getUriFilename(context, coverUri)
        val isJpg = coverName.endsWith(".jpg", ignoreCase = true) || coverName.endsWith(".jpeg", ignoreCase = true)
        val ext = if (isJpg) "jpg" else "png"
        val mimeType = if (isJpg) "image/jpeg" else "image/png"
        val fileName = "Stego_${timeStamp}.$ext"

        val result = MediaStoreOutputWriter.writeImage(
            context = context,
            fileName = fileName,
            mimeType = mimeType
        ) { out ->
            context.contentResolver.openInputStream(coverUri)?.use { cIn ->
                context.contentResolver.openInputStream(archiveUri)?.use { aIn ->
                    SteganographyHelper.createStegoImage(cIn, aIn, out) > 0L
                }
            } ?: false
        }
        return result is MediaStoreOutputWriter.OutputResult.Success
    }

    fun extractArchiveFromStego(context: Context, stegoUri: Uri): String? {
        val inspection = inspectStegoUri(context, stegoUri)
        if (!inspection.hasArchive || inspection.offset <= 0L) {
            return null
        }

        val ext = inspection.type.extension
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFileName = "Extracted_${timeStamp}.$ext"

        val success = writeStreamToDownload(context, outFileName) { outStream ->
            val streamBlock: (InputStream) -> Boolean = { inStream ->
                SteganographyHelper.extractArchiveStream(
                    input = inStream,
                    output = outStream,
                    offset = inspection.offset,
                    expectedLength = if (inspection.isV2) inspection.archiveSize else -1L,
                    expectedSha256 = inspection.sha256
                )
            }

            try {
                context.contentResolver.openFileDescriptor(stegoUri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use(streamBlock)
                } ?: run {
                    context.contentResolver.openInputStream(stegoUri)?.use(streamBlock) ?: false
                }
            } catch (_: Exception) {
                context.contentResolver.openInputStream(stegoUri)?.use(streamBlock) ?: false
            }
        }

        return if (success) outFileName else null
    }

    /**
     * 保存字节数据至系统公共 Download/RePix 目录
     */
    fun saveBytesToDownload(context: Context, fileName: String, bytes: ByteArray): Boolean {
        return writeStreamToDownload(context, fileName) { out ->
            out.write(bytes)
            out.flush()
            true
        }
    }

    /**
     * 流式保存输入流数据至系统公共 Download/RePix 目录（无内存峰值）
     */
    fun saveStreamToDownload(context: Context, fileName: String, inputStream: InputStream): Boolean {
        return writeStreamToDownload(context, fileName) { out ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
            out.flush()
            true
        }
    }

    /**
     * 以流方式直接向系统公共 Download/RePix 目录写出数据，
     * 利用 MediaStoreOutputWriter 实现事务性写入与 IS_PENDING 失败回滚清理。
     */
    fun writeStreamToDownload(
        context: Context,
        fileName: String,
        writeBlock: (OutputStream) -> Boolean
    ): Boolean {
        val result = MediaStoreOutputWriter.writeDownload(
            context = context,
            fileName = fileName,
            writer = writeBlock
        )
        return result is MediaStoreOutputWriter.OutputResult.Success
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
