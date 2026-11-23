package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.CustomFeatureSettings
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.AutoReplyPresets
import com.example.data.repository.AutoReplyRepository
import com.example.data.repository.CallRecordingRepository
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.service.CallAutoReplyCoordinator
import com.example.service.CallStateManager
import com.example.service.shouldBypassSlowScreeningLookup
import com.example.ui.components.ContactProfileSheet
import com.example.ui.screens.CallRecordingsScreen
import com.example.ui.screens.ContactDetailsScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.ui.viewmodel.computeMostFrequentContacts
import com.example.utils.ExternalIntentHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class LyraCustomFeaturesAndSecurityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun autoReplyRepository_seeds24Modes_andPersistsCustomSmsAcrossReseed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, LyraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = AutoReplyRepository(db.autoReplyModeDao(), db.customFeatureSettingsDao())
            repo.ensureSeeded()

            val modes = repo.getAllModesOnce()
            assertEquals(24, modes.size)
            assertEquals(24, AutoReplyPresets.defaultModes().size)

            val initialSettings = repo.getSettingsOnce()
            assertFalse(initialSettings.autoReplyMasterEnabled)
            assertFalse(initialSettings.accessibilityAutomationEnabled)
            assertTrue(initialSettings.showLyraLogoOnIncoming)
            assertTrue(initialSettings.showMostFrequentContacts)
            assertTrue(initialSettings.ussdDirectHandlerEnabled)

            repo.saveCustomMessage("driving", "In transit, calling back at 6 PM.")
            repo.setMasterEnabled(enabled = true, selectedModeId = "driving")
            repo.updateFeatureSettings {
                it.copy(showLyraLogoOnIncoming = false, accessibilityAutomationEnabled = true)
            }

            // Re-running seed (simulating app restart) must NOT overwrite user-edited messages or toggles
            repo.ensureSeeded()

            val active = repo.getActiveAutoReplyModeIfEnabled()
            assertNotNull(active)
            assertEquals("driving", active?.modeId)
            assertEquals("In transit, calling back at 6 PM.", active?.customMessage)

            val reloadedSettings = repo.getSettingsOnce()
            assertTrue(reloadedSettings.autoReplyMasterEnabled)
            assertFalse(reloadedSettings.showLyraLogoOnIncoming)
            assertTrue(reloadedSettings.accessibilityAutomationEnabled)
        } finally {
            db.close()
        }
    }

    @Test
    fun callAutoReplyCoordinator_evaluatesActiveModeAndRespectsDedupWindow() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LyraDatabase.getDatabase(app)
        val repo = AutoReplyRepository(db.autoReplyModeDao(), db.customFeatureSettingsDao())
        val prefs = LyraPreferences(app)

        CallAutoReplyCoordinator.clearDedupForTesting()
        prefs.setLyraEnabled(true)
        repo.ensureSeeded()
        repo.saveCustomMessage("sleeping", "Sleeping now, text me if urgent.")
        repo.setMasterEnabled(enabled = true, selectedModeId = "sleeping")
        prefs.setAutoReplyState(masterEnabled = true, activeModeId = "sleeping")

        assertFalse(
            shouldBypassSlowScreeningLookup(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = false,
                phoneNumber = "+919876543210",
                hasActiveAutoReplyMode = true
            )
        )

        val firstDecision = CallAutoReplyCoordinator.evaluateAndExecuteIfActive(
            context = app,
            incomingPhoneNumber = "+91 98765 43210",
            nowMillis = 100_000L
        )
        assertTrue(firstDecision.shouldCutCall)
        assertTrue(firstDecision.modeTitle?.contains("Sleeping") == true)
        assertEquals("Sleeping now, text me if urgent.", firstDecision.smsText)

        // Within 15s dedup window -> call still rejected, no duplicate SMS attempt
        val secondDecision = CallAutoReplyCoordinator.evaluateAndExecuteIfActive(
            context = app,
            incomingPhoneNumber = "+919876543210",
            nowMillis = 105_000L
        )
        assertTrue(secondDecision.shouldCutCall)

        // Disable master switch -> call must no longer be auto-cut
        repo.setMasterEnabled(enabled = false, selectedModeId = "sleeping")
        prefs.setAutoReplyState(masterEnabled = false, activeModeId = "sleeping")
        val disabledDecision = CallAutoReplyCoordinator.evaluateAndExecuteIfActive(
            context = app,
            incomingPhoneNumber = "+919876543210",
            nowMillis = 120_000L
        )
        assertFalse(disabledDecision.shouldCutCall)
    }

    @Test
    fun computeMostFrequentContacts_includesOnlyConnectedNonZeroDurationCalls() {
        val contacts = listOf(
            ContactItemData(id = "1", name = "Priya", phoneNumber = "+91 98111 22222", isPriority = true),
            ContactItemData(id = "2", name = "Rohan", phoneNumber = "+91 98222 33333")
        )
        val logs = listOf(
            // 3 missed / 0s calls from Rohan must NEVER count toward connected frequency
            CallHistoryItemData("m1", "+91 98222 33333", "Rohan", callType = CallTypeEnum.MISSED, timestamp = 1000L, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Today"),
            CallHistoryItemData("m2", "+91 98222 33333", "Rohan", callType = CallTypeEnum.REJECTED, timestamp = 2000L, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Today"),
            CallHistoryItemData("m3", "+91 98222 33333", "Rohan", callType = CallTypeEnum.INCOMING, timestamp = 3000L, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Today"),
            // 1 connected outgoing call for Rohan
            CallHistoryItemData("c1", "+91 98222 33333", "Rohan", callType = CallTypeEnum.OUTGOING, timestamp = 4000L, durationSeconds = 45L, formattedDuration = "45s", formattedDate = "Today"),
            // 2 connected calls for Priya
            CallHistoryItemData("c2", "+91 98111 22222", "Priya", callType = CallTypeEnum.INCOMING, timestamp = 5000L, durationSeconds = 120L, formattedDuration = "2m 0s", formattedDate = "Today"),
            CallHistoryItemData("c3", "+91 98111 22222", "Priya", callType = CallTypeEnum.OUTGOING, timestamp = 6000L, durationSeconds = 80L, formattedDuration = "1m 20s", formattedDate = "Today"),
            // Missed-only caller never appears
            CallHistoryItemData("m4", "+91 99999 00000", "Spammer", callType = CallTypeEnum.MISSED, timestamp = 7000L, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Today")
        )

        val frequent = computeMostFrequentContacts(logs, contacts)
        assertEquals(2, frequent.size)
        assertEquals("Priya", frequent[0].displayName)
        assertEquals(2, frequent[0].connectedCallCount)
        assertEquals(200L, frequent[0].totalDurationSeconds)

        assertEquals("Rohan", frequent[1].displayName)
        assertEquals(1, frequent[1].connectedCallCount)
        assertEquals(45L, frequent[1].totalDurationSeconds)
    }

    @Test
    fun externalIntentHelper_identifiesUssdAndFormatsWhatsAppDigits() {
        assertTrue(ExternalIntentHelper.isUssdCode("*121#"))
        assertTrue(ExternalIntentHelper.isUssdCode("*#06#"))
        assertTrue(ExternalIntentHelper.isUssdCode("#31#"))
        assertFalse(ExternalIntentHelper.isUssdCode("+919876543210"))
        assertFalse(ExternalIntentHelper.isUssdCode("*#"))

        assertEquals("919876543210", ExternalIntentHelper.formatWhatsAppDigits("9876543210"))
        assertEquals("919876543210", ExternalIntentHelper.formatWhatsAppDigits("09876543210"))
        assertEquals("14155550199", ExternalIntentHelper.formatWhatsAppDigits("+1 (415) 555-0199"))
    }

    @Test
    fun callRecordingRepository_disablesNativeRecordingOnAndroid15Plus() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = CallRecordingRepository(LyraDatabase.getDatabase(app).callRecordingDao())

        val cap35 = repo.getCapability(sdkInt = 35)
        assertFalse(cap35.isSupported)
        assertTrue(cap35.notice.contains("Android 15+"))

        val cap36 = repo.getCapability(sdkInt = 36)
        assertFalse(cap36.isSupported)

        val cap34 = repo.getCapability(sdkInt = 34)
        assertTrue(cap34.isSupported)
    }

    @Test
    fun incomingCallScreen_rendersElevatedCallerCard_togglesLogo_andHasNoEmergencyButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        CallStateManager.onIncomingCall(
            CallerInfo(
                phoneNumber = "+91 98765 43210",
                formattedNumber = "+91 98765 43210",
                displayName = "Ajay",
                isKnown = true,
                isPriority = true,
                isOwner = true,
                contactLabel = "Owner"
            )
        )
        viewModel.setShowLyraLogoOnIncoming(true)

        assertTrue(AppThemeStyle.entries.size >= 10)
        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.AURORA_PRISM) {
                IncomingCallScreen(
                    viewModel = viewModel,
                    onAcceptCall = {},
                    onDeclineCall = {}
                )
            }
        }
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.showLyraLogoOnIncoming.value
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("incoming_caller_identity_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("incoming_lyra_logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emergency").assertDoesNotExist()

        viewModel.setShowLyraLogoOnIncoming(false)
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            !viewModel.showLyraLogoOnIncoming.value
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("incoming_lyra_logo").assertDoesNotExist()
        CallStateManager.clearState()
    }

    @Test
    fun contactDetailsAndProfileSheet_exposeWhatsAppActionButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        val contact = ContactItemData(
            id = "c_wa",
            name = "Meera Kapoor",
            phoneNumber = "+91 98100 11223",
            label = "Mobile"
        )
        viewModel.selectContact(contact)

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.SUNSET_EMBER) {
                ContactDetailsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("contact_whatsapp_btn").assertIsDisplayed()
    }

    @Test
    fun contactProfileSheet_rendersWhatsAppButtonAndHandlesClick() {
        var clicked = false
        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.ROSE_GOLD_LUXE) {
                ContactProfileSheet(
                    name = "Kabir",
                    phoneNumber = "+91 98765 00112",
                    photoUri = null,
                    label = "Mobile",
                    isOwner = false,
                    isPriority = false,
                    onCallClick = {},
                    onSmsClick = {},
                    onQuickAutoSmsClick = {},
                    onAddNoteClick = {},
                    onEditClick = {},
                    onWhatsAppClick = { clicked = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_sheet_whatsapp_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_sheet_whatsapp_btn").performClick()
        assertTrue(clicked)
    }

    @Test
    fun callRecordingsScreen_rendersUnifiedTopBarAndAndroid15RestrictionNotice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.EMERALD_MATRIX) {
                CallRecordingsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("call_recordings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_bar_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recordings_capability_notice").assertIsDisplayed()
    }

    @Test
    fun permissionsScreen_rendersDeviceAdminAndOptionalAccessibilityToggleOffByDefault() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.IOS_GLASS) {
                PermissionsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("permissions_screen")
            .performScrollToNode(hasTestTag("device_admin_permission_card"))
        composeTestRule.onNodeWithTag("device_admin_permission_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("permissions_screen")
            .performScrollToNode(hasTestTag("accessibility_service_permission_card"))
        composeTestRule.onNodeWithTag("accessibility_service_permission_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("permissions_accessibility_toggle").assertIsOff()
    }

    @Test
    fun settingsScreen_rendersAutoReply24ModesAndCustomFeaturesSection() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        runBlocking {
            viewModel.autoReplyRepository.ensureSeeded()
        }

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.IOS_GLASS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onNavigateToAbout = {},
                    onNavigateToPermissions = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand Auto-Reply Status Modes module
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_auto_reply_modes"))
        composeTestRule.onNodeWithTag("settings_module_header_auto_reply_modes").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("auto_reply_master_switch"))
        composeTestRule.onNodeWithTag("auto_reply_master_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_reply_mode_dropdown").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_reply_message_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_reply_message_input").performTextReplacement("Custom busy message!")
        composeTestRule.onNodeWithTag("auto_reply_save_btn").performClick()
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.autoReplyModes.value.any { it.customMessage == "Custom busy message!" }
        }

        // Expand Custom Features & Security module
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_custom_features"))
        composeTestRule.onNodeWithTag("settings_module_header_custom_features").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_show_lyra_logo"))
        composeTestRule.onNodeWithTag("toggle_show_lyra_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_most_frequent_contacts").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_ussd_handler").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_accessibility_automation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_Optional Accessibility Automation").assertIsOff()
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_device_admin_action"))
        composeTestRule.onNodeWithTag("settings_device_admin_action").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_android15_recording_note").assertIsDisplayed()
    }
}
