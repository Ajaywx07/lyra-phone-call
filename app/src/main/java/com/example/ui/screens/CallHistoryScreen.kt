package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.navigation.LyraDestinations
import com.example.ui.components.CapsuleButton
import com.example.ui.components.CapsuleFilterBar
import com.example.ui.components.CapsuleSearchBar
import com.example.ui.components.ContactProfileSheet
import com.example.ui.components.EditContactDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.ListItemContextMenuDialog
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraBottomNavBar
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.components.RecentCallCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SimChooserDialog
import com.example.ui.components.shouldPromptForSim
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class GroupedRecentsEntry(
    val representative: CallHistoryItemData,
    val calls: List<CallHistoryItemData>
) {
    val callCount: Int get() = calls.size
}

internal data class RecentsDateGroup(
    val dateKey: String,
    val dateLabel: String,
    val entries: List<GroupedRecentsEntry>,
    val totalCallCount: Int
)

internal fun buildRecentsDateGroups(
    calls: List<CallHistoryItemData>,
    nowMillis: Long = System.currentTimeMillis()
): List<RecentsDateGroup> {
    if (calls.isEmpty()) return emptyList()

    val todayCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val yesterdayCal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val itemCal = Calendar.getInstance()
    val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

    val sortedCalls = calls.sortedByDescending { it.timestamp }
    val byDay = LinkedHashMap<String, Pair<String, MutableList<CallHistoryItemData>>>()

    for (call in sortedCalls) {
        itemCal.timeInMillis = call.timestamp
        val year = itemCal.get(Calendar.YEAR)
        val dayOfYear = itemCal.get(Calendar.DAY_OF_YEAR)
        val dateKey = "$year-$dayOfYear"

        val bucket = byDay.getOrPut(dateKey) {
            val label = when {
                year == todayCal.get(Calendar.YEAR) && dayOfYear == todayCal.get(Calendar.DAY_OF_YEAR) -> "Today"
                year == yesterdayCal.get(Calendar.YEAR) && dayOfYear == yesterdayCal.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                else -> dateFormatter.format(Date(call.timestamp))
            }
            label to mutableListOf()
        }
        bucket.second.add(call)
    }

    return byDay.entries.map { (dateKey, pair) ->
        val (dateLabel, dayCalls) = pair
        val byContactOnDay = LinkedHashMap<String, MutableList<CallHistoryItemData>>()
        for (call in dayCalls) {
            val core = PhoneNumberUtils.extractCoreDigits(call.phoneNumber)
            val contactKey = core.ifBlank { call.phoneNumber.ifBlank { call.id } }
            byContactOnDay.getOrPut(contactKey) { mutableListOf() }.add(call)
        }
        val groupedEntries = byContactOnDay.values.map { list ->
            GroupedRecentsEntry(representative = list.first(), calls = list)
        }
        RecentsDateGroup(
            dateKey = dateKey,
            dateLabel = dateLabel,
            entries = groupedEntries,
            totalCallCount = dayCalls.size
        )
    }
}

private enum class RecentsFilterTab(val label: String, val filter: CallTypeEnum?) {
    ALL("All", null),
    INCOMING("Incoming", CallTypeEnum.INCOMING),
    OUTGOING("Outgoing", CallTypeEnum.OUTGOING),
    MISSED("Missed", CallTypeEnum.MISSED)
}

@Composable
fun CallHistoryScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit,
    onNavigateTab: ((String) -> Unit)? = null,
    onOpenContactDetails: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val callList by viewModel.filteredCallLog.collectAsStateWithLifecycle()
    val allCallLogs by viewModel.callLog.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val activeFilter by viewModel.callLogFilter.collectAsStateWithLifecycle()
    val perms by viewModel.permissionsState.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()
    val preferredSim by viewModel.preferredSim.collectAsStateWithLifecycle()
    val fixDefaultSim by viewModel.fixDefaultSim.collectAsStateWithLifecycle()
    val unreadMissedCount by viewModel.unreadMissedCallCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.markMissedCallsAsRead()
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeNoteItem by remember { mutableStateOf<CallHistoryItemData?>(null) }
    var noteInputText by remember { mutableStateOf("") }
    var selectedDetailItem by remember { mutableStateOf<CallHistoryItemData?>(null) }
    var contextMenuItem by remember { mutableStateOf<CallHistoryItemData?>(null) }
    var pendingDeleteCallLog by remember { mutableStateOf<CallHistoryItemData?>(null) }
    var editingContact by remember { mutableStateOf<ContactItemData?>(null) }
    var pendingSimCallNumber by remember { mutableStateOf<String?>(null) }

    val displayedCalls by remember(callList, searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isBlank()) {
                callList
            } else {
                callList.filter { item ->
                    (item.contactName?.contains(query, ignoreCase = true) == true) ||
                        item.phoneNumber.contains(query, ignoreCase = true) ||
                        item.callType.name.contains(query, ignoreCase = true)
                }
            }
        }
    }

    val groupedCallsByDate by remember(displayedCalls) {
        derivedStateOf {
            buildRecentsDateGroups(displayedCalls)
        }
    }

    val contactsByCore = remember(contacts) {
        val map = HashMap<String, ContactItemData>(contacts.size * 2)
        for (contact in contacts) {
            val core = PhoneNumberUtils.extractCoreDigits(contact.phoneNumber)
            if (core.isNotEmpty() && !map.containsKey(core)) {
                map[core] = contact
            }
        }
        map
    }

    val resolveContactForHistoryItem: (CallHistoryItemData) -> ContactItemData = { item ->
        val core = PhoneNumberUtils.extractCoreDigits(item.phoneNumber)
        val fastMatch = if (core.isNotEmpty()) contactsByCore[core] else null
        val matched = fastMatch ?: contacts.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, item.phoneNumber) }
        matched ?: ContactItemData(
            id = "c_${core.ifBlank { item.id }}",
            name = item.contactName?.takeIf { it.isNotBlank() } ?: item.phoneNumber,
            phoneNumber = item.phoneNumber,
            photoUri = item.photoUri,
            label = if (item.isOwner) "Owner" else if (item.isPriority) "Priority" else "Recent",
            isPriority = item.isPriority,
            isOwner = item.isOwner,
            isDemoData = item.isDemoData
        )
    }

    val startCallWithSimCheck: (String) -> Unit = { number ->
        if (number.isNotBlank()) {
            if (shouldPromptForSim(preferredSim, fixDefaultSim, availableSims)) {
                pendingSimCallNumber = number
            } else {
                viewModel.placeCall(phoneNumber = number, simKeyOverride = preferredSim)
            }
        }
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.loadData()
    }

    val selectedTab = when (activeFilter) {
        null -> RecentsFilterTab.ALL
        CallTypeEnum.INCOMING -> RecentsFilterTab.INCOMING
        CallTypeEnum.OUTGOING -> RecentsFilterTab.OUTGOING
        CallTypeEnum.MISSED, CallTypeEnum.REJECTED -> RecentsFilterTab.MISSED
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
                .testTag("call_history_screen")
        ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                LyraTopBar(
                    title = stringResource(R.string.recents_title),
                    subtitle = stringResource(R.string.recents_subtitle),
                    showBack = onNavigateTab == null,
                    onBackClick = onBackClick,
                    statusText = "${displayedCalls.size} Calls"
                )
            }

            if (!perms.callLogAllowed) {
                item {
                    GlassCard(borderColor = AmberPriority.copy(alpha = 0.5f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneCallback,
                                    contentDescription = null,
                                    tint = AmberPriority,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Call Log Access Needed",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant Call Log permission so Lyra displays real incoming, outgoing, missed, and rejected calls with contact photos.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            CapsuleButton(
                                text = "Grant Call Log Permission",
                                onClick = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                                modifier = Modifier.testTag("grant_call_log_btn")
                            )
                        }
                    }
                }
            }

            // Pill Search Bar
            item {
                CapsuleSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search recent callers or numbers...",
                    testTag = "recents_search_input"
                )
            }

            // Double-Capsule 4-Filter Selector (All, Incoming, Outgoing, Missed)
            item {
                CapsuleFilterBar(
                    items = RecentsFilterTab.entries,
                    selectedItem = selectedTab,
                    labelProvider = { it.label },
                    onSelect = { tab -> viewModel.setCallLogFilter(tab.filter) },
                    testTagPrefix = "recents_filter"
                )
            }

            if (groupedCallsByDate.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = "No Calls Found",
                        description = if (searchQuery.isNotBlank()) {
                            "No call logs matching \"$searchQuery\"."
                        } else {
                            "No call logs matching the current filter.\nMake or receive calls to view logs here."
                        }
                    )
                }
            } else {
                groupedCallsByDate.forEach { dateGroup ->
                    item(key = "header_${dateGroup.dateKey}") {
                        SectionHeader(
                            title = "${dateGroup.dateLabel} (${dateGroup.totalCallCount})",
                            modifier = Modifier.testTag("recents_date_header_${dateGroup.dateKey}")
                        )
                    }
                    items(
                        items = dateGroup.entries,
                        key = { entry -> "${dateGroup.dateKey}_${entry.representative.id}_${entry.representative.phoneNumber}" }
                    ) { entry ->
                        val representative = entry.representative
                        RecentCallCard(
                            item = representative,
                            callCount = entry.callCount,
                            onCallClick = { startCallWithSimCheck(representative.phoneNumber) },
                            onItemClick = { selectedDetailItem = representative },
                            onLongClick = { contextMenuItem = representative }
                        )
                    }
                }
            }
        }

        if (onNavigateTab != null) {
            LyraBottomNavBar(
                currentRoute = LyraDestinations.CALL_HISTORY,
                onNavigate = onNavigateTab,
                unreadMissedCount = unreadMissedCount
            )
        }
    }
    }

    // Long-press Context Menu Dialog (Edit / Delete; delete hidden when Priority switch is ON)
    contextMenuItem?.let { target ->
        val resolvedTarget = resolveContactForHistoryItem(target)
        val isPriorityProtected = target.isOwner ||
            target.isPriority ||
            resolvedTarget.isOwner ||
            resolvedTarget.isPriority
        ListItemContextMenuDialog(
            title = target.contactName ?: target.phoneNumber,
            subtitle = "${target.callType.name.lowercase().replaceFirstChar { it.uppercase() }} • ${target.phoneNumber}",
            canDelete = !isPriorityProtected,
            deleteLabel = "Delete Call Log Entry",
            onEditClick = {
                editingContact = resolvedTarget
            },
            onDeleteClick = {
                pendingDeleteCallLog = target
            },
            onDismiss = { contextMenuItem = null }
        )
    }

    pendingDeleteCallLog?.let { target ->
        val displayCaller = target.contactName?.takeIf { it.isNotBlank() } ?: target.phoneNumber
        LyraDeleteConfirmationDialog(
            title = "Delete Call Log Entry?",
            message = "Are you sure you want to remove this call log entry for $displayCaller?",
            onConfirm = {
                viewModel.deleteCallLogEntry(target.id)
                pendingDeleteCallLog = null
            },
            onDismiss = { pendingDeleteCallLog = null }
        )
    }

    // Rich Contact Profile Bottom Sheet on Click (complete chronological history, no delete call log button)
    selectedDetailItem?.let { detail ->
        val resolvedContact = resolveContactForHistoryItem(detail)
        val matchingNotes = allNotes.filter {
            PhoneNumberUtils.areNumbersEqual(it.callerNumber, detail.phoneNumber) ||
                (!detail.contactName.isNullOrBlank() && it.callerName.equals(detail.contactName, ignoreCase = true))
        }
        val numberHistory = allCallLogs
            .filter { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, detail.phoneNumber) }
            .sortedByDescending { it.timestamp }
        val cleanType = detail.callType.name.lowercase().replaceFirstChar { it.uppercase() }
        val callMeta = buildString {
            append(cleanType)
            if (!detail.simLabel.isNullOrBlank()) {
                append(" • ")
                append(detail.simLabel)
            }
            append(" • ")
            append(detail.formattedDate)
            if (detail.durationSeconds > 0L && detail.formattedDuration.isNotBlank()) {
                append(" • ")
                append(detail.formattedDuration)
            }
        }

        ContactProfileSheet(
            name = resolvedContact.name,
            phoneNumber = detail.phoneNumber,
            photoUri = resolvedContact.photoUri ?: detail.photoUri,
            label = resolvedContact.label,
            isOwner = resolvedContact.isOwner,
            isPriority = resolvedContact.isPriority,
            callMetadata = callMeta,
            callHistory = numberHistory,
            notes = matchingNotes,
            onCallClick = {
                val numberToCall = detail.phoneNumber
                selectedDetailItem = null
                startCallWithSimCheck(numberToCall)
            },
            onSmsClick = {
                selectedDetailItem = null
                viewModel.initiateSms(detail.phoneNumber)
            },
            onQuickAutoSmsClick = {
                selectedDetailItem = null
                viewModel.sendQuickResponseSms(
                    phoneNumber = detail.phoneNumber,
                    message = "I'll call you back soon."
                )
            },
            onAddNoteClick = {
                selectedDetailItem = null
                activeNoteItem = detail
                noteInputText = ""
            },
            onEditClick = {
                selectedDetailItem = null
                editingContact = resolvedContact
            },
            onWhatsAppClick = {
                val number = detail.phoneNumber
                selectedDetailItem = null
                com.example.utils.ExternalIntentHelper.openWhatsAppChat(context, number)
            },
            onTogglePriorityClick = {
                viewModel.toggleContactPriority(resolvedContact)
            },
            onOpenFullProfileClick = {
                selectedDetailItem = null
                viewModel.selectContact(resolvedContact)
                onOpenContactDetails()
            },
            onDismiss = { selectedDetailItem = null }
        )
    }

    editingContact?.let { target ->
        EditContactDialog(
            contact = target,
            onDismiss = { editingContact = null },
            onSave = { newName, newPhone, newPhotoUri, isPriority ->
                viewModel.updateContact(
                    original = target,
                    newName = newName,
                    newPhone = newPhone,
                    newPhotoUri = newPhotoUri,
                    isPriority = isPriority
                )
                editingContact = null
            }
        )
    }

    // Add Note to Call Log Dialog
    activeNoteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { activeNoteItem = null },
            title = {
                Text(
                    text = "Add Note for ${item.contactName ?: item.phoneNumber}",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Call on ${item.formattedDate} (${item.formattedDuration})",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
                        placeholder = { Text("Enter meeting notes, follow-up item...", color = colors.textMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteInputText.isNotBlank()) {
                            viewModel.saveCallNote(
                                callerNumber = item.phoneNumber,
                                callerName = item.contactName ?: "Caller",
                                text = noteInputText
                            )
                        }
                        activeNoteItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save Note", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeNoteItem = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    // SIM Chooser Dialog for Recents
    pendingSimCallNumber?.let { targetNumber ->
        SimChooserDialog(
            phoneNumber = targetNumber,
            availableSims = availableSims,
            onSelectSim = { simKey, fixForFuture ->
                if (fixForFuture) {
                    viewModel.setPreferredSim(simKey)
                    viewModel.setFixDefaultSim(true)
                }
                pendingSimCallNumber = null
                viewModel.placeCall(
                    phoneNumber = targetNumber,
                    simKeyOverride = simKey
                )
            },
            onDismiss = { pendingSimCallNumber = null }
        )
    }
}
