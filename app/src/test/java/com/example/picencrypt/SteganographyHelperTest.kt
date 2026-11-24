package com.example.picencrypt

import com.example.picencrypt.utils.SteganographyHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Random

class SteganographyHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSteganographyV2Flow() {
        val imageFile = tempFolder.newFile("test_cover.jpg")
        val zipFile = tempFolder.newFile("test_archive.zip")
        val stegoFile = tempFolder.newFile("test_stego.jpg")
        val extractedFile = tempFolder.newFile("test_extracted.zip")

        // 1. Generate dummy image bytes (50 KB)
        val imageBytes = ByteArray(50 * 1024)
        Random(42).nextBytes(imageBytes)
        imageBytes[0] = 0xFF.toByte()
        imageBytes[1] = 0xD8.toByte()
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        // 2. Generate dummy ZIP bytes with PK\x03\x04 magic (20 KB)
        val zipBytes = ByteArray(20 * 1024)
        Random(1337).nextBytes(zipBytes)
        zipBytes[0] = 0x50
        zipBytes[1] = 0x4B
        zipBytes[2] = 0x03
        zipBytes[3] = 0x04
        FileOutputStream(zipFile).use { it.write(zipBytes) }

        // 3. Create stego image (V2 default)
        val totalBytes = SteganographyHelper.createStegoImage(imageFile, zipFile, stegoFile, enableV2 = true, archiveType = SteganographyHelper.ArchiveType.ZIP)
        val expectedTotal = imageBytes.size + zipBytes.size + SteganographyHelper.TRAILER_V2_SIZE
        assertEquals(expectedTotal.toLong(), totalBytes)
        assertEquals(totalBytes, stegoFile.length())

        // 4. Detect offset and type (O(1) V2 Trailer)
        val detection = SteganographyHelper.detectArchiveOffset(stegoFile)
        assertNotNull(detection)
        assertTrue("Must be identified as V2", detection!!.isV2)
        assertEquals(SteganographyHelper.ArchiveType.ZIP, detection.type)
        assertEquals(imageBytes.size.toLong(), detection.offset)
        assertEquals(zipBytes.size.toLong(), detection.archiveSize)
        assertNotNull(detection.sha256)

        // 5. Extract archive with SHA-256 verification
        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertTrue(extracted)
        assertEquals(zipBytes.size.toLong(), extractedFile.length())
        assertArrayEquals(zipBytes, extractedFile.readBytes())
    }

    @Test
    fun testStegoV1LegacyBackwardCompatibility() {
        val imageFile = tempFolder.newFile("legacy_cover.jpg")
        val zipFile = tempFolder.newFile("legacy_archive.zip")
        val stegoFile = tempFolder.newFile("legacy_stego.jpg")
        val extractedFile = tempFolder.newFile("legacy_extracted.zip")

        val imageBytes = ByteArray(30 * 1024)
        Random(111).nextBytes(imageBytes)
        imageBytes[0] = 0xFF.toByte()
        imageBytes[1] = 0xD8.toByte()
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x0A, 0x0B, 0x0C)
        FileOutputStream(zipFile).use { it.write(zipBytes) }

        // Create with enableV2 = false (legacy V1 append)
        val totalBytes = SteganographyHelper.createStegoImage(imageFile, zipFile, stegoFile, enableV2 = false)
        assertEquals((imageBytes.size + zipBytes.size).toLong(), totalBytes)

        val detection = SteganographyHelper.detectArchiveOffset(stegoFile)
        assertNotNull(detection)
        assertFalse("V1 should not have isV2 flag", detection!!.isV2)
        assertEquals(SteganographyHelper.ArchiveType.ZIP, detection.type)
        assertEquals(imageBytes.size.toLong(), detection.offset)

        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertTrue(extracted)
        assertArrayEquals(zipBytes, extractedFile.readBytes())
    }

    @Test
    fun testStegoV2TamperingRejection() {
        val imageFile = tempFolder.newFile("tamper_cover.jpg")
        val zipFile = tempFolder.newFile("tamper_archive.zip")
        val stegoFile = tempFolder.newFile("tamper_stego.jpg")
        val extractedFile = tempFolder.newFile("tamper_extracted.zip")

        val imageBytes = ByteArray(1024) { 0xAA.toByte() }
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x12, 0x34, 0x56, 0x78)
        FileOutputStream(imageFile).use { it.write(imageBytes) }
        FileOutputStream(zipFile).use { it.write(zipBytes) }

        SteganographyHelper.createStegoImage(imageFile, zipFile, stegoFile, enableV2 = true)

        // Tamper with 1 byte of the payload inside stegoFile
        RandomAccessFile(stegoFile, "rw").use { raf ->
            raf.seek(imageBytes.size.toLong() + 4)
            raf.write(0xFF) // Mutate payload byte
        }

        // Extraction must fail due to SHA-256 integrity check
        val extracted = SteganographyHelper.extractArchive(stegoFile, extractedFile)
        assertFalse("Tampered V2 stego payload must fail SHA-256 check", extracted)
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

        SteganographyHelper.createStegoImage(imageFile, sevenZFile, stegoFile, enableV2 = true, archiveType = SteganographyHelper.ArchiveType.SEVEN_Z)
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

        SteganographyHelper.createStegoImage(imageFile, rarFile, stegoFile, enableV2 = true, archiveType = SteganographyHelper.ArchiveType.RAR)
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

        SteganographyHelper.createStegoImage(pngFile, dummyZipFile, stegoFile, enableV2 = false)

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

    @Test
    fun testDetectArchiveFromStream() {
        val imageFile = tempFolder.newFile("stream_cover.jpg")
        val zipFile = tempFolder.newFile("stream_arch.zip")
        val stegoFile = tempFolder.newFile("stream_stego.jpg")

        val imgBytes = ByteArray(12 * 1024)
        Random(555).nextBytes(imgBytes)
        imgBytes[0] = 0xFF.toByte()
        imgBytes[1] = 0xD8.toByte()
        FileOutputStream(imageFile).use { it.write(imgBytes) }

        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x10, 0x20, 0x30, 0x40)
        FileOutputStream(zipFile).use { it.write(zipBytes) }

        SteganographyHelper.createStegoImage(imageFile, zipFile, stegoFile, enableV2 = true)

        // Stream inspection test
        val inspection = FileInputStream(stegoFile).use { fis ->
            SteganographyHelper.detectArchiveFromStream(fis, stegoFile.length())
        }

        assertTrue(inspection.hasArchive)
        assertTrue(inspection.isV2)
        assertEquals(SteganographyHelper.ArchiveType.ZIP, inspection.type)
        assertEquals(imgBytes.size.toLong(), inspection.offset)
        assertEquals(stegoFile.length(), inspection.totalSize)
        assertEquals(zipBytes.size.toLong(), inspection.archiveSize)

        // Plain image stream inspection test
        val plainInspection = FileInputStream(imageFile).use { fis ->
            SteganographyHelper.detectArchiveFromStream(fis, imageFile.length())
        }
        assertFalse(plainInspection.hasArchive)
        assertEquals(0L, plainInspection.archiveSize)
    }

    @Test
    fun testExtractArchiveStreamPureStream() {
        val dummyImage = ByteArray(1024) { 0x11 }
        val dummyArchive = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val combined = dummyImage + dummyArchive

        val inStream = ByteArrayInputStream(combined)
        val outStream = ByteArrayOutputStream()

        val success = SteganographyHelper.extractArchiveStream(inStream, outStream, dummyImage.size.toLong())
        assertTrue(success)
        assertArrayEquals(dummyArchive, outStream.toByteArray())
    }

    @Test
    fun testSkipFullyHandlesTruncation() {
        val data = ByteArray(500) { 0x01 }
        val inStream = ByteArrayInputStream(data)
        assertTrue(SteganographyHelper.skipFully(inStream, 300))
        assertEquals(200, inStream.available())

        // Skipping more than remaining should return false
        assertFalse(SteganographyHelper.skipFully(inStream, 500))
    }

    @Test
    fun testArchiveTypeSniffingUnit() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        val rar4Header = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        val rar5Header = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
        val sevenZHeader = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        val unknownHeader = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        assertEquals(SteganographyHelper.ArchiveType.ZIP, SteganographyHelper.sniffArchiveType(zipHeader))
        assertEquals(SteganographyHelper.ArchiveType.RAR, SteganographyHelper.sniffArchiveType(rar4Header))
        assertEquals(SteganographyHelper.ArchiveType.RAR, SteganographyHelper.sniffArchiveType(rar5Header))
        assertEquals(SteganographyHelper.ArchiveType.SEVEN_Z, SteganographyHelper.sniffArchiveType(sevenZHeader))
        assertEquals(SteganographyHelper.ArchiveType.UNKNOWN, SteganographyHelper.sniffArchiveType(unknownHeader))
    }

    @Test
    fun testAutoSniffArchiveTypeWithoutExplicitParam() {
        val imageFile = tempFolder.newFile("auto_sniff_cover.jpg")
        FileOutputStream(imageFile).use { it.write(ByteArray(512) { 0xFF.toByte() }) }

        // 1. Test RAR auto-sniffing
        val rarFile = tempFolder.newFile("auto_sniff.rar")
        val rarBytes = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0xAA.toByte(), 0xBB.toByte())
        FileOutputStream(rarFile).use { it.write(rarBytes) }

        val stegoRarFile = tempFolder.newFile("stego_auto_rar.jpg")
        // Note: archiveType parameter omitted, should automatically sniff RAR!
        SteganographyHelper.createStegoImage(imageFile, rarFile, stegoRarFile, enableV2 = true)

        val rarDetection = SteganographyHelper.detectArchiveOffset(stegoRarFile)
        assertNotNull(rarDetection)
        assertEquals("Should auto-sniff RAR type", SteganographyHelper.ArchiveType.RAR, rarDetection!!.type)
        assertTrue(rarDetection.isV2)

        // 2. Test 7Z auto-sniffing
        val sevenZFile = tempFolder.newFile("auto_sniff.7z")
        val sevenZBytes = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x11, 0x22)
        FileOutputStream(sevenZFile).use { it.write(sevenZBytes) }

        val stego7zFile = tempFolder.newFile("stego_auto_7z.jpg")
        // archiveType parameter omitted, should automatically sniff 7Z!
        SteganographyHelper.createStegoImage(imageFile, sevenZFile, stego7zFile, enableV2 = true)

        val sevenZDetection = SteganographyHelper.detectArchiveOffset(stego7zFile)
        assertNotNull(sevenZDetection)
        assertEquals("Should auto-sniff 7Z type", SteganographyHelper.ArchiveType.SEVEN_Z, sevenZDetection!!.type)
        assertTrue(sevenZDetection.isV2)

        // 3. Test Unknown auto-sniffing
        val unknownFile = tempFolder.newFile("auto_sniff.bin")
        val unknownBytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x00, 0x01)
        FileOutputStream(unknownFile).use { it.write(unknownBytes) }

        val stegoUnknownFile = tempFolder.newFile("stego_auto_unknown.jpg")
        SteganographyHelper.createStegoImage(imageFile, unknownFile, stegoUnknownFile, enableV2 = true)

        val unknownDetection = SteganographyHelper.detectArchiveOffset(stegoUnknownFile)
        assertNotNull(unknownDetection)
        assertEquals("Should auto-sniff UNKNOWN type", SteganographyHelper.ArchiveType.UNKNOWN, unknownDetection!!.type)
        assertTrue(unknownDetection.isV2)
    }
}
