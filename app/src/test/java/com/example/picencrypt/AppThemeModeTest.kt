package com.example.picencrypt

import com.example.picencrypt.model.AppThemeMode
import com.example.picencrypt.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AppThemeMode] and ViewModel theme preferences.
 */
class AppThemeModeTest {

    @Test
    fun testAppThemeModeFromId() {
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, AppThemeMode.fromId(0))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromId(1))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromId(2))
        // Invalid id fallback
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, AppThemeMode.fromId(999))
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, AppThemeMode.fromId(-1))
    }

    @Test
    fun testDefaultViewModelThemeIsFollowSystem() {
        val viewModel = MainViewModel()
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, viewModel.appThemeMode)
    }

    @Test
    fun testSetAppThemeMode() {
        val viewModel = MainViewModel()

        viewModel.setAppThemeMode(AppThemeMode.DARK, context = null)
        assertEquals(AppThemeMode.DARK, viewModel.appThemeMode)

        viewModel.setAppThemeMode(AppThemeMode.LIGHT, context = null)
        assertEquals(AppThemeMode.LIGHT, viewModel.appThemeMode)

        viewModel.setAppThemeMode(AppThemeMode.FOLLOW_SYSTEM, context = null)
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, viewModel.appThemeMode)
    }
}
