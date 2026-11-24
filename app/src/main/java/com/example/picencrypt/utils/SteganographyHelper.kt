package com.example.picencrypt.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest

object SteganographyHelper {

    enum class ArchiveType(val extension: String, val code: Int) {
        ZIP("zip", 1),
        RAR("rar", 2),
        SEVEN_Z("7z", 3),
        UNKNOWN("bin", 4);

        companion object {
            fun fromCode(code: Int): ArchiveType = entries.find { it.code == code } ?: UNKNOWN
        }
    }

    const val TRAILER_V2_SIZE = 64
    val STEGO_V2_MAGIC = byteArrayOf(0x52, 0x50, 0x58, 0x53, 0x54, 0x45, 0x47, 0x32) // "RPXSTEG2"

    data class DetectionResult(
        val type: ArchiveType,
        val offset: Long,
        val isV2: Boolean = false,
        val archiveSize: Long = 0L,
        val sha256: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DetectionResult
            if (type != other.type) return false
            if (offset != other.offset) return false
            if (isV2 != other.isV2) return false
            if (archiveSize != other.archiveSize) return false
            if (sha256 != null) {
                if (other.sha256 == null) return false
                if (!sha256.contentEquals(other.sha256)) return false
            } else if (other.sha256 != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + offset.hashCode()
            result = 31 * result + isV2.hashCode()
            result = 31 * result + archiveSize.hashCode()
            result = 31 * result + (sha256?.contentHashCode() ?: 0)
            return result
        }
    }

    data class StegoInspection(
        val hasArchive: Boolean,
        val type: ArchiveType = ArchiveType.UNKNOWN,
        val offset: Long = 0L,
        val totalSize: Long = 0L,
        val archiveSize: Long = 0L,
        val isV2: Boolean = false,
        val sha256: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StegoInspection
            if (hasArchive != other.hasArchive) return false
            if (type != other.type) return false
            if (offset != other.offset) return false
            if (totalSize != other.totalSize) return false
            if (archiveSize != other.archiveSize) return false
            if (isV2 != other.isV2) return false
            if (sha256 != null) {
                if (other.sha256 == null) return false
                if (!sha256.contentEquals(other.sha256)) return false
            } else if (other.sha256 != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = hasArchive.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + offset.hashCode()
            result = 31 * result + totalSize.hashCode()
            result = 31 * result + archiveSize.hashCode()
            result = 31 * result + isV2.hashCode()
            result = 31 * result + (sha256?.contentHashCode() ?: 0)
            return result
        }
    }

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val RAR4_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val RAR5_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Inspects magic headers to detect archive type (ZIP, RAR, 7Z, or UNKNOWN).
     */
    fun sniffArchiveType(headerBytes: ByteArray): ArchiveType {
        return when {
            matches(headerBytes, 0, ZIP_MAGIC) -> ArchiveType.ZIP
            matches(headerBytes, 0, RAR4_MAGIC) || matches(headerBytes, 0, RAR5_MAGIC) -> ArchiveType.RAR
            matches(headerBytes, 0, SEVEN_Z_MAGIC) -> ArchiveType.SEVEN_Z
            else -> ArchiveType.UNKNOWN
        }
    }

    /**
     * 将压缩包直接追加到图片文件的末尾（生成“图种”文件）
     * @param enableV2 启用 V2 结构化尾部索引与 SHA-256 完整性摘要
     * @param archiveType 压缩包格式，若为 null 则自动嗅探文件头识别 ZIP / RAR / 7Z
     */
    fun createStegoImage(
        imageInput: InputStream,
        archiveInput: InputStream,
        output: OutputStream,
        bufferSize: Int = 64 * 1024,
        enableV2: Boolean = true,
        archiveType: ArchiveType? = null
    ): Long {
        val archStream = if (archiveInput.markSupported()) {
            archiveInput
        } else {
            java.io.BufferedInputStream(archiveInput, bufferSize.coerceAtLeast(64))
        }

        val resolvedArchiveType = if (archiveType != null) {
            archiveType
        } else {
            archStream.mark(32)
            val sniffHeader = ByteArray(16)
            var readSniff = 0
            while (readSniff < sniffHeader.size) {
                val r = archStream.read(sniffHeader, readSniff, sniffHeader.size - readSniff)
                if (r == -1) break
                readSniff += r
            }
            archStream.reset()
            if (readSniff > 0) sniffArchiveType(sniffHeader.copyOf(readSniff)) else ArchiveType.UNKNOWN
        }

        if (enableV2) {
            val digest = MessageDigest.getInstance("SHA-256")
            var currentOffset = 0L
            val buffer = ByteArray(bufferSize)

            // 1. Write carrier image
            imageInput.use { img ->
                var read: Int
                while (img.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    currentOffset += read
                }
            }

            val archiveOffset = currentOffset
            var archiveLength = 0L

            // 2. Write archive payload while calculating SHA-256
            archStream.use { arch ->
                var read: Int
                while (arch.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    archiveLength += read
                }
            }

            val sha256 = digest.digest()

            // 3. Write 64-byte V2 Trailer
            val trailer = ByteBuffer.allocate(TRAILER_V2_SIZE).apply {
                putLong(archiveOffset)
                putLong(archiveLength)
                putInt(resolvedArchiveType.code)
                put(sha256)
                putShort(2.toShort()) // version 2
                putShort(0.toShort()) // flags
                put(STEGO_V2_MAGIC) // 8 bytes: "RPXSTEG2"
            }.array()

            output.write(trailer)
            output.flush()

            return archiveOffset + archiveLength + TRAILER_V2_SIZE
        } else {
            // Legacy V1 creation without trailer
            var totalBytes = 0L
            val buffer = ByteArray(bufferSize)

            imageInput.use { img ->
                var read: Int
                while (img.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalBytes += read
                }
            }

            archStream.use { arch ->
                var read: Int
                while (arch.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalBytes += read
                }
            }

            output.flush()
            return totalBytes
        }
    }

    /**
     * 便捷文件重载
     */
    fun createStegoImage(
        imageFile: File,
        archiveFile: File,
        outputFile: File,
        enableV2: Boolean = true,
        archiveType: ArchiveType? = null
    ): Long {
        return createStegoImage(
            FileInputStream(imageFile),
            FileInputStream(archiveFile),
            FileOutputStream(outputFile),
            enableV2 = enableV2,
            archiveType = archiveType
        )
    }

    /**
     * 解析 Stego V2 64-byte 尾部索引
     */
    fun parseV2Trailer(trailerBytes: ByteArray, totalFileSize: Long = 0L): DetectionResult? {
        if (trailerBytes.size != TRAILER_V2_SIZE) return null
        for (i in 0 until 8) {
            if (trailerBytes[56 + i] != STEGO_V2_MAGIC[i]) return null
        }
        val buffer = ByteBuffer.wrap(trailerBytes)
        val offset = buffer.long
        val length = buffer.long
        val typeCode = buffer.int
        val sha256 = ByteArray(32)
        buffer.get(sha256)
        val version = buffer.short
        val flags = buffer.short

        if (version.toInt() != 2) return null
        if (offset < 8L || length <= 0L) return null
        if (totalFileSize > 0L && offset + length + TRAILER_V2_SIZE != totalFileSize) {
            return null
        }
        return DetectionResult(
            type = ArchiveType.fromCode(typeCode),
            offset = offset,
            isV2 = true,
            archiveSize = length,
            sha256 = sha256
        )
    }

    /**
     * 流式预检探查：优先通过文件尾部通道执行 O(1) V2 秒级检索，若非 V2 则平滑降级至 V1 扫描
     */
    fun detectArchiveFromStream(input: InputStream, totalSize: Long = 0L): StegoInspection {
        // 1. 尝试通过可寻址通道在 O(1) 内命中 V2 Trailer
        if (totalSize >= TRAILER_V2_SIZE + 16L) {
            val channel = (input as? FileInputStream)?.channel
            if (channel != null) {
                try {
                    val currentPos = channel.position()
                    channel.position(totalSize - TRAILER_V2_SIZE)
                    val trailerBuffer = ByteBuffer.allocate(TRAILER_V2_SIZE)
                    while (trailerBuffer.hasRemaining()) {
                        val read = channel.read(trailerBuffer)
                        if (read == -1) break
                    }
                    if (!trailerBuffer.hasRemaining()) {
                        val v2 = parseV2Trailer(trailerBuffer.array(), totalSize)
                        if (v2 != null) {
                            return StegoInspection(
                                hasArchive = true,
                                type = v2.type,
                                offset = v2.offset,
                                totalSize = totalSize,
                                archiveSize = v2.archiveSize,
                                isV2 = true,
                                sha256 = v2.sha256
                            )
                        }
                    }
                    channel.position(currentPos)
                } catch (e: Exception) {
                    // Fall back to sequential scan
                }
            }
        }

        // 2. 传统 V1 流式扫描兜底
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
                        archiveSize = archSize,
                        isV2 = false
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
     * 智能扫描文件：优先以 O(1) 检查末尾 V2 Trailer，若非 V2 则通过 PNG IEND / 启发式扫描兜底
     */
    fun detectArchiveOffset(stegoFile: File): DetectionResult? {
        if (!stegoFile.exists() || stegoFile.length() < 32) return null

        // 1. O(1) 检索 V2 Trailer
        if (stegoFile.length() >= TRAILER_V2_SIZE + 16L) {
            try {
                RandomAccessFile(stegoFile, "r").use { raf ->
                    raf.seek(stegoFile.length() - TRAILER_V2_SIZE)
                    val trailerBytes = ByteArray(TRAILER_V2_SIZE)
                    raf.readFully(trailerBytes)
                    val v2Result = parseV2Trailer(trailerBytes, stegoFile.length())
                    if (v2Result != null) {
                        return v2Result
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall back to V1 heuristic scan
            }
        }

        // 2. V1 启发式定位
        val pngEnd = findPngImageEnd(stegoFile)
        val searchStartOffset = when {
            pngEnd != null && pngEnd < stegoFile.length() -> pngEnd
            else -> 16L
        }

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
                        return currentOffset + length.toLong() + 4L
                    }

                    val skipBytes = length.toLong() + 4L
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
                    return DetectionResult(found.first, actualOffset, isV2 = false)
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
        return try {
            FileInputStream(stegoFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    extractArchiveStream(
                        fis,
                        fos,
                        detection.offset,
                        expectedLength = if (detection.isV2) detection.archiveSize else -1L,
                        expectedSha256 = detection.sha256
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 流式提取隐藏压缩包：跳过前置图片数据后直接向目标输出流分块写入。
     * 若提供了 expectedLength 与 expectedSha256，则执行精准长度截断与哈希校验，杜绝截断或篡改。
     */
    fun extractArchiveStream(
        input: InputStream,
        output: OutputStream,
        offset: Long,
        expectedLength: Long = -1L,
        expectedSha256: ByteArray? = null
    ): Boolean {
        if (!skipFully(input, offset)) return false
        val buffer = ByteArray(64 * 1024)

        if (expectedLength > 0L) {
            val digest = if (expectedSha256 != null) MessageDigest.getInstance("SHA-256") else null
            var remaining = expectedLength
            while (remaining > 0L) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read == -1) return false
                output.write(buffer, 0, read)
                digest?.update(buffer, 0, read)
                remaining -= read
            }
            output.flush()

            if (expectedSha256 != null && digest != null) {
                val actual = digest.digest()
                if (!actual.contentEquals(expectedSha256)) {
                    return false
                }
            }
            return true
        } else {
            // Legacy V1 mode: stream until EOF
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
            return true
        }
    }

    /**
     * 健壮地跳过指定字节数，兼容不同底层 InputStream.skip 的截断行为
     */
    fun skipFully(input: InputStream, bytesToSkip: Long): Boolean {
        var remaining = bytesToSkip
        val buffer = ByteArray(minOf(64 * 1024, remaining.toInt()).coerceAtLeast(1024))
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) return false
                remaining -= read
            }
        }
        return remaining == 0L
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
