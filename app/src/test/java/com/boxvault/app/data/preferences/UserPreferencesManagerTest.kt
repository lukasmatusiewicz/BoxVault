package com.boxvault.app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: UserPreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences before each test
        context.getSharedPreferences("boxvault_user_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferencesManager = UserPreferencesManager(context)
    }

    @Test
    fun defaultSettings_areSystemTheme_andSensoryEnabled() = runTest {
        assertEquals(ThemeMode.SYSTEM, preferencesManager.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, preferencesManager.themeMode.first())
        assertTrue(preferencesManager.isSoundEffectsEnabled())
        assertTrue(preferencesManager.soundEffectsEnabled.first())
        assertTrue(preferencesManager.isHapticsEnabled())
        assertTrue(preferencesManager.hapticsEnabled.first())
    }

    @Test
    fun setThemeMode_persistsLightAndDarkModes() = runTest {
        preferencesManager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, preferencesManager.getThemeMode())
        assertEquals(ThemeMode.LIGHT, preferencesManager.themeMode.first())

        preferencesManager.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, preferencesManager.getThemeMode())
        assertEquals(ThemeMode.DARK, preferencesManager.themeMode.first())

        preferencesManager.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, preferencesManager.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, preferencesManager.themeMode.first())
    }

    @Test
    fun invalidStoredTheme_fallsBackToSystem() = runTest {
        context.getSharedPreferences("boxvault_user_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("pref_theme_mode", "UNKNOWN_CORRUPT_MODE")
            .commit()

        val reloadedManager = UserPreferencesManager(context)
        assertEquals(ThemeMode.SYSTEM, reloadedManager.getThemeMode())
    }

    @Test
    fun setSoundEffectsEnabled_togglesPreferenceAndFlow() = runTest {
        preferencesManager.setSoundEffectsEnabled(false)
        assertFalse(preferencesManager.isSoundEffectsEnabled())
        assertFalse(preferencesManager.soundEffectsEnabled.first())

        preferencesManager.setSoundEffectsEnabled(true)
        assertTrue(preferencesManager.isSoundEffectsEnabled())
        assertTrue(preferencesManager.soundEffectsEnabled.first())
    }

    @Test
    fun setHapticsEnabled_togglesPreferenceAndFlow() = runTest {
        preferencesManager.setHapticsEnabled(false)
        assertFalse(preferencesManager.isHapticsEnabled())
        assertFalse(preferencesManager.hapticsEnabled.first())

        preferencesManager.setHapticsEnabled(true)
        assertTrue(preferencesManager.isHapticsEnabled())
        assertTrue(preferencesManager.hapticsEnabled.first())
    }
}
