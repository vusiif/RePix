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
import com.example.picencrypt.utils.ExifUtils
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.ImageMetrics
import com.example.picencrypt.utils.ImageMetricsHelper
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
import java.util.UUID

sealed class SaveResult {
    object Success : SaveResult()
    data class Failure(val message: String, val throwable: Throwable? = null) : SaveResult()
}

class MyImage(
    val uri: Uri,
    context: Context,
    val id: String = UUID.randomUUID().toString()
) {
    // Enforce Application Context to avoid Activity leakage (P1-4)
    private val appContext: Context = context.applicationContext

    var filename: String
    var thumbnail: Bitmap
    private var cachedMetrics: ImageMetrics? = null

    var originalWidth: Int = 0
        private set
    var originalHeight: Int = 0
        private set

    var exifInfo: ExifUtils.ExifInfo? = null
        private set

    private var _bitmap: Bitmap? = null

    /**
     * Lazy on-demand decode for full-size bitmap.
     * Prevents out-of-memory errors when importing multiple high-resolution photos (P0-1 / P0-2).
     */
    var bitmap: Bitmap
        get() {
            var b = _bitmap
            if (b == null || b.isRecycled) {
                b = loadFullBitmap()
                _bitmap = b
            }
            return b
        }
        set(value) {
            _bitmap = value
            originalWidth = value.width
            originalHeight = value.height
        }

    init {
        filename = loadFilename()
        inspectDimensions()
        thumbnail = loadThumbnailFromUri()
    }

    private fun inspectDimensions() {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            originalWidth = boundsOptions.outWidth.coerceAtLeast(0)
            originalHeight = boundsOptions.outHeight.coerceAtLeast(0)
            exifInfo = ExifUtils.readExif(appContext, uri)
        } catch (_: Exception) {}
    }

    private fun loadFullBitmap(): Bitmap {
        val loaded = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        originalWidth = loaded.width
        originalHeight = loaded.height
        return loaded
    }

    fun isBitmapLoaded(): Boolean = _bitmap?.isRecycled == false

    fun loadFilename(): String {
        var name = ""
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        val cursor = appContext.contentResolver.query(uri, projection, null, null, null)
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

    /**
     * Decode thumbnail on initial load or reload with downsampling (P0-1)
     */
    fun loadThumbnailFromUri(maxSize: Int = 400): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }

        val outW = boundsOptions.outWidth
        val outH = boundsOptions.outHeight
        val sampledBitmap = if (outW > 0 && outH > 0) {
            var sampleSize = 1
            val maxDim = maxOf(outW, outH)
            while (maxDim / (sampleSize * 2) >= maxSize) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        return createThumbnailFromBitmap(sampledBitmap, maxSize)
    }

    /**
     * Create thumbnail directly from the current in-memory bitmap (zero disk I/O, keeps thumbnail and metrics updated)
     */
    fun createThumbnailFromBitmap(src: Bitmap, maxSize: Int = 400): Bitmap {
        val ratio: Float = if (src.width > src.height) {
            if (src.width > maxSize) src.width.toFloat() / maxSize else 1f
        } else {
            if (src.height > maxSize) src.height.toFloat() / maxSize else 1f
        }
        val targetW = (src.width.toFloat() / ratio).toInt().coerceAtLeast(1)
        val targetH = (src.height.toFloat() / ratio).toInt().coerceAtLeast(1)
        return ThumbnailUtils.extractThumbnail(src, targetW, targetH)
    }

    /**
     * Backward-compatible helper: extracts thumbnail from current bitmap
     */
    fun loadThumbnail(): Bitmap = createThumbnailFromBitmap(bitmap)

    fun process(
        algorithm: Algorithm,
        processType: ProcessType,
        key: String = "0.666",
        forceCompat: Boolean = false,
        onFallback: (() -> Unit)? = null
    ): Boolean {
        // LCG_SHUFFLE: Direct Zero-Copy in-place processing on Bitmap, strictly check JNI result (P0-2)
        if (algorithm == Algorithm.LCG_SHUFFLE) {
            if (!NativeShuffleBridge.isAvailable()) {
                return false
            }
            val seed = NativeShuffleBridge.stringToSeed(key)
            val success = NativeShuffleBridge.scrambleBitmap(bitmap, seed, processType == ProcessType.DECRYPT)
            if (!success) {
                return false
            }
            cachedMetrics = null
            thumbnail = createThumbnailFromBitmap(bitmap)
            return true
        }

        val currentBmp = bitmap
        val pixels = IntArray(currentBmp.width * currentBmp.height)
        currentBmp.getPixels(pixels, 0, currentBmp.width, 0, 0, currentBmp.width, currentBmp.height)
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
                image = NativeImageScramble.process(pixels, currentBmp.width, currentBmp.height, algoCode, processType, key)
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
                val fallbackImage = Image(pixels, currentBmp.width, currentBmp.height)
                image = when (algorithm) {
                    Algorithm.TOMATO -> TomatoScramble(fallbackImage, key.toDoubleOrNull() ?: 1.0).process(processType)
                    Algorithm.BLOCK -> BlockScramble(fallbackImage, key).process(processType)
                    Algorithm.ROW_PIXEL -> RowPixelScramble(fallbackImage, key).process(processType)
                    Algorithm.PER_PIXEL -> PerPixelScramble(fallbackImage, key).process(processType)
                    Algorithm.PIC_ENCRYPT_ROW -> PicEncryptRowScramble(fallbackImage, key.toDoubleOrNull() ?: 0.5).process(processType)
                    Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN -> PicEncryptRowColumnScramble(fallbackImage, key.toDoubleOrNull() ?: 0.5).process(processType)
                    Algorithm.LCG_SHUFFLE -> fallbackImage
                }
            } catch (t: Throwable) {
                return false
            }
        }

        if (didFallback) {
            onFallback?.invoke()
        }

        bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        cachedMetrics = null
        thumbnail = createThumbnailFromBitmap(bitmap)
        return true
    }

    fun getMetrics(): ImageMetrics {
        val cached = cachedMetrics
        if (cached != null) return cached
        val calculated = ImageMetricsHelper.calculateMetrics(thumbnail)
        cachedMetrics = calculated
        return calculated
    }

    fun rotate(degree: Float) {
        val current = bitmap
        val matrix = Matrix()
        matrix.setRotate(degree)
        bitmap = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        cachedMetrics = null
        thumbnail = createThumbnailFromBitmap(bitmap)
    }

    fun reload() {
        filename = loadFilename()
        _bitmap = null // Release in-memory modified bitmap
        inspectDimensions()
        cachedMetrics = null
        thumbnail = loadThumbnailFromUri()
    }

    fun saveWithDetail(
        index: Int = 0,
        format: ImageExportFormat = ImageExportFormat.PNG,
        quality: Int = format.defaultQuality
    ): SaveResult {
        val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        var ms = System.currentTimeMillis()
        ms -= ms / 1000 * 1000 + index
        val s = String.format(Locale.US, "%03d", ms)
        val imageName = "PicEncrypt_${dateTime + s}.${format.extension}"
        val appName = appContext.getString(R.string.app_name)
        val saveDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            appName
        )

        return try {
            val currentBmp = bitmap
            val compressFormat = format.getCompressFormat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$appName")
                }
                val insertUri = appContext.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return SaveResult.Failure(appContext.getString(R.string.save_failed_mediastore))

                val written = appContext.contentResolver.openOutputStream(insertUri)?.use { out ->
                    currentBmp.compress(compressFormat, quality, out)
                } ?: false

                if (written) SaveResult.Success else SaveResult.Failure(appContext.getString(R.string.save_failed_io))
            } else {
                if (!saveDir.exists() && !saveDir.mkdirs()) {
                    return SaveResult.Failure(appContext.getString(R.string.save_failed_dir))
                }
                val filePath = File(saveDir, imageName)
                FileOutputStream(filePath).use { out ->
                    currentBmp.compress(compressFormat, quality, out)
                    out.flush()
                }
                FileUtils.notifyMediaScanner(appContext, filePath, format.mimeType)
                SaveResult.Success
            }
        } catch (e: SecurityException) {
            SaveResult.Failure(appContext.getString(R.string.save_failed_permission), e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: appContext.getString(R.string.save_failed_io)
            SaveResult.Failure(msg, e)
        }
    }

    fun save(index: Int = 0): Boolean = saveWithDetail(index) is SaveResult.Success
}
