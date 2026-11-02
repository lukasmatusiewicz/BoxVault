package com.boxvault.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * 100% Offline, app-private local preferences manager.
 * Persists user theme preference and sensory (audio & haptics) toggles.
 */
class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _themeMode = MutableStateFlow(readStoredThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _soundEffectsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SOUND_EFFECTS, DEFAULT_SOUND_EFFECTS)
    )
    val soundEffectsEnabled: StateFlow<Boolean> = _soundEffectsEnabled.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HAPTICS, DEFAULT_HAPTICS)
    )
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    fun getThemeMode(): ThemeMode = _themeMode.value

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun isSoundEffectsEnabled(): Boolean = _soundEffectsEnabled.value

    fun setSoundEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
        _soundEffectsEnabled.value = enabled
    }

    fun isHapticsEnabled(): Boolean = _hapticsEnabled.value

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    private fun readStoredThemeMode(): ThemeMode {
        val storedName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(storedName ?: ThemeMode.SYSTEM.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    companion object {
        private const val PREFS_NAME = "boxvault_user_preferences"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_SOUND_EFFECTS = "pref_sound_effects_enabled"
        private const val KEY_HAPTICS = "pref_haptics_enabled"

        const val DEFAULT_SOUND_EFFECTS = true
        const val DEFAULT_HAPTICS = true
    }
}
