package com.example.ui.components

import android.telecom.CallAudioState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ContactItemData
import com.example.ui.screens.DIAL_KEYS
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.utils.PhoneNumberUtils
import kotlinx.coroutines.awaitCancellation

private enum class AddCallTab(val label: String) {
    DIALPAD("Dialpad"),
    CONTACTS("Contacts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallMoreOptionsSheet(
    isHold: Boolean,
    conferenceParticipantCount: Int,
    canMergeConference: Boolean,
    isConferenceMerged: Boolean,
    currentAudioRoute: Int,
    isBluetoothAvailable: Boolean,
    onToggleHold: () -> Unit,
    onAddCallClick: () -> Unit,
    onMergeConference: () -> Unit,
    onSelectAudioRoute: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        modifier = Modifier.testTag("in_call_more_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.call_more_options_title),
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.call_more_options_subtitle),
                color = colors.textSecondary,
                fontSize = 12.sp
            )

            // 1. Conference Call (Merge or Add 2nd Caller)
            MoreSheetActionRow(
                icon = if (canMergeConference || conferenceParticipantCount >= 2) {
                    Icons.Default.CallMerge
                } else {
                    Icons.Default.Groups
                },
                title = if (isConferenceMerged) {
                    "Conference Active ($conferenceParticipantCount callers)"
                } else if (canMergeConference || conferenceParticipantCount >= 2) {
                    "Merge into Conference Call"
                } else {
                    "Conference Call"
                },
                subtitle = if ((canMergeConference || conferenceParticipantCount >= 2) && !isConferenceMerged) {
                    "Combine active & held calls into a multi-party conference"
                } else if (isConferenceMerged) {
                    "All $conferenceParticipantCount callers connected together"
                } else {
                    "Add another caller to start a conference call"
                },
                accentColor = if (isConferenceMerged || canMergeConference) colors.acceptColor else colors.primary,
                isActive = isConferenceMerged || canMergeConference || conferenceParticipantCount >= 2,
                onClick = {
                    if (canMergeConference || conferenceParticipantCount >= 2) {
                        onMergeConference()
                        onDismiss()
                    } else {
                        onDismiss()
                        onAddCallClick()
                    }
                },
                testTagName = "more_option_conference"
            )

            // 2. Hold / Resume Call
            MoreSheetActionRow(
                icon = if (isHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                title = if (isHold) "Resume Call" else "Hold Call",
                subtitle = if (isHold) "Unhold caller and restore audio" else "Place current call on hold",
                accentColor = colors.priorityColor,
                isActive = isHold,
                onClick = {
                    onToggleHold()
                    onDismiss()
                },
                testTagName = "more_option_hold"
            )

            // 3. Add Another Caller shortcut
            MoreSheetActionRow(
                icon = Icons.Default.PersonAdd,
                title = stringResource(R.string.call_action_add_call),
                subtitle = "Hold current line and dial or pick another contact",
                accentColor = colors.secondaryGlow,
                isActive = false,
                onClick = {
                    onDismiss()
                    onAddCallClick()
                },
                testTagName = "more_option_add_call"
            )

            // 4. Audio Route Selector (Earpiece / Speaker / Bluetooth only if actively connected)
            @Suppress("DEPRECATION")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.call_audio_output_header),
                    color = colors.primaryGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val earpieceLabel = stringResource(R.string.call_audio_earpiece)
                    val speakerLabel = stringResource(R.string.call_audio_speaker)
                    val bluetoothLabel = stringResource(R.string.call_audio_bluetooth)
                    val routes = buildList {
                        add(Triple(CallAudioState.ROUTE_EARPIECE, earpieceLabel to "earpiece", Icons.Default.Hearing))
                        add(Triple(CallAudioState.ROUTE_SPEAKER, speakerLabel to "speaker", Icons.Default.VolumeUp))
                        if (isBluetoothAvailable) {
                            add(Triple(CallAudioState.ROUTE_BLUETOOTH, bluetoothLabel to "bluetooth", Icons.Default.BluetoothAudio))
                        }
                    }
                    routes.forEach { (routeFlag, labelAndTag, icon) ->
                        val (label, routeTag) = labelAndTag
                        val selected = (currentAudioRoute and routeFlag) != 0
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectAudioRoute(routeFlag) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                                selectedLabelColor = colors.primary,
                                selectedLeadingIconColor = colors.primary,
                                containerColor = colors.background,
                                labelColor = colors.textSecondary,
                                iconColor = colors.textMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = colors.primary,
                                borderColor = colors.glassBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("more_audio_$routeTag")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dynamic Audio Output & Bluetooth Popup Menu.
 * Displays Earpiece, Speaker, and (only when actively connected) Bluetooth.
 */
@Suppress("DEPRECATION")
@Composable
fun AudioRoutePickerDialog(
    currentAudioRoute: Int,
    isBluetoothAvailable: Boolean,
    onSelectAudioRoute: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    val earpieceLabel = stringResource(R.string.call_audio_earpiece)
    val speakerLabel = stringResource(R.string.call_audio_speaker)
    val bluetoothLabel = stringResource(R.string.call_audio_bluetooth)
    val routeOptions = buildList {
        add(
            AudioRouteMenuOption(
                routeFlag = CallAudioState.ROUTE_EARPIECE,
                label = earpieceLabel,
                subtitle = "Phone ear speaker",
                icon = Icons.Default.Hearing,
                testTag = "audio_route_earpiece"
            )
        )
        add(
            AudioRouteMenuOption(
                routeFlag = CallAudioState.ROUTE_SPEAKER,
                label = speakerLabel,
                subtitle = "Phone loudspeaker",
                icon = Icons.Default.VolumeUp,
                testTag = "audio_route_speaker"
            )
        )
        if (isBluetoothAvailable) {
            add(
                AudioRouteMenuOption(
                    routeFlag = CallAudioState.ROUTE_BLUETOOTH,
                    label = bluetoothLabel,
                    subtitle = "Connected Bluetooth audio device",
                    icon = Icons.Default.BluetoothAudio,
                    testTag = "audio_route_bluetooth"
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("audio_route_popup"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.call_audio_route_picker_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = if (isBluetoothAvailable) {
                        "Choose $earpieceLabel, $speakerLabel, or $bluetoothLabel"
                    } else {
                        "Choose $earpieceLabel or $speakerLabel"
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                routeOptions.forEach { option ->
                    val isSelected = (currentAudioRoute and option.routeFlag) != 0
                    Surface(
                        onClick = {
                            onSelectAudioRoute(option.routeFlag)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = if (isSelected) colors.primary.copy(alpha = 0.16f) else colors.background,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) colors.primary.copy(alpha = 0.7f) else colors.glassBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .testTag(option.testTag)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) colors.primary.copy(alpha = 0.2f) else colors.surfaceElevated
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (isSelected) colors.primary else colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = option.label,
                                        color = if (isSelected) colors.primary else colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = option.subtitle,
                                        color = colors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "${option.label} active",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(28.dp)
    )
}

private data class AudioRouteMenuOption(
    val routeFlag: Int,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Full-Screen Add Call / Conference Interface featuring a live Dialpad and Contact Search / Picker.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun FullScreenAddCallOverlay(
    contacts: List<ContactItemData>,
    activeCallerName: String,
    onPlaceSecondaryCall: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(onBack = onDismiss)

    var activeTab by rememberSaveable { mutableStateOf(AddCallTab.DIALPAD) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var numberFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val dialedNumber = numberFieldValue.text

    val setDialNumber: (String) -> Unit = { newValue ->
        numberFieldValue = TextFieldValue(
            text = newValue,
            selection = TextRange(newValue.length)
        )
    }

    val insertDialToken: (String) -> Unit = { token ->
        val currentText = numberFieldValue.text
        val selStart = numberFieldValue.selection.min.coerceIn(0, currentText.length)
        val selEnd = numberFieldValue.selection.max.coerceIn(0, currentText.length)
        val updatedText = currentText.substring(0, selStart) + token + currentText.substring(selEnd)
        val cursor = selStart + token.length
        numberFieldValue = TextFieldValue(
            text = updatedText,
            selection = TextRange(cursor)
        )
    }

    val backspaceDialToken: () -> Unit = {
        val currentText = numberFieldValue.text
        val selMin = numberFieldValue.selection.min.coerceIn(0, currentText.length)
        val selMax = numberFieldValue.selection.max.coerceIn(0, currentText.length)
        if (selMin != selMax) {
            val updatedText = currentText.removeRange(selMin, selMax)
            numberFieldValue = TextFieldValue(updatedText, TextRange(selMin))
        } else if (selMin > 0) {
            val updatedText = currentText.removeRange(selMin - 1, selMin)
            numberFieldValue = TextFieldValue(updatedText, TextRange(selMin - 1))
        }
    }

    val filteredDialpadContacts by remember(dialedNumber, contacts) {
        derivedStateOf {
            if (dialedNumber.isBlank()) {
                contacts.sortedByDescending { it.isPriority }.take(12)
            } else {
                val cleanQuery = PhoneNumberUtils.extractCoreDigits(dialedNumber)
                contacts.filter { contact ->
                    val cleanNum = PhoneNumberUtils.extractCoreDigits(contact.phoneNumber)
                    (cleanQuery.isNotEmpty() && cleanNum.contains(cleanQuery)) ||
                        contact.name.contains(dialedNumber, ignoreCase = true)
                }
            }
        }
    }

    val filteredSearchContacts by remember(searchQuery, contacts) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                contacts
            } else {
                val cleanQuery = PhoneNumberUtils.extractCoreDigits(searchQuery)
                contacts.filter { contact ->
                    contact.name.contains(searchQuery, ignoreCase = true) ||
                        contact.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                        (cleanQuery.isNotEmpty() &&
                            PhoneNumberUtils.extractCoreDigits(contact.phoneNumber).contains(cleanQuery))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("full_screen_add_call_overlay")
    ) {
        val compactHeight = maxHeight < 650.dp
        val keySize = if (compactHeight) 56.dp else 64.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            CapsuleTopHeader(
                title = "ADD CALL",
                subtitle = "On hold: $activeCallerName",
                showBack = true,
                onBackClick = onDismiss,
                statusText = "Conference"
            )

            // Double-Capsule mode switcher: Dialpad vs Full Contact Search/Picker
            CapsuleFilterBar(
                items = AddCallTab.entries.toList(),
                selectedItem = activeTab,
                labelProvider = { it.label },
                onSelect = { activeTab = it },
                testTagPrefix = "add_call_tab"
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (activeTab == AddCallTab.CONTACTS) {
                CapsuleSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search contacts by name or number...",
                    testTag = "add_call_search_input"
                )

                Spacer(modifier = Modifier.height(10.dp))

                val queryDigits = PhoneNumberUtils.normalize(searchQuery)
                if (queryDigits.count { it.isDigit() } >= 3) {
                    Surface(
                        onClick = {
                            onPlaceSecondaryCall(queryDigits)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = EmeraldAccept.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldAccept.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("add_call_direct_search_dial_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dialpad,
                                    contentDescription = null,
                                    tint = EmeraldAccept,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Dial \"$searchQuery\"",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call number",
                                tint = EmeraldAccept,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (filteredSearchContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Default.Contacts,
                            title = "No matching contacts",
                            description = "Switch to Dialpad or enter a phone number to add a participant.",
                            buttonText = "Open Dialpad",
                            onButtonClick = { activeTab = AddCallTab.DIALPAD }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 18.dp)
                    ) {
                        items(
                            count = filteredSearchContacts.size,
                            key = { idx ->
                                val contact = filteredSearchContacts[idx]
                                "search_${contact.id}_${contact.phoneNumber}_$idx"
                            }
                        ) { idx ->
                            val contact = filteredSearchContacts[idx]
                            AddCallContactPickerRow(
                                contact = contact,
                                onSelect = {
                                    onPlaceSecondaryCall(contact.phoneNumber)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            } else {
                // Dialpad Mode: Live matching contacts list at top + Keypad & Call capsule docked cleanly at bottom
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (filteredDialpadContacts.isEmpty() && dialedNumber.isNotBlank()) {
                        item {
                            Text(
                                text = "Tap Call below to dial $dialedNumber",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(
                            count = filteredDialpadContacts.size,
                            key = { idx ->
                                val contact = filteredDialpadContacts[idx]
                                "dialpad_${contact.id}_${contact.phoneNumber}_$idx"
                            }
                        ) { idx ->
                            val contact = filteredDialpadContacts[idx]
                            AddCallContactPickerRow(
                                contact = contact,
                                onSelect = {
                                    onPlaceSecondaryCall(contact.phoneNumber)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Editable Number Input Capsule (system IME suppressed!)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surfaceElevated,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (dialedNumber.isNotEmpty()) colors.primary.copy(alpha = 0.55f) else colors.glassBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(34.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dialedNumber.isEmpty()) {
                                Text(
                                    text = "Enter number to add",
                                    color = colors.textMuted.copy(alpha = 0.65f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            InterceptPlatformTextInput(
                                interceptor = { _, _ -> awaitCancellation() }
                            ) {
                                BasicTextField(
                                    value = numberFieldValue,
                                    onValueChange = { updated ->
                                        val newText = updated.text
                                        if (newText.isEmpty()) {
                                            numberFieldValue = updated
                                        } else if (PhoneNumberUtils.isValidDialpadFieldText(newText)) {
                                            val cleanText = newText.filter { it.isDigit() || it in "+*#" }
                                            numberFieldValue = if (cleanText == newText) {
                                                updated
                                            } else {
                                                updated.copy(
                                                    text = cleanText,
                                                    selection = TextRange(
                                                        updated.selection.end.coerceAtMost(cleanText.length)
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    textStyle = TextStyle(
                                        color = colors.textPrimary,
                                        fontSize = when {
                                            dialedNumber.length > 15 -> 19.sp
                                            dialedNumber.length > 10 -> 23.sp
                                            else -> 26.sp
                                        },
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    cursorBrush = SolidColor(colors.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                keyboardController?.hide()
                                            }
                                        }
                                        .testTag("add_call_number_input")
                                )
                            }
                        }

                        if (dialedNumber.isEmpty() && clipboardManager.hasText()) {
                            IconButton(
                                onClick = {
                                    val rawClipboard = clipboardManager.getText()?.text.orEmpty()
                                    val validNumber = PhoneNumberUtils.sanitizeDialpadInput(rawClipboard)
                                    if (validNumber != null) {
                                        setDialNumber(validNumber)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                modifier = Modifier.size(34.dp).testTag("add_call_paste_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste number",
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (dialedNumber.isNotEmpty()) {
                            IconButton(
                                onClick = { setDialNumber("") },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear number",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(34.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3x4 Circular Dialpad Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0..2) {
                                val key = DIAL_KEYS[row * 3 + col]
                                Box(
                                    modifier = Modifier
                                        .size(keySize)
                                        .clip(CircleShape)
                                        .background(colors.surfaceElevated.copy(alpha = 0.92f))
                                        .border(1.dp, colors.glassBorder, CircleShape)
                                        .combinedClickable(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                insertDialToken(key.digit)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (key.digit == "0") {
                                                    insertDialToken("+")
                                                }
                                            }
                                        )
                                        .testTag("add_call_dial_key_${key.digit}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = key.digit,
                                            color = colors.textPrimary,
                                            fontSize = if (key.digit in listOf("*", "#")) 24.sp else 21.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 22.sp
                                        )
                                        if (key.letters.isNotEmpty()) {
                                            Text(
                                                text = key.letters,
                                                color = colors.textMuted,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Call & Backspace Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(50.dp))

                    Surface(
                        onClick = {
                            if (dialedNumber.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlaceSecondaryCall(dialedNumber)
                                onDismiss()
                            }
                        },
                        enabled = dialedNumber.isNotBlank(),
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = if (dialedNumber.isNotBlank()) 6.dp else 0.dp,
                        modifier = Modifier
                            .height(58.dp)
                            .defaultMinSize(minWidth = 128.dp)
                            .testTag("add_call_confirm_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (dialedNumber.isNotBlank()) {
                                        Brush.horizontalGradient(
                                            colors = listOf(EmeraldAccept, EmeraldAccept.copy(alpha = 0.85f))
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                colors.surfaceElevated,
                                                colors.surfaceElevated
                                            )
                                        )
                                    }
                                )
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Add Call",
                                    tint = if (dialedNumber.isNotBlank()) Color.White else colors.textMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Add Call",
                                    color = if (dialedNumber.isNotBlank()) Color.White else colors.textMuted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dialedNumber.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.glassBorder, CircleShape)
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            backspaceDialToken()
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            setDialNumber("")
                                        }
                                    )
                                    .testTag("add_call_backspace_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCallContactPickerRow(
    contact: ContactItemData,
    onSelect: () -> Unit
) {
    val colors = LyraTheme.colors
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(22.dp),
        color = colors.glassSurface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                contact.isOwner -> colors.primary.copy(alpha = 0.45f)
                contact.isPriority -> AmberPriority.copy(alpha = 0.35f)
                else -> colors.glassBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_call_contact_${contact.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                LyraContactAvatar(
                    name = contact.name,
                    photoUri = contact.photoUri,
                    isOwner = contact.isOwner,
                    isPriority = contact.isPriority,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = contact.name,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (contact.isOwner) {
                            CapsuleStatusChip(text = "Owner", color = colors.primary)
                        } else if (contact.isPriority) {
                            CapsuleStatusChip(text = "★ VIP", color = AmberPriority)
                        }
                    }
                    Text(
                        text = contact.phoneNumber,
                        color = colors.primaryGlow,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            GreenCallActionCapsule(
                onClick = onSelect,
                contentDescription = "Add ${contact.name} to call"
            )
        }
    }
}

@Composable
private fun MoreSheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    val colors = LyraTheme.colors
    Surface(
        color = if (isActive) accentColor.copy(alpha = 0.14f) else colors.background,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActive) accentColor.copy(alpha = 0.6f) else colors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
            if (isActive) {
                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InCallDtmfKeypadDialog(
    onSendDtmf: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    var dtmfDigits by remember { mutableStateOf("") }
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.call_dtmf_keypad_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dtmfDigits.ifBlank { "—" },
                    color = colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            Surface(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, colors.glassBorder, CircleShape),
                                color = colors.background,
                                onClick = {
                                    dtmfDigits += digit
                                    digit.firstOrNull()?.let(onSendDtmf)
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = digit,
                                        color = colors.textPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close), color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { dtmfDigits = "" }) {
                Text(text = "Clear", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated
    )
}
