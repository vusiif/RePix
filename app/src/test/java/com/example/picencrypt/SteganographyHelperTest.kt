package com.example.picencrypt

import com.example.picencrypt.utils.SteganographyHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class SteganographyHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSteganographyFlow() {
        val imageFile = tempFolder.newFile("test_cover.jpg")
        val zipFile = tempFolder.newFile("test_archive.zip")
        val stegoFile = tempFolder.newFile("test_stego.jpg")
        val extractedFile = tempFolder.newFile("test_extracted.zip")

        // 1. Generate dummy image bytes (e.g. 50 KB)
        val imageBytes = ByteArray(50 * 1024)
        Random(42).nextBytes(imageBytes)
        // Mark JPEG-like magic at start
        imageBytes[0] = 0xFF.toByte()
        imageBytes[1] = 0xD8.toByte()
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        // 2. Generate dummy ZIP bytes with PK\x03\x04 magic (e.g. 20 KB)
        val zipBytes = ByteArray(20 * 1024)
        Random(1337).nextBytes(zipBytes)
        zipBytes[0] = 0x50
        zipBytes[1] = 0x4B
        zipBytes[2] = 0x03
        zipBytes[3] = 0x04
        FileOutputStream(zipFile).use { it.write(zipBytes) }

        // 3. Create stego image (image + zip)
        val totalBytes = SteganographyHelper.createStegoImage(imageFile, zipFile, stegoFile)
        assertEquals((imageBytes.size + zipBytes.size).toLong(), totalBytes)
        assertEquals(totalBytes, stegoFile.length())

        // 4. Detect offset and type
        val detection = SteganographyHelper.detectArchiveOffset(stegoFile)
        assertNotNull(detection)
        assertEquals(SteganographyHelper.ArchiveType.ZIP, detection!!.type)
        assertEquals(imageBytes.size.toLong(), detection.offset)

        // 5. Extract archive
        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertTrue(extracted)
        assertEquals(zipBytes.size.toLong(), extractedFile.length())
        assertArrayEquals(zipBytes, extractedFile.readBytes())
    }

    @Test
    fun testSevenZipSteganographyFlow() {
        val imageFile = tempFolder.newFile("cover.png")
        val sevenZFile = tempFolder.newFile("archive.7z")
        val stegoFile = tempFolder.newFile("stego.png")
        val extractedFile = tempFolder.newFile("extracted.7z")

        val imageBytes = ByteArray(10 * 1024)
        Random(123).nextBytes(imageBytes)
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        val sevenZBytes = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x00, 0x04, 0x01, 0x02)
        FileOutputStream(sevenZFile).use { it.write(sevenZBytes) }

        SteganographyHelper.createStegoImage(imageFile, sevenZFile, stegoFile)
        val detection = SteganographyHelper.detectArchiveOffset(stegoFile)
        assertNotNull(detection)
        assertEquals(SteganographyHelper.ArchiveType.SEVEN_Z, detection!!.type)
        assertEquals(imageBytes.size.toLong(), detection.offset)

        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertTrue(extracted)
        assertArrayEquals(sevenZBytes, extractedFile.readBytes())
    }

    @Test
    fun testRarSteganographyFlow() {
        val imageFile = tempFolder.newFile("cover2.png")
        val rarFile = tempFolder.newFile("archive.rar")
        val stegoFile = tempFolder.newFile("stego2.png")
        val extractedFile = tempFolder.newFile("extracted.rar")

        val imageBytes = ByteArray(8 * 1024)
        Random(999).nextBytes(imageBytes)
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        val rarBytes = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0x11, 0x22, 0x33)
        FileOutputStream(rarFile).use { it.write(rarBytes) }

        SteganographyHelper.createStegoImage(imageFile, rarFile, stegoFile)
        val detection = SteganographyHelper.detectArchiveOffset(stegoFile)
        assertNotNull(detection)
        assertEquals(SteganographyHelper.ArchiveType.RAR, detection!!.type)
        assertEquals(imageBytes.size.toLong(), detection.offset)

        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertTrue(extracted)
        assertArrayEquals(rarBytes, extractedFile.readBytes())
    }

    @Test
    fun testPngIendDetectionResistsFakeInternalMagic() {
        // Construct a valid minimal PNG header + IEND chunk, with fake PK\x03\x04 inside a preceding IDAT
        val pngFile = tempFolder.newFile("fake_magic.png")
        val stegoFile = tempFolder.newFile("fake_magic_stego.png")
        val extractedFile = tempFolder.newFile("extracted_real.zip")

        FileOutputStream(pngFile).use { fos ->
            // PNG Signature (8 bytes)
            fos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            // Dummy IDAT Chunk containing fake PK\x03\x04
            val idatData = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x11, 0x22, 0x33, 0x44)
            fos.write(byteArrayOf(0, 0, 0, idatData.size.toByte())) // Length
            fos.write("IDAT".toByteArray(Charsets.US_ASCII)) // Type
            fos.write(idatData) // Data
            fos.write(byteArrayOf(0x12, 0x34, 0x56, 0x78)) // Dummy CRC
            // IEND Chunk
            fos.write(byteArrayOf(0, 0, 0, 0)) // Length = 0
            fos.write("IEND".toByteArray(Charsets.US_ASCII)) // Type
            fos.write(byteArrayOf(0xAE.toByte(), 0x42, 0x60, 0x82.toByte())) // Valid IEND CRC
        }
        val pngEndOffset = pngFile.length()

        // Real ZIP appended after PNG
        val realZipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x99.toByte(), 0x88.toByte())
        val dummyZipFile = tempFolder.newFile("real.zip")
        FileOutputStream(dummyZipFile).use { it.write(realZipBytes) }

        SteganographyHelper.createStegoImage(pngFile, dummyZipFile, stegoFile)

        // Should detect real zip at pngEndOffset, NOT the fake PK\x03\x04 inside IDAT
        val detection = SteganographyHelper.detectArchiveOffset(stegoFile)
        assertNotNull(detection)
        assertEquals(SteganographyHelper.ArchiveType.ZIP, detection!!.type)
        assertEquals(pngEndOffset, detection.offset)

        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertTrue(extracted)
        assertArrayEquals(realZipBytes, extractedFile.readBytes())
    }

    @Test
    fun testPureImageWithoutArchiveReturnsNull() {
        val imageFile = tempFolder.newFile("plain.png")
        val imageBytes = ByteArray(16 * 1024)
        Random(777).nextBytes(imageBytes)
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        val detection = SteganographyHelper.detectArchiveOffset(imageFile)
        assertNull(detection)
    }
}
