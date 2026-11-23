package com.example

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.ToneGenerator
import android.net.Uri
import android.provider.CallLog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.service.CallStateManager
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.buildDialerSuggestionsWithFrequent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.ui.viewmodel.computeCurrentMonthKey
import com.example.ui.viewmodel.computeCurrentMonthStartMillis
import com.example.ui.viewmodel.computeHomeStats
import com.example.ui.viewmodel.computeMostFrequentContacts
import com.example.utils.DialpadAudioFeedbackManager
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class LyraAddendumFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun calMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun computeMostFrequentContacts_capsAt20_ranksByCallsAndTalkTime_andResetsMonthlyOn1stAt0000() {
        val now = calMillis(2026, Calendar.NOVEMBER, 15, 10, 30)
        val monthStart = computeCurrentMonthStartMillis(now)
        assertEquals("2026-11", computeCurrentMonthKey(now))
        assertEquals(calMillis(2026, Calendar.NOVEMBER, 1, 0, 0), monthStart)

        val logs = mutableListOf<CallHistoryItemData>()
        // Demo data must ALWAYS be excluded
        logs += CallHistoryItemData(
            id = "demo_1",
            phoneNumber = "+91 90000 99999",
            contactName = "Demo Caller",
            callType = CallTypeEnum.OUTGOING,
            timestamp = now - 1000L,
            durationSeconds = 999L,
            formattedDuration = "16m",
            formattedDate = "Today",
            isDemoData = true
        )

        // Previous month call (1 millisecond before 1st at 00:00) -> excluded when cycleStartMillis = monthStart
        logs += CallHistoryItemData(
            id = "prev_month_1",
            phoneNumber = "+91 98000 00099",
            contactName = "Last Month Star",
            callType = CallTypeEnum.INCOMING,
            timestamp = monthStart - 1L,
            durationSeconds = 5000L,
            formattedDuration = "1h 23m",
            formattedDate = "Last Month",
            isDemoData = false
        )

        // 25 unique connected real callers in current month
        for (i in 1..25) {
            val num = "+91 98100 %05d".format(i)
            val callCount = if (i <= 2) 3 else if (i <= 5) 2 else 1
            // Caller #2 has higher talk time than Caller #1 for tie-breaker check (3 calls each)
            val durationEach = if (i == 2) 300L else if (i == 1) 120L else (50L + i)
            repeat(callCount) { cIdx ->
                logs += CallHistoryItemData(
                    id = "real_${i}_$cIdx",
                    phoneNumber = num,
                    contactName = "Caller $i",
                    callType = CallTypeEnum.OUTGOING,
                    timestamp = monthStart + (i * 10_000L) + cIdx,
                    durationSeconds = durationEach,
                    formattedDuration = "${durationEach}s",
                    formattedDate = "This Month",
                    isDemoData = false
                )
            }
        }

        val monthlyTop20 = computeMostFrequentContacts(
            callLogs = logs,
            contacts = emptyList(),
            limit = 20,
            cycleStartMillis = monthStart
        )
        assertEquals(20, monthlyTop20.size)
        assertTrue(monthlyTop20.none { it.displayName == "Demo Caller" })
        assertTrue(monthlyTop20.none { it.displayName == "Last Month Star" })
        // Tie-breaker between Caller 2 (3 calls, 900s) and Caller 1 (3 calls, 360s)
        assertEquals("Caller 2", monthlyTop20[0].displayName)
        assertEquals(3, monthlyTop20[0].connectedCallCount)
        assertEquals(900L, monthlyTop20[0].totalDurationSeconds)
        assertEquals("Caller 1", monthlyTop20[1].displayName)

        // When monthly auto-reset is OFF (cycleStartMillis = null), previous month real calls count too, still capped at 20
        val cumulativeTop20 = computeMostFrequentContacts(
            callLogs = logs,
            contacts = emptyList(),
            limit = 20,
            cycleStartMillis = null
        )
        assertEquals(20, cumulativeTop20.size)
        assertTrue(cumulativeTop20.none { it.displayName == "Demo Caller" })
        assertTrue(cumulativeTop20.any { it.displayName == "Last Month Star" })
    }

    @Test
    fun computeHomeStats_excludesDemoData_andRespectsMonthlyAutoReset() {
        val now = calMillis(2026, Calendar.NOVEMBER, 15, 10, 30)
        val monthStart = computeCurrentMonthStartMillis(now)

        val contacts = listOf(
            ContactItemData("d1", "Demo VIP", "+91 11111 11111", isDemoData = true),
            ContactItemData("r1", "Real Friend", "+91 98111 22334", isDemoData = false)
        )
        val logs = listOf(
            CallHistoryItemData("demo_call", "+91 11111 11111", "Demo VIP", callType = CallTypeEnum.MISSED, timestamp = now, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Today", isDemoData = true),
            CallHistoryItemData("old_call", "+91 99999 88888", null, callType = CallTypeEnum.MISSED, timestamp = monthStart - 10_000L, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Oct", isDemoData = false),
            CallHistoryItemData("cur_call_1", "+91 98111 22334", "Real Friend", callType = CallTypeEnum.INCOMING, timestamp = monthStart + 5_000L, durationSeconds = 65L, formattedDuration = "1m 5s", formattedDate = "Nov", isDemoData = false),
            CallHistoryItemData("cur_call_2", "+91 97777 66666", "Unknown Caller", callType = CallTypeEnum.MISSED, timestamp = now - 1_000L, durationSeconds = 0L, formattedDuration = "0s", formattedDate = "Today", isDemoData = false)
        )

        val monthlyStats = computeHomeStats(logs, contacts, monthlyAutoResetEnabled = true, nowMillis = now)
        assertEquals(2, monthlyStats.callsToday)
        assertEquals(1, monthlyStats.missedCalls)
        assertEquals(1, monthlyStats.knownCallers)
        assertEquals(1, monthlyStats.unknownCallers)

        val cumulativeStats = computeHomeStats(logs, contacts, monthlyAutoResetEnabled = false, nowMillis = now)
        assertEquals(3, cumulativeStats.callsToday)
        assertEquals(2, cumulativeStats.missedCalls)
        assertEquals(1, cumulativeStats.knownCallers)
        assertEquals(2, cumulativeStats.unknownCallers)
    }

    @Test
    fun dialpadAudioFeedbackManager_mapsDigitsAndSymbols_andHandlesFastTypingWithoutBlocking() {
        assertEquals(ToneGenerator.TONE_DTMF_0, DialpadAudioFeedbackManager.mapDigitToDtmfTone("0"))
        assertEquals(ToneGenerator.TONE_DTMF_9, DialpadAudioFeedbackManager.mapDigitToDtmfTone("9"))
        assertEquals(ToneGenerator.TONE_DTMF_S, DialpadAudioFeedbackManager.mapDigitToDtmfTone("*"))
        assertEquals(ToneGenerator.TONE_DTMF_P, DialpadAudioFeedbackManager.mapDigitToDtmfTone("#"))
        assertEquals(ToneGenerator.TONE_DTMF_0, DialpadAudioFeedbackManager.mapDigitToDtmfTone("+"))
        assertNull(DialpadAudioFeedbackManager.mapDigitToDtmfTone("A"))

        assertEquals("5", DialpadAudioFeedbackManager.mapDigitToSpokenWord("5"))
        assertEquals("star", DialpadAudioFeedbackManager.mapDigitToSpokenWord("*"))
        assertEquals("hash", DialpadAudioFeedbackManager.mapDigitToSpokenWord("#"))
        assertEquals("plus", DialpadAudioFeedbackManager.mapDigitToSpokenWord("+"))
        assertNull(DialpadAudioFeedbackManager.mapDigitToSpokenWord("x"))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = DialpadAudioFeedbackManager(app)
        val startNs = System.nanoTime()
        repeat(20) { idx ->
            manager.playDigitFeedback(digit = (idx % 10).toString(), playTone = true, speakDigit = true)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        // Async single-executor dispatch must return immediately with zero UI lag
        assertTrue("Expected fast non-blocking dispatch (< 250ms), was ${elapsedMs}ms", elapsedMs < 250L)
        manager.release()
    }

    @Test
    fun settingsScreen_exposesMostFrequentMonthlyResetAndKeypadAudioToggles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        runBlocking { viewModel.autoReplyRepository.ensureSeeded() }

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

        // Most Frequent ON by default
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_most_frequent_contacts"))
        composeTestRule.onNodeWithTag("toggle_most_frequent_contacts").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_Most Frequent Connected Contacts").assertIsOn()

        // Monthly Auto-Reset ON by default -> toggle OFF
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_monthly_auto_reset"))
        composeTestRule.onNodeWithTag("toggle_monthly_auto_reset").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_Monthly Counter & Dashboard Auto-Reset").assertIsOn()
        composeTestRule.onNodeWithTag("switch_Monthly Counter & Dashboard Auto-Reset").performClick()
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            !viewModel.monthlyStatsAutoResetEnabled.value
        }
        composeTestRule.onNodeWithTag("switch_Monthly Counter & Dashboard Auto-Reset").assertIsOff()

        // Spoken Number Voice OFF by default -> toggle ON
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_keypad_voice_announcement"))
        composeTestRule.onNodeWithTag("toggle_keypad_voice_announcement").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_Keypad Spoken Number Voice").assertIsOff()
        composeTestRule.onNodeWithTag("switch_Keypad Spoken Number Voice").performClick()
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.keypadVoiceAnnouncementEnabled.value
        }
        composeTestRule.onNodeWithTag("switch_Keypad Spoken Number Voice").assertIsOn()

        // Keypad Button Press Sounds ON by default -> toggle OFF
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_keypad_tone_sound"))
        composeTestRule.onNodeWithTag("toggle_keypad_tone_sound").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_Keypad Button Press Sounds").assertIsOn()
        composeTestRule.onNodeWithTag("switch_Keypad Button Press Sounds").performClick()
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            !viewModel.keypadToneEnabled.value
        }
        composeTestRule.onNodeWithTag("switch_Keypad Button Press Sounds").assertIsOff()
    }

    @Test
    fun dialerScreen_displaysMostFrequentTop20_andKeepsCompactCardHeightWhenCollapsed() {
        CallStateManager.clearState()
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)

        val now = System.currentTimeMillis()
        val fakeProvider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor {
                val cursor = MatrixCursor(
                    arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION
                    )
                )
                for (i in 1..24) {
                    val count = if (i == 1) 4 else 1
                    repeat(count) { c ->
                        cursor.addRow(
                            arrayOf<Any?>(
                                "log_${i}_$c",
                                "+91 98200 %05d".format(i),
                                "Frequent Person $i",
                                CallLog.Calls.OUTGOING_TYPE,
                                now - (i * 1000L) - (c * 60_000L),
                                60L + i
                            )
                        )
                    }
                }
                return cursor
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, fakeProvider)

        val viewModel = LyraMainViewModel(app)
        viewModel.setShowMostFrequentContacts(true)
        viewModel.loadData()

        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.callLog.value.size < 24 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
        ShadowLooper.idleMainLooper()
        assertTrue(viewModel.callLog.value.size >= 24)

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
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.mostFrequentContacts.value.size == 20
        }
        composeTestRule.waitForIdle()
        assertEquals(20, viewModel.mostFrequentContacts.value.size)

        val built = buildDialerSuggestionsWithFrequent(
            dialedNumber = "",
            contacts = viewModel.contacts.value,
            callLog = viewModel.callLog.value,
            mostFrequentContacts = viewModel.mostFrequentContacts.value,
            showMostFrequentContacts = true,
            maxFrequentCount = 20
        )
        assertEquals(20, built.size)
        assertEquals("Frequent Person 1", built.first().displayName)
        assertEquals(1, built.first().frequentRank)

        val topCardBoundsOpen = composeTestRule.onNodeWithTag("dialer_top_suggestion_card").getBoundsInRoot()
        val openHeight = topCardBoundsOpen.bottom - topCardBoundsOpen.top

        // Collapse keypad via bottom Keypad nav toggle
        composeTestRule.onNodeWithTag("nav_keypad").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialer_most_frequent_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Most Frequent (20/20)").assertIsDisplayed()

        val firstExpandedTag = "dialer_expanded_item_${built.first().id}"
        composeTestRule.onNodeWithTag(firstExpandedTag).assertIsDisplayed()
        val expandedRowBounds = composeTestRule.onNodeWithTag(firstExpandedTag).getBoundsInRoot()
        val expandedHeight = expandedRowBounds.bottom - expandedRowBounds.top

        // Expanded contact list item must remain compact (~62dp, matching Recents/Dialpad card height, never growing tall)
        assertTrue("Expected compact expanded card height <= 66.dp, was $expandedHeight", expandedHeight <= 66.dp)
        assertTrue(
            "Expected collapsed item height within 8.dp of open card height ($openHeight vs $expandedHeight)",
            kotlin.math.abs((openHeight - expandedHeight).value) <= 8f
        )
    }

    @Test
    fun homeScreen_omitsLyraOnlineIndicator_andShowsLiveCycleLabel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()
        ShadowLooper.idleMainLooper()

        composeTestRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDialer = {},
                    onNavigateToHistory = {},
                    onNavigateToContacts = {},
                    onNavigateToSettings = {},
                    onNavigateToPermissions = {},
                    onNavigateToAbout = {},
                    onNavigateToIncomingCall = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("● Lyra Online").assertDoesNotExist()
        composeTestRule.onNodeWithText("○ Standby").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_stats_cycle_label").assertIsDisplayed()
    }

    @Test
    fun callHistoryScreen_omitsDummyCallCountHeaderBadge() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()
        ShadowLooper.idleMainLooper()

        composeTestRule.setContent {
            MyApplicationTheme {
                CallHistoryScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onNavigateTab = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("600 Calls").assertDoesNotExist()
        composeTestRule.onNodeWithText("0 Calls").assertDoesNotExist()
        composeTestRule.onNodeWithText("5 Calls").assertDoesNotExist()
    }

    @Test
    fun contactsScreen_omitsCallerIdentificationTestDropdownAndButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()
        ShadowLooper.idleMainLooper()

        composeTestRule.setContent {
            MyApplicationTheme {
                ContactsScreen(
                    mainViewModel = viewModel,
                    onContactClick = {},
                    onBackClick = {},
                    onNavigateTab = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_caller_id_tester").assertDoesNotExist()
        composeTestRule.onNodeWithText("Caller Identification Test").assertDoesNotExist()
    }
}
