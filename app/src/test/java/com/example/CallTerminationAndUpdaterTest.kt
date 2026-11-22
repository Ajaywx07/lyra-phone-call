package com.example

import android.app.Application
import android.content.Intent
import android.os.UserManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.UniversalVoicemailAiRepository
import com.example.domain.model.AiEngineConfig
import com.example.domain.model.AiProvider
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.service.CallStateManager
import com.example.service.TelecomCallBridge
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.screens.AboutScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.GitHubReleaseInfo
import com.example.utils.GitHubUpdateManager
import com.example.utils.ensureFirebaseInitialized
import kotlinx.coroutines.test.runTest
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h915dp-xxhdpi")
class CallTerminationAndUpdaterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingTelecomBridge(
        var rejectReturnValue: Boolean = true
    ) : TelecomCallBridge {
        var answerCalls = 0
        var rejectCalls = 0
        var muteHistory = mutableListOf<Boolean>()
        var speakerHistory = mutableListOf<Boolean>()

        override fun answerCall() {
            answerCalls++
        }

        override fun rejectOrDisconnectCall(): Boolean {
            rejectCalls++
            return rejectReturnValue
        }

        override fun toggleHold(hold: Boolean) = Unit

        override fun applyMute(muted: Boolean) {
            muteHistory.add(muted)
        }

        override fun setSpeaker(speaker: Boolean) {
            speakerHistory.add(speaker)
        }

        override fun sendDtmf(digit: Char) = Unit
    }

    @Before
    fun setUp() {
        CallStateManager.clearState()
        CallStateManager.registerTelecomBridge(null)
    }

    @After
    fun tearDown() {
        CallStateManager.clearState()
        CallStateManager.registerTelecomBridge(null)
    }

    @Test
    fun declineAndCancelCalls_invokeTelecomBridgeAndUpdateStateImmediately() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        CallStateManager.bindApplicationContext(app)

        val bridge = RecordingTelecomBridge(rejectReturnValue = true)
        CallStateManager.registerTelecomBridge(bridge)

        val caller = CallerInfo(
            phoneNumber = "+919876543210",
            formattedNumber = "+91 98765 43210",
            displayName = "BOSS",
            isKnown = true
        )
        CallStateManager.onIncomingCall(caller, isSimulated = false)
        assertEquals(CallStateEnum.RINGING, CallStateManager.callState.value)

        CallStateManager.declineOrEndActiveCall(null)
        assertEquals(1, bridge.rejectCalls)
        assertEquals(CallStateEnum.DISCONNECTED, CallStateManager.callState.value)

        CallStateManager.onOutgoingCall(caller, isSimulated = false)
        assertEquals(CallStateEnum.DIALING, CallStateManager.callState.value)
        CallStateManager.cancelDialingOrUnconnectedCall()
        assertEquals(2, bridge.rejectCalls)
        assertEquals(CallStateEnum.IDLE, CallStateManager.callState.value)
    }

    @Test
    fun routeToVoicemail_keepsGreetingUnmutedOnSpeakerThenMutesDuringRecording() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        CallStateManager.bindApplicationContext(app)

        val bridge = RecordingTelecomBridge()
        CallStateManager.registerTelecomBridge(bridge)

        val caller = CallerInfo(
            phoneNumber = "+918073469203",
            formattedNumber = "+91 80734 69203",
            displayName = "Unknown",
            isKnown = false
        )
        CallStateManager.onIncomingCall(caller, isSimulated = false)

        // Phase 1: Enter voicemail -> answers call, unmuted TX mic + speaker ON so greeting reaches caller
        CallStateManager.routeToVoicemail()
        assertEquals(1, bridge.answerCalls)
        assertEquals(CallStateEnum.VOICEMAIL, CallStateManager.callState.value)
        assertFalse(CallStateManager.isMuted.value)
        assertTrue(CallStateManager.isSpeakerOn.value)
        assertEquals(false, bridge.muteHistory.lastOrNull())
        assertEquals(true, bridge.speakerHistory.lastOrNull())

        // Telecom audio callbacks during voicemail must not override acoustic bridge mute/speaker
        CallStateManager.syncAudioState(muted = true, speakerOn = false, fromTelecom = true)
        assertFalse(CallStateManager.isMuted.value)
        assertTrue(CallStateManager.isSpeakerOn.value)

        // Phase 2: Caller recording starts -> mute owner mic while keeping speaker ON
        CallStateManager.setVoicemailAcousticBridge(speakingGreeting = false)
        assertTrue(CallStateManager.isMuted.value)
        assertTrue(CallStateManager.isSpeakerOn.value)
        assertEquals(true, bridge.muteHistory.lastOrNull())

        // Phase 3: Restore clean audio after voicemail ends
        CallStateManager.restoreAfterVoicemail()
        assertEquals(false, bridge.muteHistory.lastOrNull())
        assertEquals(false, bridge.speakerHistory.lastOrNull())
    }

    @Test
    fun firebaseInitAndVoicemailAiRepository_failGracefullyWhenDeviceLockedWithoutCrash() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val userManager = app.getSystemService(UserManager::class.java)
        Shadows.shadowOf(userManager).setUserUnlocked(false)

        assertFalse(ensureFirebaseInitialized(app))

        val repo = UniversalVoicemailAiRepository(app)
        val dummyWav = ByteArray(128) { 1 }
        val result = repo.analyzeVoicemailAudio(
            wavBytes = dummyWav,
            callerName = "BOSS",
            config = AiEngineConfig(provider = AiProvider.FIREBASE_AI, modelName = "gemini-3.8-flash")
        )
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Firebase", ignoreCase = true) == true
        )

        Shadows.shadowOf(userManager).setUserUnlocked(true)
    }

    @Test
    fun callActivity_handlesDebugTokenIntentWithoutCrashing() {
        val caller = CallerInfo(
            phoneNumber = "+918795989222",
            formattedNumber = "+91 87959 89222",
            displayName = "BOSS",
            isKnown = true
        )
        CallStateManager.onIncomingCall(caller, isSimulated = true)
        val launchIntent = Intent("ACTION_INCOMING_CALL").apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-debug-token-1234")
        }
        val controller = Robolectric.buildActivity(CallActivity::class.java, launchIntent).setup()
        ShadowLooper.idleMainLooper()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun gitHubUpdateManager_parsesVersionsAndValidatesReleaseJsonAndIntent() {
        assertEquals("ajay" to "lyra", GitHubUpdateManager.normalizeRepoSlug("https://github.com/ajay/lyra/"))
        assertEquals("ajay" to "lyra", GitHubUpdateManager.normalizeRepoSlug("ajay/lyra"))
        assertNull(GitHubUpdateManager.normalizeRepoSlug("invalid-slug"))

        assertTrue(GitHubUpdateManager.isNewerVersion("v2.1.0", "2.0"))
        assertTrue(GitHubUpdateManager.isNewerVersion("2.0.1", "2.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v2.0.0", "2.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v2.0-beta.1", "2.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.9.5", "2.0"))

        val sampleJson = """
            {
              "tag_name": "v2.1.0",
              "name": "Lyra 2.1.0",
              "body": "Immediate call disconnect & voicemail audio improvements",
              "html_url": "https://github.com/ajay/lyra/releases/tag/v2.1.0",
              "published_at": "2026-11-21T10:00:00Z",
              "draft": false,
              "assets": [
                {
                  "name": "lyra-v2.1.0-release.apk",
                  "browser_download_url": "https://github.com/ajay/lyra/releases/download/v2.1.0/lyra-v2.1.0-release.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = GitHubUpdateManager.parseReleaseJson(sampleJson)
        assertNotNull(parsed)
        assertEquals("v2.1.0", parsed?.tagName)
        assertEquals("2.1.0", parsed?.normalizedVersion)
        assertTrue(parsed?.apkDownloadUrl?.endsWith(".apk") == true)

        val viewIntent = GitHubUpdateManager.buildExternalUpdateIntent(parsed!!)
        assertNotNull(viewIntent)
        assertEquals(Intent.ACTION_VIEW, viewIntent?.action)
        assertEquals(parsed.apkDownloadUrl, viewIntent?.dataString)

        val untrustedRelease = parsed.copy(
            htmlUrl = "http://evil.example.com/app.apk",
            apkDownloadUrl = "https://evil.example.com/app.apk"
        )
        assertNull(GitHubUpdateManager.buildExternalUpdateIntent(untrustedRelease))
    }

    @Test
    fun updateAvailableDialog_andAboutScreenUpdaterCard_renderAndTriggerActions() {
        var downloaded = false
        var skipped = false
        var dismissed = false
        val release = GitHubReleaseInfo(
            tagName = "v2.2.0",
            normalizedVersion = "2.2.0",
            releaseTitle = "Lyra 2.2.0 Release",
            releaseNotes = "- Instant SIP 603 decline\n- GitHub Releases updater",
            htmlUrl = "https://github.com/ajay/lyra/releases/tag/v2.2.0",
            apkDownloadUrl = "https://github.com/ajay/lyra/releases/download/v2.2.0/lyra.apk",
            publishedAt = "2026-11-21T12:00:00Z"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                UpdateAvailableDialog(
                    release = release,
                    onDownloadClick = { downloaded = true },
                    onSkipVersion = { skipped = true },
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("update_available_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0 → v2.2.0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_download_btn").performClick()
        assertTrue(downloaded)
        composeTestRule.onNodeWithTag("update_skip_btn").performClick()
        assertTrue(skipped)
        composeTestRule.onNodeWithTag("update_later_btn").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun aboutScreen_withViewModelDisplaysGitHubReleasesUpdaterCard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onBackClick = {}, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_screen")
            .performScrollToNode(hasTestTag("check_updates_btn"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("github_update_repo_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("check_updates_btn").assertIsDisplayed()
    }
}
