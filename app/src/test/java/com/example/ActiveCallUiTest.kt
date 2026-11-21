package com.example

import android.app.Application
import android.telecom.CallAudioState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.service.CallStateManager
import com.example.ui.components.AudioRoutePickerDialog
import com.example.ui.components.FullScreenAddCallOverlay
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveCallUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Suppress("DEPRECATION")
    @Test
    fun audioRoutePopup_hidesBluetoothWhenDisconnected_andShowsWhenConnected() {
        var bluetoothAvailable by mutableStateOf(false)
        var currentRoute by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE)

        composeTestRule.setContent {
            MyApplicationTheme {
                AudioRoutePickerDialog(
                    currentAudioRoute = currentRoute,
                    isBluetoothAvailable = bluetoothAvailable,
                    onSelectAudioRoute = { currentRoute = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("audio_route_earpiece").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_route_speaker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_route_bluetooth").assertDoesNotExist()

        // Connect Bluetooth device dynamically
        bluetoothAvailable = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audio_route_bluetooth").assertIsDisplayed()

        // Select Bluetooth
        composeTestRule.onNodeWithTag("audio_route_bluetooth").performClick()
        assertEquals(CallAudioState.ROUTE_BLUETOOTH, currentRoute)
    }

    @Test
    fun fullScreenAddCallOverlay_supportsDialpadAndContactSearchSelection() {
        val sampleContacts = listOf(
            ContactItemData(
                id = "c1",
                name = "Rohan Sharma",
                phoneNumber = "+91 98111 22334",
                label = "Doctor",
                isPriority = true
            ),
            ContactItemData(
                id = "c2",
                name = "Meera Patel",
                phoneNumber = "+91 98222 33445",
                label = "Work"
            )
        )
        var placedNumber: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                FullScreenAddCallOverlay(
                    contacts = sampleContacts,
                    activeCallerName = "Ajay",
                    onPlaceSecondaryCall = { placedNumber = it },
                    onDismiss = {}
                )
            }
        }

        // Verify Dialpad mode displays keypad & number entry
        composeTestRule.onNodeWithTag("full_screen_add_call_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_call_dial_key_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_call_number_input").assertIsDisplayed()

        // Switch to Contacts tab, search by name, and tap contact
        composeTestRule.onNodeWithTag("add_call_tab_contacts").performClick()
        composeTestRule.onNodeWithTag("add_call_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_call_search_input").performTextInput("Meera")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_call_contact_c2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_call_contact_c1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("add_call_contact_c2").performClick()

        assertEquals("+91 98222 33445", placedNumber)
    }

    @Test
    fun activeCallScreen_omitsBluetoothButton_andTransitionsAddCallToMerge() {
        CallStateManager.clearState()
        CallStateManager.onOutgoingCall(
            caller = CallerInfo(
                phoneNumber = "+918795989222",
                formattedNumber = "+91 87959 89222",
                displayName = "BOSS",
                isKnown = true
            ),
            simLabel = "SIM 1",
            isSimulated = true
        )
        CallStateManager.onCallAnswered()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    viewModel = viewModel,
                    onEndCall = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Dedicated Bluetooth button must NOT exist on the main screen; Audio + Add Call must exist
        composeTestRule.onNodeWithTag("call_action_Bluetooth").assertDoesNotExist()
        composeTestRule.onNodeWithTag("call_action_Audio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_action_Add Call").assertIsDisplayed()

        // Simulate 2nd call connected (Call 1 on hold + Call 2 active) -> Add Call dynamically transforms to Merge
        CallStateManager.toggleHold()
        CallStateManager.registerSecondaryParticipant("Rohan", connectedImmediately = false)
        CallStateManager.markSecondaryCallConnected("Rohan")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("call_action_Add Call").assertDoesNotExist()
        composeTestRule.onNodeWithTag("call_action_Merge").assertIsDisplayed()

        // Tap Merge -> transitions to merged conference and Add Call becomes accessible again
        composeTestRule.onNodeWithTag("call_action_Merge").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_action_Add Call").assertIsDisplayed()

        CallStateManager.clearState()
    }
}
