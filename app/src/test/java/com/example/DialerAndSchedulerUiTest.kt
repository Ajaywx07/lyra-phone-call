package com.example

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.navigation.LyraDestinations
import com.example.service.CallStateManager
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.ScheduledCallEditorDialog
import com.example.ui.screens.resolvePickedContact
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PhoneNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DialerAndSchedulerUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainActivity_startsDirectlyInDialerWithoutSplashDelay() {
        CallStateManager.clearState()
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()

        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(null))
        assertEquals(
            LyraDestinations.DIALER,
            activity.resolveStartDestination(Intent(Intent.ACTION_MAIN))
        )

        // Unlocked incoming call stays on DIALER (banner-only) until user taps banner or screen is locked
        CallStateManager.onIncomingCall(
            CallerInfo(
                phoneNumber = "+918795989222",
                formattedNumber = "+91 87959 89222",
                displayName = "BOSS",
                isKnown = true
            )
        )
        assertEquals(LyraDestinations.DIALER, activity.resolveStartDestination(null))
        assertEquals(
            LyraDestinations.INCOMING_CALL,
            activity.resolveStartDestination(Intent("ACTION_INCOMING_CALL"))
        )
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        org.robolectric.Shadows.shadowOf(km).setKeyguardLocked(true)
        assertEquals(LyraDestinations.INCOMING_CALL, activity.resolveStartDestination(null))
        org.robolectric.Shadows.shadowOf(km).setKeyguardLocked(false)
        CallStateManager.clearState()
        assertEquals(CallStateEnum.IDLE, CallStateManager.callState.value)
    }

    @Test
    fun sanitizeDialpadInput_rejectsTextAndNonNumericStrings_acceptsPhoneNumbers() {
        assertNull(PhoneNumberUtils.sanitizeDialpadInput(null))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput(""))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("   "))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("---"))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("Hello World"))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("abc123"))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("Call 9876543210"))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("test@example.com"))
        assertNull(PhoneNumberUtils.sanitizeDialpadInput("$500"))

        assertEquals("9876543210", PhoneNumberUtils.sanitizeDialpadInput("9876543210"))
        assertEquals("+919876543210", PhoneNumberUtils.sanitizeDialpadInput("+91 98765-43210"))
        assertEquals("9876543210", PhoneNumberUtils.sanitizeDialpadInput("(987) 654-3210"))
        assertEquals("*123#", PhoneNumberUtils.sanitizeDialpadInput("*123#"))
    }

    @Test
    fun dialerScreen_blocksTextPasteAndTextTyping_allowsValidPhoneNumberPaste() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "Hello World"))

        val viewModel = LyraMainViewModel(app)

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
        // Tapping paste when clipboard has non-numeric text must be rejected completely
        composeTestRule.onNodeWithTag("dialer_paste_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_paste_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dial number using keypad").assertIsDisplayed()

        // Direct text / mixed-word typing or system paste must also be rejected
        composeTestRule.onNodeWithTag("dialer_number_display").performTextInput("CallMe123")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dial number using keypad").assertIsDisplayed()

        // Valid formatted phone number paste succeeds and cleans formatting
        clipboard.setPrimaryClip(ClipData.newPlainText("phone", "+91 98765-43210"))
        composeTestRule.onNodeWithTag("dialer_paste_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_number_display").assertTextContains("+919876543210")
    }

    @Test
    fun scheduledCallEditor_showsPickContactButton_andAllowsOptionalBlankName() {
        var savedName: String? = null
        var savedNumber: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                ScheduledCallEditorDialog(
                    initialCall = null,
                    contacts = emptyList(),
                    availableSims = listOf("SIM_1" to "SIM 1 (Jio)"),
                    onDismiss = {},
                    onSave = { name, number, _, _ ->
                        savedName = name
                        savedNumber = number
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("schedule_pick_contact_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_number_input").assertIsDisplayed()

        // Leave Contact Name blank (optional) and enter only Phone Number
        composeTestRule.onNodeWithTag("schedule_number_input").performTextInput("9811122334")
        composeTestRule.onNodeWithTag("save_scheduled_call_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("9811122334", savedNumber)
        assertEquals("9811122334", savedName)
    }

    @Test
    fun resolvePickedContact_extractsNameAndPhoneNumberFromContactUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = "com.example.test.contacts"
        val mockUri = Uri.parse("content://$authority/data/15")

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
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                )
                cursor.addRow(arrayOf("Dr. Rohan", "+91 98111 22334"))
                return cursor
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?
            ): Int = 0
        }

        ShadowContentResolver.registerProviderInternal(authority, fakeProvider)
        val result = resolvePickedContact(context, mockUri)
        assertEquals("Dr. Rohan" to "+91 98111 22334", result)
    }
}
