package com.example.picencrypt

import com.example.picencrypt.utils.ExifUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ExifUtils.ExifInfo] parsing, formatting, and privacy status.
 */
class ExifUtilsTest {

    @Test
    fun testCleanExifIsClean() {
        val clean = ExifUtils.cleanExif()
        assertTrue(clean.isClean)
        assertFalse(clean.hasGps)
        assertNull(clean.formatCoordinates())
        assertNull(clean.getDeviceDescription())
    }

    @Test
    fun testFormatCoordinatesNorthEast() {
        val info = ExifUtils.ExifInfo(
            hasGps = true,
            latitude = 39.9042,
            longitude = 116.4074
        )
        assertFalse(info.isClean)
        assertEquals("39.9042°N, 116.4074°E", info.formatCoordinates())
    }

    @Test
    fun testFormatCoordinatesSouthWest() {
        val info = ExifUtils.ExifInfo(
            hasGps = true,
            latitude = -22.9068,
            longitude = -43.1729
        )
        assertFalse(info.isClean)
        assertEquals("22.9068°S, 43.1729°W", info.formatCoordinates())
    }

    @Test
    fun testDeviceDescriptionSeparateMakeModel() {
        val info = ExifUtils.ExifInfo(
            hasGps = false,
            make = "Apple",
            model = "iPhone 15 Pro"
        )
        assertFalse(info.isClean)
        assertEquals("Apple iPhone 15 Pro", info.getDeviceDescription())
    }

    @Test
    fun testDeviceDescriptionModelContainsMake() {
        val info = ExifUtils.ExifInfo(
            hasGps = false,
            make = "Xiaomi",
            model = "Xiaomi 13 Ultra"
        )
        assertEquals("Xiaomi 13 Ultra", info.getDeviceDescription())
    }
}
