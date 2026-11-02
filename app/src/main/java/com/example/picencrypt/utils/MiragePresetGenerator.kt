package com.example.picencrypt.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Built-in demo presets for Mirage Tank.
 * Allows users to instantly experience and test Mirage Tank without searching for paired photos.
 */
object MiragePresetGenerator {

    enum class PresetTheme(val labelResId: Int) {
        DAY_AND_NIGHT(0),
        QUESTION_AND_TRUTH(1)
    }

    private data class PresetDescriptor(
        val coverEmoji: String,
        val coverTitle: String,
        val hiddenEmoji: String,
        val hiddenTitle: String
    )

    fun generatePresetBitmaps(theme: PresetTheme, size: Int = 400): Pair<Bitmap, Bitmap> {
        val desc = when (theme) {
            PresetTheme.DAY_AND_NIGHT -> PresetDescriptor(
                coverEmoji = "☀️",
                coverTitle = "晴空万里 (表图)",
                hiddenEmoji = "🌙",
                hiddenTitle = "暗夜星空 (里图)"
            )
            PresetTheme.QUESTION_AND_TRUTH -> PresetDescriptor(
                coverEmoji = "❓",
                coverTitle = "表面充满疑问",
                hiddenEmoji = "❗",
                hiddenTitle = "点开揭晓真相！"
            )
        }

        val coverBmp = drawCard(size, isDark = false, desc.coverEmoji, desc.coverTitle)
        val hiddenBmp = drawCard(size, isDark = true, desc.hiddenEmoji, desc.hiddenTitle)
        return Pair(coverBmp, hiddenBmp)
    }

    private fun drawCard(size: Int, isDark: Boolean, emoji: String, title: String): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(if (isDark) Color.BLACK else Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.WHITE else Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        // Main Emoji
        paint.textSize = size * 0.28f
        canvas.drawText(emoji, size / 2f, size * 0.44f, paint)

        // Subtitle Text
        paint.textSize = size * 0.075f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, size / 2f, size * 0.68f, paint)

        // Subtle Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.DKGRAY else Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(8f, 8f, size - 8f, size - 8f, borderPaint)

        return bmp
    }

    fun createPresetUris(context: Context, theme: PresetTheme): Pair<Uri, Uri> {
        val (coverBmp, hiddenBmp) = generatePresetBitmaps(theme)
        val cacheDir = File(context.cacheDir, "mirage_presets")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val coverFile = File(cacheDir, "preset_cover_${theme.name.lowercase()}.png")
        val hiddenFile = File(cacheDir, "preset_hidden_${theme.name.lowercase()}.png")

        FileOutputStream(coverFile).use { coverBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileOutputStream(hiddenFile).use { hiddenBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val coverUri = Uri.fromFile(coverFile)
        val hiddenUri = Uri.fromFile(hiddenFile)
        return Pair(coverUri, hiddenUri)
    }
}
