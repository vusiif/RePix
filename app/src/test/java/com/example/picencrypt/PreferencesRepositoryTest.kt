package com.example.picencrypt

import com.example.picencrypt.data.InMemoryPreferencesRepository
import com.example.picencrypt.data.PreferencesRepository
import com.example.picencrypt.model.AppThemeMode
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.utils.ExportFilenameGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PreferencesRepository] and its in-memory testing implementation.
 */
class PreferencesRepositoryTest {

    @Test
    fun testDefaultValues() {
        val repo = InMemoryPreferencesRepository()
        assertTrue(repo.isHapticFeedbackEnabled())
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, repo.getAppThemeMode())
        assertTrue(repo.isStripExifOnExport())
        assertEquals(ImageExportFormat.PNG, repo.getExportFormat())
        assertEquals(90, repo.getExportQuality())
        assertEquals(ExportFilenameGenerator.PATTERN_TIMESTAMP, repo.getExportFilenamePattern())
        assertEquals("RePix", repo.getExportCustomPrefix())
    }

    @Test
    fun testMutationsAndClamping() {
        val repo = InMemoryPreferencesRepository()

        repo.setHapticFeedbackEnabled(false)
        assertFalse(repo.isHapticFeedbackEnabled())

        repo.setAppThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, repo.getAppThemeMode())

        repo.setStripExifOnExport(false)
        assertFalse(repo.isStripExifOnExport())

        repo.setExportFormat(ImageExportFormat.WEBP)
        assertEquals(ImageExportFormat.WEBP, repo.getExportFormat())

        repo.setExportQuality(120) // Should clamp to 100
        assertEquals(100, repo.getExportQuality())

        repo.setExportQuality(5) // Should clamp to 10
        assertEquals(10, repo.getExportQuality())

        repo.setExportFilenamePattern(ExportFilenameGenerator.PATTERN_SEQUENTIAL)
        assertEquals(ExportFilenameGenerator.PATTERN_SEQUENTIAL, repo.getExportFilenamePattern())

        repo.setExportCustomPrefix("MyPrefix")
        assertEquals("MyPrefix", repo.getExportCustomPrefix())
    }

    @Test
    fun testSingletonFallbackAndTestingInjection() {
        PreferencesRepository.setInstanceForTesting(null)
        val defaultInstance = PreferencesRepository.getInstance()
        assertTrue("Default instance should fallback to InMemoryPreferencesRepository in JVM test",
            defaultInstance is InMemoryPreferencesRepository)

        val customRepo = InMemoryPreferencesRepository(initialHapticFeedbackEnabled = false)
        PreferencesRepository.setInstanceForTesting(customRepo)
        assertEquals(customRepo, PreferencesRepository.getInstance())
        assertFalse(PreferencesRepository.getInstance().isHapticFeedbackEnabled())

        PreferencesRepository.setInstanceForTesting(null)
    }
}
