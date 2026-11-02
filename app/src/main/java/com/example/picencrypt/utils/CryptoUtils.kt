package com.example.picencrypt.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard Cryptographic Security Engine for RePix (AES-256-GCM + PBKDF2).
 * Provides authenticated, tamper-proof container (.rpx) encryption and decryption.
 */
object CryptoUtils {

    // Magic Header: "RPX1"
    val MAGIC_HEADER = byteArrayOf(0x52.toByte(), 0x50.toByte(), 0x58.toByte(), 0x31.toByte())
    const val CURRENT_VERSION: Byte = 1
    const val ALGORITHM_AES_256_GCM: Byte = 1

    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 15000
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    data class RpxHeader(
        val version: Byte,
        val algo: Byte,
        val salt: ByteArray,
        val iv: ByteArray,
        val originalFilename: String,
        val plainLength: Long
    )

    sealed class DecryptResult {
        data class Success(val originalFilename: String, val plainBytes: ByteArray) : DecryptResult()
        object WrongPasswordOrCorrupted : DecryptResult()
        data class InvalidFormat(val reason: String) : DecryptResult()
    }

    /**
     * Derive a 256-bit AES key from password and salt using PBKDF2-HMAC-SHA256
     */
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Inspect and parse header of an .rpx stream without decrypting payload
     */
    fun inspectHeader(inputStream: InputStream): RpxHeader? {
        val magic = ByteArray(MAGIC_HEADER.size)
        if (readFully(inputStream, magic) != magic.size) return null
        if (!magic.contentEquals(MAGIC_HEADER)) return null

        val version = inputStream.read().toByte()
        val algo = inputStream.read().toByte()
        if (version != CURRENT_VERSION || algo != ALGORITHM_AES_256_GCM) return null

        val salt = ByteArray(SALT_LENGTH)
        if (readFully(inputStream, salt) != SALT_LENGTH) return null

        val iv = ByteArray(IV_LENGTH)
        if (readFully(inputStream, iv) != IV_LENGTH) return null

        val nameLenBytes = ByteArray(2)
        if (readFully(inputStream, nameLenBytes) != 2) return null
        val nameLen = ByteBuffer.wrap(nameLenBytes).short.toInt() and 0xFFFF

        val nameBytes = ByteArray(nameLen)
        if (readFully(inputStream, nameBytes) != nameLen) return null
        val filename = String(nameBytes, StandardCharsets.UTF_8)

        val lenBytes = ByteArray(8)
        if (readFully(inputStream, lenBytes) != 8) return null
        val plainLength = ByteBuffer.wrap(lenBytes).long

        return RpxHeader(version, algo, salt, iv, filename, plainLength)
    }

    /**
     * Encrypt an arbitrary stream into .rpx format with AES-256-GCM
     */
    fun encryptStream(
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

        // 1. Write Header
        output.write(MAGIC_HEADER)
        output.write(CURRENT_VERSION.toInt())
        output.write(ALGORITHM_AES_256_GCM.toInt())
        output.write(salt)
        output.write(iv)

        val nameBytes = originalFilename.toByteArray(StandardCharsets.UTF_8)
        val nameLenBytes = ByteBuffer.allocate(2).putShort(nameBytes.size.toShort()).array()
        output.write(nameLenBytes)
        output.write(nameBytes)

        val lenBytes = ByteBuffer.allocate(8).putLong(plainLength).array()
        output.write(lenBytes)

        // 2. Initialize AES-GCM Cipher
        val keySpec = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        // 3. Encrypt payload
        CipherOutputStream(output, cipher).use { cipherOut ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                cipherOut.write(buffer, 0, read)
            }
            cipherOut.flush()
        }
    }

    /**
     * Decrypt an .rpx stream with AES-256-GCM and verify integrity
     */
    fun decryptStream(input: InputStream, output: OutputStream, password: CharArray): DecryptResult {
        val header = inspectHeader(input) ?: return DecryptResult.InvalidFormat("Not a valid RePix .rpx file")

        val keySpec = deriveKey(password, header.salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, header.iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        try {
            CipherInputStream(input, cipher).use { cipherIn ->
                val buffer = ByteArray(8192)
                var read: Int
                while (cipherIn.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
            return DecryptResult.Success(header.originalFilename, ByteArray(0))
        } catch (e: Exception) {
            val root = getRootCause(e)
            if (root is AEADBadTagException || root.javaClass.simpleName.contains("BadTag")) {
                return DecryptResult.WrongPasswordOrCorrupted
            }
            return DecryptResult.WrongPasswordOrCorrupted
        }
    }

    /**
     * In-memory byte array encryption
     */
    fun encryptBytes(plainBytes: ByteArray, password: CharArray, originalFilename: String): ByteArray {
        val inStream = ByteArrayInputStream(plainBytes)
        val outStream = ByteArrayOutputStream()
        encryptStream(inStream, outStream, password, originalFilename, plainBytes.size.toLong())
        return outStream.toByteArray()
    }

    /**
     * In-memory byte array decryption
     */
    fun decryptBytes(encryptedBytes: ByteArray, password: CharArray): DecryptResult {
        val inStream = ByteArrayInputStream(encryptedBytes)
        val header = inspectHeader(inStream) ?: return DecryptResult.InvalidFormat("Not a valid RePix .rpx file")

        val keySpec = deriveKey(password, header.salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, header.iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val remainingEncrypted = encryptedBytes.copyOfRange(
            MAGIC_HEADER.size + 1 + 1 + SALT_LENGTH + IV_LENGTH + 2 + header.originalFilename.toByteArray(StandardCharsets.UTF_8).size + 8,
            encryptedBytes.size
        )

        return try {
            val plain = cipher.doFinal(remainingEncrypted)
            DecryptResult.Success(header.originalFilename, plain)
        } catch (e: Exception) {
            val root = getRootCause(e)
            if (root is AEADBadTagException || root.javaClass.simpleName.contains("BadTag")) {
                DecryptResult.WrongPasswordOrCorrupted
            } else {
                DecryptResult.WrongPasswordOrCorrupted
            }
        }
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
