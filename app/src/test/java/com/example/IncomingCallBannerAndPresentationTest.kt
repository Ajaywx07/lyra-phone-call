package com.example

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.navigation.LyraDestinations
import com.example.service.CallStateManager
import com.example.service.IncomingCallPresentationMode
import com.example.service.NotificationHelper
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class IncomingCallBannerAndPresentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: Application

    private val testCaller = CallerInfo(
        phoneNumber = "+919876543210",
        formattedNumber = "+91 98765 43210",
        displayName = "Ajay",
        isKnown = true,
        isPriority = true,
        isOwner = true,
        contactLabel = "Owner"
    )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        CallStateManager.clearState()
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(false)
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        Shadows.shadowOf(pm).setIsInteractive(true)
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Shadows.shadowOf(app).clearStartedServices()
        Shadows.shadowOf(app).clearNextStartedActivities()
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

    @After
    fun tearDown() {
        CallStateManager.clearState()
        NotificationHelper.cancelAllCallNotifications(app)
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun presentationPolicy_strictlySeparatesUnlockedLockedAndFullScreenStates() {
        assertEquals(
            IncomingCallPresentationMode.TOP_BANNER_UNLOCKED,
            NotificationHelper.resolveIncomingCallPresentationMode(
                isLockedOrScreenOff = false,
                incomingScreenEnabled = true,
                fullScreenUiVisible = false
            )
        )
        assertEquals(
            IncomingCallPresentationMode.FULL_SCREEN_LOCKED,
            NotificationHelper.resolveIncomingCallPresentationMode(
                isLockedOrScreenOff = true,
                incomingScreenEnabled = true,
                fullScreenUiVisible = false
            )
        )
        // Mutual exclusion: once full-screen incoming UI is visible, banner presentation becomes NONE
        assertEquals(
            IncomingCallPresentationMode.NONE,
            NotificationHelper.resolveIncomingCallPresentationMode(
                isLockedOrScreenOff = false,
                incomingScreenEnabled = true,
                fullScreenUiVisible = true
            )
        )
    }

    @Test
    fun dispatchIncomingCall_whenUnlocked_doesNotLaunchCallActivityOrSetFullScreenIntent() {
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(false)
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        Shadows.shadowOf(pm).setIsInteractive(true)

        CallStateManager.onIncomingCall(testCaller)
        NotificationHelper.dispatchIncomingCall(
            context = app,
            caller = testCaller,
            fullscreenPreferenceEnabled = true,
            incomingScreenEnabled = true,
            backgroundCallingEnabled = true,
            persistentNotificationEnabled = true,
            showCallerPhoto = false
        )

        val launchedActivity = Shadows.shadowOf(app).nextStartedActivity
        assertNull("Unlocked screen must NOT auto-launch full-screen CallActivity", launchedActivity)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_INCOMING)
        assertNotNull(posted)
        assertNull("Unlocked incoming notification must not attach fullScreenIntent", posted!!.fullScreenIntent)
    }

    @Test
    fun dispatchIncomingCall_whenLockedOrScreenOff_launchesCallActivityAndSetsFullScreenIntent() {
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(true)

        CallStateManager.onIncomingCall(testCaller)
        NotificationHelper.dispatchIncomingCall(
            context = app,
            caller = testCaller,
            fullscreenPreferenceEnabled = true,
            incomingScreenEnabled = true,
            backgroundCallingEnabled = true,
            persistentNotificationEnabled = true,
            showCallerPhoto = false
        )

        val launchedActivity = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull("Locked screen MUST launch full-screen CallActivity directly", launchedActivity)
        assertEquals(CallActivity::class.java.name, launchedActivity!!.component?.className)
        assertEquals("ACTION_INCOMING_CALL", launchedActivity.action)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_INCOMING)
        assertNotNull(posted)
        assertNotNull("Locked screen notification must set fullScreenIntent", posted!!.fullScreenIntent)
    }

    @Test
    fun unlockedIncomingCall_showsOnlyTopBanner_andOpensFullScreenOnlyOnBannerTapOrPickup() {
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(false)
        assertEquals(LyraDestinations.DIALER, composeRule.activity.resolveStartDestination(null))

        composeRule.runOnUiThread {
            CallStateManager.onIncomingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
        }
        settleUi()

        // Unlocked: ONLY top banner shows; full-screen incoming UI must NOT open automatically
        composeRule.onNodeWithTag("top_incoming_call_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_caller_photo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_caller_name", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_accept_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_decline_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("incoming_call_screen").assertDoesNotExist()

        // Verify non-overlapping horizontal layout of photo, name, decline & accept buttons
        val photoBounds = composeRule.onNodeWithTag("top_banner_caller_photo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val nameBounds = composeRule.onNodeWithTag("top_banner_caller_name", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val declineBounds = composeRule.onNodeWithTag("top_banner_decline_btn").getUnclippedBoundsInRoot()
        val acceptBounds = composeRule.onNodeWithTag("top_banner_accept_btn").getUnclippedBoundsInRoot()
        assertTrue(photoBounds.right <= nameBounds.left)
        assertTrue(nameBounds.right <= declineBounds.left)
        assertTrue(declineBounds.right <= acceptBounds.left)

        // Tapping the banner opens full-screen incoming UI and hides the top banner immediately
        composeRule.onNodeWithTag("top_incoming_call_banner").performClick()
        settleUi()

        composeRule.onNodeWithTag("incoming_call_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("top_incoming_call_banner").assertDoesNotExist()
    }

    @Test
    fun unlockedIncomingCall_pickingUpFromTopBannerTransitionsToActiveCallAndDismissesBanner() {
        composeRule.runOnUiThread {
            CallStateManager.onIncomingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
        }
        settleUi()

        composeRule.onNodeWithTag("top_incoming_call_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_accept_btn").performClick()
        settleUi()

        assertEquals(CallStateEnum.ACTIVE, CallStateManager.callState.value)
        composeRule.onNodeWithTag("active_call_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("top_incoming_call_banner").assertDoesNotExist()
    }

    @Test
    fun lockedIncomingCall_opensFullScreenUiDirectlyWithoutShowingTopBanner() {
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(true)

        composeRule.runOnUiThread {
            CallStateManager.onIncomingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
        }
        settleUi()

        assertEquals(LyraDestinations.INCOMING_CALL, composeRule.activity.resolveStartDestination(null))
        composeRule.onNodeWithTag("incoming_call_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("top_incoming_call_banner").assertDoesNotExist()
    }

    @Test
    fun unlockedIncomingCall_decliningFromTopBannerEndsCallAndDismissesBanner() {
        CallStateManager.clearState()
        assertFalse(CallStateManager.isFullScreenIncomingUiVisible.value)

        composeRule.runOnUiThread {
            CallStateManager.onIncomingCall(testCaller, simLabel = "SIM 2", isSimulated = true)
        }
        settleUi()

        composeRule.onNodeWithText("OWNER PRIORITY • SIM 2").assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_decline_btn").performClick()
        settleUi()

        assertEquals(CallStateEnum.IDLE, CallStateManager.callState.value)
        composeRule.onNodeWithTag("top_incoming_call_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("incoming_call_screen").assertDoesNotExist()
    }

    @Test
    fun unlockedIncomingCall_pillContainerMaintainsMarginsAndTargetsAcrossRotation() {
        composeRule.runOnUiThread {
            CallStateManager.onIncomingCall(testCaller, simLabel = "SIM 1", isSimulated = true)
        }
        settleUi()

        val bannerBounds = composeRule.onNodeWithTag("top_incoming_call_banner").getUnclippedBoundsInRoot()
        val photoBounds = composeRule.onNodeWithTag("top_banner_caller_photo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val acceptBounds = composeRule.onNodeWithTag("top_banner_accept_btn").getUnclippedBoundsInRoot()
        val declineBounds = composeRule.onNodeWithTag("top_banner_decline_btn").getUnclippedBoundsInRoot()

        // Ensure halo/blur margin encloses inner content cleanly and buttons preserve >= 48dp targets
        assertTrue(photoBounds.left > bannerBounds.left)
        assertTrue(acceptBounds.right < bannerBounds.right)
        assertTrue((acceptBounds.right.value - acceptBounds.left.value) >= 47.5f)
        assertTrue((declineBounds.right.value - declineBounds.left.value) >= 47.5f)

        // Advance rotation cycle across multiple quadrants to verify smooth rendering with no crash
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(650L)
            ShadowLooper.idleMainLooper()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_incoming_call_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("top_banner_caller_name", useUnmergedTree = true).assertIsDisplayed()
    }
}
