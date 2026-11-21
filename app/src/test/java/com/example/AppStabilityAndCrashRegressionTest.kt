package com.example

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.service.CallStateManager
import com.example.service.LyraCallService
import com.example.service.QuickSmsService
import com.example.service.evaluateCallScreeningDecision
import com.example.ui.components.FullScreenAddCallOverlay
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.buildDialerMatchingSuggestions
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PhoneNumberUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class AppStabilityAndCrashRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        CallStateManager.clearState()
    }

    @After
    fun tearDown() {
        CallStateManager.clearState()
    }

    @Test
    fun mainActivity_setupRecreateAndNavigationDoNotCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        ShadowLooper.idleMainLooper()

        val activity = controller.get()
        assertNotNull(activity)

        // Simulate configuration change (rotation) with active MainActivity
        controller.recreate()
        ShadowLooper.idleMainLooper()
        assertNotNull(controller.get())

        controller.pause().stop().destroy()
        ShadowLooper.idleMainLooper()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h580dp-mdpi")
    fun mainActivity_compactScreenQualifierSetupDoesNotCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        ShadowLooper.idleMainLooper()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun dialerScreen_multiNumberDuplicateContactId_doesNotCrashWhenExpandingSuggestions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)

        val fakeContactsProvider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor {
                if (uri.toString().contains("phone") || uri == ContactsContract.CommonDataKinds.Phone.CONTENT_URI) {
                    return MatrixCursor(
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                            ContactsContract.CommonDataKinds.Phone.TYPE
                        )
                    ).apply {
                        // Same CONTACT_ID "42" with two phone numbers (Mobile + Work)
                        addRow(
                            arrayOf<Any?>(
                                "42",
                                "Dr. Rohan Verma",
                                "+91 98111 22334",
                                null,
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                            )
                        )
                        addRow(
                            arrayOf<Any?>(
                                "42",
                                "Dr. Rohan Verma",
                                "+91 98111 22335",
                                null,
                                ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                            )
                        )
                    }
                }
                return MatrixCursor(arrayOf("_id"))
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, fakeContactsProvider)

        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()

        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.contacts.value.size < 2 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
        ShadowLooper.idleMainLooper()
        assertTrue(viewModel.contacts.value.count { it.id == "42" } >= 2)

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

        // Expanding suggestions creates a LazyColumn with both numbers for contact "42"
        composeTestRule.onNodeWithTag("dialer_suggestion_expand_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_expanded_contacts_list").assertIsDisplayed()
    }

    @Test
    fun fullScreenAddCallOverlay_multiNumberDuplicateContactId_doesNotCrash() {
        val multiNumberContacts = listOf(
            ContactItemData(
                id = "42",
                name = "Dr. Rohan Verma",
                phoneNumber = "+91 98111 22334",
                label = "Mobile",
                isPriority = true
            ),
            ContactItemData(
                id = "42",
                name = "Dr. Rohan Verma",
                phoneNumber = "+91 98111 22335",
                label = "Work",
                isPriority = true
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                FullScreenAddCallOverlay(
                    contacts = multiNumberContacts,
                    activeCallerName = "Ajay",
                    onPlaceSecondaryCall = {},
                    onDismiss = {}
                )
            }
        }

        // Dialpad tab renders both items safely
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_screen_add_call_overlay").assertIsDisplayed()

        // Contacts tab also renders both items safely
        composeTestRule.onNodeWithTag("add_call_tab_contacts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_call_search_input").assertIsDisplayed()
    }

    @Test
    fun lyraCallService_promotesToForegroundImmediatelyBeforeStopOrAsyncWork() {
        // 1. IDLE state: service started via startForegroundService MUST call startForeground synchronously before stopForeground/stopSelf
        CallStateManager.clearState()
        val idleController = Robolectric.buildService(LyraCallService::class.java).create()
        val idleService = idleController.get()
        idleService.onStartCommand(Intent(idleService, LyraCallService::class.java), 0, 1)
        val idleShadow = Shadows.shadowOf(idleService)
        assertTrue(
            "LyraCallService must promote then stopForeground when IDLE to prevent ForegroundServiceDidNotStartInTimeException",
            idleShadow.lastForegroundNotification != null || idleShadow.isForegroundStopped
        )
        assertTrue(idleShadow.isStoppedBySelf)
        idleController.destroy()

        // 2. RINGING state: service must call startForeground synchronously on onStartCommand BEFORE async DataStore reads complete
        val caller = CallerInfo(
            phoneNumber = "+919876543210",
            formattedNumber = "+91 98765 43210",
            displayName = "Ajay",
            isKnown = true
        )
        CallStateManager.onIncomingCall(caller)
        val ringController = Robolectric.buildService(LyraCallService::class.java).create()
        val ringService = ringController.get()
        ringService.onStartCommand(Intent(ringService, LyraCallService::class.java), 0, 1)
        assertNotNull(
            "LyraCallService must invoke startForeground synchronously when RINGING",
            Shadows.shadowOf(ringService).lastForegroundNotification
        )
        ShadowLooper.idleMainLooper()
        ringController.destroy()
        CallStateManager.clearState()
    }

    @Test
    fun callActivity_lifecycleAcrossRingingActiveAndDisconnectedDoesNotCrash() {
        val caller = CallerInfo(
            phoneNumber = "+919876543210",
            formattedNumber = "+91 98765 43210",
            displayName = "Ajay",
            isKnown = true
        )
        CallStateManager.onIncomingCall(caller, isSimulated = true)
        val controller = Robolectric.buildActivity(
            CallActivity::class.java,
            Intent("ACTION_INCOMING_CALL")
        ).setup()
        ShadowLooper.idleMainLooper()

        CallStateManager.onCallAnswered()
        composeTestRule.waitForIdle()
        ShadowLooper.idleMainLooper()

        CallStateManager.onCallEnded()
        composeTestRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        val finalState = CallStateManager.callState.value
        assertTrue(
            "Expected DISCONNECTED or IDLE after call end, got $finalState",
            finalState == CallStateEnum.IDLE || finalState == CallStateEnum.DISCONNECTED
        )
        controller.pause().stop().destroy()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun quickSmsService_handlesNullIntentAndStartSendSmsSafely() {
        val serviceController = Robolectric.buildService(QuickSmsService::class.java).create()
        val service = serviceController.get()
        service.onStartCommand(null, 0, 1)
        ShadowLooper.idleMainLooper()
        val shadow = Shadows.shadowOf(service)
        assertTrue(shadow.lastForegroundNotification != null || shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
        serviceController.destroy()

        val app = ApplicationProvider.getApplicationContext<Application>()
        QuickSmsService.startSendSms(app, "+919811122334", "Calling back shortly")
        val started = Shadows.shadowOf(app).nextStartedService
        assertNotNull(started)
    }

    @Test
    fun version20_buildConfigAndAboutScreenMatch() {
        assertEquals("2.0", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onBackClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2.0 (Production Release)").assertIsDisplayed()
    }

    @Test
    fun commercialTelemarketingPrefixMatcher_blocksSeries14And18AndAllowsMobile6To9() {
        assertTrue(PhoneNumberUtils.isCommercialTelemarketingNumber("1408800123"))
        assertTrue(PhoneNumberUtils.isCommercialTelemarketingNumber("+91 1409990011"))
        assertTrue(PhoneNumberUtils.isCommercialTelemarketingNumber("18001234567"))
        assertTrue(PhoneNumberUtils.isCommercialTelemarketingNumber("01800-123-456"))
        assertTrue(PhoneNumberUtils.isCommercialTelemarketingNumber("1412345678"))
        assertTrue(PhoneNumberUtils.isCommercialTelemarketingNumber("1812345678"))

        assertFalse(PhoneNumberUtils.isCommercialTelemarketingNumber("+91 98765 43210"))
        assertFalse(PhoneNumberUtils.isCommercialTelemarketingNumber("8800112233"))
        assertFalse(PhoneNumberUtils.isCommercialTelemarketingNumber("7011223344"))
        assertFalse(PhoneNumberUtils.isCommercialTelemarketingNumber("6123456789"))
        assertFalse(PhoneNumberUtils.isCommercialTelemarketingNumber("09876543210"))
    }

    @Test
    fun evaluateCallScreeningDecision_respectsSwitchesAndProtectsSavedContacts() {
        val unknownCaller = CallerInfo(
            phoneNumber = "1408800123",
            formattedNumber = "1408800123",
            displayName = "Unknown",
            isKnown = false,
            isPriority = false,
            isOwner = false
        )
        val savedCommercialLookalike = unknownCaller.copy(isKnown = true, displayName = "Bank Helpline")
        val ownerCaller = CallerInfo(
            phoneNumber = "+919876543210",
            formattedNumber = "+91 98765 43210",
            displayName = "Ajay",
            isKnown = true,
            isPriority = true,
            isOwner = true
        )
        val unknownMobile = CallerInfo(
            phoneNumber = "+919811122334",
            formattedNumber = "+91 98111 22334",
            displayName = "Unsaved Personal",
            isKnown = false
        )

        // With switches OFF, no unexpected auto-rejections occur
        assertFalse(
            evaluateCallScreeningDecision(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = false,
                phoneNumber = "1408800123",
                caller = unknownCaller
            ).shouldBlock
        )

        // Commercial blocker blocks 140/1800 unsaved calls while allowing unsaved 6-9 personal numbers
        assertTrue(
            evaluateCallScreeningDecision(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = true,
                phoneNumber = "1408800123",
                caller = unknownCaller
            ).shouldBlock
        )
        assertFalse(
            evaluateCallScreeningDecision(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = true,
                phoneNumber = "+919811122334",
                caller = unknownMobile
            ).shouldBlock
        )

        // Unknown Call Blocker blocks unsaved mobile numbers
        assertTrue(
            evaluateCallScreeningDecision(
                isLyraEnabled = true,
                blockUnknownCalls = true,
                blockCommercialPrefixes = false,
                phoneNumber = "+919811122334",
                caller = unknownMobile
            ).shouldBlock
        )

        // Saved, Owner, and Priority contacts are never auto-rejected even with both blockers ON
        assertFalse(
            evaluateCallScreeningDecision(
                isLyraEnabled = true,
                blockUnknownCalls = true,
                blockCommercialPrefixes = true,
                phoneNumber = "1408800123",
                caller = savedCommercialLookalike
            ).shouldBlock
        )
        assertFalse(
            evaluateCallScreeningDecision(
                isLyraEnabled = true,
                blockUnknownCalls = true,
                blockCommercialPrefixes = true,
                phoneNumber = "+919876543210",
                caller = ownerCaller
            ).shouldBlock
        )
    }

    @Test
    fun settingsScreen_displaysBlockerSwitchesThemesAndV2AndLocalOnlyGuardsCloudBackup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
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

        // Verify iOS Glass & Aurora Prism theme options are displayed
        composeTestRule.onNodeWithTag("app_theme_card_IOS_GLASS").assertIsDisplayed()

        // Scroll to Call Screening & Blocker switches and toggle both
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasText("Unknown Call Blocker"))
        composeTestRule.waitForIdle()

        assertFalse(viewModel.blockUnknownCalls.value)
        composeTestRule.onNodeWithTag("setting_block_unknown_calls").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.blockUnknownCalls.value
        }

        assertFalse(viewModel.blockCommercialPrefixes.value)
        composeTestRule.onNodeWithTag("setting_block_commercial_prefixes").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.blockCommercialPrefixes.value
        }

        // Scroll to About section and verify v2.0 label
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasText("Lyra Calling Assistant v2.0"))
        composeTestRule.onNodeWithText("Lyra Calling Assistant v2.0").assertIsDisplayed()

        // Local-Only Operation Mode prevents unintended cloud backup/restore
        viewModel.setLocalOnlyMode(true)
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.isLocalOnlyMode.value
        }
        viewModel.backupContactsToCloud()
        ShadowLooper.idleMainLooper()
        assertTrue(viewModel.backupStatus.value?.contains("Local-Only", ignoreCase = true) == true)
    }

    @Test
    fun callScreens_containNoEmergencyButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        CallStateManager.onIncomingCall(
            CallerInfo(
                phoneNumber = "+919876543210",
                formattedNumber = "+91 98765 43210",
                displayName = "Ajay",
                isKnown = true,
                isOwner = true
            ),
            isSimulated = true
        )
        var showActive by mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                if (!showActive) {
                    IncomingCallScreen(
                        viewModel = viewModel,
                        onAcceptCall = {},
                        onDeclineCall = {}
                    )
                } else {
                    ActiveCallScreen(
                        viewModel = viewModel,
                        onEndCall = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Emergency", ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("SOS", ignoreCase = true).assertDoesNotExist()

        CallStateManager.onCallAnswered()
        composeTestRule.runOnIdle { showActive = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Emergency", ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("SOS", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun buildDialerMatchingSuggestions_scalesCleanlyForLargeDatasets() {
        val largeContacts = (1..1500).map { index ->
            ContactItemData(
                id = "c_$index",
                name = "Contact $index",
                phoneNumber = "+9198${index.toString().padStart(8, '0')}",
                label = "Mobile",
                isPriority = index <= 10
            )
        }
        val largeRecents = (1..500).map { index ->
            CallHistoryItemData(
                id = "r_$index",
                contactName = "Contact $index",
                phoneNumber = "+9198${index.toString().padStart(8, '0')}",
                callType = CallTypeEnum.INCOMING,
                timestamp = 1_700_000_000_000L - index * 1000L,
                durationSeconds = 30,
                formattedDuration = "00:30",
                formattedDate = "Today"
            )
        }

        val startNs = System.nanoTime()
        val blankSuggestions = buildDialerMatchingSuggestions("", largeContacts, largeRecents)
        val typedSuggestions = buildDialerMatchingSuggestions("98000012", largeContacts, largeRecents)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        assertEquals(1500, blankSuggestions.size)
        assertTrue(typedSuggestions.isNotEmpty())
        assertTrue("Expected fast dialer suggestion computation (< 500ms), took ${elapsedMs}ms", elapsedMs < 500L)
    }
}
