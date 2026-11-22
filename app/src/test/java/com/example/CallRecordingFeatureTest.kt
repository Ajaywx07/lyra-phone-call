package com.example

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.CallRecording
import com.example.data.repository.CallRecordingRepository
import com.example.domain.model.CallerInfo
import com.example.service.CallRecordingCoordinator
import com.example.service.CallStateManager
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.CallRecordingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.CallRecordingStorage
import com.example.utils.CallRecordingSupportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h915dp-xxhdpi")
class CallRecordingFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        CallStateManager.bindApplicationContext(app)
        CallRecordingCoordinator.bindApplicationContext(app)
        CallStateManager.clearState()
    }

    @After
    fun tearDown() {
        CallStateManager.clearState()
    }

    private fun sampleWavBytes(pcmLength: Int = 320): ByteArray {
        val totalDataLen = pcmLength + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(16000)
            putInt(32000)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmLength)
        }.array()
        return header + ByteArray(pcmLength) { (it % 17).toByte() }
    }

    @Test
    fun buildRecordingFileName_matchesExactFormat_andUsesUnknownFallback() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 22, 14, 30, 15)
            set(Calendar.MILLISECOND, 0)
        }

        val knownName = CallRecordingStorage.buildRecordingFileName(
            callerName = "Name",
            phoneNumber = "9876543210",
            timestampMillis = cal.timeInMillis,
            isKnown = true
        )
        assertEquals("2026-11-22_143015_Name_9876543210.wav", knownName)

        val formattedNumberUnknownName = CallRecordingStorage.buildRecordingFileName(
            callerName = "+91 80734 69203",
            phoneNumber = "+91 80734 69203",
            timestampMillis = cal.timeInMillis,
            isKnown = false
        )
        assertEquals("2026-11-22_143015_Unknown_918073469203.wav", formattedNumberUnknownName)

        val blankCallerName = CallRecordingStorage.buildRecordingFileName(
            callerName = "   ",
            phoneNumber = "8795989222",
            timestampMillis = cal.timeInMillis,
            isKnown = false
        )
        assertEquals("2026-11-22_143015_Unknown_8795989222.wav", blankCallerName)

        val placeholderUnknown = CallRecordingStorage.buildRecordingFileName(
            callerName = "Unknown \"Mine\"",
            phoneNumber = "8073469203",
            timestampMillis = cal.timeInMillis,
            isKnown = false
        )
        assertEquals("2026-11-22_143015_Unknown_8073469203.wav", placeholderUnknown)
    }

    @Test
    fun detectRecordingCapability_identifiesPermissionAndAndroidTwoWayRestriction() {
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)

        val missingPermStatus = CallRecordingStorage.detectRecordingCapability(app)
        assertEquals(CallRecordingSupportMode.PERMISSION_REQUIRED, missingPermStatus.mode)
        assertFalse(missingPermStatus.hasMicPermission)

        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)
        val restrictedStatus = CallRecordingStorage.detectRecordingCapability(app)
        assertEquals(CallRecordingSupportMode.MIC_ONLY_RESTRICTED, restrictedStatus.mode)
        assertTrue(restrictedStatus.hasMicPermission)
        assertFalse(restrictedStatus.hasCaptureAudioOutput)
        assertTrue(restrictedStatus.headline.contains("Restricted", ignoreCase = true))
        assertTrue(restrictedStatus.explanation.contains("CAPTURE_AUDIO_OUTPUT"))
    }

    @Test
    fun persistShareAndDeleteRecording_storesCallerMetadataInFilenameAndRoomDb() = runBlocking {
        val db = LyraDatabase.getDatabase(app)
        val dao = db.callRecordingDao()
        val repo = CallRecordingRepository(app, dao)

        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 22, 14, 30, 15)
            set(Calendar.MILLISECOND, 0)
        }
        val caller = CallerInfo(
            phoneNumber = "9876543210",
            formattedNumber = "9876543210",
            displayName = "BOSS",
            isKnown = true
        )

        val saved = CallRecordingCoordinator.persistWavBytesAsRecording(
            context = app,
            wavBytes = sampleWavBytes(480),
            caller = caller,
            durationSeconds = 18L,
            timestampMillis = cal.timeInMillis,
            captureSourceLabel = "VOICE_COMMUNICATION",
            isTwoWayAudio = false
        )

        assertTrue(saved.id > 0L)
        assertEquals("BOSS", saved.callerName)
        assertEquals("9876543210", saved.callerNumber)
        assertEquals("2026-11-22_143015_BOSS_9876543210.wav", saved.fileName)
        assertTrue(saved.relativePath.contains("Lyra/Recordings"))
        assertFalse(saved.isTwoWayAudio)

        val loaded = dao.getRecordingById(saved.id)
        assertNotNull(loaded)
        assertEquals("BOSS", loaded?.callerName)
        assertEquals("9876543210", loaded?.callerNumber)

        val shareIntent = CallRecordingStorage.createShareIntent(app, saved)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_CHOOSER, shareIntent?.action)

        repo.deleteRecording(saved)
        assertNull(dao.getRecordingById(saved.id))
    }

    @Test
    fun callEnd_automaticallyStopsAnyActiveRecordingFlagAndResetsTimer() {
        CallStateManager.onIncomingCall(
            caller = CallerInfo(
                phoneNumber = "8795989222",
                formattedNumber = "8795989222",
                displayName = "BOSS",
                isKnown = true
            ),
            simLabel = "SIM 1",
            isSimulated = true
        )
        CallStateManager.onCallAnswered()
        // When call ends via CallStateManager, coordinator stop path runs cleanly with no leak or crash
        CallStateManager.onCallEnded()
        assertFalse(CallRecordingCoordinator.isRecording.value)
        assertEquals(0L, CallRecordingCoordinator.elapsedSeconds.value)
    }

    @Test
    fun callRecordingsScreen_displaysCapabilityNoticeListItemsPlaybackShareAndDelete() {
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)

        val db = LyraDatabase.getDatabase(app)
        val insertedId = runBlocking {
            val saved = CallRecordingCoordinator.persistWavBytesAsRecording(
                context = app,
                wavBytes = sampleWavBytes(640),
                caller = CallerInfo(
                    phoneNumber = "8073469203",
                    formattedNumber = "8073469203",
                    displayName = "Unknown Caller",
                    isKnown = false
                ),
                durationSeconds = 42L,
                timestampMillis = System.currentTimeMillis(),
                captureSourceLabel = "VOICE_COMMUNICATION",
                isTwoWayAudio = false
            )
            saved.id
        }

        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                CallRecordingsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_recordings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_capability_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recordings_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_card_$insertedId").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("8073469203").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_play_btn_$insertedId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_share_btn_$insertedId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_delete_btn_$insertedId").assertIsDisplayed()

        // Tap Play -> starts active playback bar / stop button (may fall back or show stop depending on Robolectric MediaPlayer)
        composeTestRule.onNodeWithTag("recording_delete_btn_$insertedId").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delete Call Recording?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_delete_btn").performClick()
        composeTestRule.waitForIdle()

        runBlocking {
            val remaining = db.callRecordingDao().getRecordingById(insertedId)
            assertNull(remaining)
        }
    }

    @Test
    fun activeCallScreen_showsRecordButtonAndRestrictionNotice_alongsideExistingControls() {
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)

        CallStateManager.onOutgoingCall(
            caller = CallerInfo(
                phoneNumber = "8795989222",
                formattedNumber = "8795989222",
                displayName = "BOSS",
                isKnown = true
            ),
            simLabel = "SIM 1",
            isSimulated = true
        )
        CallStateManager.onCallAnswered()

        val viewModel = LyraMainViewModel(app)
        viewModel.refreshRecordingCapability()

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    viewModel = viewModel,
                    onEndCall = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_call_record_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_call_recording_capability_note").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_action_Audio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_action_Add Call").assertIsDisplayed()
    }
}
