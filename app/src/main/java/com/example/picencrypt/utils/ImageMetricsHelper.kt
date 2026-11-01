package com.example.picencrypt.utils

import android.graphics.Bitmap
import kotlin.math.log2
import kotlin.math.sqrt

data class ImageMetrics(
    val entropy: Double,            // 香农信息熵 (0.0 ~ 8.0 bits)
    val correlation: Double,        // 相邻像素相关性 (-1.0 ~ 1.0)
    val scrambleScore: Float,       // 混乱度评分 (0.0% ~ 100.0%)
    val rHistogram: FloatArray,     // 32 bins 归一化 R 通道直方图
    val gHistogram: FloatArray,     // 32 bins 归一化 G 通道直方图
    val bHistogram: FloatArray      // 32 bins 归一化 B 通道直方图
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImageMetrics
        if (entropy != other.entropy) return false
        if (correlation != other.correlation) return false
        if (scrambleScore != other.scrambleScore) return false
        if (!rHistogram.contentEquals(other.rHistogram)) return false
        if (!gHistogram.contentEquals(other.gHistogram)) return false
        if (!bHistogram.contentEquals(other.bHistogram)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entropy.hashCode()
        result = 31 * result + correlation.hashCode()
        result = 31 * result + scrambleScore.hashCode()
        result = 31 * result + rHistogram.contentHashCode()
        result = 31 * result + gHistogram.contentHashCode()
        result = 31 * result + bHistogram.contentHashCode()
        return result
    }
}

object ImageMetricsHelper {

    /**
     * 高性能计算图像信息熵、相邻相关性与 RGB 通道直方图 (O(N) 单次遍历)
     */
    fun calculateMetrics(bitmap: Bitmap): ImageMetrics {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        if (totalPixels <= 0) {
            return ImageMetrics(0.0, 0.0, 0f, FloatArray(32), FloatArray(32), FloatArray(32))
        }

        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return calculateMetrics(pixels, width, height)
    }

    /**
     * 纯像素数据核心重载：解耦 Android Bitmap，支持 JVM 快速单元测试
     */
    fun calculateMetrics(pixels: IntArray, width: Int, height: Int): ImageMetrics {
        val totalPixels = width * height
        if (totalPixels <= 0 || pixels.size < totalPixels) {
            return ImageMetrics(0.0, 0.0, 0f, FloatArray(32), FloatArray(32), FloatArray(32))
        }

        // 1. 直方图统计 (32 bins) 与 灰度频数统计 (256 bins)
        val rBins = IntArray(32)
        val gBins = IntArray(32)
        val bBins = IntArray(32)
        val grayCounts = LongArray(256)

        // 2. 相邻像素相关性统计 (统计水平相邻像素对)
        var sumX = 0.0
        var sumY = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0
        var sumXY = 0.0
        var pairCount = 0L

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val color = pixels[rowStart + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                rBins[r shr 3]++
                gBins[g shr 3]++
                bBins[b shr 3]++

                // 工业级灰度加权：0.299R + 0.587G + 0.114B
                val gray = (r * 299 + g * 587 + b * 114) / 1000
                grayCounts[gray]++

                // 水平相邻像素相关对
                if (x < width - 1) {
                    val nextColor = pixels[rowStart + x + 1]
                    val nr = (nextColor shr 16) and 0xFF
                    val ng = (nextColor shr 8) and 0xFF
                    val nb = nextColor and 0xFF
                    val nextGray = (nr * 299 + ng * 587 + nb * 114) / 1000

                    val gx = gray.toDouble()
                    val gy = nextGray.toDouble()
                    sumX += gx
                    sumY += gy
                    sumX2 += gx * gx
                    sumY2 += gy * gy
                    sumXY += gx * gy
                    pairCount++
                }
            }
        }

        // 计算香农信息熵
        var entropy = 0.0
        val totalDouble = totalPixels.toDouble()
        for (i in 0 until 256) {
            val count = grayCounts[i]
            if (count > 0) {
                val p = count / totalDouble
                entropy -= p * (log2(p))
            }
        }
        entropy = entropy.coerceIn(0.0, 8.0)

        // 计算水平相邻像素 Pearson 相关系数
        var correlation = 0.0
        if (pairCount > 0) {
            val n = pairCount.toDouble()
            val meanX = sumX / n
            val meanY = sumY / n
            val varX = (sumX2 / n) - (meanX * meanX)
            val varY = (sumY2 / n) - (meanY * meanY)
            val covXY = (sumXY / n) - (meanX * meanY)

            if (varX > 1e-4 && varY > 1e-4) {
                correlation = covXY / (sqrt(varX) * sqrt(varY))
            }
        }
        correlation = correlation.coerceIn(-1.0, 1.0)

        // 混乱度综合评分 (0% ~ 100%)
        val decorrelationFactor = (1.0 - correlation.coerceIn(0.0, 1.0))
        val entropyFactor = (entropy / 8.0).coerceIn(0.0, 1.0)
        val score = ((decorrelationFactor * 0.6 + entropyFactor * 0.4) * 100.0).toFloat().coerceIn(0f, 100f)

        // 归一化直方图 (0.0f ~ 1.0f)
        val maxR = (rBins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val maxG = (gBins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val maxB = (bBins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()

        val rNorm = FloatArray(32) { rBins[it] / maxR }
        val gNorm = FloatArray(32) { gBins[it] / maxG }
        val bNorm = FloatArray(32) { bBins[it] / maxB }

        return ImageMetrics(
            entropy = entropy,
            correlation = correlation,
            scrambleScore = score,
            rHistogram = rNorm,
            gHistogram = gNorm,
            bHistogram = bNorm
        )
    }
}
