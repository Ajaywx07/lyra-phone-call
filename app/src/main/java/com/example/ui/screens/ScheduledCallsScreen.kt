package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ScheduledCall
import com.example.service.ScheduledCallScheduler
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraContactAvatar
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.SimManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduledCallsScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scheduledCalls by viewModel.allScheduledCalls.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()
    val perms by viewModel.permissionsState.collectAsStateWithLifecycle()
    val colors = LyraTheme.colors

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingCall by remember { mutableStateOf<ScheduledCall?>(null) }
    var pendingDeleteSchedule by remember { mutableStateOf<ScheduledCall?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.loadData()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.loadData()
    }

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCall = null
                    showEditorDialog = true
                },
                shape = CircleShape,
                containerColor = colors.primary,
                contentColor = colors.onPrimaryButton,
                modifier = Modifier.testTag("add_scheduled_call_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Schedule Call")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(innerPadding)
                .testTag("scheduled_calls_screen")
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "scheduled_top_bar", contentType = "top_bar") {
                    LyraTopBar(
                        title = "SCHEDULED CALLS",
                        subtitle = "Automated Outgoing Call Scheduler",
                        showBack = true,
                        onBackClick = onBackClick
                    )
                }

                val needsReliabilitySetup = !perms.exactAlarmAllowed ||
                    !perms.phoneAllowed ||
                    !perms.batteryOptimizationIgnored ||
                    !perms.fullScreenIntentAllowed
                if (needsReliabilitySetup) {
                    item(key = "scheduled_permissions_banner", contentType = "banner") {
                        GlassCard(borderColor = AmberPriority.copy(alpha = 0.55f)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberPriority, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Permissions Needed for Exact Auto-Call", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Grant Exact Alarms, Phone Call, Full-Screen Intent & Unrestricted Battery so OnePlus/OxygenOS Doze never delays scheduled calls.",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!perms.phoneAllowed) {
                                        item {
                                            Button(
                                                onClick = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) },
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                                shape = CircleShape
                                            ) {
                                                Text("Allow Calls", color = colors.onPrimaryButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (!perms.exactAlarmAllowed) {
                                        item {
                                            OutlinedButton(
                                                onClick = {
                                                    settingsLauncher.launch(PermissionHelper.createExactAlarmSettingsIntent(context))
                                                },
                                                border = BorderStroke(1.dp, AmberPriority),
                                                shape = CircleShape
                                            ) {
                                                Text("Allow Exact Alarms", color = AmberPriority, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (!perms.batteryOptimizationIgnored) {
                                        item {
                                            OutlinedButton(
                                                onClick = {
                                                    settingsLauncher.launch(PermissionHelper.createBatteryOptimizationIntent())
                                                },
                                                border = BorderStroke(1.dp, colors.primary),
                                                shape = CircleShape
                                            ) {
                                                Text("Unrestrict Battery", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (!perms.fullScreenIntentAllowed) {
                                        item {
                                            OutlinedButton(
                                                onClick = {
                                                    settingsLauncher.launch(PermissionHelper.createFullScreenIntentSettingsIntent(context))
                                                },
                                                border = BorderStroke(1.dp, colors.secondaryGlow),
                                                shape = CircleShape
                                            ) {
                                                Text("Allow Lock-Screen UI", color = colors.secondaryGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "scheduled_info_card", contentType = "info_card") {
                    GlassCard(borderColor = colors.glassBorder) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = colors.primaryGlow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Uses exact AlarmManager (setAlarmClock) + a Foreground Wake Service to trigger on time even in Doze or when Lyra is closed. Enable Default Phone Role & Display Over Other Apps for seamless call handoff; if an OEM blocks lock-screen calling, Lyra posts a 'Call Now' alert.",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                if (scheduledCalls.isEmpty()) {
                    item(key = "scheduled_empty", contentType = "empty_state") {
                        EmptyState(
                            icon = Icons.Default.Schedule,
                            title = "No Scheduled Calls",
                            description = "Tap '+' to schedule an automatic outgoing call to any contact or number with SIM 1 / SIM 2.",
                            buttonText = "Schedule a Call",
                            onButtonClick = {
                                editingCall = null
                                showEditorDialog = true
                            }
                        )
                    }
                } else {
                    items(
                        items = scheduledCalls,
                        key = { it.id },
                        contentType = { "scheduled_call_card" }
                    ) { item ->
                        val isPriorityContact = remember(contacts, item.phoneNumber) {
                            contacts.any { c ->
                                (c.isPriority || c.isOwner) &&
                                    PhoneNumberUtils.areNumbersEqual(c.phoneNumber, item.phoneNumber)
                            }
                        }
                        ScheduledCallCard(
                            call = item,
                            canDelete = !isPriorityContact,
                            onEdit = {
                                editingCall = item
                                showEditorDialog = true
                            },
                            onCancel = { viewModel.cancelScheduledCall(item) },
                            onDelete = { pendingDeleteSchedule = item },
                            onCallNow = {
                                viewModel.placeCall(
                                    phoneNumber = item.phoneNumber,
                                    simKeyOverride = item.simKey
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        ScheduledCallEditorDialog(
            initialCall = editingCall,
            contacts = contacts.take(15),
            availableSims = availableSims.map { it.simKey to "${it.label} (${it.carrierName})" },
            onDismiss = { showEditorDialog = false },
            onSave = { name, number, simKey, timeMillis ->
                val existing = editingCall
                if (existing == null) {
                    viewModel.scheduleCall(name, number, simKey, timeMillis)
                } else {
                    viewModel.updateScheduledCall(existing, name, number, simKey, timeMillis)
                }
                showEditorDialog = false
            }
        )
    }

    pendingDeleteSchedule?.let { target ->
        LyraDeleteConfirmationDialog(
            title = "Delete Scheduled Call?",
            message = "Are you sure you want to delete the scheduled call to ${target.contactName.ifBlank { target.phoneNumber }}?",
            onConfirm = {
                viewModel.deleteScheduledCall(target.id)
                pendingDeleteSchedule = null
            },
            onDismiss = { pendingDeleteSchedule = null }
        )
    }
}

@Composable
private fun ScheduledCallCard(
    call: ScheduledCall,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onCallNow: () -> Unit
) {
    val colors = LyraTheme.colors
    val formatter = remember { SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault()) }
    val statusColor = when (call.status) {
        "PENDING" -> colors.primary
        "PLACED" -> EmeraldAccept
        "FAILED" -> CrimsonDecline
        else -> colors.textMuted
    }

    GlassCard(
        borderColor = statusColor.copy(alpha = 0.38f),
        modifier = Modifier.testTag("scheduled_call_card_${call.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    LyraContactAvatar(
                        name = call.contactName.ifBlank { call.phoneNumber },
                        photoUri = null,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = call.contactName.ifBlank { call.phoneNumber },
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = call.phoneNumber,
                            color = colors.primaryGlow,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = call.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = colors.secondaryGlow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatter.format(Date(call.scheduledAtMillis)),
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SimCard, contentDescription = null, tint = colors.primaryGlow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = call.simKey.replace("_", " "),
                        color = colors.primaryGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (call.statusMessage.isNotBlank()) {
                Text(
                    text = call.statusMessage,
                    color = if (call.status == "FAILED") CrimsonDecline else colors.textMuted,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCallNow) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = EmeraldAccept, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call Now", color = EmeraldAccept, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (call.status == "PENDING") {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Schedule", tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel Schedule", tint = AmberPriority, modifier = Modifier.size(18.dp))
                    }
                }
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_schedule_btn_${call.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Schedule", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScheduledCallEditorDialog(
    initialCall: ScheduledCall?,
    contacts: List<com.example.domain.model.ContactItemData>,
    availableSims: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (name: String, number: String, simKey: String, scheduledAtMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    var name by remember { mutableStateOf(initialCall?.contactName ?: "") }
    var number by remember { mutableStateOf(initialCall?.phoneNumber ?: "") }
    var simKey by remember { mutableStateOf(initialCall?.simKey ?: SimManager.SIM_1) }
    var scheduledAtMillis by remember {
        mutableLongStateOf(
            initialCall?.scheduledAtMillis
                ?: ScheduledCallScheduler.normalizeToMinuteStart(System.currentTimeMillis() + 5 * 60_000L)
        )
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val picked = resolvePickedContact(context, uri)
            if (picked != null && picked.second.isNotBlank()) {
                name = picked.first
                number = picked.second
            } else {
                Toast.makeText(
                    context,
                    "Selected contact does not have a phone number",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val dateTimeFormatter = remember { SimpleDateFormat("EEE, dd MMM yyyy • hh:mm:ss a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCall == null) "Schedule Automatic Call" else "Edit Scheduled Call",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recipient",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            val pickerIntent = Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                            )
                            runCatching {
                                contactPickerLauncher.launch(pickerIntent)
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Unable to open Contacts picker",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("schedule_pick_contact_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = "Pick Contact",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pick Contact",
                            color = colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (contacts.isNotEmpty()) {
                    Text("Quick Pick Contact", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(contacts) { contact ->
                            Surface(
                                color = colors.background,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.glassBorder),
                                modifier = Modifier.clickable {
                                    name = contact.name
                                    number = contact.phoneNumber
                                }
                            ) {
                                Text(
                                    text = contact.name,
                                    color = colors.primaryGlow,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_name_input")
                )

                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_number_input")
                )

                Text("SIM Slot", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableSims) { (key, label) ->
                        FilterChip(
                            selected = simKey == key,
                            onClick = { simKey = key },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary.copy(alpha = 0.2f),
                                selectedLabelColor = colors.primary,
                                containerColor = colors.background,
                                labelColor = colors.textSecondary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = simKey == SimManager.SIM_ASK,
                            onClick = { simKey = SimManager.SIM_ASK },
                            label = { Text("Default", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary.copy(alpha = 0.2f),
                                selectedLabelColor = colors.primary,
                                containerColor = colors.background,
                                labelColor = colors.textSecondary
                            )
                        )
                    }
                }

                Text(
                    text = "Scheduled For: ${dateTimeFormatter.format(Date(scheduledAtMillis))}",
                    color = colors.secondaryGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    cal.set(Calendar.YEAR, y)
                                    cal.set(Calendar.MONTH, m)
                                    cal.set(Calendar.DAY_OF_MONTH, d)
                                    scheduledAtMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pick Date", color = colors.primary, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, min)
                                    cal.set(Calendar.SECOND, 0)
                                    cal.set(Calendar.MILLISECOND, 0)
                                    scheduledAtMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        border = BorderStroke(1.dp, colors.secondaryGlow.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pick Time", color = colors.secondaryGlow, fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2 to "+2m", 15 to "+15m", 60 to "+1h").forEach { (mins, label) ->
                            Surface(
                                color = colors.background,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.glassBorder),
                                modifier = Modifier.clickable {
                                    scheduledAtMillis = ScheduledCallScheduler.normalizeToMinuteStart(
                                        System.currentTimeMillis() + mins * 60_000L
                                    )
                                }
                            ) {
                            Text(
                                text = label,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (number.isNotBlank()) {
                        onSave(
                            name.trim().ifBlank { number.trim() },
                            number.trim(),
                            simKey,
                            scheduledAtMillis
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                modifier = Modifier.testTag("save_scheduled_call_btn")
            ) {
                Text("Save Schedule", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated
    )
}

internal fun resolvePickedContact(context: Context, uri: Uri): Pair<String, String>? {
    val resolver = context.contentResolver ?: return null
    val phoneProjection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    runCatching {
        resolver.query(uri, phoneProjection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val pickedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                val pickedNumber = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                if (pickedNumber.isNotBlank()) {
                    return pickedName to pickedNumber
                }
            }
        }
    }

    // Fallback for OEM contact pickers returning a ContactsContract.Contacts lookup URI
    if (PermissionHelper.hasContactsPermission(context)) {
        runCatching {
            val contactProjection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
            )
            resolver.query(uri, contactProjection, null, null, null)?.use { contactCursor ->
                if (contactCursor.moveToFirst()) {
                    val idIdx = contactCursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = contactCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val contactId = if (idIdx >= 0) contactCursor.getString(idIdx).orEmpty() else ""
                    val contactName = if (nameIdx >= 0) contactCursor.getString(nameIdx).orEmpty().trim() else ""
                    if (contactId.isNotBlank()) {
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )?.use { numCursor ->
                            if (numCursor.moveToFirst()) {
                                val numCol = numCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val phoneNumber = if (numCol >= 0) numCursor.getString(numCol).orEmpty().trim() else ""
                                if (phoneNumber.isNotBlank()) {
                                    return contactName to phoneNumber
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return null
}
