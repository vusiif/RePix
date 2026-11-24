package com.example.picencrypt

import com.example.picencrypt.data.InMemoryPreferencesRepository
import com.example.picencrypt.utils.MirageTankBridge
import com.example.picencrypt.viewmodel.MirageViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MirageViewModel] feature state management.
 */
class MirageViewModelTest {

    @Test
    fun testDefaultState() {
        val repo = InMemoryPreferencesRepository(initialHapticFeedbackEnabled = true)
        val vm = MirageViewModel(preferencesRepository = repo)

        assertTrue(vm.mirageIsColoredMode)
        assertEquals(MirageTankBridge.MirageScaleMode.FIT, vm.mirageScaleMode)
        assertNull(vm.mirageCoverUri)
        assertNull(vm.mirageHiddenUri)
        assertNull(vm.mirageCoverThumbnail)
        assertNull(vm.mirageHiddenThumbnail)
        assertNull(vm.miragePreviewBitmap)
        assertEquals(0f, vm.mirageCoverBrightness, 0.001f)
        assertEquals(1f, vm.mirageCoverContrast, 0.001f)
        assertEquals(0f, vm.mirageHiddenBrightness, 0.001f)
        assertEquals(1f, vm.mirageHiddenContrast, 0.001f)
        assertFalse(vm.isMirageProcessing)
        assertTrue(vm.isHapticFeedbackEnabled)
    }

    @Test
    fun testResetParams() {
        val vm = MirageViewModel()
        vm.mirageCoverBrightness = 25f
        vm.mirageCoverContrast = 1.5f
        vm.mirageHiddenBrightness = -15f
        vm.mirageHiddenContrast = 0.8f

        vm.resetMirageParams()
        assertEquals(0f, vm.mirageCoverBrightness, 0.001f)
        assertEquals(1f, vm.mirageCoverContrast, 0.001f)
        assertEquals(0f, vm.mirageHiddenBrightness, 0.001f)
        assertEquals(1f, vm.mirageHiddenContrast, 0.001f)
    }

    @Test
    fun testClearMirageState() {
        val vm = MirageViewModel()
        vm.mirageIsColoredMode = false
        vm.isMirageProcessing = true

        vm.clearMirageState()
        assertNull(vm.mirageCoverUri)
        assertNull(vm.mirageHiddenUri)
        assertFalse(vm.isMirageProcessing)
    }
}
