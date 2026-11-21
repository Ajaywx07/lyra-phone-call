package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.CallLogRepository
import com.example.data.repository.ContactsRepository
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.navigation.LyraDestinations
import com.example.service.CallNotificationActionReceiver
import com.example.service.CallStateManager
import com.example.service.NotificationHelper
import com.example.service.shouldBypassSlowScreeningLookup
import com.example.ui.components.ContactCard
import com.example.ui.components.ContactProfileSheet
import com.example.ui.components.LyraBottomNavBar
import com.example.ui.components.RecentCallCard
import com.example.ui.screens.ContactDetailsScreen
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
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
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class MissedCallAndRecentsOptimizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        CallStateManager.clearState()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            LyraPreferences(app).markAllMissedCallsAsRead(0L)
        }
        NotificationHelper.clearMissedCallNotification(app)
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        CallStateManager.clearState()
        runBlocking {
            LyraPreferences(app).markAllMissedCallsAsRead(0L)
        }
        NotificationHelper.clearMissedCallNotification(app)
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun missedCallNotification_eightMissedCalls_createsExpandableInboxStyleWithIndividualLines() = runBlocking<Unit> {
        val baseTime = 1_730_000_000_000L
        for (i in 1..8) {
            NotificationHelper.recordAndNotifyMissedCall(
                context = app,
                callerName = "Caller $i",
                phoneNumber = "+91 98765 0000$i",
                timestamp = baseTime + i * 60_000L,
                simLabel = if (i % 2 == 0) "SIM 2" else "SIM 1"
            )
        }

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_MISSED_CALL)
        assertNotNull("Expected missed call notification to be posted", posted)

        val shadowNotif = Shadows.shadowOf(posted!!)
        assertEquals("8 missed calls", shadowNotif.contentTitle.toString())
        assertEquals(8, posted.number)

        val textLines = posted.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull("Expected InboxStyle EXTRA_TEXT_LINES for expandable missed call list", textLines)
        assertEquals("Expected all 8 missed calls listed separately when expanded", 8, textLines!!.size)
        assertTrue(textLines[0].toString().contains("Caller 8"))
        assertTrue(textLines[0].toString().contains("SIM 2"))
        assertTrue(textLines[7].toString().contains("Caller 1"))
        assertTrue(textLines[7].toString().contains("SIM 1"))

        val tapIntent = Shadows.shadowOf(posted.contentIntent).savedIntent
        assertEquals(NotificationHelper.ACTION_OPEN_RECENTS, tapIntent.action)
        assertEquals(
            LyraDestinations.CALL_HISTORY,
            tapIntent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_ROUTE)
        )
        assertEquals(MainActivity::class.java.name, tapIntent.component?.className)
    }

    @Test
    fun tappingMissedCallNotification_launchesRecentsAndResetsUnreadCountForNextCalls() = runBlocking<Unit> {
        NotificationHelper.recordAndNotifyMissedCall(
            context = app,
            callerName = "Priya Sharma",
            phoneNumber = "+91 98222 33445",
            timestamp = System.currentTimeMillis() - 120_000L,
            simLabel = "SIM 2"
        )
        NotificationHelper.recordAndNotifyMissedCall(
            context = app,
            callerName = "Dr. Rohan Verma",
            phoneNumber = "+91 98111 22334",
            timestamp = System.currentTimeMillis() - 60_000L,
            simLabel = "SIM 1"
        )

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val initialPosted = Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_MISSED_CALL)!!
        assertEquals(2, initialPosted.number)
        assertEquals(2, LyraPreferences(app).unreadMissedCalls.first().size)

        val savedIntent = Shadows.shadowOf(initialPosted.contentIntent).savedIntent
        ActivityScenario.launch<MainActivity>(savedIntent).use {
            val deadline = System.currentTimeMillis() + 5_000L
            while (LyraPreferences(app).unreadMissedCalls.first().isNotEmpty() &&
                System.currentTimeMillis() < deadline
            ) {
                ShadowLooper.idleMainLooper()
                Thread.sleep(25)
            }
            ShadowLooper.idleMainLooper()
            assertEquals(0, LyraPreferences(app).unreadMissedCalls.first().size)
            assertNull(
                "Notification must clear once Recents is opened",
                Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_MISSED_CALL)
            )
        }

        // Subsequent new missed call starts count fresh at 1 (only unread calls)
        NotificationHelper.recordAndNotifyMissedCall(
            context = app,
            callerName = "Vikram",
            phoneNumber = "+91 98333 44556",
            timestamp = System.currentTimeMillis(),
            simLabel = "SIM 2"
        )
        val newPosted = Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_MISSED_CALL)!!
        assertEquals("Missed call", Shadows.shadowOf(newPosted).contentTitle.toString())
        assertEquals(1, newPosted.number)
        assertEquals(1, LyraPreferences(app).unreadMissedCalls.first().size)
    }

    @Test
    fun clearMissedCallsReceiver_resetsUnreadCountAndCancelsNotification() = runBlocking<Unit> {
        NotificationHelper.recordAndNotifyMissedCall(
            context = app,
            callerName = "Caller A",
            phoneNumber = "+91 90000 11111"
        )
        assertEquals(1, LyraPreferences(app).unreadMissedCalls.first().size)

        CallNotificationActionReceiver().onReceive(
            app,
            Intent(app, CallNotificationActionReceiver::class.java).apply {
                action = CallNotificationActionReceiver.ACTION_CLEAR_MISSED_CALLS
            }
        )

        val deadline = System.currentTimeMillis() + 3_000L
        while (LyraPreferences(app).unreadMissedCalls.first().isNotEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals(0, LyraPreferences(app).unreadMissedCalls.first().size)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNull(Shadows.shadowOf(nm).getNotification(NotificationHelper.NOTIFICATION_ID_MISSED_CALL))
    }

    @Test
    fun recentCallCard_matchesCompactContactCardSize_hasCleanLabelsAndSimBadge() {
        val sampleContact = ContactItemData(
            id = "c_1",
            name = "Aarav Sharma",
            phoneNumber = "+91 98765 43210",
            label = "Mobile"
        )
        val missedItem = CallHistoryItemData(
            id = "r_missed",
            phoneNumber = "+91 98111 22334",
            contactName = "Dr. Rohan Verma",
            callType = CallTypeEnum.MISSED,
            timestamp = System.currentTimeMillis(),
            durationSeconds = 0L,
            formattedDuration = "Missed",
            formattedDate = "Today, 09:15 AM",
            simLabel = "SIM 2"
        )
        val rejectedItem = CallHistoryItemData(
            id = "r_rejected",
            phoneNumber = "+91 91234 56789",
            contactName = "Unknown Caller",
            callType = CallTypeEnum.REJECTED,
            timestamp = System.currentTimeMillis() - 60_000L,
            durationSeconds = 0L,
            formattedDuration = "Rejected",
            formattedDate = "Today, 09:10 AM",
            simLabel = "SIM 1"
        )
        val answeredItem = CallHistoryItemData(
            id = "r_answered",
            phoneNumber = "+91 98222 33445",
            contactName = "Priya Sharma",
            callType = CallTypeEnum.INCOMING,
            timestamp = System.currentTimeMillis() - 120_000L,
            durationSeconds = 125L,
            formattedDuration = "2m 05s",
            formattedDate = "Today, 08:40 AM",
            simLabel = "SIM 2"
        )

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.IOS_GLASS) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactCard(
                        contact = sampleContact,
                        onContactClick = {},
                        onCallClick = {},
                        onTogglePriority = {},
                        onLongClick = {}
                    )
                    RecentCallCard(
                        item = missedItem,
                        callCount = 2,
                        onCallClick = {},
                        onItemClick = {},
                        onLongClick = {}
                    )
                    RecentCallCard(
                        item = rejectedItem,
                        callCount = 1,
                        onCallClick = {},
                        onItemClick = {},
                        onLongClick = {}
                    )
                    RecentCallCard(
                        item = answeredItem,
                        callCount = 1,
                        onCallClick = {},
                        onItemClick = {},
                        onLongClick = {}
                    )
                    LyraBottomNavBar(
                        currentRoute = LyraDestinations.HOME,
                        onNavigate = {},
                        unreadMissedCount = 8
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val contactBounds = composeTestRule.onNodeWithTag("contact_item_c_1").getBoundsInRoot()
        val recentBounds = composeTestRule.onNodeWithTag("call_history_item_r_missed").getBoundsInRoot()
        val contactHeight = (contactBounds.bottom - contactBounds.top).value
        val recentHeight = (recentBounds.bottom - recentBounds.top).value

        assertTrue(
            "RecentCallCard (${recentHeight}dp) should closely match ContactCard (${contactHeight}dp)",
            kotlin.math.abs(recentHeight - contactHeight) <= 10f && recentHeight in 54f..74f
        )

        composeTestRule.onNodeWithTag("recent_status_r_missed", useUnmergedTree = true).assertTextContains("Missed")
        composeTestRule.onNodeWithText("Missed • Missed").assertDoesNotExist()
        composeTestRule.onNodeWithTag("recent_status_r_rejected", useUnmergedTree = true).assertTextContains("Rejected")
        composeTestRule.onNodeWithText("Rejected • Rejected").assertDoesNotExist()
        composeTestRule.onNodeWithTag("recent_status_r_answered", useUnmergedTree = true).assertTextContains("Incoming • 2m 05s")

        composeTestRule.onNodeWithTag("recent_sim_badge_r_missed", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("recent_sim_badge_r_missed", useUnmergedTree = true).assertTextContains("SIM 2")
        composeTestRule.onNodeWithTag("nav_recents_unread_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_recents_unread_badge", useUnmergedTree = true).assertTextContains("8")
    }

    @Test
    fun callLogRepository_andDetailViews_surfaceDualSimLabels() = runBlocking<Unit> {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)
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
                        CallLog.Calls.DURATION,
                        CallLog.Calls.PHONE_ACCOUNT_ID
                    )
                )
                cursor.addRow(
                    arrayOf(
                        "log_1",
                        "+91 98111 22334",
                        "Dr. Rohan Verma",
                        CallLog.Calls.MISSED_TYPE,
                        System.currentTimeMillis() - 30_000L,
                        0L,
                        "account_sim1"
                    )
                )
                cursor.addRow(
                    arrayOf(
                        "log_2",
                        "+91 98111 22334",
                        "Dr. Rohan Verma",
                        CallLog.Calls.INCOMING_TYPE,
                        System.currentTimeMillis() - 90_000L,
                        120L,
                        "account_sim2"
                    )
                )
                return cursor
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, fakeProvider)

        val db = LyraDatabase.getDatabase(app)
        val prefs = LyraPreferences(app)
        val contactsRepo = ContactsRepository(app, db.priorityContactDao(), prefs)
        val callLogRepo = CallLogRepository(app, contactsRepo)
        val logs = callLogRepo.getCallLog(preloadedContacts = emptyList())

        assertEquals(2, logs.size)
        assertEquals("SIM 1", logs[0].simLabel)
        assertEquals("SIM 2", logs[1].simLabel)

        val viewModel = LyraMainViewModel(app)
        viewModel.selectContact(
            ContactItemData(
                id = "contact_rohan",
                name = "Dr. Rohan Verma",
                phoneNumber = "+91 98111 22334",
                label = "Doctor",
                isPriority = true
            )
        )
        viewModel.loadData()
        val deadline = System.currentTimeMillis() + 3_000L
        while (viewModel.callLog.value.isEmpty() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()

        composeTestRule.setContent {
            MyApplicationTheme {
                ContactProfileSheet(
                    name = "Dr. Rohan Verma",
                    phoneNumber = "+91 98111 22334",
                    photoUri = null,
                    label = "Doctor",
                    isOwner = false,
                    isPriority = true,
                    callMetadata = "Missed • Today • SIM 1",
                    callHistory = logs,
                    notes = emptyList(),
                    onCallClick = {},
                    onSmsClick = {},
                    onQuickAutoSmsClick = {},
                    onAddNoteClick = {},
                    onEditClick = {},
                    onTogglePriorityClick = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_sim_badge_log_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_sim_badge_log_1").assertTextContains("SIM 1")
        composeTestRule.onNodeWithTag("profile_sim_badge_log_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_sim_badge_log_2").assertTextContains("SIM 2")
    }

    @Test
    fun contactDetailsScreen_showsSimBadgesOnCallHistoryEntries() = runBlocking<Unit> {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)
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
                        CallLog.Calls.DURATION,
                        CallLog.Calls.PHONE_ACCOUNT_ID
                    )
                )
                cursor.addRow(
                    arrayOf(
                        "d_log_1",
                        "+91 98111 22334",
                        "Dr. Rohan Verma",
                        CallLog.Calls.MISSED_TYPE,
                        System.currentTimeMillis() - 10_000L,
                        0L,
                        "slot_2"
                    )
                )
                return cursor
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, fakeProvider)

        val viewModel = LyraMainViewModel(app)
        viewModel.selectContact(
            ContactItemData(
                id = "contact_rohan",
                name = "Dr. Rohan Verma",
                phoneNumber = "+91 98111 22334",
                label = "Doctor",
                isPriority = true
            )
        )
        viewModel.loadData()
        val deadline = System.currentTimeMillis() + 3_000L
        while (viewModel.callLog.value.isEmpty() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()

        composeTestRule.setContent {
            MyApplicationTheme {
                ContactDetailsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("details_sim_badge_d_log_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("details_sim_badge_d_log_1").assertTextContains("SIM 2")
    }

    @Test
    fun incomingCallDelayOptimization_bypassesSlowScreening_usesPhoneLookup_andPreservesRingerState() = runBlocking<Unit> {
        // 1. Screening fast-path bypass skips Contacts/Room lookup when safe
        assertTrue(
            shouldBypassSlowScreeningLookup(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = false,
                phoneNumber = "+919876543210"
            )
        )
        assertTrue(
            shouldBypassSlowScreeningLookup(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = true,
                phoneNumber = "+919876543210"
            )
        )
        assertFalse(
            shouldBypassSlowScreeningLookup(
                isLyraEnabled = true,
                blockUnknownCalls = false,
                blockCommercialPrefixes = true,
                phoneNumber = "1408800123"
            )
        )

        // 2. ContactsRepository.resolveCaller uses indexed PhoneLookup instead of full Email/Phone scan
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        val queriedUris = mutableListOf<String>()
        val contactsProvider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor {
                queriedUris.add(uri.toString())
                if (uri.toString().startsWith(ContactsContract.PhoneLookup.CONTENT_FILTER_URI.toString())) {
                    val cursor = MatrixCursor(
                        arrayOf(
                            ContactsContract.PhoneLookup.DISPLAY_NAME,
                            ContactsContract.PhoneLookup.PHOTO_URI,
                            ContactsContract.PhoneLookup.STARRED
                        )
                    )
                    cursor.addRow(arrayOf("Fast Lookup Caller", null, 1))
                    return cursor
                }
                return MatrixCursor(arrayOf("_id"))
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, contactsProvider)

        val db = LyraDatabase.getDatabase(app)
        val prefs = LyraPreferences(app)
        val repo = ContactsRepository(app, db.priorityContactDao(), prefs)
        val resolved = repo.resolveCaller("+91 98765 12345")

        assertEquals("Fast Lookup Caller", resolved.displayName)
        assertTrue(resolved.isKnown)
        assertTrue(resolved.isPriority)
        assertTrue(
            "Expected PhoneLookup query",
            queriedUris.any { it.contains("phone_lookup") }
        )
        assertFalse(
            "Must NOT run full Email table scan on incoming call resolution",
            queriedUris.any { it.contains("email", ignoreCase = true) }
        )

        // 3. Optimistic incoming dispatch + async enrichment preserves RINGING state & silenced ringer
        val preliminary = CallerInfo(
            phoneNumber = "+919876512345",
            formattedNumber = "+91 98765 12345",
            displayName = "+91 98765 12345",
            isKnown = false
        )
        CallStateManager.onIncomingCall(preliminary, simLabel = "SIM 2")
        CallStateManager.silenceRinger()
        CallStateManager.updateCurrentCaller(resolved, simLabel = "SIM 2")

        assertEquals(CallStateEnum.RINGING, CallStateManager.callState.value)
        assertEquals("Fast Lookup Caller", CallStateManager.currentCaller.value?.displayName)
        assertEquals("SIM 2", CallStateManager.activeSimLabel.value)
        assertTrue(CallStateManager.isRingerSilenced.value)
    }
}
