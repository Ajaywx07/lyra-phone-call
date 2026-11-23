package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraTopBar
import com.example.ui.components.PermissionCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PermissionHelper

@Composable
fun PermissionsScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val perms by viewModel.permissionsState.collectAsStateWithLifecycle()
    val colors = LyraTheme.colors

    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadData()
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.loadData()
    }

    val activityResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.loadData()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("permissions_screen"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LyraTopBar(
                title = "MAX PERMISSIONS",
                subtitle = "Full Calling, Telecom & Security Access",
                showBack = true,
                onBackClick = onBackClick
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // One-Tap Grant All Runtime Permissions Hero Card
        item {
            GlassCard(borderColor = colors.primary.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Full Advance Permission Suite",
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant Phone, Dual-SIM, Contacts, Logs, Bluetooth, Mic & Alerts together.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            multiLauncher.launch(PermissionHelper.getAllRuntimePermissions())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().testTag("grant_all_permissions_btn")
                    ) {
                        Text(
                            text = "Grant All Core & Advanced Permissions",
                            color = colors.onPrimaryButton,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Privacy First Banner
        item {
            GlassCard(borderColor = colors.acceptColor.copy(alpha = 0.35f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colors.acceptColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Lyra operates locally on your device. Cloud contact backup only runs when you explicitly sign in with Google and tap Backup.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Telecom & Default Phone Roles")
        }

        // Default Dialer Role
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Default Phone App Role (Incoming & Outgoing)",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (perms.isDefaultDialer) {
                                "Active ✓ Lyra controls full-screen incoming & outgoing Telecom calls."
                            } else {
                                "Required to show Lyra's incoming/outgoing call screens as your primary phone app."
                            },
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    if (!perms.isDefaultDialer) {
                        Button(
                            onClick = {
                                val intent = PermissionHelper.createDefaultDialerIntent(context)
                                if (intent != null) {
                                    runCatching { activityResultLauncher.launch(intent) }
                                        .onFailure { PermissionHelper.openAppSettings(context) }
                                } else {
                                    runCatching {
                                        activityResultLauncher.launch(PermissionHelper.createDefaultAppsSettingsIntent())
                                    }.onFailure { PermissionHelper.openAppSettings(context) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = CircleShape,
                            modifier = Modifier.testTag("grant_role_btn")
                        ) {
                            Text("Set Default", color = colors.onPrimaryButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = colors.acceptColor)
                    }
                }
            }
        }

        // Call Screening Role
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Call Screening & Caller ID Role",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (perms.isCallScreener) {
                                "Active ✓ Lyra screens incoming numbers natively."
                            } else {
                                "Enables background Caller ID screening & spam detection before ring."
                            },
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    if (!perms.isCallScreener) {
                        Button(
                            onClick = {
                                val intent = PermissionHelper.createCallScreeningRoleIntent(context)
                                if (intent != null) {
                                    runCatching { activityResultLauncher.launch(intent) }
                                        .onFailure { PermissionHelper.openAppSettings(context) }
                                } else {
                                    PermissionHelper.openAppSettings(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondary),
                            shape = CircleShape,
                            modifier = Modifier.testTag("grant_screening_role_btn")
                        ) {
                            Text("Enable", color = colors.onSecondaryButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = colors.acceptColor)
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Calling, Dual-SIM & Audio Permissions")
        }

        item {
            PermissionCard(
                title = "Phone State & Outgoing Calls",
                description = "Detects incoming/outgoing call events and enables placing calls directly.",
                isGranted = perms.phoneAllowed,
                onGrantClick = {
                    multiLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.CALL_PHONE
                        )
                    )
                }
            )
        }

        item {
            PermissionCard(
                title = "Answer Phone Calls",
                description = "Permits answering or ending incoming calls cleanly via Telecom controls.",
                isGranted = perms.answerCallsAllowed,
                onGrantClick = {
                    singleLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS)
                }
            )
        }

        item {
            PermissionCard(
                title = "Dual-SIM & Phone Numbers",
                description = "Reads SIM 1 / SIM 2 carrier names and active slot information for Dual-SIM dialing.",
                isGranted = perms.phoneNumbersAllowed,
                onGrantClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        singleLauncher.launch(Manifest.permission.READ_PHONE_NUMBERS)
                    } else {
                        singleLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                }
            )
        }

        item {
            PermissionCard(
                title = "Bluetooth Audio Routing",
                description = "Routes active phone audio to Bluetooth earbuds, headsets, and car kits.",
                isGranted = perms.bluetoothAllowed,
                onGrantClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        singleLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
            )
        }

        item {
            SectionHeader(title = "Contacts, Logs & Media Permissions")
        }

        item {
            PermissionCard(
                title = "Contacts (Read & Write)",
                description = "Identifies callers, priority contacts, and syncs restored contacts to device.",
                isGranted = perms.contactsAllowed && perms.writeContactsAllowed,
                onGrantClick = {
                    multiLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                        )
                    )
                }
            )
        }

        item {
            PermissionCard(
                title = "Call Log (Read & Write)",
                description = "Displays recent incoming/outgoing/missed calls and updates call history.",
                isGranted = perms.callLogAllowed && perms.writeCallLogAllowed,
                onGrantClick = {
                    multiLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.WRITE_CALL_LOG
                        )
                    )
                }
            )
        }

        item {
            PermissionCard(
                title = "Notifications & Full-Screen Call Alerts",
                description = "Shows heads-up incoming call cards with Accept/Decline controls.",
                isGranted = perms.notificationsAllowed,
                onGrantClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        item {
            PermissionCard(
                title = stringResource(R.string.permissions_mic_voicemail_title),
                description = stringResource(R.string.permissions_mic_voicemail_desc),
                isGranted = perms.microphoneAllowed,
                onGrantClick = {
                    singleLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        }

        item {
            SectionHeader(title = "Advanced System Special Access")
        }

        item {
            PermissionCard(
                title = "Full-Screen Lock-Screen Call Alert",
                description = "Required on Android 14+ to display full-screen incoming call UI when the phone is locked.",
                isGranted = perms.fullScreenIntentAllowed,
                onGrantClick = {
                    runCatching {
                        activityResultLauncher.launch(PermissionHelper.createFullScreenIntentSettingsIntent(context))
                    }.onFailure { PermissionHelper.openAppSettings(context) }
                }
            )
        }

        item {
            PermissionCard(
                title = "Exact Alarms (Scheduled Calls)",
                description = "Lets Lyra trigger scheduled automatic calls on time even in power-saving mode.",
                isGranted = perms.exactAlarmAllowed,
                onGrantClick = {
                    runCatching {
                        activityResultLauncher.launch(PermissionHelper.createExactAlarmSettingsIntent(context))
                    }.onFailure { PermissionHelper.openAppSettings(context) }
                }
            )
        }

        item {
            PermissionCard(
                title = "Display Over Other Apps",
                description = "Enables seamless incoming/outgoing call interface switching above other apps without system dialer interception.",
                isGranted = perms.overlayAllowed,
                onGrantClick = {
                    runCatching {
                        activityResultLauncher.launch(PermissionHelper.createOverlaySettingsIntent(context))
                    }.onFailure { PermissionHelper.openAppSettings(context) }
                }
            )
        }

        item {
            PermissionCard(
                title = "Do Not Disturb Policy Access",
                description = "Lets priority Owner/VIP calls notify you even when Do Not Disturb is active.",
                isGranted = perms.dndAllowed,
                onGrantClick = {
                    runCatching {
                        activityResultLauncher.launch(PermissionHelper.createDndSettingsIntent())
                    }.onFailure { PermissionHelper.openAppSettings(context) }
                }
            )
        }

        item {
            PermissionCard(
                title = "Unrestricted Battery (Background Calls)",
                description = "Prevents aggressive OEM power savers from suspending incoming call alerts and scheduled calls.",
                isGranted = perms.batteryOptimizationIgnored,
                onGrantClick = {
                    runCatching {
                        activityResultLauncher.launch(PermissionHelper.createBatteryOptimizationIntent())
                    }.onFailure { PermissionHelper.openAppSettings(context) }
                }
            )
        }

        item {
            PermissionCard(
                title = "Quick Response Auto-SMS",
                description = "Permits sending canned call quick-reply SMS messages directly in the background.",
                isGranted = perms.sendSmsAllowed,
                onGrantClick = {
                    singleLauncher.launch(Manifest.permission.SEND_SMS)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { PermissionHelper.openAppSettings(context) },
                border = BorderStroke(1.dp, colors.glassBorder),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().testTag("open_system_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Android System App Settings",
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
