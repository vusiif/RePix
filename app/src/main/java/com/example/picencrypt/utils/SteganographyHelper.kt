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

    data class StegoInspection(
        val hasArchive: Boolean,
        val type: ArchiveType = ArchiveType.UNKNOWN,
        val offset: Long = 0L,
        val totalSize: Long = 0L,
        val archiveSize: Long = 0L
    )

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val RAR4_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val RAR5_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

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
     * 流式预检探查：直接在流上探查隐藏压缩包，避免完整复制文件带来的大 I/O 阻塞
     */
    fun detectArchiveFromStream(input: InputStream, totalSize: Long = 0L): StegoInspection {
        return try {
            input.use { stream ->
                var currentOffset = 0L
                val header = ByteArray(8)
                val readH = stream.read(header)
                if (readH != 8) {
                    return StegoInspection(hasArchive = false, totalSize = totalSize)
                }
                currentOffset += 8

                val isPng = header.contentEquals(PNG_MAGIC)
                if (isPng) {
                    val chunkHeader = ByteArray(8)
                    while (true) {
                        val readChunk = stream.read(chunkHeader)
                        if (readChunk < 8) break
                        currentOffset += 8

                        val length = ((chunkHeader[0].toInt() and 0xFF) shl 24) or
                                ((chunkHeader[1].toInt() and 0xFF) shl 16) or
                                ((chunkHeader[2].toInt() and 0xFF) shl 8) or
                                (chunkHeader[3].toInt() and 0xFF)
                        val type = String(chunkHeader, 4, 4, Charsets.US_ASCII)

                        val skipBytes = length.toLong() + 4L
                        if (type == "IEND") {
                            var skipped = 0L
                            while (skipped < skipBytes) {
                                val s = stream.skip(skipBytes - skipped)
                                if (s <= 0) break
                                skipped += s
                            }
                            currentOffset += skipped
                            break
                        } else {
                            var skipped = 0L
                            while (skipped < skipBytes) {
                                val s = stream.skip(skipBytes - skipped)
                                if (s <= 0) break
                                skipped += s
                            }
                            currentOffset += skipped
                        }
                    }
                } else {
                    var skipped = 0L
                    val needSkip = 16L - currentOffset
                    if (needSkip > 0) {
                        while (skipped < needSkip) {
                            val s = stream.skip(needSkip - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                        currentOffset += skipped
                    }
                }

                val buffer = ByteArray(64 * 1024)
                var prevOverlap = ByteArray(0)
                var foundResult: DetectionResult? = null

                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break

                    val combined = if (prevOverlap.isNotEmpty()) {
                        val temp = ByteArray(prevOverlap.size + read)
                        System.arraycopy(prevOverlap, 0, temp, 0, prevOverlap.size)
                        System.arraycopy(buffer, 0, temp, prevOverlap.size, read)
                        temp
                    } else {
                        buffer.copyOf(read)
                    }

                    val found = findFirstMagic(combined, 0)
                    if (found != null) {
                        val relativeOffset = found.second
                        val actualOffset = (currentOffset - prevOverlap.size) + relativeOffset
                        foundResult = DetectionResult(found.first, actualOffset)
                        break
                    }

                    val overlapLen = minOf(16, read)
                    prevOverlap = buffer.copyOfRange(read - overlapLen, read)
                    currentOffset += read
                }

                if (foundResult != null) {
                    val archSize = if (totalSize > foundResult.offset) totalSize - foundResult.offset else 0L
                    StegoInspection(
                        hasArchive = true,
                        type = foundResult.type,
                        offset = foundResult.offset,
                        totalSize = totalSize,
                        archiveSize = archSize
                    )
                } else {
                    StegoInspection(
                        hasArchive = false,
                        totalSize = totalSize
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            StegoInspection(hasArchive = false, totalSize = totalSize)
        }
    }

    /**
     * 智能扫描文件，优先通过 PNG IEND / JPEG EOI 定位真实图片结束边界，
     * 避免图片内部压缩数据随机特征码假阳性（P0-3 优化）
     */
    fun detectArchiveOffset(stegoFile: File): DetectionResult? {
        if (!stegoFile.exists() || stegoFile.length() < 32) return null

        // 1. 尝试通过标准容器头部定位精确文件尾
        val pngEnd = findPngImageEnd(stegoFile)
        val searchStartOffset = when {
            pngEnd != null && pngEnd < stegoFile.length() -> pngEnd
            else -> 16L // 通用兜底，避开最前导 Header
        }

        // 2. 从目标起始偏移开始进行特征码搜索
        return scanForArchive(stegoFile, searchStartOffset)
    }

    /**
     * 沿 PNG Chunk 链精准寻找 IEND 块结束偏移量
     */
    fun findPngImageEnd(file: File): Long? {
        if (file.length() < 24) return null
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(8)
                if (fis.read(header) != 8 || !header.contentEquals(PNG_MAGIC)) return null

                var currentOffset = 8L
                val chunkHeader = ByteArray(8)
                while (true) {
                    val read = fis.read(chunkHeader)
                    if (read < 8) break
                    currentOffset += 8

                    val length = ((chunkHeader[0].toInt() and 0xFF) shl 24) or
                            ((chunkHeader[1].toInt() and 0xFF) shl 16) or
                            ((chunkHeader[2].toInt() and 0xFF) shl 8) or
                            (chunkHeader[3].toInt() and 0xFF)
                    val type = String(chunkHeader, 4, 4, Charsets.US_ASCII)

                    if (type == "IEND") {
                        // IEND 数据长度为 0，后面跟 4 字节 CRC
                        return currentOffset + length.toLong() + 4L
                    }

                    val skipBytes = length.toLong() + 4L // 数据 + CRC
                    var skipped = 0L
                    while (skipped < skipBytes) {
                        val s = fis.skip(skipBytes - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    currentOffset += skipBytes
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun scanForArchive(file: File, startOffset: Long): DetectionResult? {
        FileInputStream(file).use { fis ->
            // 跳过图片前置数据
            var skipped = 0L
            while (skipped < startOffset) {
                val s = fis.skip(startOffset - skipped)
                if (s <= 0) break
                skipped += s
            }

            val buffer = ByteArray(64 * 1024)
            var currentPos = startOffset
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

                val found = findFirstMagic(combined, 0)
                if (found != null) {
                    val relativeOffset = found.second
                    val actualOffset = (currentPos - prevOverlap.size) + relativeOffset
                    return DetectionResult(found.first, actualOffset)
                }

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
