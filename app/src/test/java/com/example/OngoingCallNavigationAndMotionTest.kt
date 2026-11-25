package com.example

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.navigation.LyraDestinations
import com.example.service.CallStateManager
import com.example.service.NotificationHelper
import com.example.service.TelecomCallBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class OngoingCallNavigationAndMotionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: Application

    private val testCaller = CallerInfo(
        phoneNumber = "+919876543210",
        formattedNumber = "+91 98765 43210",
        displayName = "Ananya Verma",
        isKnown = true,
        isPriority = true
    )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        CallStateManager.clearState()
        CallStateManager.registerTelecomBridge(null)
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(false)
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        Shadows.shadowOf(pm).setIsInteractive(true)
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Shadows.shadowOf(app).clearNextStartedActivities()
    }

    @After
    fun tearDown() {
        CallStateManager.registerTelecomBridge(null)
        CallStateManager.clearState()
        NotificationHelper.cancelAllCallNotifications(app)
        ShadowLooper.idleMainLooper()
    }

    private fun settleUi() {
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()
        repeat(45) {
            composeRule.mainClock.advanceTimeBy(16L)
            ShadowLooper.idleMainLooper()
        }
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun resolveStartDestination_neverForcesCallScreenOnLauncherReopen_duringActiveCalls() {
        val activity = composeRule.activity

        CallStateManager.onOutgoingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(null))
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(Intent(Intent.ACTION_MAIN)))
        assertEquals(
            LyraDestinations.ACTIVE_CALL,
            activity.resolveStartDestination(Intent(NotificationHelper.ACTION_RETURN_TO_CALL))
        )

        CallStateManager.onCallAnswered()
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(null))
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(Intent(Intent.ACTION_MAIN)))
        assertEquals(
            LyraDestinations.ACTIVE_CALL,
            activity.resolveStartDestination(Intent(NotificationHelper.ACTION_RETURN_TO_CALL))
        )

        CallStateManager.toggleHold()
        assertTrue(CallStateManager.isHold.value)
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(null))

        CallStateManager.routeToVoicemail()
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(null))
        assertEquals(
            LyraDestinations.VOICEMAIL,
            activity.resolveStartDestination(Intent(NotificationHelper.ACTION_RETURN_TO_CALL))
        )
    }

    @Test
    fun ongoingCall_allowsFreeNavigationAcrossAppScreens_andShowsReturnToCallBar() {
        composeRule.runOnUiThread {
            CallStateManager.onOutgoingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
            CallStateManager.onCallAnswered()
        }
        settleUi()

        // MainActivity stays on Dialer (no hijacking) and shows Return to Call banner
        composeRule.onNodeWithTag("dialer_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("active_call_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("return_to_call_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Ananya Verma", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Return to Call", useUnmergedTree = true).assertIsDisplayed()

        // Navigate freely across Recents, Contacts, and Home without affecting active call
        composeRule.onNodeWithTag("nav_recents").performClick()
        settleUi()
        composeRule.onNodeWithTag("call_history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("return_to_call_banner").assertIsDisplayed()
        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)

        composeRule.onNodeWithTag("nav_contacts").performClick()
        settleUi()
        composeRule.onNodeWithTag("contacts_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("return_to_call_banner").assertIsDisplayed()

        // Hold state reflects live inside the Return-to-Call banner without resetting call
        composeRule.runOnUiThread {
            CallStateManager.toggleHold()
        }
        settleUi()
        composeRule.onNodeWithText("On Hold • 00:00", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("nav_lyra_ai").performClick()
        settleUi()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("return_to_call_banner").assertIsDisplayed()
        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)
        assertTrue(CallStateManager.isHold.value)
    }

    @Test
    fun returnToCallBanner_andBackNavigation_returnsWithoutEndingCall() {
        composeRule.runOnUiThread {
            CallStateManager.onOutgoingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
            CallStateManager.onCallAnswered()
        }
        settleUi()

        // Tap Return to Call -> opens ActiveCallScreen cleanly & hides banner
        composeRule.onNodeWithTag("return_to_call_banner").performClick()
        settleUi()
        composeRule.onNodeWithTag("active_call_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("return_to_call_banner").assertDoesNotExist()
        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)

        // System Back from ActiveCallScreen minimizes call UI and keeps call ACTIVE
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        settleUi()

        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)
        assertTrue(CallStateManager.isUserMinimizedCallUi.value)
        composeRule.onNodeWithTag("active_call_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("dialer_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("return_to_call_banner").assertIsDisplayed()
    }

    @Test
    fun returnToCallBanner_launchesCallActivityWhenTelecomBridgeIsLive() {
        val bridge = object : TelecomCallBridge {
            override fun answerCall() {}
            override fun rejectOrDisconnectCall(): Boolean = true
            override fun toggleHold(hold: Boolean) {}
            override fun applyMute(muted: Boolean) {}
            override fun setSpeaker(speaker: Boolean) {}
            override fun sendDtmf(digit: Char) {}
            override fun hasLiveCalls(): Boolean = true
        }
        CallStateManager.registerTelecomBridge(bridge)
        CallStateManager.setUserMinimizedCallUi(true)

        composeRule.runOnUiThread {
            CallStateManager.onOutgoingCall(testCaller, simLabel = "SIM 2", isSimulated = false)
            CallStateManager.onCallAnswered()
        }
        settleUi()

        Shadows.shadowOf(app).clearNextStartedActivities()
        composeRule.onNodeWithTag("return_to_call_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("return_to_call_banner").performClick()
        settleUi()

        val launched = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull("Tapping Return to Call with live Telecom bridge must launch CallActivity", launched)
        assertEquals(CallActivity::class.java.name, launched!!.component?.className)
        assertEquals(NotificationHelper.ACTION_RETURN_TO_CALL, launched.action)
        assertFalse(CallStateManager.isUserMinimizedCallUi.value)
        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)
    }

    @Test
    fun ongoingCallNotification_displaysStatusChronometerAndReturnPendingIntent() {
        val startMs = System.currentTimeMillis() - 12_000L
        val activeNotif = NotificationHelper.buildOngoingCallNotification(
            context = app,
            caller = testCaller,
            callState = CallStateEnum.ACTIVE,
            isMuted = false,
            isSpeakerOn = false,
            isHold = false,
            callStartTimeMs = startMs,
            useFullScreenIntent = false
        )

        assertTrue(activeNotif.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("Ananya Verma", activeNotif.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(activeNotif.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Ongoing"))

        val pendingShadow = Shadows.shadowOf(activeNotif.contentIntent)
        val savedIntent = pendingShadow.savedIntent
        assertEquals(CallActivity::class.java.name, savedIntent.component?.className)
        assertEquals(NotificationHelper.ACTION_RETURN_TO_CALL, savedIntent.action)
        assertTrue((savedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0)
        assertTrue((savedIntent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0)

        val heldNotif = NotificationHelper.buildOngoingCallNotification(
            context = app,
            caller = testCaller,
            callState = CallStateEnum.ACTIVE,
            isMuted = false,
            isSpeakerOn = false,
            isHold = true,
            callStartTimeMs = startMs,
            useFullScreenIntent = false
        )
        assertTrue(heldNotif.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("On Hold"))
    }
}
