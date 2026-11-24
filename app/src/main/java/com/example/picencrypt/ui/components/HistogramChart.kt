package com.example.picencrypt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * RGB 3-Channel Histogram Distribution Chart Component.
 */
@Composable
fun HistogramChart(
    rHist: FloatArray,
    gHist: FloatArray,
    bHist: FloatArray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val binCount = 32
        val binWidth = w / binCount

        // Background reference grid lines for spectrum distribution
        val gridColor = Color.Gray.copy(alpha = 0.22f)
        drawLine(gridColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h * 0.50f), Offset(w, h * 0.50f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 2f)

        fun drawChannel(hist: FloatArray, color: Color) {
            val fillPath = Path()
            val strokePath = Path()
            fillPath.moveTo(0f, h)
            for (i in 0 until binCount) {
                val value = hist.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                val x = i * binWidth + binWidth / 2f
                val y = h - (value * h * 0.88f)
                fillPath.lineTo(x, y)
                if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
            }
            fillPath.lineTo(w, h)
            fillPath.close()
            drawPath(fillPath, color.copy(alpha = 0.28f))
            drawPath(strokePath, color.copy(alpha = 0.85f), style = Stroke(width = 2.5f))
        }

        drawChannel(rHist, Color.Red)
        drawChannel(gHist, Color.Green)
        drawChannel(bHist, Color.Blue)
    }
}
