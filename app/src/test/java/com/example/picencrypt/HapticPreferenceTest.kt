package com.example.picencrypt

import com.example.picencrypt.viewmodel.MainViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for haptic feedback toggle and preferences state.
 */
class HapticPreferenceTest {

    @Test
    fun testDefaultHapticStateEnabled() {
        val viewModel = MainViewModel()
        assertTrue("Haptic feedback should be enabled by default", viewModel.isHapticFeedbackEnabled)
    }

    @Test
    fun testDisableAndEnableHapticState() {
        val viewModel = MainViewModel()
        
        // Turn off haptic feedback
        viewModel.setHapticFeedbackEnabled(false, context = null)
        assertFalse("Haptic feedback should be disabled", viewModel.isHapticFeedbackEnabled)

        // Turn on haptic feedback
        viewModel.setHapticFeedbackEnabled(true, context = null)
        assertTrue("Haptic feedback should be re-enabled", viewModel.isHapticFeedbackEnabled)
    }
}
