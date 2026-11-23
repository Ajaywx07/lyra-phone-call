package com.example

import android.app.Application
import android.content.res.Configuration
import android.telecom.CallAudioState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.service.CallStateManager
import com.example.service.TelecomCallBridge
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.CallRecording
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.CallRecordingsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.CallRecordingStorage
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
import org.robolectric.shadows.ShadowLooper
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h915dp-xxhdpi")
class LyraFixesAndLocalizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        CallStateManager.registerTelecomBridge(null)
        CallStateManager.clearState()
    }

    @After
    fun tearDown() {
        CallStateManager.registerTelecomBridge(null)
        CallStateManager.clearState()
    }

    @Test
    fun dialpadScreen_bottomKeypadButtonTogglesDialpad_withInvariantBackgroundListSize() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()

        val deadline = System.currentTimeMillis() + 4_000L
        while (viewModel.callLog.value.isEmpty() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()

        composeTestRule.setContent {
            MyApplicationTheme {
                DialerScreen(
                    viewModel = viewModel,
                    onNavigateTab = {},
                    onNavigateToActiveCall = {},
                    onNavigateToContactDetails = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Initially dialpad is open and background list container spans full height
        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_background_list_container").assertIsDisplayed()
        val openContainerBounds = composeTestRule.onNodeWithTag("dialer_background_list_container").getBoundsInRoot()
        val openListBounds = composeTestRule.onNodeWithTag("dialer_background_list").getBoundsInRoot()

        // Tap bottom Keypad/Dialpad nav button -> hides dialpad & shows full list
        composeTestRule.onNodeWithTag("nav_keypad").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dialer_expanded_contacts_list").assertIsDisplayed()

        val closedContainerBounds = composeTestRule.onNodeWithTag("dialer_background_list_container").getBoundsInRoot()
        val closedListBounds = composeTestRule.onNodeWithTag("dialer_background_list").getBoundsInRoot()

        // Background list container & box height must remain identical whether dialpad is open or closed
        assertEquals(openContainerBounds.top, closedContainerBounds.top)
        assertEquals(openContainerBounds.bottom, closedContainerBounds.bottom)
        assertEquals(openListBounds.top, closedListBounds.top)
        assertEquals(openListBounds.bottom, closedListBounds.bottom)

        // Tap bottom Keypad/Dialpad nav button again -> reopens dialpad cleanly
        composeTestRule.onNodeWithTag("nav_keypad").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_expanded_contacts_list").assertDoesNotExist()

        // Existing top chevron expand + collapse header toggle still works seamlessly
        composeTestRule.onNodeWithTag("dialer_suggestion_expand_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertDoesNotExist()

        // Reopen via bottom Dialpad button even after opening via chevron (no state conflict!)
        composeTestRule.onNodeWithTag("nav_keypad").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertIsDisplayed()
    }

    @Test
    fun callRecording_integration_modelDaoStorageCoordinatorAndScreenAgree() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LyraDatabase.getDatabase(app)
        val recordingDao = db.callRecordingDao()

        // 1. Model & Room DAO integration test
        val recording = CallRecording(
            callerName = "Ajay",
            callerNumber = "+919876543210",
            fileName = "2026-11-22_143015_Ajay_9876543210.wav",
            mediaStoreUri = "content://media/external/audio/media/101",
            relativePath = "Recordings/Lyra",
            durationSeconds = 45L,
            fileSizeBytes = 3_969_044L,
            timestamp = System.currentTimeMillis(),
            captureSource = "MIC",
            isTwoWayAudio = false
        )
        val insertedId = recordingDao.insertRecording(recording)
        assertTrue(insertedId > 0)
        val retrieved = recordingDao.getRecordingById(insertedId)
        assertEquals("Ajay", retrieved?.callerName)
        assertEquals("+919876543210", retrieved?.callerNumber)
        assertEquals("2026-11-22_143015_Ajay_9876543210.wav", retrieved?.fileName)

        // 2. Storage helper accesses callerName and fileName and formats properly
        val generatedName = CallRecordingStorage.generateFileName("Ajay", "+919876543210")
        assertTrue(generatedName.contains("Ajay"))
        assertTrue(generatedName.endsWith(".wav"))

        val unknownName = CallRecordingStorage.generateFileName(null, "")
        assertTrue(unknownName.contains("Unknown"))

        val shareIntent = CallRecordingStorage.createShareIntent(app, retrieved!!)
        assertEquals("audio/wav", shareIntent.type)

        // 3. ViewModel exposes allCallRecordings, callRecordingCapability, recordingPlaybackState, and recordingActionMessage
        val viewModel = LyraMainViewModel(app)
        assertTrue(viewModel.callRecordingCapability.value.isSupported)
        assertFalse(viewModel.recordingPlaybackState.value.isPlaying)
        assertEquals(null, viewModel.recordingActionMessage.value)

        // 4. CallRecordingsScreen renders with capability notice, recordings list, play button, share button, and delete button
        composeTestRule.setContent {
            MyApplicationTheme {
                CallRecordingsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recordings_back_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_recordings_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_recording_item_${insertedId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_play_btn_${insertedId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_share_btn_${insertedId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_delete_btn_${insertedId}").assertIsDisplayed()

        // Clean up test database record
        recordingDao.deleteRecordingById(insertedId)
    }

    @Test
    @Suppress("DEPRECATION")
    fun callStateManager_liveTelecomPickupSyncAudioAndDisconnect_stayResponsive() {
        var liveCalls = true
        var syncedCount = 0
        var lastMuted: Boolean? = null
        var lastSpeaker: Boolean? = null
        var lastAudioRoute: Int? = null
        var answered = false
        var disconnected = false

        val fakeBridge = object : TelecomCallBridge {
            override fun answerCall() {
                answered = true
                CallStateManager.onCallAnswered()
            }

            override fun rejectOrDisconnectCall(): Boolean {
                disconnected = true
                liveCalls = false
                CallStateManager.clearState()
                return true
            }

            override fun toggleHold(hold: Boolean) {
                CallStateManager.setHoldState(hold)
            }

            override fun applyMute(muted: Boolean) {
                lastMuted = muted
            }

            override fun setSpeaker(speaker: Boolean) {
                lastSpeaker = speaker
            }

            override fun setAudioRouteMode(route: Int) {
                lastAudioRoute = route
            }

            override fun sendDtmf(digit: Char) {}

            override fun hasLiveCalls(): Boolean = liveCalls

            override fun syncCallsState() {
                syncedCount++
                if (liveCalls && CallStateManager.callState.value == CallStateEnum.DISCONNECTED) {
                    CallStateManager.onCallAnswered()
                }
            }
        }

        CallStateManager.registerTelecomBridge(fakeBridge)
        CallStateManager.onIncomingCall(
            caller = CallerInfo(
                phoneNumber = "+918073469203",
                formattedNumber = "+91 80734 69203",
                displayName = "Caller",
                isKnown = false
            ),
            isSimulated = false
        )

        // Pick up via CallStateManager
        CallStateManager.acceptActiveCall()
        assertTrue(answered)
        assertTrue(CallStateManager.hasLiveTelecomCall())
        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)
        assertTrue(syncedCount >= 1)

        // Audio controls update state and forward cleanly to Telecom bridge
        CallStateManager.toggleMute()
        assertTrue(CallStateManager.isMuted.value)
        assertEquals(true, lastMuted)

        CallStateManager.toggleSpeaker()
        assertTrue(CallStateManager.isSpeakerOn.value)
        assertEquals(true, lastSpeaker)
        assertEquals(CallAudioState.ROUTE_SPEAKER, lastAudioRoute)

        // Sync recovers live Telecom call rather than closing screen
        CallStateManager.syncWithLiveTelecomBridge()
        assertTrue(CallStateManager.hasLiveTelecomCall())

        CallStateManager.declineOrEndActiveCall()
        assertTrue(disconnected)
        assertFalse(CallStateManager.hasLiveTelecomCall())
    }

    @Test
    fun aboutScreen_expandableGuideBox_revealsAllSixSectionsOnClick() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onBackClick = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("about_guide_expandable_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_guide_expanded_content", useUnmergedTree = true).assertDoesNotExist()

        composeTestRule.onNodeWithTag("about_guide_expandable_card").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("about_guide_expanded_content", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("How to Use the App", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Available Settings", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Recommended / Best Settings", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Do's & Don'ts", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Usage Framework", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("App Details", useUnmergedTree = true).assertExists()

        // Scroll slightly to verify App Details remains cleanly rendered inside expanded box
        composeTestRule.onNodeWithTag("about_screen").performScrollToNode(hasTestTag("about_guide_expandable_card"))
        composeTestRule.waitForIdle()
    }

    @Test
    fun localization_switchesCleanlyBetweenEnglishAndHindiResources() {
        val baseContext = ApplicationProvider.getApplicationContext<Application>()

        val enConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val enContext = baseContext.createConfigurationContext(enConfig)

        val hiConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("hi"))
        }
        val hiContext = baseContext.createConfigurationContext(hiConfig)

        assertEquals("Dialpad", enContext.getString(R.string.nav_dialpad))
        assertEquals("डायलपैड", hiContext.getString(R.string.nav_dialpad))

        assertEquals("How to Use the App", enContext.getString(R.string.about_guide_section_how_to_use))
        assertNotEquals(
            enContext.getString(R.string.about_guide_section_how_to_use),
            hiContext.getString(R.string.about_guide_section_how_to_use)
        )

        assertEquals("Mute", enContext.getString(R.string.call_action_mute))
        assertEquals("म्यूट", hiContext.getString(R.string.call_action_mute))
    }
}
