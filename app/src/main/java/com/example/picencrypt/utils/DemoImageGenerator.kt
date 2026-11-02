package com.example.picencrypt.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Generates built-in demo sample images for quick scrambling and unscrambling testing.
 */
object DemoImageGenerator {

    data class DemoSampleInfo(
        val name: String,
        val width: Int,
        val height: Int,
        val description: String
    )

    fun getSampleInfo(): DemoSampleInfo = DemoSampleInfo(
        name = "repix_demo_sample.png",
        width = 600,
        height = 600,
        description = "High-contrast geometric pattern for algorithm benchmarking"
    )

    /**
     * Creates a high-contrast test bitmap with rich geometric and frequency features.
     */
    fun createDemoBitmap(width: Int = 600, height: Int = 600): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Vibrant gradient background
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.rgb(33, 150, 243),
                Color.rgb(156, 39, 176),
                Color.rgb(255, 87, 34)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // 2. High frequency geometric shapes for algorithm testing
        paint.color = Color.WHITE
        paint.alpha = 230
        canvas.drawCircle(width / 2f, height / 2f, width / 3.2f, paint)

        paint.color = Color.rgb(255, 193, 7)
        paint.alpha = 240
        canvas.drawRect(width * 0.32f, height * 0.32f, width * 0.68f, height * 0.68f, paint)

        // 3. Central badge and text
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * 0.08f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("RePix", width / 2f, height * 0.48f, paint)

        paint.textSize = width * 0.045f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("算法体验样本", width / 2f, height * 0.58f, paint)

        // 4. Contrast grid borders
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        canvas.drawRect(12f, 12f, width - 12f, height - 12f, paint)

        return bitmap
    }

    /**
     * Writes the demo bitmap to the cache directory and returns its local Uri.
     */
    fun createDemoImageUri(context: Context): Uri {
        val bitmap = createDemoBitmap()
        val cacheDir = File(context.cacheDir, "demo_samples")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val sampleFile = File(cacheDir, "repix_demo_sample.png")
        FileOutputStream(sampleFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(sampleFile)
    }
}
