package com.example.picencrypt.utils

import androidx.annotation.VisibleForTesting
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard Cryptographic Security Engine for RePix (AES-256-GCM + PBKDF2).
 * Provides authenticated, tamper-proof container (.rpx) streaming encryption and decryption.
 *
 * Supports RPX V2 (with full header AAD authentication & 60,000 PBKDF2 iterations)
 * and preserves backward-compatible read-only decryption for legacy RPX V1 files.
 */
object CryptoUtils {

    // Magic Header: "RPX1"
    val MAGIC_HEADER = byteArrayOf(0x52.toByte(), 0x50.toByte(), 0x58.toByte(), 0x31.toByte())

    const val VERSION_1: Byte = 1
    const val VERSION_2: Byte = 2
    const val VERSION_3: Byte = 3
    const val CURRENT_VERSION: Byte = VERSION_3

    const val ALGORITHM_AES_256_GCM: Byte = 1

    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    const val PBKDF2_ITERATIONS_V1 = 15000
    const val PBKDF2_ITERATIONS_V2 = 60000
    private const val KEY_LENGTH_BITS = 256
    const val DEFAULT_CHUNK_SIZE = 64 * 1024

    private val secureRandom = SecureRandom()

    data class RpxHeader(
        val version: Byte,
        val algo: Byte,
        val salt: ByteArray,
        val iv: ByteArray,
        val originalFilename: String,
        val plainLength: Long,
        val iterations: Int = PBKDF2_ITERATIONS_V1,
        val aadBytes: ByteArray? = null
    ) {
        val isFilenameProtected: Boolean
            get() = version >= VERSION_3

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RpxHeader
            if (version != other.version) return false
            if (algo != other.algo) return false
            if (!salt.contentEquals(other.salt)) return false
            if (!iv.contentEquals(other.iv)) return false
            if (originalFilename != other.originalFilename) return false
            if (plainLength != other.plainLength) return false
            if (iterations != other.iterations) return false
            if (aadBytes != null) {
                if (other.aadBytes == null || !aadBytes.contentEquals(other.aadBytes)) return false
            } else if (other.aadBytes != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = version.toInt()
            result = 31 * result + algo.toInt()
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + originalFilename.hashCode()
            result = 31 * result + plainLength.hashCode()
            result = 31 * result + iterations
            result = 31 * result + (aadBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    sealed class DecryptResult {
        data class Success(val originalFilename: String, val plainBytes: ByteArray) : DecryptResult()
        object WrongPasswordOrCorrupted : DecryptResult()
        data class InvalidFormat(val reason: String) : DecryptResult()
    }

    /**
     * Derive a 256-bit AES key from password and salt using PBKDF2-HMAC-SHA256
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS_V2
    ): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Inspect and parse header of an .rpx stream without decrypting payload.
     * Stream position advances to the start of ciphertext upon return.
     */
    fun inspectHeader(inputStream: InputStream): RpxHeader? {
        val magic = ByteArray(MAGIC_HEADER.size)
        if (readFully(inputStream, magic) != magic.size) return null
        if (!magic.contentEquals(MAGIC_HEADER)) return null

        val version = inputStream.read()
        if (version == -1) return null
        val versionByte = version.toByte()

        val algo = inputStream.read()
        if (algo == -1) return null
        val algoByte = algo.toByte()
        if (algoByte != ALGORITHM_AES_256_GCM) return null

        val iterations = when (versionByte) {
            VERSION_1 -> PBKDF2_ITERATIONS_V1
            VERSION_2, VERSION_3 -> {
                val iterBytes = ByteArray(4)
                if (readFully(inputStream, iterBytes) != 4) return null
                val iters = ByteBuffer.wrap(iterBytes).int
                if (iters < 1000 || iters > 1_000_000) return null
                iters
            }
            else -> return null
        }

        val salt = ByteArray(SALT_LENGTH)
        if (readFully(inputStream, salt) != SALT_LENGTH) return null

        val iv = ByteArray(IV_LENGTH)
        if (readFully(inputStream, iv) != IV_LENGTH) return null

        if (versionByte == VERSION_3) {
            val lenBytes = ByteArray(8)
            if (readFully(inputStream, lenBytes) != 8) return null
            val plainLength = ByteBuffer.wrap(lenBytes).long

            val totalHeaderSize = MAGIC_HEADER.size + 1 + 1 + 4 + SALT_LENGTH + IV_LENGTH + 8
            val aadBytes = ByteBuffer.allocate(totalHeaderSize).apply {
                put(MAGIC_HEADER)
                put(versionByte)
                put(algoByte)
                putInt(iterations)
                put(salt)
                put(iv)
                putLong(plainLength)
            }.array()

            return RpxHeader(
                version = versionByte,
                algo = algoByte,
                salt = salt,
                iv = iv,
                originalFilename = "",
                plainLength = plainLength,
                iterations = iterations,
                aadBytes = aadBytes
            )
        }

        val nameLenBytes = ByteArray(2)
        if (readFully(inputStream, nameLenBytes) != 2) return null
        val nameLen = ByteBuffer.wrap(nameLenBytes).short.toInt() and 0xFFFF
        if (nameLen > 4096) return null

        val nameBytes = ByteArray(nameLen)
        if (readFully(inputStream, nameBytes) != nameLen) return null
        val filename = String(nameBytes, StandardCharsets.UTF_8)

        val lenBytes = ByteArray(8)
        if (readFully(inputStream, lenBytes) != 8) return null
        val plainLength = ByteBuffer.wrap(lenBytes).long

        val aadBytes = if (versionByte == VERSION_2) {
            val totalHeaderSize = MAGIC_HEADER.size + 1 + 1 + 4 + SALT_LENGTH + IV_LENGTH + 2 + nameBytes.size + 8
            ByteBuffer.allocate(totalHeaderSize).apply {
                put(MAGIC_HEADER)
                put(versionByte)
                put(algoByte)
                putInt(iterations)
                put(salt)
                put(iv)
                putShort(nameBytes.size.toShort())
                put(nameBytes)
                putLong(plainLength)
            }.array()
        } else {
            null
        }

        return RpxHeader(
            version = versionByte,
            algo = algoByte,
            salt = salt,
            iv = iv,
            originalFilename = filename,
            plainLength = plainLength,
            iterations = iterations,
            aadBytes = aadBytes
        )
    }

    /**
     * Encrypt an arbitrary stream into .rpx format with AES-256-GCM (Default V3 Format with Encrypted Filename)
     */
    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        originalFilename: String,
        plainLength: Long,
        bufferSize: Int = DEFAULT_CHUNK_SIZE,
        version: Byte = CURRENT_VERSION
    ) {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cleanName = FileUtils.sanitizeFilename(originalFilename, "file")
        val nameBytes = cleanName.toByteArray(StandardCharsets.UTF_8)
        val iterations = if (version == VERSION_1) PBKDF2_ITERATIONS_V1 else PBKDF2_ITERATIONS_V2

        when (version) {
            VERSION_3 -> {
                val totalHeaderSize = MAGIC_HEADER.size + 1 + 1 + 4 + SALT_LENGTH + IV_LENGTH + 8
                val headerBytes = ByteBuffer.allocate(totalHeaderSize).apply {
                    put(MAGIC_HEADER)
                    put(VERSION_3)
                    put(ALGORITHM_AES_256_GCM)
                    putInt(iterations)
                    put(salt)
                    put(iv)
                    putLong(plainLength)
                }.array()

                // 1. Write outer unencrypted header (no filename leakage)
                output.write(headerBytes)

                // 2. Init AES-256-GCM and authenticate outer header via AAD
                val keySpec = deriveKey(password, salt, iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                cipher.updateAAD(headerBytes)

                // 3. Encrypt payload metadata inside ciphertext (2 bytes length + UTF-8 name)
                val nameLenBytes = ByteBuffer.allocate(2).putShort(nameBytes.size.toShort()).array()
                val prefixLen = cipher.update(nameLenBytes)
                if (prefixLen != null && prefixLen.isNotEmpty()) output.write(prefixLen)
                val prefixName = cipher.update(nameBytes)
                if (prefixName != null && prefixName.isNotEmpty()) output.write(prefixName)

                // 4. Encrypt file contents
                val buffer = ByteArray(bufferSize)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val chunk = cipher.update(buffer, 0, read)
                    if (chunk != null && chunk.isNotEmpty()) {
                        output.write(chunk)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null && finalBlock.isNotEmpty()) {
                    output.write(finalBlock)
                }
                output.flush()
            }
            VERSION_2 -> {
                val totalHeaderSize = MAGIC_HEADER.size + 1 + 1 + 4 + SALT_LENGTH + IV_LENGTH + 2 + nameBytes.size + 8
                val headerBytes = ByteBuffer.allocate(totalHeaderSize).apply {
                    put(MAGIC_HEADER)
                    put(VERSION_2)
                    put(ALGORITHM_AES_256_GCM)
                    putInt(iterations)
                    put(salt)
                    put(iv)
                    putShort(nameBytes.size.toShort())
                    put(nameBytes)
                    putLong(plainLength)
                }.array()

                output.write(headerBytes)

                val keySpec = deriveKey(password, salt, iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                cipher.updateAAD(headerBytes)

                val buffer = ByteArray(bufferSize)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val chunk = cipher.update(buffer, 0, read)
                    if (chunk != null && chunk.isNotEmpty()) {
                        output.write(chunk)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null && finalBlock.isNotEmpty()) {
                    output.write(finalBlock)
                }
                output.flush()
            }
            else -> {
                encryptStreamV1ForTesting(input, output, password, cleanName, plainLength)
            }
        }
    }

    /**
     * Decrypt an .rpx stream with AES-256-GCM and verify integrity (supports V1, V2, and V3)
     */
    fun decryptStream(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        bufferSize: Int = DEFAULT_CHUNK_SIZE
    ): DecryptResult {
        val header = inspectHeader(input) ?: return DecryptResult.InvalidFormat("Not a valid RePix .rpx file")

        val keySpec = deriveKey(password, header.salt, header.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, header.iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (header.aadBytes != null) {
            cipher.updateAAD(header.aadBytes)
        }

        return try {
            val buffer = ByteArray(bufferSize)
            var read: Int

            if (header.version == VERSION_3) {
                val extractor = PayloadHeaderExtractor()
                while (input.read(buffer).also { read = it } != -1) {
                    val chunk = cipher.update(buffer, 0, read)
                    if (chunk != null && chunk.isNotEmpty()) {
                        extractor.processChunk(chunk, output)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null && finalBlock.isNotEmpty()) {
                    extractor.processChunk(finalBlock, output)
                }
                output.flush()

                if (!extractor.isHeaderComplete) {
                    return DecryptResult.InvalidFormat("Corrupted or incomplete RPX V3 payload")
                }
                val sanitized = FileUtils.sanitizeFilename(extractor.filename, "decrypted_file")
                DecryptResult.Success(sanitized, ByteArray(0))
            } else {
                while (input.read(buffer).also { read = it } != -1) {
                    val chunk = cipher.update(buffer, 0, read)
                    if (chunk != null && chunk.isNotEmpty()) {
                        output.write(chunk)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null && finalBlock.isNotEmpty()) {
                    output.write(finalBlock)
                }
                output.flush()
                val sanitized = FileUtils.sanitizeFilename(header.originalFilename, "decrypted_file")
                DecryptResult.Success(sanitized, ByteArray(0))
            }
        } catch (e: Exception) {
            val root = getRootCause(e)
            if (root is AEADBadTagException || root is BadPaddingException || root.javaClass.simpleName.contains("BadTag")) {
                DecryptResult.WrongPasswordOrCorrupted
            } else {
                DecryptResult.WrongPasswordOrCorrupted
            }
        }
    }

    private class PayloadHeaderExtractor {
        private var nameLength: Int = -1
        private val lenBuffer = ByteArray(2)
        private var lenPos = 0
        private var nameBuffer: ByteArray? = null
        private var namePos = 0
        var filename: String = ""
            private set
        var isHeaderComplete: Boolean = false
            private set

        fun processChunk(chunk: ByteArray, output: OutputStream) {
            var offset = 0
            val total = chunk.size

            if (!isHeaderComplete) {
                // Read 2-byte short length
                while (lenPos < 2 && offset < total) {
                    lenBuffer[lenPos++] = chunk[offset++]
                }
                if (lenPos == 2 && nameLength == -1) {
                    nameLength = ByteBuffer.wrap(lenBuffer).short.toInt() and 0xFFFF
                    nameBuffer = ByteArray(nameLength)
                }

                // Read name bytes
                val buf = nameBuffer
                if (buf != null) {
                    while (namePos < nameLength && offset < total) {
                        buf[namePos++] = chunk[offset++]
                    }
                    if (namePos == nameLength) {
                        filename = String(buf, StandardCharsets.UTF_8)
                        isHeaderComplete = true
                    }
                }
            }

            // Write decrypted payload data to output
            if (isHeaderComplete && offset < total) {
                output.write(chunk, offset, total - offset)
            }
        }
    }

    /**
     * In-memory byte array encryption
     */
    fun encryptBytes(
        plainBytes: ByteArray,
        password: CharArray,
        originalFilename: String,
        version: Byte = CURRENT_VERSION
    ): ByteArray {
        val inStream = ByteArrayInputStream(plainBytes)
        val outStream = ByteArrayOutputStream()
        encryptStream(inStream, outStream, password, originalFilename, plainBytes.size.toLong(), version = version)
        return outStream.toByteArray()
    }

    /**
     * In-memory byte array decryption
     */
    fun decryptBytes(encryptedBytes: ByteArray, password: CharArray): DecryptResult {
        val inStream = ByteArrayInputStream(encryptedBytes)
        val outStream = ByteArrayOutputStream()
        val result = decryptStream(inStream, outStream, password)
        return when (result) {
            is DecryptResult.Success -> DecryptResult.Success(result.originalFilename, outStream.toByteArray())
            is DecryptResult.WrongPasswordOrCorrupted -> result
            is DecryptResult.InvalidFormat -> result
        }
    }

    /**
     * Helper for tests to create legacy RPX V2 container
     */
    @VisibleForTesting
    fun encryptStreamV2ForTesting(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        originalFilename: String,
        plainLength: Long
    ) {
        encryptStream(
            input = input,
            output = output,
            password = password,
            originalFilename = originalFilename,
            plainLength = plainLength,
            version = VERSION_2
        )
    }

    /**
     * Helper for tests to create legacy RPX V1 container
     */
    @VisibleForTesting
    fun encryptStreamV1ForTesting(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        originalFilename: String,
        plainLength: Long
    ) {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        output.write(MAGIC_HEADER)
        output.write(VERSION_1.toInt())
        output.write(ALGORITHM_AES_256_GCM.toInt())
        output.write(salt)
        output.write(iv)

        val nameBytes = originalFilename.toByteArray(StandardCharsets.UTF_8)
        val nameLenBytes = ByteBuffer.allocate(2).putShort(nameBytes.size.toShort()).array()
        output.write(nameLenBytes)
        output.write(nameBytes)

        val lenBytes = ByteBuffer.allocate(8).putLong(plainLength).array()
        output.write(lenBytes)

        val keySpec = deriveKey(password, salt, PBKDF2_ITERATIONS_V1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            val chunk = cipher.update(buffer, 0, read)
            if (chunk != null && chunk.isNotEmpty()) {
                output.write(chunk)
            }
        }
        val finalBlock = cipher.doFinal()
        if (finalBlock != null && finalBlock.isNotEmpty()) {
            output.write(finalBlock)
        }
        output.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = input.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    private fun getRootCause(throwable: Throwable): Throwable {
        var cause = throwable
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause
    }
}
