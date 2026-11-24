package com.example.picencrypt

import com.example.picencrypt.data.InMemoryPreferencesRepository
import com.example.picencrypt.viewmodel.SafeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SafeViewModel] feature state management.
 */
class SafeViewModelTest {

    @Test
    fun testDefaultState() {
        val repo = InMemoryPreferencesRepository(initialHapticFeedbackEnabled = true)
        val vm = SafeViewModel(preferencesRepository = repo)

        assertEquals(0, vm.safeMode)
        assertEquals("", vm.safePassword)
        assertNull(vm.safeSourceUri)
        assertEquals("", vm.safeSourceFileName)
        assertEquals(0L, vm.safeSourceFileSize)
        assertNull(vm.safeRpxHeader)
        assertFalse(vm.isSafeProcessing)
        assertTrue(vm.isHapticFeedbackEnabled)
    }

    @Test
    fun testClearPassword() {
        val vm = SafeViewModel()
        vm.safePassword = "SuperSecretKey999"
        assertEquals("SuperSecretKey999", vm.safePassword)

        vm.clearPassword()
        assertEquals("", vm.safePassword)
    }

    @Test
    fun testClearSafeState() {
        val vm = SafeViewModel()
        vm.safeMode = 1
        vm.safePassword = "SecretPassword123"
        vm.isSafeProcessing = true

        vm.clearSafeState()
        assertEquals("", vm.safePassword)
        assertNull(vm.safeSourceUri)
        assertFalse(vm.isSafeProcessing)
    }
}
