package com.boxvault.app.util.sensory

import android.content.Context
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.preferences.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensoryFeedbackManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var sensoryManager: SensoryFeedbackManager

    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val testHapticFeedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performedHaptics.add(hapticFeedbackType)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("boxvault_user_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferencesManager = UserPreferencesManager(context)
        sensoryManager = SensoryFeedbackManager(context, preferencesManager)
        performedHaptics.clear()
    }

    @Test
    fun onScanSuccess_performsLongPressHaptic_whenEnabled() {
        preferencesManager.setHapticsEnabled(true)
        sensoryManager.onScanSuccess(testHapticFeedback)

        assertEquals(1, performedHaptics.size)
        assertEquals(HapticFeedbackType.LongPress, performedHaptics[0])
    }

    @Test
    fun onScanSuccess_suppressesHaptic_whenDisabled() {
        preferencesManager.setHapticsEnabled(false)
        sensoryManager.onScanSuccess(testHapticFeedback)

        assertTrue(performedHaptics.isEmpty())
    }

    @Test
    fun onBulkEntrySubmit_performsTextHandleMoveHaptic_whenEnabled() {
        preferencesManager.setHapticsEnabled(true)
        sensoryManager.onBulkEntrySubmit(testHapticFeedback)

        assertEquals(1, performedHaptics.size)
        assertEquals(HapticFeedbackType.TextHandleMove, performedHaptics[0])
    }

    @Test
    fun onQuantityStep_performsTextHandleMoveHaptic_whenEnabled() {
        preferencesManager.setHapticsEnabled(true)
        sensoryManager.onQuantityStep(testHapticFeedback)

        assertEquals(1, performedHaptics.size)
        assertEquals(HapticFeedbackType.TextHandleMove, performedHaptics[0])
    }

    @Test
    fun onDeleteAction_performsLongPressHaptic_whenEnabled() {
        preferencesManager.setHapticsEnabled(true)
        sensoryManager.onDeleteAction(testHapticFeedback)

        assertEquals(1, performedHaptics.size)
        assertEquals(HapticFeedbackType.LongPress, performedHaptics[0])
    }

    @Test
    fun playClickSound_doesNotCrash_whenEnabledOrDisabled() {
        preferencesManager.setSoundEffectsEnabled(true)
        sensoryManager.playClickSound()

        preferencesManager.setSoundEffectsEnabled(false)
        sensoryManager.playClickSound()
    }

    @Test
    fun playScanChime_doesNotCrash_whenEnabledOrDisabled() {
        preferencesManager.setSoundEffectsEnabled(true)
        sensoryManager.playScanChime()

        preferencesManager.setSoundEffectsEnabled(false)
        sensoryManager.playScanChime()
    }
}
