package com.example

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.example.data.local.entity.CallNote
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallScreenStyle
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.service.CallStateManager
import com.example.ui.components.ContactCard
import com.example.ui.components.ListItemContextMenuDialog
import com.example.ui.screens.ContactDetailsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class UiRefinementVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialerScreen_rendersCleanlyAcrossAllSixThemes_includingHashKeyBounds() {
        CallStateManager.clearState()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        val activeTheme = mutableStateOf(AppThemeStyle.LYRA_CYBER)

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = activeTheme.value) {
                DialerScreen(
                    viewModel = viewModel,
                    onNavigateTab = {},
                    onNavigateToActiveCall = {},
                    onNavigateToContactDetails = {}
                )
            }
        }

        AppThemeStyle.entries.forEach { style ->
            activeTheme.value = style
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("dial_key_#").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dial_key_*").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dialer_keypad_grid").assertIsDisplayed()

            val gridBounds = composeTestRule.onNodeWithTag("dialer_keypad_grid").getBoundsInRoot()
            val hashBounds = composeTestRule.onNodeWithTag("dial_key_#").getBoundsInRoot()
            val actionBounds = composeTestRule.onNodeWithTag("dialer_action_row").getBoundsInRoot()

            assertTrue(
                "Hash key must remain strictly inside keypad grid bounds for ${style.key}",
                hashBounds.top >= gridBounds.top && hashBounds.bottom <= gridBounds.bottom + 1.dp
            )
            assertTrue(
                "Hash key must sit cleanly above action row for ${style.key}",
                hashBounds.bottom < actionBounds.top
            )
        }
    }

    @Test
    fun contactCard_usesReducedVerticalThicknessWithAccessibleControls() {
        val sampleContact = ContactItemData(
            id = "42",
            name = "Aarav Sharma",
            phoneNumber = "+91 98765 43210",
            label = "Mobile",
            isPriority = true
        )

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.IOS_GLASS) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ContactCard(
                        contact = sampleContact,
                        onContactClick = {},
                        onCallClick = {},
                        onTogglePriority = {},
                        onLongClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val cardNode = composeTestRule.onNodeWithTag("contact_item_42")
        cardNode.assertIsDisplayed()
        composeTestRule.onNodeWithTag("star_42").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_contact_42").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mobile").assertIsDisplayed()

        val bounds = cardNode.getBoundsInRoot()
        val heightDp = (bounds.bottom - bounds.top).value
        // Compact card (~58-72dp, approx 20% slimmer than previous ~86dp height)
        assertTrue("Expected compact ContactCard height <= 74dp, was ${heightDp}dp", heightDp <= 74f)
        assertTrue("Expected accessible ContactCard height >= 56dp, was ${heightDp}dp", heightDp >= 56f)
    }

    @Test
    fun settingsScreen_groupsAiProviderAndCallScreenIntoCollapsibleModules() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.LYRA_CYBER) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onNavigateToAbout = {},
                    onNavigateToPermissions = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // AI Engine and Call Screen modules render compact headers collapsed by default
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_ai_engine"))
        composeTestRule.onNodeWithTag("settings_module_header_ai_engine").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_model_input").assertDoesNotExist()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_call_screen"))
        composeTestRule.onNodeWithTag("settings_module_header_call_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("style_chip_${CallScreenStyle.IPHONE.key}").assertDoesNotExist()

        // Expand AI Engine module on tap and verify fields appear
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_ai_engine"))
        composeTestRule.onNodeWithTag("settings_module_header_ai_engine").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("ai_model_input"))
        composeTestRule.onNodeWithTag("ai_model_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_api_key_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voicemail_greeting_input").assertIsDisplayed()

        // Expand Call Screen Style & Animations module on tap and verify style/preview controls appear
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_module_header_call_screen"))
        composeTestRule.onNodeWithTag("settings_module_header_call_screen").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("style_chip_${CallScreenStyle.IPHONE.key}"))
        composeTestRule.onNodeWithTag("style_chip_${CallScreenStyle.IPHONE.key}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_incoming_style_btn").assertIsDisplayed()

        // Selecting style updates live setting
        composeTestRule.onNodeWithTag("style_chip_${CallScreenStyle.IPHONE.key}").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            viewModel.callScreenStyle.value == CallScreenStyle.IPHONE
        }
    }

    @Test
    fun contactDetailsScreen_hidesDeleteWhenPriorityOn_andRequiresConfirmationWhenOff() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LyraMainViewModel(app)
        val priorityContact = ContactItemData(
            id = "prio_99",
            name = "VIP Priya",
            phoneNumber = "+91 98111 22334",
            label = "Priority",
            isPriority = true,
            isOwner = false
        )
        val regularContact = ContactItemData(
            id = "reg_100",
            name = "Regular Rohan",
            phoneNumber = "+91 98111 99887",
            label = "Mobile",
            isPriority = false,
            isOwner = false
        )
        viewModel.selectContact(priorityContact)

        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.LYRA_CYBER) {
                ContactDetailsScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Priority ON -> Delete Contact button must be hidden
        composeTestRule.onNodeWithTag("contact_details_delete_btn").assertDoesNotExist()

        // Switch to non-priority contact -> Delete Contact button appears & opens confirmation dialog
        viewModel.selectContact(regularContact)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("contact_details_delete_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("contact_details_delete_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("delete_confirmation_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_delete_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_delete_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("delete_confirmation_title").assertDoesNotExist()
    }

    @Test
    fun contextMenuDialog_hidesDeleteWhenCanDeleteFalse() {
        var deleteClicked = false
        composeTestRule.setContent {
            MyApplicationTheme(appThemeStyle = AppThemeStyle.LYRA_CYBER) {
                ListItemContextMenuDialog(
                    title = "VIP Contact",
                    subtitle = "+91 98765 00000",
                    canDelete = false,
                    deleteLabel = "Delete Contact",
                    onEditClick = {},
                    onDeleteClick = { deleteClicked = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("context_menu_edit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("context_menu_delete").assertDoesNotExist()
        assertFalse(deleteClicked)
    }
}
