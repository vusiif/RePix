package com.example.picencrypt

import com.example.picencrypt.data.InMemoryPreferencesRepository
import com.example.picencrypt.viewmodel.StegoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StegoViewModel] feature state management.
 */
class StegoViewModelTest {

    @Test
    fun testDefaultState() {
        val repo = InMemoryPreferencesRepository(initialHapticFeedbackEnabled = true)
        val vm = StegoViewModel(preferencesRepository = repo)

        assertEquals(0, vm.stegoMode)
        assertNull(vm.stegoCoverUri)
        assertNull(vm.stegoArchiveUri)
        assertNull(vm.stegoCoverThumbnail)
        assertEquals(0L, vm.stegoCoverFileSize)
        assertEquals(0L, vm.stegoArchiveFileSize)
        assertNull(vm.stegoExtractTargetUri)
        assertNull(vm.stegoExtractThumbnail)
        assertEquals(0L, vm.stegoExtractFileSize)
        assertNull(vm.stegoInspection)
        assertFalse(vm.isStegoInspecting)
        assertFalse(vm.isStegoProcessing)
        assertTrue(vm.isHapticFeedbackEnabled)
    }

    @Test
    fun testClearStegoState() {
        val vm = StegoViewModel()
        vm.stegoMode = 1
        vm.isStegoProcessing = true

        vm.clearStegoState()
        assertNull(vm.stegoCoverUri)
        assertNull(vm.stegoArchiveUri)
        assertFalse(vm.isStegoProcessing)
    }
}
