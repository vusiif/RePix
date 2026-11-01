package com.example.picencrypt.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object SteganographyHelper {

    enum class ArchiveType(val extension: String) {
        ZIP("zip"),
        RAR("rar"),
        SEVEN_Z("7z"),
        UNKNOWN("bin")
    }

    data class DetectionResult(
        val type: ArchiveType,
        val offset: Long
    )

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val RAR4_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val RAR5_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    /**
     * 将压缩包直接追加到图片文件的末尾（生成“图种”文件）
     */
    fun createStegoImage(
        imageInput: InputStream,
        archiveInput: InputStream,
        output: OutputStream,
        bufferSize: Int = 64 * 1024
    ): Long {
        var totalBytes = 0L
        val buffer = ByteArray(bufferSize)

        imageInput.use { img ->
            var read: Int
            while (img.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                totalBytes += read
            }
        }

        archiveInput.use { arch ->
            var read: Int
            while (arch.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                totalBytes += read
            }
        }

        output.flush()
        return totalBytes
    }

    /**
     * 便捷文件重载
     */
    fun createStegoImage(
        imageFile: File,
        archiveFile: File,
        outputFile: File
    ): Long {
        return createStegoImage(
            FileInputStream(imageFile),
            FileInputStream(archiveFile),
            FileOutputStream(outputFile)
        )
    }

    /**
     * 扫描文件，检测末尾是否藏有压缩包特征码并返回其文件偏移量及类型
     */
    fun detectArchiveOffset(stegoFile: File): DetectionResult? {
        if (!stegoFile.exists() || stegoFile.length() < 32) return null

        val fileSize = stegoFile.length()
        // 图种通常在图片末尾追加，我们以滑动窗口流式匹配 magic
        FileInputStream(stegoFile).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var currentPos = 0L
            var prevOverlap = ByteArray(0)

            while (true) {
                val read = fis.read(buffer)
                if (read <= 0) break

                val combined = if (prevOverlap.isNotEmpty()) {
                    val temp = ByteArray(prevOverlap.size + read)
                    System.arraycopy(prevOverlap, 0, temp, 0, prevOverlap.size)
                    System.arraycopy(buffer, 0, temp, prevOverlap.size, read)
                    temp
                } else {
                    buffer.copyOf(read)
                }

                // 避免在文件最开头就把纯 zip 误判为图种（图种至少有图片数据）
                val searchStart = if (currentPos == 0L) 16 else 0

                val found = findFirstMagic(combined, searchStart)
                if (found != null) {
                    val relativeOffset = found.second
                    val actualOffset = (currentPos - prevOverlap.size) + relativeOffset
                    return DetectionResult(found.first, actualOffset)
                }

                // 保留尾部 16 字节以防 magic 跨缓冲区
                val overlapLen = minOf(16, read)
                prevOverlap = buffer.copyOfRange(read - overlapLen, read)
                currentPos += read
            }
        }
        return null
    }

    /**
     * 从图种中提取出原始压缩包
     */
    fun extractArchive(stegoFile: File, outputFile: File): Boolean {
        val detection = detectArchiveOffset(stegoFile) ?: return false
        val offset = detection.offset

        FileInputStream(stegoFile).use { fis ->
            var skipped = 0L
            while (skipped < offset) {
                val s = fis.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }
            if (skipped < offset) return false

            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                }
                fos.flush()
            }
        }
        return true
    }

    private fun findFirstMagic(bytes: ByteArray, startIndex: Int): Pair<ArchiveType, Int>? {
        for (i in startIndex until bytes.size) {
            if (matches(bytes, i, ZIP_MAGIC)) {
                return Pair(ArchiveType.ZIP, i)
            }
            if (matches(bytes, i, RAR4_MAGIC) || matches(bytes, i, RAR5_MAGIC)) {
                return Pair(ArchiveType.RAR, i)
            }
            if (matches(bytes, i, SEVEN_Z_MAGIC)) {
                return Pair(ArchiveType.SEVEN_Z, i)
            }
        }
        return null
    }

    private fun matches(bytes: ByteArray, offset: Int, pattern: ByteArray): Boolean {
        if (offset + pattern.size > bytes.size) return false
        for (k in pattern.indices) {
            if (bytes[offset + k] != pattern[k]) return false
        }
        return true
    }
}
