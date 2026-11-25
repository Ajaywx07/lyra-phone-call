package com.example

import android.app.Application
import android.telecom.CallAudioState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.VoiceChangerDspProcessor
import com.example.audio.VoiceMode
import com.example.data.local.preferences.LyraPreferences
import com.example.domain.model.CallerInfo
import com.example.service.CallStateManager
import com.example.ui.components.InCallMoreOptionsSheet
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class VoiceChangerFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var prefs: LyraPreferences

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        prefs = LyraPreferences(app)
        prefs.setVoiceChangerEnabled(false)
        CallStateManager.clearState()
    }

    @After
    fun tearDown() = runBlocking {
        CallStateManager.clearState()
        prefs.setVoiceChangerEnabled(false)
    }

    @Test
    fun settingsToggle_persistsVoiceChangerPreference_andUpdatesUi() {
        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onNavigateToAbout = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_custom_features"))
        composeTestRule.onNodeWithTag("settings_module_header_custom_features").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_voice_changer"))
        composeTestRule.onNodeWithTag("toggle_voice_changer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_Real-Time Voice Changer").assertIsOff()

        composeTestRule.onNodeWithTag("toggle_voice_changer").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.isVoiceChangerEnabled.value
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_Real-Time Voice Changer").assertIsOn()

        val persisted = runBlocking { prefs.isVoiceChangerEnabled.first() }
        assertTrue(persisted)
    }

    @Test
    fun inCallMoreMenu_hidesVoiceChangerWhenDisabled_andShowsWhenEnabled() {
        var enabled by mutableStateOf(false)
        var voiceChangerClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                InCallMoreOptionsSheet(
                    isHold = false,
                    conferenceParticipantCount = 1,
                    canMergeConference = false,
                    isConferenceMerged = false,
                    currentAudioRoute = CallAudioState.ROUTE_EARPIECE,
                    isBluetoothAvailable = false,
                    onToggleHold = {},
                    onAddCallClick = {},
                    onMergeConference = {},
                    onSelectAudioRoute = {},
                    onDismiss = {},
                    isVoiceChangerEnabled = enabled,
                    activeVoiceMode = VoiceMode.NONE,
                    onVoiceChangerClick = { voiceChangerClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_option_voice_changer").assertDoesNotExist()

        enabled = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_option_voice_changer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_option_voice_changer").performClick()
        assertTrue(voiceChangerClicked)
    }

    @Test
    fun activeCallScreen_voiceChangerPopup_switchesModesAndResetsOnCallEnd() = runBlocking {
        prefs.setVoiceChangerEnabled(true)
        CallStateManager.onOutgoingCall(
            caller = CallerInfo(
                phoneNumber = "+919876543210",
                formattedNumber = "+91 98765 43210",
                displayName = "Ajay",
                isKnown = true
            ),
            simLabel = "SIM 1",
            isSimulated = true
        )
        CallStateManager.onCallAnswered()

        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    viewModel = viewModel,
                    onEndCall = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) { viewModel.isVoiceChangerEnabled.value }

        // Open More menu -> tap Voice Changer
        composeTestRule.onNodeWithTag("call_action_More").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_option_voice_changer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_option_voice_changer").performClick()
        composeTestRule.waitForIdle()

        // Verify popup displays Male, Female, and Robot modes
        composeTestRule.onNodeWithTag("voice_changer_popup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_male").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_female").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_robot").assertIsDisplayed()
        composeTestRule.onNodeWithText("Male").assertIsDisplayed()
        composeTestRule.onNodeWithText("Female").assertIsDisplayed()
        composeTestRule.onNodeWithText("Robot").assertIsDisplayed()

        // Switch Male -> Female -> Robot smoothly
        composeTestRule.onNodeWithTag("voice_mode_male").performClick()
        assertEquals(VoiceMode.MALE, viewModel.voiceMode.value)

        composeTestRule.onNodeWithTag("voice_mode_female").performClick()
        assertEquals(VoiceMode.FEMALE, viewModel.voiceMode.value)

        composeTestRule.onNodeWithTag("voice_mode_robot").performClick()
        assertEquals(VoiceMode.ROBOT, viewModel.voiceMode.value)

        // Reset via Normal (Off) button
        composeTestRule.onNodeWithTag("voice_mode_reset_normal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_reset_normal").performClick()
        assertEquals(VoiceMode.NONE, viewModel.voiceMode.value)

        // Activate Male again, then end call -> must auto-stop/reset voice mode
        composeTestRule.onNodeWithTag("voice_mode_male").performClick()
        assertEquals(VoiceMode.MALE, viewModel.voiceMode.value)

        CallStateManager.onCallEnded()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            viewModel.voiceMode.value == VoiceMode.NONE && !viewModel.isVoiceChangerPipelineActive.value
        }
        assertEquals(VoiceMode.NONE, viewModel.voiceMode.value)
        assertFalse(viewModel.isVoiceChangerPipelineActive.value)
    }

    @Test
    fun dspProcessor_generatesDistinctNonZeroPcm16OutputsForEachMode() {
        val sampleRate = 16_000
        val frames = 1600
        val input = ShortArray(frames) { idx ->
            (12_000.0 * sin(2.0 * PI * 220.0 * idx / sampleRate)).toInt().toShort()
        }

        fun runMode(mode: VoiceMode): ShortArray {
            val dsp = VoiceChangerDspProcessor(sampleRate)
            dsp.setMode(mode)
            val out = ShortArray(frames)
            // Run two blocks so the 15 ms crossfade & delay buffer settle cleanly
            dsp.processPcm16(input, out, frames)
            dsp.processPcm16(input, out, frames)
            return out
        }

        val noneOut = runMode(VoiceMode.NONE)
        val maleOut = runMode(VoiceMode.MALE)
        val femaleOut = runMode(VoiceMode.FEMALE)
        val robotOut = runMode(VoiceMode.ROBOT)

        assertTrue(maleOut.any { it.toInt() != 0 })
        assertTrue(femaleOut.any { it.toInt() != 0 })
        assertTrue(robotOut.any { it.toInt() != 0 })

        assertNotEquals(noneOut.toList(), maleOut.toList())
        assertNotEquals(noneOut.toList(), femaleOut.toList())
        assertNotEquals(noneOut.toList(), robotOut.toList())
        assertNotEquals(maleOut.toList(), femaleOut.toList())
        assertNotEquals(maleOut.toList(), robotOut.toList())
        assertNotEquals(femaleOut.toList(), robotOut.toList())
    }
}
