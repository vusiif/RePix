package com.example.picencrypt.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.picencrypt.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object MirageTankBridge {

    init {
        try {
            System.loadLibrary("repix_native")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    /**
     * JNI 原生光棱坦克（全彩幻影坦克）处理
     * @return true 处理成功, false 失败
     */
    external fun processColoredMirageTank(
        coverBitmap: Bitmap,
        hiddenBitmap: Bitmap,
        outBitmap: Bitmap
    ): Boolean

    /**
     * JNI 原生黑白幻影坦克处理
     * @return true 处理成功, false 失败
     */
    external fun processGrayMirageTank(
        coverBitmap: Bitmap,
        hiddenBitmap: Bitmap,
        outBitmap: Bitmap
    ): Boolean

    /**
     * 将两张可能尺寸不同的 Bitmap 缩放至统一规格，并确保配置为 ARGB_8888
     */
    fun alignBitmaps(
        cover: Bitmap,
        hidden: Bitmap,
        maxDimension: Int = 1200
    ): Triple<Bitmap, Bitmap, Bitmap> {
        var targetW = max(cover.width, hidden.width)
        var targetH = max(cover.height, hidden.height)

        if (maxDimension > 0 && (targetW > maxDimension || targetH > maxDimension)) {
            val ratio = if (targetW > targetH) {
                targetW.toFloat() / maxDimension
            } else {
                targetH.toFloat() / maxDimension
            }
            targetW = (targetW / ratio).toInt().coerceAtLeast(1)
            targetH = (targetH / ratio).toInt().coerceAtLeast(1)
        }

        val scaledCover = Bitmap.createScaledBitmap(cover, targetW, targetH, true).ensureArgb8888()
        val scaledHidden = Bitmap.createScaledBitmap(hidden, targetW, targetH, true).ensureArgb8888()
        val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

        return Triple(scaledCover, scaledHidden, outBitmap)
    }

    /**
     * 高阶合成流程：支持全彩/黑白模式生成，并强制以 PNG 格式保存到系统相册
     */
    fun createAndSaveMirageTank(
        context: Context,
        cover: Bitmap,
        hidden: Bitmap,
        isColored: Boolean = true,
        maxDimension: Int = 1200
    ): Pair<Boolean, Bitmap?> {
        val (scaledCover, scaledHidden, outBitmap) = alignBitmaps(cover, hidden, maxDimension)

        val success = if (isColored) {
            processColoredMirageTank(scaledCover, scaledHidden, outBitmap)
        } else {
            processGrayMirageTank(scaledCover, scaledHidden, outBitmap)
        }

        if (!success) {
            return Pair(false, null)
        }

        val saved = saveMiragePng(context, outBitmap)
        return Pair(saved, outBitmap)
    }

    /**
     * 幻影坦克关键：必须以 PNG 无损格式保存，严禁使用 JPEG 压缩破坏 Alpha 通道
     */
    fun saveMiragePng(context: Context, bitmap: Bitmap): Boolean {
        val appName = context.getString(R.string.app_name)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Mirage_${timeStamp}.png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$appName")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return false
            } else {
                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName)
                if (!saveDir.exists()) saveDir.mkdirs()
                val outFile = File(saveDir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://${outFile.absolutePath}")))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun Bitmap.ensureArgb8888(): Bitmap {
        if (this.config == Bitmap.Config.ARGB_8888 && this.isMutable) {
            return this
        }
        val copy = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(copy)
        canvas.drawBitmap(this, 0f, 0f, null)
        return copy
    }
}
