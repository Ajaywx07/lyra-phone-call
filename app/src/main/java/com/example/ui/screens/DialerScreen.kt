package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.ContactItemData
import com.example.navigation.LyraDestinations
import com.example.ui.components.CapsuleStatusChip
import com.example.ui.components.GreenCallActionCapsule
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraBottomNavBar
import com.example.ui.components.LyraContactAvatar
import com.example.ui.components.LyraTopBar
import com.example.ui.components.OuterCapsuleContainer
import com.example.ui.components.SimChooserDialog
import com.example.ui.components.shouldPromptForSim
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PhoneNumberUtils
import com.example.utils.SimManager
import kotlinx.coroutines.awaitCancellation
import kotlin.math.abs
import kotlin.math.roundToInt

data class DialKey(
    val digit: String,
    val letters: String
)

internal data class DialerSuggestion(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String?,
    val subtitle: String,
    val isPriority: Boolean,
    val isOwner: Boolean,
    val isRecentCall: Boolean,
    val contact: ContactItemData? = null
)

internal fun buildDialerMatchingSuggestions(
    dialedNumber: String,
    contacts: List<ContactItemData>,
    callLog: List<CallHistoryItemData>
): List<DialerSuggestion> {
    val usedIds = mutableSetOf<String>()
    fun uniqueId(baseId: String, phoneNumber: String): String {
        val numberToken = PhoneNumberUtils.extractCoreDigits(phoneNumber)
            .ifBlank { phoneNumber.filter { it.isDigit() } }
            .ifBlank { "n" }
        val candidate = "${baseId}_$numberToken"
        if (usedIds.add(candidate)) return candidate
        var suffix = 2
        while (!usedIds.add("${candidate}_$suffix")) {
            suffix++
        }
        return "${candidate}_$suffix"
    }

    val trimmed = dialedNumber.trim()
    if (trimmed.isBlank()) {
        val contactsByCore = HashMap<String, ContactItemData>(contacts.size * 2)
        val contactsByDigits = HashMap<String, ContactItemData>(contacts.size * 2)
        for (contact in contacts) {
            val core = PhoneNumberUtils.extractCoreDigits(contact.phoneNumber)
            if (core.isNotEmpty() && !contactsByCore.containsKey(core)) {
                contactsByCore[core] = contact
            }
            val digits = contact.phoneNumber.filter { it.isDigit() }
            if (digits.isNotEmpty() && !contactsByDigits.containsKey(digits)) {
                contactsByDigits[digits] = contact
            }
        }

        fun fastLookupKey(phoneNumber: String): String {
            val core = PhoneNumberUtils.extractCoreDigits(phoneNumber)
            if (core.isNotEmpty()) return core
            return phoneNumber.filter { it.isDigit() }.ifEmpty { phoneNumber.trim() }
        }

        val seenKeys = HashSet<String>((contacts.size + callLog.size).coerceAtLeast(16) * 2)
        val result = ArrayList<DialerSuggestion>(contacts.size + callLog.size)

        callLog.firstOrNull()?.let { recent ->
            val recentCore = PhoneNumberUtils.extractCoreDigits(recent.phoneNumber)
            val recentDigits = recent.phoneNumber.filter { it.isDigit() }
            val matchedContact = contactsByCore[recentCore]
                ?: contactsByDigits[recentDigits]
                ?: contacts.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, recent.phoneNumber) }
            seenKeys.add(fastLookupKey(recent.phoneNumber))
            result.add(
                DialerSuggestion(
                    id = uniqueId("recent_${recent.id}", recent.phoneNumber),
                    displayName = matchedContact?.name ?: recent.contactName?.takeIf { it.isNotBlank() } ?: recent.phoneNumber,
                    phoneNumber = recent.phoneNumber,
                    photoUri = matchedContact?.photoUri ?: recent.photoUri,
                    subtitle = "Last Call • ${recent.formattedDate}",
                    isPriority = matchedContact?.isPriority ?: recent.isPriority,
                    isOwner = matchedContact?.isOwner ?: recent.isOwner,
                    isRecentCall = true,
                    contact = matchedContact
                )
            )
        }

        for (contact in contacts) {
            val key = fastLookupKey(contact.phoneNumber)
            if (seenKeys.add(key)) {
                result.add(
                    DialerSuggestion(
                        id = uniqueId("contact_${contact.id}", contact.phoneNumber),
                        displayName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        photoUri = contact.photoUri,
                        subtitle = contact.label,
                        isPriority = contact.isPriority,
                        isOwner = contact.isOwner,
                        isRecentCall = false,
                        contact = contact
                    )
                )
            }
        }

        for (i in 1 until callLog.size) {
            val recent = callLog[i]
            val key = fastLookupKey(recent.phoneNumber)
            if (seenKeys.add(key)) {
                result.add(
                    DialerSuggestion(
                        id = uniqueId("recent_${recent.id}", recent.phoneNumber),
                        displayName = recent.contactName?.takeIf { it.isNotBlank() } ?: recent.phoneNumber,
                        phoneNumber = recent.phoneNumber,
                        photoUri = recent.photoUri,
                        subtitle = "Recent • ${recent.formattedDate}",
                        isPriority = recent.isPriority,
                        isOwner = recent.isOwner,
                        isRecentCall = true,
                        contact = null
                    )
                )
            }
        }
        return result
    }

    val queryDigits = trimmed.filter { it.isDigit() }
    val coreQuery = PhoneNumberUtils.extractCoreDigits(trimmed)

    data class ScoredSuggestion(val score: Int, val suggestion: DialerSuggestion)

    val scored = mutableListOf<ScoredSuggestion>()
    val seenNumbers = mutableSetOf<String>()

    contacts.forEach { contact ->
        val contactDigits = contact.phoneNumber.filter { it.isDigit() }
        val contactCore = PhoneNumberUtils.extractCoreDigits(contact.phoneNumber)
        val isExact = PhoneNumberUtils.areNumbersEqual(contact.phoneNumber, trimmed)
        val matchesDigits = queryDigits.isNotEmpty() &&
            (contactDigits.contains(queryDigits) || (coreQuery.isNotEmpty() && contactCore.contains(coreQuery)))
        val matchesName = contact.name.contains(trimmed, ignoreCase = true)

        if (isExact || matchesDigits || matchesName) {
            val score = when {
                isExact -> 0
                queryDigits.isNotEmpty() && contactCore.startsWith(queryDigits) -> 1
                queryDigits.isNotEmpty() && contactDigits.startsWith(queryDigits) -> 2
                matchesDigits -> 3
                else -> 4
            }
            val key = contactCore.ifBlank { contact.phoneNumber }
            if (seenNumbers.add(key)) {
                scored.add(
                    ScoredSuggestion(
                        score = score,
                        suggestion = DialerSuggestion(
                            id = uniqueId("contact_${contact.id}", contact.phoneNumber),
                            displayName = contact.name,
                            phoneNumber = contact.phoneNumber,
                            photoUri = contact.photoUri,
                            subtitle = contact.label,
                            isPriority = contact.isPriority,
                            isOwner = contact.isOwner,
                            isRecentCall = false,
                            contact = contact
                        )
                    )
                )
            }
        }
    }

    callLog.forEach { recent ->
        val recentDigits = recent.phoneNumber.filter { it.isDigit() }
        val recentCore = PhoneNumberUtils.extractCoreDigits(recent.phoneNumber)
        val isExact = PhoneNumberUtils.areNumbersEqual(recent.phoneNumber, trimmed)
        val matchesDigits = queryDigits.isNotEmpty() &&
            (recentDigits.contains(queryDigits) || (coreQuery.isNotEmpty() && recentCore.contains(coreQuery)))
        val matchesName = recent.contactName?.contains(trimmed, ignoreCase = true) == true
        val key = recentCore.ifBlank { recent.phoneNumber }

        if ((isExact || matchesDigits || matchesName) && seenNumbers.add(key)) {
            val score = when {
                isExact -> 0
                queryDigits.isNotEmpty() && recentCore.startsWith(queryDigits) -> 1
                matchesDigits -> 3
                else -> 4
            }
            scored.add(
                ScoredSuggestion(
                    score = score,
                    suggestion = DialerSuggestion(
                        id = uniqueId("recent_${recent.id}", recent.phoneNumber),
                        displayName = recent.contactName?.takeIf { it.isNotBlank() } ?: recent.phoneNumber,
                        phoneNumber = recent.phoneNumber,
                        photoUri = recent.photoUri,
                        subtitle = "Recent • ${recent.formattedDate}",
                        isPriority = recent.isPriority,
                        isOwner = recent.isOwner,
                        isRecentCall = true,
                        contact = null
                    )
                )
            )
        }
    }

    return scored
        .sortedWith(compareBy<ScoredSuggestion> { it.score }.thenByDescending { it.suggestion.isPriority }.thenBy { it.suggestion.displayName })
        .map { it.suggestion }
}

val DIAL_KEYS = listOf(
    DialKey("1", "➿"),
    DialKey("2", "ABC"),
    DialKey("3", "DEF"),
    DialKey("4", "GHI"),
    DialKey("5", "JKL"),
    DialKey("6", "MNO"),
    DialKey("7", "PQRS"),
    DialKey("8", "TUV"),
    DialKey("9", "WXYZ"),
    DialKey("*", ""),
    DialKey("0", "+"),
    DialKey("#", "")
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DialerScreen(
    viewModel: LyraMainViewModel,
    onNavigateTab: (String) -> Unit,
    onNavigateToActiveCall: () -> Unit,
    onNavigateToContactDetails: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var numberFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val dialedNumber = numberFieldValue.text
    var showAddContactDialog by rememberSaveable { mutableStateOf(false) }
    var showSimChooserDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCallNumber by rememberSaveable { mutableStateOf("") }
    var isSuggestionsExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSuggestionsExpanded) {
        isSuggestionsExpanded = false
    }

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

    val prefilledNumber by viewModel.prefilledDialNumber.collectAsStateWithLifecycle()
    LaunchedEffect(prefilledNumber) {
        if (!prefilledNumber.isNullOrBlank()) {
            val consumed = viewModel.consumePrefilledDialNumber()
            PhoneNumberUtils.sanitizeDialpadInput(consumed)?.let(setDialNumber)
        }
    }

    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLog by viewModel.callLog.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()
    val preferredSim by viewModel.preferredSim.collectAsStateWithLifecycle()
    val fixDefaultSim by viewModel.fixDefaultSim.collectAsStateWithLifecycle()
    val unreadMissedCount by viewModel.unreadMissedCallCount.collectAsStateWithLifecycle()

    val initiateDialCall: (String) -> Unit = { targetNumber ->
        if (targetNumber.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (shouldPromptForSim(preferredSim, fixDefaultSim, availableSims)) {
                pendingCallNumber = targetNumber
                showSimChooserDialog = true
            } else {
                viewModel.placeCall(
                    phoneNumber = targetNumber,
                    simKeyOverride = preferredSim,
                    onNavigateToOutgoingCall = onNavigateToActiveCall
                )
            }
        }
    }

    val matchingSuggestions by remember(dialedNumber, contacts, callLog) {
        derivedStateOf {
            buildDialerMatchingSuggestions(
                dialedNumber = dialedNumber,
                contacts = contacts,
                callLog = callLog
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LyraAmbientBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("dialer_screen")
        ) {
        // Tighter Top Header for cleaner vertical hierarchy with minimal empty space
        LyraTopBar(
            title = "DIALPAD",
            subtitle = "Dual-SIM Phone & Assistant",
            statusText = "● AI Active",
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact Double-Capsule Dual-SIM selector ('Ask', 'SIM 1', 'SIM 2')
            OuterCapsuleContainer(
                containerColor = colors.surface,
                borderColor = colors.glassBorder,
                contentPadding = PaddingValues(3.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("dialer_sim_capsule_container")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SimCapsuleTab(
                        label = "Ask",
                        isSelected = preferredSim == SimManager.SIM_ASK,
                        onClick = {
                            viewModel.setPreferredSim(SimManager.SIM_ASK)
                            viewModel.setFixDefaultSim(false)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "sim_pill_ask"
                    )
                    SimCapsuleTab(
                        label = "SIM 1",
                        isSelected = preferredSim == SimManager.SIM_1,
                        onClick = {
                            viewModel.setPreferredSim(SimManager.SIM_1)
                            viewModel.setFixDefaultSim(false)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "sim_pill_${SimManager.SIM_1}"
                    )
                    SimCapsuleTab(
                        label = "SIM 2",
                        isSelected = preferredSim == SimManager.SIM_2,
                        onClick = {
                            viewModel.setPreferredSim(SimManager.SIM_2)
                            viewModel.setFixDefaultSim(false)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "sim_pill_${SimManager.SIM_2}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isSuggestionsExpanded) {
                // Expanded vertical list of all matching contacts (hides keypad & covers screen)
                var collapseDragAccumulator by remember { mutableFloatStateOf(0f) }
                Surface(
                    color = colors.surfaceElevated,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { collapseDragAccumulator = 0f },
                                onDragCancel = { collapseDragAccumulator = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    collapseDragAccumulator += dragAmount
                                    if (abs(collapseDragAccumulator) > 36f) {
                                        isSuggestionsExpanded = false
                                        collapseDragAccumulator = 0f
                                    }
                                }
                            )
                        }
                        .clickable { isSuggestionsExpanded = false }
                        .testTag("dialer_collapse_suggestions_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = "Show Keypad",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (dialedNumber.isNotBlank()) {
                                    "Matches for \"$dialedNumber\" (${matchingSuggestions.size})"
                                } else {
                                    "Contacts & Recent (${matchingSuggestions.size})"
                                },
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Keypad",
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse Suggestions",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (matchingSuggestions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("dialer_expanded_contacts_list"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching contacts for \"$dialedNumber\".\nSwipe or tap Keypad to return.",
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("dialer_expanded_contacts_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 14.dp)
                    ) {
                        items(matchingSuggestions, key = { it.id }) { suggestion ->
                            ExpandedSuggestionRow(
                                suggestion = suggestion,
                                onSelect = {
                                    setDialNumber(suggestion.phoneNumber)
                                    isSuggestionsExpanded = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onCallClick = { initiateDialCall(suggestion.phoneNumber) },
                                onAvatarClick = suggestion.contact?.let { contactItem ->
                                    {
                                        viewModel.selectContact(contactItem)
                                        onNavigateToContactDetails(contactItem.phoneNumber)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Bottom-anchored Keypad/Input/Action Controls + Upper Recent/Suggestions Area
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isCompactHeight = maxHeight < 520.dp
                    val lipHeight = 10.dp
                    val inputHeight = if (isCompactHeight) 52.dp else 56.dp
                    val keySize = if (isCompactHeight) 52.dp else 60.dp
                    val keyRowGap = if (isCompactHeight) 6.dp else 8.dp
                    val gridVerticalGap = if (isCompactHeight) 8.dp else 10.dp
                    val actionRowHeight = if (isCompactHeight) 54.dp else 58.dp
                    val bottomAnchorMargin = if (isCompactHeight) 6.dp else 8.dp
                    val opaquePadHeight =
                        inputHeight +
                            gridVerticalGap +
                            (keySize * 4 + keyRowGap * 3) +
                            gridVerticalGap +
                            actionRowHeight +
                            bottomAnchorMargin

                    val exposedHeight = (maxHeight - opaquePadHeight).coerceAtLeast(60.dp)
                    val slotGap = 8.dp
                    val slotCount = ((exposedHeight + slotGap) / 68.dp).roundToInt().coerceAtLeast(2)
                    val dynamicCardHeight =
                        ((exposedHeight - slotGap * (slotCount - 1)) / (slotCount - 0.27f))
                            .coerceIn(54.dp, 68.dp)
                    val upperSuggestions = matchingSuggestions.take(slotCount)
                    val fillsUpperToLip = upperSuggestions.size == slotCount && upperSuggestions.size >= 2

                    var dragAccumulator by remember { mutableFloatStateOf(0f) }

                    // Upper Space: Recent calls & contact suggestions filling cleanly from SIM capsule down to number pad
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(exposedHeight)
                            .align(Alignment.TopCenter)
                            .pointerInput(matchingSuggestions.isNotEmpty()) {
                                detectVerticalDragGestures(
                                    onDragEnd = { dragAccumulator = 0f },
                                    onDragCancel = { dragAccumulator = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragAccumulator += dragAmount
                                        if (abs(dragAccumulator) > 36f) {
                                            isSuggestionsExpanded = true
                                            dragAccumulator = 0f
                                        }
                                    }
                                )
                            }
                            .testTag("dialer_suggestion_area")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true),
                            verticalArrangement = Arrangement.spacedBy(slotGap)
                        ) {
                            upperSuggestions.forEachIndexed { index, suggestion ->
                                val isBottomEdgeFaded = fillsUpperToLip && index == upperSuggestions.lastIndex
                                val cardTag = when {
                                    index == 0 -> "dialer_top_suggestion_card"
                                    isBottomEdgeFaded -> "dialer_bottom_faded_suggestion_card"
                                    else -> "dialer_suggestion_card_$index"
                                }
                                val callBtnTag = when {
                                    index == 0 -> "dialer_suggestion_call_btn"
                                    isBottomEdgeFaded -> "dialer_bottom_faded_suggestion_call_btn"
                                    else -> "dialer_suggestion_call_btn_$index"
                                }

                                val baseModifier = Modifier
                                    .fillMaxWidth()
                                    .height(dynamicCardHeight)

                                val finalModifier = if (isBottomEdgeFaded) {
                                    baseModifier
                                        .graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                            alpha = 0.80f
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colorStops = arrayOf(
                                                        0.0f to Color.Black,
                                                        0.45f to Color.Black.copy(alpha = 0.85f),
                                                        0.74f to Color.Black.copy(alpha = 0.28f),
                                                        1.0f to Color.Transparent
                                                    )
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                        .testTag(cardTag)
                                } else {
                                    baseModifier.testTag(cardTag)
                                }

                                TopDialerSuggestionCard(
                                    suggestion = suggestion,
                                    onFillNumber = {
                                        setDialNumber(suggestion.phoneNumber)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    onQuickCall = { initiateDialCall(suggestion.phoneNumber) },
                                    onExpandClick = { isSuggestionsExpanded = true },
                                    showExpandButton = index == 0,
                                    modifier = finalModifier,
                                    callButtonTestTag = callBtnTag
                                )
                            }
                        }
                    }

                    // Bottom-Anchored Foreground Number Pad Section: feathered lip + fixed number display, keypad & actions near footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lipHeight)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            colors.background.copy(alpha = 0.72f),
                                            colors.background
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(opaquePadHeight)
                                .background(colors.background)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Strictly fixed Number Input Capsule
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(inputHeight)
                                    .testTag("dialer_number_container"),
                                color = colors.surfaceElevated,
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    if (dialedNumber.isNotEmpty()) colors.primary.copy(alpha = 0.55f) else colors.glassBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(40.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (dialedNumber.isEmpty()) {
                                            Text(
                                                text = "Dial number using keypad",
                                                color = colors.textMuted.copy(alpha = 0.65f),
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp,
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
                                                        dialedNumber.length > 15 -> 20.sp
                                                        dialedNumber.length > 10 -> 24.sp
                                                        else -> 28.sp
                                                    },
                                                    lineHeight = 34.sp,
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
                                                    .testTag("dialer_number_display")
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier.size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (dialedNumber.isEmpty() && clipboardManager.hasText()) {
                                            IconButton(
                                                onClick = {
                                                    val rawClipboard = clipboardManager.getText()?.text.orEmpty()
                                                    val validNumber = PhoneNumberUtils.sanitizeDialpadInput(rawClipboard)
                                                    if (validNumber != null) {
                                                        setDialNumber(validNumber)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (rawClipboard.isNotBlank()) {
                                                        Toast.makeText(
                                                            context,
                                                            "Only numeric phone numbers can be pasted",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                modifier = Modifier.size(40.dp).testTag("dialer_paste_btn")
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
                                                modifier = Modifier.size(40.dp).testTag("dialer_clear_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear number",
                                                    tint = colors.textMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(gridVerticalGap))

                            // Strictly fixed 3-column Keypad Grid aligned vertically with bottom Action Row
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .testTag("dialer_keypad_grid"),
                                verticalArrangement = Arrangement.spacedBy(keyRowGap),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (row in 0..3) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (col in 0..2) {
                                            val index = row * 3 + col
                                            val key = DIAL_KEYS[index]

                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                DialpadButton(
                                                    key = key,
                                                    buttonSize = keySize,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        insertDialToken(key.digit)
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (key.digit == "0") {
                                                            insertDialToken("+")
                                                        } else if (key.digit == "1") {
                                                            setDialNumber("*86")
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(gridVerticalGap))

                            // Clean horizontal Action Row aligned directly beneath the 3 keypad columns & close to footer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(actionRowHeight)
                                    .padding(horizontal = 12.dp)
                                    .testTag("dialer_action_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Column: Add to Contacts (aligned under 1/4/7/*)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        onClick = { showAddContactDialog = true },
                                        enabled = dialedNumber.isNotBlank(),
                                        shape = CircleShape,
                                        color = colors.surfaceElevated,
                                        border = BorderStroke(1.dp, colors.glassBorder),
                                        modifier = Modifier
                                            .size(50.dp)
                                            .alpha(if (dialedNumber.isNotBlank()) 1f else 0.32f)
                                            .testTag("dialer_add_contact_btn")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PersonAdd,
                                                contentDescription = "Add to Contacts",
                                                tint = colors.primary,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                    }
                                }

                                // Center Column: Emerald Call Capsule (aligned under 2/5/8/0)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        onClick = { initiateDialCall(dialedNumber) },
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .width(108.dp)
                                            .height(actionRowHeight)
                                            .testTag("dialer_call_btn")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(EmeraldAccept, EmeraldAccept.copy(alpha = 0.85f))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = "Place Call",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                if (preferredSim != SimManager.SIM_ASK) {
                                                    Text(
                                                        text = preferredSim.replace("_", ""),
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Right Column: Delete / Backspace (aligned under 3/6/9/#)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .alpha(if (dialedNumber.isNotEmpty()) 1f else 0.35f)
                                            .clip(CircleShape)
                                            .background(colors.surfaceElevated)
                                            .border(1.dp, colors.glassBorder, CircleShape)
                                            .combinedClickable(
                                                enabled = dialedNumber.isNotEmpty(),
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    backspaceDialToken()
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    setDialNumber("")
                                                }
                                            )
                                            .testTag("dialer_backspace_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(bottomAnchorMargin))
                        }
                    }
                }
            }
        }

        // Strictly fixed Bottom Navigation Footer
        LyraBottomNavBar(
            currentRoute = LyraDestinations.DIALER,
            onNavigate = onNavigateTab,
            unreadMissedCount = unreadMissedCount
        )
    }
    }

    // SIM Chooser Dialog (shown when "Ask Every Time" is selected)
    if (showSimChooserDialog) {
        val numberForSimDialog = pendingCallNumber.ifBlank { dialedNumber }
        SimChooserDialog(
            phoneNumber = numberForSimDialog,
            availableSims = availableSims,
            onSelectSim = { simKey, fixForFuture ->
                if (fixForFuture) {
                    viewModel.setPreferredSim(simKey)
                    viewModel.setFixDefaultSim(true)
                }
                showSimChooserDialog = false
                viewModel.placeCall(
                    phoneNumber = numberForSimDialog,
                    simKeyOverride = simKey,
                    onNavigateToOutgoingCall = onNavigateToActiveCall
                )
            },
            onDismiss = { showSimChooserDialog = false }
        )
    }

    // Add Contact Dialog
    if (showAddContactDialog) {
        var nameInput by rememberSaveable { mutableStateOf("") }
        var isPriorityCheck by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = {
                Text(
                    text = "Save to Contacts",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Number: $dialedNumber",
                        color = colors.primaryGlow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_contact_name_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isPriorityCheck = !isPriorityCheck }
                    ) {
                        Checkbox(
                            checked = isPriorityCheck,
                            onCheckedChange = { isPriorityCheck = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AmberPriority,
                                uncheckedColor = colors.textMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mark as Priority Contact",
                            color = colors.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.addContactFromDialer(
                                name = nameInput,
                                phoneNumber = dialedNumber,
                                isPriority = isPriorityCheck
                            )
                            showAddContactDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier.testTag("save_contact_confirm_btn")
                ) {
                    Text("Save", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            },
            containerColor = colors.surfaceElevated,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun TopDialerSuggestionCard(
    suggestion: DialerSuggestion,
    onFillNumber: () -> Unit,
    onQuickCall: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    showExpandButton: Boolean = true,
    callButtonTestTag: String = "dialer_suggestion_call_btn"
) {
    val colors = LyraTheme.colors
    Surface(
        onClick = onFillNumber,
        shape = RoundedCornerShape(22.dp),
        color = colors.glassSurface,
        border = BorderStroke(
            1.dp,
            when {
                suggestion.isOwner -> colors.primary.copy(alpha = 0.55f)
                suggestion.isPriority -> AmberPriority.copy(alpha = 0.45f)
                else -> colors.glassBorder
            }
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            ) {
                LyraContactAvatar(
                    name = suggestion.displayName,
                    photoUri = suggestion.photoUri,
                    isOwner = suggestion.isOwner,
                    isPriority = suggestion.isPriority,
                    size = 38.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = suggestion.displayName,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (suggestion.isRecentCall) {
                            CapsuleStatusChip(text = "Recent", color = colors.secondary)
                        } else if (suggestion.isPriority) {
                            CapsuleStatusChip(text = "★ VIP", color = AmberPriority)
                        }
                    }
                    Text(
                        text = "${suggestion.phoneNumber} • ${suggestion.subtitle}",
                        color = colors.primaryGlow,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showExpandButton) {
                    IconButton(
                        onClick = onExpandClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("dialer_suggestion_expand_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand matching contacts",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                GreenCallActionCapsule(
                    onClick = onQuickCall,
                    contentDescription = "Call ${suggestion.displayName}",
                    modifier = Modifier.testTag(callButtonTestTag)
                )
            }
        }
    }
}

@Composable
private fun ExpandedSuggestionRow(
    suggestion: DialerSuggestion,
    onSelect: () -> Unit,
    onCallClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null
) {
    val colors = LyraTheme.colors
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(22.dp),
        color = colors.glassSurface,
        border = BorderStroke(
            1.dp,
            when {
                suggestion.isOwner -> colors.primary.copy(alpha = 0.45f)
                suggestion.isPriority -> AmberPriority.copy(alpha = 0.35f)
                else -> colors.glassBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dialer_expanded_item_${suggestion.id}")
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
                    name = suggestion.displayName,
                    photoUri = suggestion.photoUri,
                    isOwner = suggestion.isOwner,
                    isPriority = suggestion.isPriority,
                    size = 44.dp,
                    modifier = if (onAvatarClick != null) {
                        Modifier.clickable(onClick = onAvatarClick)
                    } else {
                        Modifier
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = suggestion.displayName,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (suggestion.isOwner) {
                            CapsuleStatusChip(text = "Owner", color = colors.primary)
                        } else if (suggestion.isPriority) {
                            CapsuleStatusChip(text = "★ VIP", color = AmberPriority)
                        }
                    }
                    Text(
                        text = suggestion.phoneNumber,
                        color = colors.primaryGlow,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = suggestion.subtitle,
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            GreenCallActionCapsule(
                onClick = onCallClick,
                contentDescription = "Call ${suggestion.displayName}"
            )
        }
    }
}

@Composable
private fun SimCapsuleTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val colors = LyraTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "sim_bg"
    )
    val borderColor = if (isSelected) colors.primary.copy(alpha = 0.55f) else Color.Transparent
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SimCard,
                contentDescription = label,
                tint = if (isSelected) colors.primary else colors.textMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (isSelected) colors.primary else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadButton(
    key: DialKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    buttonSize: Dp = 60.dp
) {
    val colors = LyraTheme.colors
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(colors.surfaceElevated, CircleShape)
            .border(1.dp, colors.glassBorder, CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("dial_key_${key.digit}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit,
                color = colors.textPrimary,
                fontSize = if (key.digit in listOf("*", "#")) 26.sp else 23.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

