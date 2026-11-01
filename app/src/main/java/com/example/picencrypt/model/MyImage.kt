package com.example.picencrypt.model

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.picencrypt.R
import com.example.picencrypt.utils.BlockScramble
import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.NativeImageScramble
import com.example.picencrypt.utils.NativeShuffleBridge
import com.example.picencrypt.utils.PerPixelScramble
import com.example.picencrypt.utils.PicEncryptRowColumnScramble
import com.example.picencrypt.utils.PicEncryptRowScramble
import com.example.picencrypt.utils.RowPixelScramble
import com.example.picencrypt.utils.TomatoScramble
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyImage(val uri: Uri, private val context: Context) {
    var filename: String
    var bitmap: Bitmap
    var thumbnail: Bitmap

    init {
        filename = loadFilename()
        bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        thumbnail = loadThumbnail()
    }

    fun loadFilename(): String {
        var name = ""
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                if (idx != -1) {
                    name = it.getString(idx) ?: ""
                }
            }
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "image"
        }
        return name
    }

    fun loadThumbnail(): Bitmap {
        val maxSize = 800
        val ratio: Float = if (bitmap.width > bitmap.height) {
            if (bitmap.width > maxSize) bitmap.width.toFloat() / maxSize else 1f
        } else {
            if (bitmap.height > maxSize) bitmap.height.toFloat() / maxSize else 1f
        }
        val targetW = (bitmap.width.toFloat() / ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height.toFloat() / ratio).toInt().coerceAtLeast(1)
        return ThumbnailUtils.extractThumbnail(bitmap, targetW, targetH)
    }

    fun process(
        algorithm: Algorithm,
        processType: ProcessType,
        key: String = "0.666",
        forceCompat: Boolean = false,
        onFallback: (() -> Unit)? = null
    ): Boolean {
        // LCG_SHUFFLE: Direct Zero-Copy in-place processing on Bitmap
        if (algorithm == Algorithm.LCG_SHUFFLE) {
            if (!NativeShuffleBridge.isAvailable()) {
                return false
            }
            val seed = NativeShuffleBridge.stringToSeed(key)
            NativeShuffleBridge.scrambleBitmap(bitmap, seed, processType == ProcessType.DECRYPT)
            thumbnail = loadThumbnail()
            return true
        }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val algoCode = when (algorithm) {
            Algorithm.TOMATO -> 0
            Algorithm.BLOCK -> 1
            Algorithm.ROW_PIXEL -> 2
            Algorithm.PER_PIXEL -> 3
            Algorithm.PIC_ENCRYPT_ROW -> 4
            Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN -> 5
            Algorithm.LCG_SHUFFLE -> 0
        }

        var image: Image? = null
        var didFallback = false

        // Default: Use C++ Native Engine
        if (!forceCompat && NativeImageScramble.isAvailable()) {
            try {
                image = NativeImageScramble.process(pixels, bitmap.width, bitmap.height, algoCode, processType, key)
            } catch (t: Throwable) {
                image = null
            }
        }

        // Fallback / Compat Mode: Use legacy Java implementation
        if (image == null) {
            if (!forceCompat) {
                didFallback = true
            }
            try {
                val fallbackImage = Image(pixels, bitmap.width, bitmap.height)
                image = when (algorithm) {
                    Algorithm.TOMATO -> TomatoScramble(fallbackImage, key.toDoubleOrNull() ?: 1.0).process(processType)
                    Algorithm.BLOCK -> BlockScramble(fallbackImage, key).process(processType)
                    Algorithm.ROW_PIXEL -> RowPixelScramble(fallbackImage, key).process(processType)
                    Algorithm.PER_PIXEL -> PerPixelScramble(fallbackImage, key).process(processType)
                    Algorithm.PIC_ENCRYPT_ROW -> PicEncryptRowScramble(fallbackImage, key.toDoubleOrNull() ?: 0.5).process(processType)
                    Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN -> PicEncryptRowColumnScramble(fallbackImage, key.toDoubleOrNull() ?: 0.5).process(processType)
                    else -> fallbackImage
                }
            } catch (t: Throwable) {
                return false
            }
        }

        if (didFallback) {
            onFallback?.invoke()
        }

        bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        thumbnail = loadThumbnail()
        return true
    }

    fun rotate(degree: Float) {
        val matrix = Matrix()
        matrix.setRotate(degree)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        thumbnail = loadThumbnail()
    }

    fun reload() {
        filename = loadFilename()
        bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: bitmap
        thumbnail = loadThumbnail()
    }

    fun save(index: Int = 0): Boolean {
        val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        var ms = System.currentTimeMillis()
        ms -= ms / 1000 * 1000 + index
        val s = String.format(Locale.US, "%03d", ms)
        val imageName = "PicEncrypt_${dateTime + s}.png"
        val appName = context.getString(R.string.app_name)
        val saveDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            appName
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$appName")
                }
                val insertUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return false

                context.contentResolver.openOutputStream(insertUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
            } else {
                if (!saveDir.exists()) {
                    saveDir.mkdirs()
                }
                val filePath = File(saveDir, imageName)
                FileOutputStream(filePath).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                context.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.parse("file://${filePath.absolutePath}")
                    )
                )
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
