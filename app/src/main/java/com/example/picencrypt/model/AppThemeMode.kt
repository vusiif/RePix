package com.example.picencrypt.model

import com.example.picencrypt.R

/**
 * Supported UI theme modes for RePix.
 */
enum class AppThemeMode(val id: Int, val stringResId: Int) {
    FOLLOW_SYSTEM(0, R.string.theme_follow_system),
    LIGHT(1, R.string.theme_light),
    DARK(2, R.string.theme_dark);

    companion object {
        fun fromId(id: Int): AppThemeMode = entries.firstOrNull { it.id == id } ?: FOLLOW_SYSTEM
    }
}
