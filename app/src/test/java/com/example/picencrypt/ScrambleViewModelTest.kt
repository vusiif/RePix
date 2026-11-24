package com.example.picencrypt

import com.example.picencrypt.data.InMemoryPreferencesRepository
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.viewmodel.ScrambleViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ScrambleViewModel] feature state management.
 */
class ScrambleViewModelTest {

    @Test
    fun testDefaultState() {
        val repo = InMemoryPreferencesRepository(initialHapticFeedbackEnabled = true)
        val vm = ScrambleViewModel(preferencesRepository = repo)

        assertTrue(vm.images.isEmpty())
        assertEquals(Algorithm.TOMATO, vm.selectedAlgorithm)
        assertEquals("1", vm.keyString)
        assertFalse(vm.isCompatMode)
        assertFalse(vm.isDropdownMenuExpanded)
        assertEquals(0f, vm.progress, 0.001f)
        assertFalse(vm.isShowProgress)
        assertEquals(0, vm.newImageUriCount)
        assertNull(vm.lastProcessType)
        assertTrue(vm.failedProcessImageIds.isEmpty())
        assertTrue(vm.failedSaveImageIds.isEmpty())
        assertTrue(vm.isHapticFeedbackEnabled)
    }

    @Test
    fun testSelectAlgorithmKeySwitching() {
        val vm = ScrambleViewModel()
        assertEquals(Algorithm.TOMATO, vm.selectedAlgorithm)
        assertEquals("1", vm.keyString)

        vm.selectAlgorithm(Algorithm.BLOCK)
        assertEquals(Algorithm.BLOCK, vm.selectedAlgorithm)
        assertEquals(Algorithm.DEFAULT_OTHER_KEY, vm.keyString)

        vm.selectAlgorithm(Algorithm.TOMATO)
        assertEquals(Algorithm.TOMATO, vm.selectedAlgorithm)
        assertEquals(Algorithm.DEFAULT_TOMATO_KEY, vm.keyString)
    }

    @Test
    fun testClearAllImages() {
        val vm = ScrambleViewModel()
        vm.failedProcessImageIds.add("img1")
        vm.failedSaveImageIds.add("img1")

        vm.clearAllImages()
        assertTrue(vm.images.isEmpty())
        assertTrue(vm.failedProcessImageIds.isEmpty())
        assertTrue(vm.failedSaveImageIds.isEmpty())
    }
}
