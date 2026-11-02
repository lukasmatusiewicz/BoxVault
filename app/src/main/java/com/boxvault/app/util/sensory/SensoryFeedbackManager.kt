package com.boxvault.app.util.sensory

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.boxvault.app.data.preferences.UserPreferencesManager

/**
 * 100% Offline tactile and audio sensory feedback helper.
 * Manages click sounds, scan chimes, and haptics, honoring user preferences.
 */
class SensoryFeedbackManager(
    private val context: Context,
    private val userPreferencesManager: UserPreferencesManager
) {
    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun playClickSound() {
        if (!userPreferencesManager.isSoundEffectsEnabled()) return
        runCatching {
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
        }
    }

    fun playScanChime() {
        if (!userPreferencesManager.isSoundEffectsEnabled()) return
        runCatching {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    fun performHaptic(haptic: HapticFeedback?, type: HapticFeedbackType) {
        if (!userPreferencesManager.isHapticsEnabled()) return
        runCatching {
            haptic?.performHapticFeedback(type)
        }
    }

    fun onScanSuccess(haptic: HapticFeedback? = null) {
        performHaptic(haptic, HapticFeedbackType.LongPress)
        playScanChime()
    }

    fun onBulkEntrySubmit(haptic: HapticFeedback? = null) {
        performHaptic(haptic, HapticFeedbackType.TextHandleMove)
        playClickSound()
    }

    fun onQuantityStep(haptic: HapticFeedback? = null) {
        performHaptic(haptic, HapticFeedbackType.TextHandleMove)
        playClickSound()
    }

    fun onDeleteAction(haptic: HapticFeedback? = null) {
        performHaptic(haptic, HapticFeedbackType.LongPress)
        playClickSound()
    }

    fun onActionSuccess(haptic: HapticFeedback? = null) {
        performHaptic(haptic, HapticFeedbackType.LongPress)
        playClickSound()
    }
}
