package com.example

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.service.CallStateManager
import com.example.ui.components.ContactProfileSheet
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.buildDialerMatchingSuggestions
import com.example.ui.screens.buildRecentsDateGroups
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class RecentsAndDialerLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialerScreen_removesItsMeOwner_showsRecentSuggestionAndExpandsOnSwipeWithFixedPositions() {
        CallStateManager.clearState()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()

        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.callLog.value.isEmpty() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
        ShadowLooper.idleMainLooper()

        var activeCallNavCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DialerScreen(
                    viewModel = viewModel,
                    onNavigateTab = {},
                    onNavigateToActiveCall = { activeCallNavCount++ },
                    onNavigateToContactDetails = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. 'It's Me Owner' and 'Preview Outgoing Screen • Lyra AI' banner must be completely removed
        composeTestRule.onNodeWithTag("owner_label_chip").assertDoesNotExist()
        composeTestRule.onNodeWithText("It's Me Owner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dialer_ai_call_btn").assertDoesNotExist()
        composeTestRule.onNodeWithText("Preview Outgoing Screen • Lyra AI").assertDoesNotExist()

        // 2. Multiple suggestions fill the upper space seamlessly from SIM capsule down to the bottom-anchored number pad,
        //    with the bottommost card (~70-75% visible) softly blended beneath the number input lip.
        composeTestRule.onNodeWithTag("dialer_suggestion_area").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_top_suggestion_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_suggestion_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_bottom_faded_suggestion_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_suggestion_call_btn").assertIsDisplayed()

        // Capture initial fixed bounds before typing
        val initialHeaderBounds = composeTestRule.onNodeWithText("DIALPAD").getBoundsInRoot()
        val simCapsuleBounds = composeTestRule.onNodeWithTag("dialer_sim_capsule_container").getBoundsInRoot()
        val simAskBounds = composeTestRule.onNodeWithTag("sim_pill_ask").getBoundsInRoot()
        val firstCardBounds = composeTestRule.onNodeWithTag("dialer_top_suggestion_card").getBoundsInRoot()
        val bottomFadedCardBounds = composeTestRule.onNodeWithTag("dialer_bottom_faded_suggestion_card").getBoundsInRoot()
        val initialInputBounds = composeTestRule.onNodeWithTag("dialer_number_container").getBoundsInRoot()
        val keypadGridBounds = composeTestRule.onNodeWithTag("dialer_keypad_grid").getBoundsInRoot()
        val zeroKeyBounds = composeTestRule.onNodeWithTag("dial_key_0").getBoundsInRoot()
        val addContactBounds = composeTestRule.onNodeWithTag("dialer_add_contact_btn").getBoundsInRoot()
        val initialCallBounds = composeTestRule.onNodeWithTag("dialer_call_btn").getBoundsInRoot()
        val initialBackspaceBounds = composeTestRule.onNodeWithTag("dialer_backspace_btn").getBoundsInRoot()
        val initialBottomNavBounds = composeTestRule.onNodeWithTag("nav_keypad").getBoundsInRoot()

        // Compact SIM outer-to-inner padding & tight spacing between SIM capsule and top suggestion
        assertTrue((simAskBounds.top - simCapsuleBounds.top) <= 4.dp)
        assertTrue((firstCardBounds.top - simCapsuleBounds.bottom) <= 10.dp)

        // Upper suggestions span cleanly into the number pad without a blank middle gap; bottommost card ~70-75% visible
        assertTrue(firstCardBounds.bottom <= initialInputBounds.top)
        assertTrue(bottomFadedCardBounds.top < initialInputBounds.top)
        assertTrue(bottomFadedCardBounds.bottom > initialInputBounds.top)
        val visibleFraction =
            (initialInputBounds.top - bottomFadedCardBounds.top) / (bottomFadedCardBounds.bottom - bottomFadedCardBounds.top)
        assertTrue("Expected bottom faded card 68%..78% visible, was $visibleFraction", visibleFraction in 0.68f..0.78f)

        // Bottom-anchored number display, keypad, and call action row sit tightly near the bottom navigation bar
        assertTrue(initialInputBounds.bottom <= keypadGridBounds.top)
        assertTrue((keypadGridBounds.top - initialInputBounds.bottom) <= 14.dp)
        assertTrue(initialCallBounds.top >= zeroKeyBounds.bottom)
        assertTrue(initialCallBounds.bottom <= initialBottomNavBounds.top)
        val bottomGap = initialBottomNavBounds.top - initialCallBounds.bottom
        assertTrue("Expected call action row shifted close to bottom nav (<= 24.dp), was $bottomGap", bottomGap <= 24.dp)

        // Horizontal alignment across the 3 action row columns
        val addCenterY = (addContactBounds.top + addContactBounds.bottom) / 2f
        val callCenterY = (initialCallBounds.top + initialCallBounds.bottom) / 2f
        val backCenterY = (initialBackspaceBounds.top + initialBackspaceBounds.bottom) / 2f
        assertTrue(kotlin.math.abs((addCenterY - callCenterY).value) < 2f)
        assertTrue(kotlin.math.abs((backCenterY - callCenterY).value) < 2f)

        // 3. Type a full phone number on the keypad (matching Priya Sharma +91 98222 33445 -> 9822233445)
        "982223344599".forEach { ch ->
            composeTestRule.onNodeWithTag("dial_key_$ch").performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_number_display").assertTextContains("982223344599")
        composeTestRule.onNodeWithTag("dialer_ai_call_btn").assertDoesNotExist()

        // Verify fixed size and position for header, input container, call button, backspace, and bottom nav
        val afterHeaderBounds = composeTestRule.onNodeWithText("DIALPAD").getBoundsInRoot()
        val afterInputBounds = composeTestRule.onNodeWithTag("dialer_number_container").getBoundsInRoot()
        val afterCallBounds = composeTestRule.onNodeWithTag("dialer_call_btn").getBoundsInRoot()
        val afterBackspaceBounds = composeTestRule.onNodeWithTag("dialer_backspace_btn").getBoundsInRoot()
        val afterBottomNavBounds = composeTestRule.onNodeWithTag("nav_keypad").getBoundsInRoot()

        assertEquals(initialHeaderBounds.top, afterHeaderBounds.top)
        assertEquals(initialHeaderBounds.bottom, afterHeaderBounds.bottom)
        assertEquals(initialInputBounds.top, afterInputBounds.top)
        assertEquals(initialInputBounds.bottom, afterInputBounds.bottom)
        assertEquals(initialCallBounds.top, afterCallBounds.top)
        assertEquals(initialCallBounds.bottom, afterCallBounds.bottom)
        assertEquals(initialBackspaceBounds.top, afterBackspaceBounds.top)
        assertEquals(initialBackspaceBounds.bottom, afterBackspaceBounds.bottom)
        assertEquals(initialBottomNavBounds.top, afterBottomNavBounds.top)

        // Clear back to empty and swipe down on suggestion area -> keypad hides & expanded list appears
        composeTestRule.onNodeWithTag("dialer_clear_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_suggestion_area").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dialer_expanded_contacts_list").assertIsDisplayed()

        // Collapse via button and verify swipeUp also expands from suggestion area
        composeTestRule.onNodeWithTag("dialer_collapse_suggestions_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dialer_suggestion_area").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_keypad_grid").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dialer_expanded_contacts_list").assertIsDisplayed()
    }

    @Test
    fun buildDialerMatchingSuggestions_prioritizesBestSingleMatchAndRecentFallback() {
        val contacts = listOf(
            ContactItemData(id = "c1", name = "Ajay", phoneNumber = "+91 98765 43210", label = "Owner", isPriority = true, isOwner = true),
            ContactItemData(id = "c2", name = "Dr. Rohan Verma", phoneNumber = "+91 98111 22334", label = "Doctor", isPriority = true),
            ContactItemData(id = "c3", name = "Priya Sharma", phoneNumber = "+91 98222 33445", label = "VIP")
        )
        val recents = listOf(
            CallHistoryItemData(
                id = "r1",
                phoneNumber = "+91 98222 33445",
                contactName = "Priya Sharma",
                callType = CallTypeEnum.INCOMING,
                timestamp = System.currentTimeMillis() - 60_000L,
                durationSeconds = 42,
                formattedDuration = "42s",
                formattedDate = "Today"
            )
        )

        val emptySuggestions = buildDialerMatchingSuggestions("", contacts, recents)
        assertTrue(emptySuggestions.first().isRecentCall)
        assertEquals("Priya Sharma", emptySuggestions.first().displayName)

        val noRecentEmptySuggestions = buildDialerMatchingSuggestions("", contacts, emptyList())
        assertTrue(noRecentEmptySuggestions.none { it.isRecentCall })

        val typedMatches = buildDialerMatchingSuggestions("98111", contacts, recents)
        assertEquals(1, typedMatches.size)
        assertEquals("Dr. Rohan Verma", typedMatches.first().displayName)
    }

    @Test
    fun buildRecentsDateGroups_groupsCallsByDateAndCollapsesRepeatedSameDayCalls() {
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis

        val todayLater = now - 10 * 60_000L
        val todayEarlier = now - 90 * 60_000L
        val yesterdayTime = now - 24 * 60 * 60_000L
        val olderTime = now - 5 * 24 * 60 * 60_000L

        val calls = listOf(
            CallHistoryItemData("1", "+91 98111 22334", "Dr. Rohan", callType = CallTypeEnum.MISSED, timestamp = todayEarlier, durationSeconds = 0, formattedDuration = "Missed", formattedDate = "Today 12:30 PM"),
            CallHistoryItemData("2", "+919811122334", "Dr. Rohan", callType = CallTypeEnum.OUTGOING, timestamp = todayLater, durationSeconds = 30, formattedDuration = "30s", formattedDate = "Today 01:50 PM"),
            CallHistoryItemData("3", "+91 98222 33445", "Priya", callType = CallTypeEnum.INCOMING, timestamp = yesterdayTime, durationSeconds = 60, formattedDuration = "1m 0s", formattedDate = "Yesterday"),
            CallHistoryItemData("4", "+91 98111 22334", "Dr. Rohan", callType = CallTypeEnum.INCOMING, timestamp = olderTime, durationSeconds = 120, formattedDuration = "2m 0s", formattedDate = "5 days ago")
        )

        val groups = buildRecentsDateGroups(calls, nowMillis = now)
        assertEquals(3, groups.size)

        // Group 0: Today -> 2 calls for same number collapsed into 1 entry with callCount = 2, latest first ("2")
        assertEquals("Today", groups[0].dateLabel)
        assertEquals(2, groups[0].totalCallCount)
        assertEquals(1, groups[0].entries.size)
        assertEquals("2", groups[0].entries[0].representative.id)
        assertEquals(2, groups[0].entries[0].callCount)

        // Group 1: Yesterday -> 1 call
        assertEquals("Yesterday", groups[1].dateLabel)
        assertEquals(1, groups[1].entries.size)
        assertEquals(1, groups[1].entries[0].callCount)

        // Group 2: Older date -> same number on a different day stays in its own date section
        assertEquals(1, groups[2].entries.size)
        assertEquals("4", groups[2].entries[0].representative.id)
    }

    @Test
    fun contactProfileSheet_displaysFullChronologicalHistoryAndOmitsDeleteCallLogButton() {
        val now = System.currentTimeMillis()
        val olderSameNumber = CallHistoryItemData(
            id = "old_99",
            phoneNumber = "+91 98111 22334",
            contactName = "Dr. Rohan",
            callType = CallTypeEnum.INCOMING,
            timestamp = now - 40L * 24 * 60 * 60 * 1000L, // > 1 month old (no date restriction)
            durationSeconds = 90,
            formattedDuration = "1m 30s",
            formattedDate = "Older"
        )
        val newerSameNumber = CallHistoryItemData(
            id = "new_100",
            phoneNumber = "+91 98111 22334",
            contactName = "Dr. Rohan",
            callType = CallTypeEnum.OUTGOING,
            timestamp = now - 5 * 60 * 1000L,
            durationSeconds = 45,
            formattedDuration = "45s",
            formattedDate = "Today"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ContactProfileSheet(
                    name = "Dr. Rohan",
                    phoneNumber = "+91 98111 22334",
                    photoUri = null,
                    label = "Doctor",
                    isOwner = false,
                    isPriority = true,
                    callHistory = listOf(olderSameNumber, newerSameNumber),
                    onCallClick = {},
                    onSmsClick = {},
                    onQuickAutoSmsClick = {},
                    onAddNoteClick = {},
                    onEditClick = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_sheet_call_history_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("CALL HISTORY (2)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_history_row_new_100").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_history_row_old_99").assertIsDisplayed()

        // Chronological order: newer entry is positioned above older entry
        val newerBounds = composeTestRule.onNodeWithTag("profile_history_row_new_100").getBoundsInRoot()
        val olderBounds = composeTestRule.onNodeWithTag("profile_history_row_old_99").getBoundsInRoot()
        assertTrue(newerBounds.top < olderBounds.top)

        // Delete Call Log Entry button must not exist in ContactProfileSheet
        composeTestRule.onNodeWithText("Delete Call Log Entry").assertDoesNotExist()
        composeTestRule.onNodeWithTag("profile_sheet_delete_btn").assertDoesNotExist()
    }

    @Test
    fun callHistoryScreen_rendersDateHeadersAndGroupedCallCountBadges() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)

        val now = System.currentTimeMillis()
        val fakeCallLogProvider = object : ContentProvider() {
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
                cursor.addRow(arrayOf<Any?>("c_today_1", "+919811122334", "Dr. Rohan", CallLog.Calls.MISSED_TYPE, now - 10_000L, 0L))
                cursor.addRow(arrayOf<Any?>("c_today_2", "+91 98111 22334", "Dr. Rohan", CallLog.Calls.INCOMING_TYPE, now - 120_000L, 55L))
                return cursor
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, fakeCallLogProvider)

        val viewModel = LyraMainViewModel(app)
        viewModel.loadData()

        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.callLog.value.size < 2 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
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

        // Same-day repeated calls for Dr. Rohan are grouped under TODAY (2) with badge "(2)"
        composeTestRule.onNodeWithText("TODAY (2)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_history_item_c_today_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_count_badge_c_today_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_history_item_c_today_2").assertDoesNotExist()

        // Clicking grouped entry opens profile sheet with all 2 calls and NO Delete Call Log Entry button
        composeTestRule.onNodeWithTag("call_history_item_c_today_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("contact_profile_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("CALL HISTORY (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete Call Log Entry").assertDoesNotExist()
    }
}
