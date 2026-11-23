package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CallNote
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.utils.ExternalIntentHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ListItemContextMenuDialog(
    title: String,
    subtitle: String,
    canDelete: Boolean = true,
    deleteLabel: String = "Delete",
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.primaryGlow,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onEditClick()
                        }
                        .testTag("context_menu_edit")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Edit",
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Modify name, number or profile photo",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (canDelete) {
                    Surface(
                        color = CrimsonDecline.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CrimsonDecline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onDeleteClick()
                            }
                            .testTag("context_menu_delete")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = deleteLabel,
                                tint = CrimsonDecline,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = deleteLabel,
                                    color = CrimsonDecline,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Remove this item from list",
                                    color = colors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileSheet(
    name: String,
    phoneNumber: String,
    photoUri: String?,
    label: String,
    isOwner: Boolean,
    isPriority: Boolean,
    callMetadata: String? = null,
    callHistory: List<CallHistoryItemData> = emptyList(),
    notes: List<CallNote> = emptyList(),
    onCallClick: () -> Unit,
    onSmsClick: () -> Unit,
    onQuickAutoSmsClick: () -> Unit,
    onAddNoteClick: () -> Unit,
    onEditClick: () -> Unit,
    onWhatsAppClick: (() -> Unit)? = null,
    onTogglePriorityClick: (() -> Unit)? = null,
    onOpenFullProfileClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedHistory = remember(callHistory) {
        callHistory.sortedByDescending { it.timestamp }
    }
    val historyDateFormatter = remember {
        SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("contact_profile_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LyraContactAvatar(
                name = name.ifBlank { phoneNumber },
                photoUri = photoUri,
                isOwner = isOwner,
                isPriority = isPriority,
                size = 86.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = name.ifBlank { "Unknown Caller" },
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = phoneNumber,
                color = colors.primaryGlow,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isOwner -> StatusBadge(type = BadgeType.OWNER)
                    isPriority -> StatusBadge(type = BadgeType.PRIORITY)
                    else -> StatusBadge(type = BadgeType.ALLOWED, customText = label.ifBlank { "Contact" })
                }

                if (!callMetadata.isNullOrBlank()) {
                    Surface(
                        color = colors.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.glassBorder)
                    ) {
                        Text(
                            text = callMetadata,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Actions Row: Call, SMS, WhatsApp, Note, Edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallActionButton(
                    icon = Icons.Default.Call,
                    label = "Call",
                    onClick = onCallClick,
                    containerColor = EmeraldAccept,
                    contentColor = Color.White,
                    size = 48.dp,
                    iconSize = 20.dp
                )
                CallActionButton(
                    icon = Icons.Default.Message,
                    label = "SMS",
                    onClick = onSmsClick,
                    containerColor = colors.primary,
                    contentColor = colors.onPrimaryButton,
                    size = 48.dp,
                    iconSize = 20.dp
                )
                CallActionButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "WhatsApp",
                    onClick = {
                        if (onWhatsAppClick != null) {
                            onWhatsAppClick()
                        } else {
                            ExternalIntentHelper.openWhatsAppChat(context, phoneNumber)
                        }
                    },
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White,
                    size = 48.dp,
                    iconSize = 20.dp,
                    testTagOverride = "profile_sheet_whatsapp_btn"
                )
                CallActionButton(
                    icon = Icons.Default.NoteAdd,
                    label = "Note",
                    onClick = onAddNoteClick,
                    containerColor = colors.secondary,
                    contentColor = colors.onSecondaryButton,
                    size = 48.dp,
                    iconSize = 20.dp
                )
                CallActionButton(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    onClick = onEditClick,
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.primary,
                    size = 48.dp,
                    iconSize = 20.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Auto-Send SMS Chip Row
            Surface(
                color = colors.surfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.glassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onQuickAutoSmsClick)
                    .testTag("profile_sheet_quick_sms_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Quick SMS",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Send Quick Follow-Up SMS",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "\"I'll call you back soon.\"",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = "SEND",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (sortedHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = colors.surfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_sheet_call_history_list")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Call History",
                                tint = colors.primaryGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "CALL HISTORY (${sortedHistory.size})",
                                color = colors.primaryGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }

                        sortedHistory.forEachIndexed { index, entry ->
                            val (typeIcon, typeColor, typeLabel) = when (entry.callType) {
                                CallTypeEnum.INCOMING -> Triple(Icons.Default.CallReceived, EmeraldAccept, "Incoming")
                                CallTypeEnum.OUTGOING -> Triple(Icons.Default.CallMade, colors.primary, "Outgoing")
                                CallTypeEnum.MISSED -> Triple(Icons.Default.CallMissed, CrimsonDecline, "Missed")
                                CallTypeEnum.REJECTED -> Triple(Icons.Default.CallEnd, CrimsonDecline, "Rejected")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_history_row_${entry.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = typeColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = typeIcon,
                                                contentDescription = typeLabel,
                                                tint = typeColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
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
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            entry.simLabel?.takeIf { it.isNotBlank() }?.let { sim ->
                                                Surface(
                                                    color = colors.surface,
                                                    shape = CircleShape,
                                                    border = BorderStroke(0.8.dp, colors.glassBorder)
                                                ) {
                                                    Text(
                                                        text = sim,
                                                        color = colors.textSecondary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        modifier = Modifier
                                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                                            .testTag("profile_sim_badge_${entry.id}")
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = historyDateFormatter.format(Date(entry.timestamp)),
                                            color = colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    text = entry.formattedDuration,
                                    color = typeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (index < sortedHistory.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.6.dp,
                                    color = colors.glassBorder.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }

            if (notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = colors.surfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SAVED NOTES (${notes.size})",
                            color = colors.primaryGlow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        notes.take(2).forEach { note ->
                            Text(
                                text = "• ${note.noteText}",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.glassBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onTogglePriorityClick != null) {
                    OutlinedButton(
                        onClick = onTogglePriorityClick,
                        border = BorderStroke(1.dp, AmberPriority.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isPriority) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Priority",
                            tint = AmberPriority,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPriority) "Priority ★" else "Make VIP",
                            color = AmberPriority,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onOpenFullProfileClick != null) {
                    Button(
                        onClick = onOpenFullProfileClick,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).testTag("profile_sheet_full_profile_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Full Profile",
                            tint = colors.onPrimaryButton,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Full Profile",
                            color = colors.onPrimaryButton,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
