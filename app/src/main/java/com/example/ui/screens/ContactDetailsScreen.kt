package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CallNote
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.ui.components.BadgeType
import com.example.ui.components.CallActionButton
import com.example.ui.components.CapsuleGroupDivider
import com.example.ui.components.CapsuleOutlinedButton
import com.example.ui.components.CapsuleSettingsGroup
import com.example.ui.components.CapsuleToggle
import com.example.ui.components.EditContactDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraContactAvatar
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SimChooserDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.shouldPromptForSim
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactDetailsScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit
) {
    val colors = LyraTheme.colors
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val allCallLogs by viewModel.callLog.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()
    val preferredSim by viewModel.preferredSim.collectAsStateWithLifecycle()
    val fixDefaultSim by viewModel.fixDefaultSim.collectAsStateWithLifecycle()

    val contact = selectedContact ?: ContactItemData(
        id = "demo_1",
        name = "Ajay",
        phoneNumber = "+91 98765 43210",
        label = "Mobile",
        isPriority = true,
        isOwner = true,
        isDemoData = true
    )

    val contactNotes = remember(allNotes, contact.phoneNumber, contact.name) {
        allNotes.filter {
            PhoneNumberUtils.areNumbersEqual(it.callerNumber, contact.phoneNumber) ||
                it.callerName.contains(contact.name, ignoreCase = true)
        }
    }

    val contactCallHistory = remember(allCallLogs, contact.phoneNumber) {
        allCallLogs
            .filter { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, contact.phoneNumber) }
            .sortedByDescending { it.timestamp }
    }
    val historyDateFormatter = remember {
        SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault())
    }

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showEditContactDialog by remember { mutableStateOf(false) }
    var showDeleteContactConfirm by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<CallNote?>(null) }
    var pendingSimCallNumber by remember { mutableStateOf<String?>(null) }
    var newNoteText by remember { mutableStateOf("") }
    var vipRingtoneEnabled by remember(contact.isPriority) { mutableStateOf(contact.isPriority) }
    var autoSummarizeEnabled by remember { mutableStateOf(true) }

    val startCallWithSimCheck: (String) -> Unit = { number ->
        if (number.isNotBlank()) {
            if (shouldPromptForSim(preferredSim, fixDefaultSim, availableSims)) {
                pendingSimCallNumber = number
            } else {
                viewModel.placeCall(phoneNumber = number, simKeyOverride = preferredSim)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LyraAmbientBackdrop()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag("contact_details_screen"),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            LyraTopBar(
                title = "CONTACT PROFILE",
                subtitle = "Lyra Intelligence File",
                showBack = true,
                onBackClick = onBackClick,
                trailingActions = {
                    IconButton(
                        onClick = { showEditContactDialog = true },
                        modifier = Modifier.testTag("top_bar_edit_contact_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit contact",
                            tint = colors.primary
                        )
                    }
                }
            )
        }

        // Profile Card
        item {
            GlassCard(contentPadding = PaddingValues(20.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LyraContactAvatar(
                        name = contact.name,
                        photoUri = contact.photoUri,
                        isOwner = contact.isOwner,
                        isPriority = contact.isPriority,
                        size = 92.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = contact.name,
                        color = colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = contact.phoneNumber,
                        color = colors.primaryGlow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (!contact.email.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = contact.email,
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (contact.isOwner) {
                            StatusBadge(type = BadgeType.OWNER)
                        } else if (contact.isPriority) {
                            StatusBadge(type = BadgeType.PRIORITY)
                        } else {
                            StatusBadge(type = BadgeType.ALLOWED, customText = contact.label)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons: Call, SMS, Note, Edit, Star Priority
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = Icons.Default.Call,
                            label = "Call",
                            onClick = { startCallWithSimCheck(contact.phoneNumber) },
                            containerColor = EmeraldAccept,
                            contentColor = Color.White,
                            size = 48.dp,
                            iconSize = 20.dp
                        )

                        CallActionButton(
                            icon = Icons.Default.Message,
                            label = "SMS",
                            onClick = { viewModel.initiateSms(contact.phoneNumber) },
                            containerColor = colors.primary,
                            contentColor = colors.onPrimaryButton,
                            size = 48.dp,
                            iconSize = 20.dp
                        )

                        CallActionButton(
                            icon = Icons.Default.NoteAdd,
                            label = "Note",
                            onClick = { showAddNoteDialog = true },
                            containerColor = colors.surfaceElevated,
                            contentColor = colors.primary,
                            size = 48.dp,
                            iconSize = 20.dp
                        )

                        CallActionButton(
                            icon = Icons.Default.Edit,
                            label = "Edit",
                            onClick = { showEditContactDialog = true },
                            containerColor = colors.secondary,
                            contentColor = colors.onSecondaryButton,
                            size = 48.dp,
                            iconSize = 20.dp
                        )

                        CallActionButton(
                            icon = if (contact.isPriority) Icons.Default.Star else Icons.Outlined.StarBorder,
                            label = if (contact.isPriority) "Priority" else "VIP",
                            onClick = { viewModel.toggleContactPriority(contact) },
                            containerColor = if (contact.isPriority) AmberPriority else colors.surfaceElevated,
                            contentColor = if (contact.isPriority) Color.Black else colors.textSecondary,
                            size = 48.dp,
                            iconSize = 20.dp
                        )
                    }

                    if (!contact.isOwner && !contact.isPriority) {
                        Spacer(modifier = Modifier.height(14.dp))
                        CapsuleOutlinedButton(
                            text = "Delete Contact",
                            icon = Icons.Default.Delete,
                            accentColor = CrimsonDecline,
                            onClick = { showDeleteContactConfirm = true },
                            modifier = Modifier.fillMaxWidth().testTag("contact_details_delete_btn")
                        )
                    }
                }
            }
        }

        // Assistant Behavior Rules for Contact
        item {
            SectionHeader(title = "Lyra Assistant Rules")

            CapsuleSettingsGroup {
                CapsuleToggle(
                    title = "VIP Priority Alert",
                    subtitle = "Bypass silent screener and alert owner immediately",
                    icon = Icons.Default.Star,
                    checked = vipRingtoneEnabled,
                    onCheckedChange = { vipRingtoneEnabled = it },
                    accentColor = AmberPriority
                )
                CapsuleGroupDivider()
                CapsuleToggle(
                    title = "Auto-Summarize Calls",
                    subtitle = "Generate smart AI notes whenever speaking with this contact",
                    icon = Icons.Default.NoteAdd,
                    checked = autoSummarizeEnabled,
                    onCheckedChange = { autoSummarizeEnabled = it }
                )
            }
        }

        // Complete Call History for this number (newest to oldest, no date restriction)
        item {
            SectionHeader(
                title = "Call History (${contactCallHistory.size})",
                modifier = Modifier.testTag("contact_details_call_history_section")
            )
        }

        if (contactCallHistory.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "No recorded calls for ${contact.phoneNumber} yet.",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(contactCallHistory, key = { "detail_call_${it.id}_${it.timestamp}" }) { entry ->
                val (typeIcon, typeColor, typeLabel) = when (entry.callType) {
                    CallTypeEnum.INCOMING -> Triple(Icons.Default.CallReceived, EmeraldAccept, "Incoming")
                    CallTypeEnum.OUTGOING -> Triple(Icons.Default.CallMade, colors.primary, "Outgoing")
                    CallTypeEnum.MISSED -> Triple(Icons.Default.CallMissed, CrimsonDecline, "Missed")
                    CallTypeEnum.REJECTED -> Triple(Icons.Default.CallEnd, CrimsonDecline, "Rejected")
                }

                GlassCard(
                    borderColor = colors.glassBorder,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.testTag("contact_details_history_row_${entry.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = typeColor.copy(alpha = 0.14f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = typeIcon,
                                        contentDescription = typeLabel,
                                        tint = typeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        color = if (entry.callType == CallTypeEnum.MISSED || entry.callType == CallTypeEnum.REJECTED) {
                                            CrimsonDecline
                                        } else {
                                            colors.textPrimary
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    entry.simLabel?.takeIf { it.isNotBlank() }?.let { sim ->
                                        Surface(
                                            color = colors.surfaceElevated,
                                            shape = CircleShape,
                                            border = BorderStroke(0.8.dp, colors.glassBorder)
                                        ) {
                                            Text(
                                                text = sim,
                                                color = colors.textSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                                    .testTag("details_sim_badge_${entry.id}")
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = historyDateFormatter.format(Date(entry.timestamp)),
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            color = typeColor.copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = entry.formattedDuration,
                                color = typeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Notes History with this Contact
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader(title = "Saved Notes (${contactNotes.size})")

                TextButton(
                    onClick = { showAddNoteDialog = true },
                    modifier = Modifier.testTag("add_contact_note_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAdd,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Note", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (contactNotes.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "No notes recorded for this contact yet.\nTap 'Add Note' or take notes during an active call.",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(contactNotes, key = { it.id }) { note ->
                GlassCard(
                    borderColor = colors.glassBorder,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = note.noteText,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tag: ${note.tag} • Local Storage",
                                color = colors.textMuted,
                                fontSize = 10.sp
                            )
                        }

                        if (!contact.isOwner && !contact.isPriority) {
                            IconButton(
                                onClick = { pendingDeleteNote = note },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("delete_note_btn_${note.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Add Note Dialog
    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = {
                Text(
                    text = "Add Note for ${contact.name}",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newNoteText,
                    onValueChange = { newNoteText = it },
                    placeholder = { Text("E.g. Medical history, project milestones...", color = colors.textMuted, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNoteText.isNotBlank()) {
                            viewModel.saveCallNote(
                                callerNumber = contact.phoneNumber,
                                callerName = contact.name,
                                text = newNoteText
                            )
                            newNoteText = ""
                        }
                        showAddNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    if (showEditContactDialog) {
        EditContactDialog(
            contact = contact,
            onDismiss = { showEditContactDialog = false },
            onSave = { newName, newPhone, newPhotoUri, isPriority ->
                viewModel.updateContact(
                    original = contact,
                    newName = newName,
                    newPhone = newPhone,
                    newPhotoUri = newPhotoUri,
                    isPriority = isPriority
                )
                showEditContactDialog = false
            }
        )
    }

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

    if (showDeleteContactConfirm) {
        LyraDeleteConfirmationDialog(
            title = "Delete Contact?",
            message = "Are you sure you want to delete ${contact.name} (${contact.phoneNumber})?",
            onConfirm = {
                showDeleteContactConfirm = false
                viewModel.deleteContact(contact)
                onBackClick()
            },
            onDismiss = { showDeleteContactConfirm = false }
        )
    }

    pendingDeleteNote?.let { note ->
        LyraDeleteConfirmationDialog(
            title = "Delete Note?",
            message = "Are you sure you want to delete this saved call note?",
            onConfirm = {
                viewModel.deleteNote(note.id)
                pendingDeleteNote = null
            },
            onDismiss = { pendingDeleteNote = null }
        )
    }
}
