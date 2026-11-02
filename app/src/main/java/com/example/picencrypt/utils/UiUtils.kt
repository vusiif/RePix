package com.example.picencrypt.utils

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Perform haptic feedback only when [enabled] is true.
 */
fun HapticFeedback.performHapticIf(type: HapticFeedbackType, enabled: Boolean) {
    if (enabled) {
        performHapticFeedback(type)
    }
}
