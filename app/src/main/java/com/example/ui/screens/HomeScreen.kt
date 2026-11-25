package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.navigation.LyraDestinations
import com.example.service.NotificationHelper
import com.example.ui.components.BadgeType
import com.example.ui.components.CapsuleButton
import com.example.ui.components.CapsuleOutlinedButton
import com.example.ui.components.CapsuleStatPill
import com.example.ui.components.CapsuleStatusChip
import com.example.ui.components.DashboardHeroGraphic
import com.example.ui.components.DashboardMediaCropDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraBottomNavBar
import com.example.ui.components.LyraTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.ProfileMediaStorage

@Composable
fun HomeScreen(
    viewModel: LyraMainViewModel,
    onNavigateToDialer: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToIncomingCall: () -> Unit,
    onNavigateToScheduledCalls: () -> Unit = {},
    onNavigateToVoicemail: () -> Unit = {},
    onNavigateTab: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val stats by viewModel.homeStats.collectAsStateWithLifecycle()
    val perms by viewModel.permissionsState.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val isLyraEnabled by viewModel.isLyraEnabled.collectAsStateWithLifecycle()
    val isBackgroundCallingEnabled by viewModel.isBackgroundCallingEnabled.collectAsStateWithLifecycle()
    val ownerName by viewModel.ownerName.collectAsStateWithLifecycle()
    val voicemails by viewModel.allVoicemails.collectAsStateWithLifecycle()
    val scheduledCalls by viewModel.allScheduledCalls.collectAsStateWithLifecycle()
    val aiModelName by viewModel.aiModelName.collectAsStateWithLifecycle()
    val dashboardMediaUri by viewModel.dashboardMediaUri.collectAsStateWithLifecycle()
    val dashboardMediaType by viewModel.dashboardMediaType.collectAsStateWithLifecycle()
    val dashboardMediaCrop by viewModel.dashboardMediaCrop.collectAsStateWithLifecycle()
    val unreadMissedCount by viewModel.unreadMissedCallCount.collectAsStateWithLifecycle()
    val monthlyStatsAutoResetEnabled by viewModel.monthlyStatsAutoResetEnabled.collectAsStateWithLifecycle()

    val colors = LyraTheme.colors
    var pendingPickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPickedIsVideo by remember { mutableStateOf(false) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingPickedIsVideo = ProfileMediaStorage.detectIsVideo(context, uri)
            pendingPickedMediaUri = uri
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LyraAmbientBackdrop()

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("home_screen")
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Bar with Capsule Controls
            item(key = "home_top_bar", contentType = "top_bar") {
                LyraTopBar(
                    title = stringResource(R.string.home_title),
                    subtitle = stringResource(R.string.home_subtitle),
                    statusText = "",
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    trailingActions = {
                        Surface(
                            onClick = onNavigateToSettings,
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = onNavigateToAbout,
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("about_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "About Lyra",
                                    tint = colors.primaryGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Main AI Orb Hero Card + Capsule Stats
            item(key = "home_hero_card", contentType = "hero_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        borderColor = colors.glassBorderPurple,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DashboardHeroGraphic(
                                mediaUri = dashboardMediaUri,
                                mediaType = dashboardMediaType,
                                mediaCrop = dashboardMediaCrop,
                                callState = callState,
                                customStatusText = if (isLyraEnabled) "Ready to assist $ownerName" else "Lyra is paused in settings",
                                onClick = {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                size = 144.dp,
                                showLabels = true,
                                testTagName = "dashboard_hero_graphic"
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (dashboardMediaUri != null) {
                                    "Tap graphic to change photo/video"
                                } else {
                                    "Tap graphic to customize with photo or video"
                                },
                                color = colors.textMuted,
                                fontSize = 10.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Stats Capsule Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CapsuleStatPill(
                                    count = stats.callsToday.toString(),
                                    label = stringResource(R.string.home_stat_calls),
                                    color = colors.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                CapsuleStatPill(
                                    count = stats.missedCalls.toString(),
                                    label = stringResource(R.string.home_stat_missed),
                                    color = colors.declineColor,
                                    modifier = Modifier.weight(1f)
                                )
                                CapsuleStatPill(
                                    count = stats.knownCallers.toString(),
                                    label = stringResource(R.string.home_stat_known),
                                    color = colors.acceptColor,
                                    modifier = Modifier.weight(1f)
                                )
                                CapsuleStatPill(
                                    count = stats.unknownCallers.toString(),
                                    label = stringResource(R.string.home_stat_unknown),
                                    color = colors.secondaryGlow,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (monthlyStatsAutoResetEnabled) {
                                    "Live Call Stats • Auto-resets on 1st at 00:00"
                                } else {
                                    "Live Call Stats • Cumulative total"
                                },
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("home_stats_cycle_label")
                            )
                        }
                    }
                }
            }

            // Quick Actions Capsule Row
            item(key = "home_quick_actions", contentType = "quick_actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCapsule(
                        icon = Icons.Default.Dialpad,
                        label = stringResource(R.string.home_quick_keypad),
                        color = colors.acceptColor,
                        onClick = onNavigateToDialer,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCapsule(
                        icon = Icons.Default.History,
                        label = stringResource(R.string.home_quick_recents),
                        color = colors.primary,
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCapsule(
                        icon = Icons.Default.People,
                        label = stringResource(R.string.home_quick_contacts),
                        color = colors.secondary,
                        onClick = onNavigateToContacts,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCapsule(
                        icon = Icons.Default.Settings,
                        label = stringResource(R.string.home_quick_settings),
                        color = colors.priorityColor,
                        onClick = onNavigateToSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Assistant Status Capsule Card
            item(key = "home_assistant_status", contentType = "status_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.home_guard_title),
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isBackgroundCallingEnabled) {
                                            stringResource(R.string.home_guard_active_desc)
                                        } else {
                                            stringResource(R.string.home_guard_paused_desc)
                                        },
                                        color = colors.primaryGlow,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            StatusBadge(
                                type = if (isBackgroundCallingEnabled) BadgeType.ALLOWED else BadgeType.REQUIRED,
                                customText = if (isBackgroundCallingEnabled) stringResource(R.string.home_status_active) else stringResource(R.string.home_status_paused)
                            )
                        }
                    }
                }
            }

            // Lyra AI Voicemail Inbox Capsule Card
            item(key = "home_voicemail_card", contentType = "feature_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    GlassCard(
                        borderColor = colors.primary.copy(alpha = 0.45f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onNavigateToVoicemail)
                            .testTag("home_voicemail_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Voicemail,
                                        contentDescription = "Lyra Voicemail Inbox",
                                        tint = colors.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.home_voicemail_title, voicemails.size),
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.home_voicemail_subtitle, aiModelName),
                                        color = colors.primaryGlow,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            CapsuleStatusChip(
                                text = stringResource(R.string.home_voicemail_chip),
                                color = colors.primary
                            )
                        }
                    }
                }
            }

            // Scheduled Automatic Calls Capsule Card
            item(key = "home_scheduled_calls_card", contentType = "feature_card") {
                val pendingScheduledCount = remember(scheduledCalls) {
                    scheduledCalls.count { it.status == "PENDING" }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    GlassCard(
                        borderColor = colors.secondary.copy(alpha = 0.45f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onNavigateToScheduledCalls)
                            .testTag("home_scheduled_calls_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.secondary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Scheduled Calls",
                                        tint = colors.secondaryGlow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Scheduled Auto-Calls ($pendingScheduledCount pending)",
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Exact AlarmManager + FGS with SIM 1 / SIM 2",
                                        color = colors.secondaryGlow,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            CapsuleStatusChip(
                                text = "Schedule",
                                color = colors.secondaryGlow
                            )
                        }
                    }
                }
            }

            // Permission & Background Readiness Card
            item(key = "home_permissions_card", contentType = "permissions_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    GlassCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Calling & Permission Status",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    onClick = onNavigateToPermissions,
                                    shape = CircleShape,
                                    color = colors.primary.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.42f)),
                                    modifier = Modifier.testTag("manage_perms_link")
                                ) {
                                    Text(
                                        text = "Manage",
                                        color = colors.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            PermissionRowItem(name = "Phone & Call Control", isAllowed = perms.phoneAllowed)
                            PermissionRowItem(name = "Notifications & Lock Alert", isAllowed = perms.notificationsAllowed && perms.fullScreenIntentAllowed)
                            PermissionRowItem(name = "Contacts & Call Log", isAllowed = perms.contactsAllowed && perms.callLogAllowed)
                            PermissionRowItem(name = "Microphone (Voicemail)", isAllowed = perms.microphoneAllowed)
                            PermissionRowItem(name = "Display Over Other Apps", isAllowed = perms.overlayAllowed, requiredLabel = "Recommended")
                            PermissionRowItem(name = "Default Dialer Role", isAllowed = perms.isDefaultDialer, requiredLabel = "Recommended")
                            PermissionRowItem(name = "Unrestricted Battery", isAllowed = perms.batteryOptimizationIgnored, requiredLabel = "Recommended")
                        }
                    }
                }
            }

            // Interactive Call Simulator Card
            item(key = "home_call_simulator_card", contentType = "simulator_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    GlassCard(borderColor = colors.primary.copy(alpha = 0.4f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Test AI Incoming Call Interface",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Simulate an incoming phone call to test the unlocked edge-glow top call banner or lock-screen full-screen caller UI.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Owner: BOSS (8795989222)  •  Unknown: \"Mine\" (8073469203)",
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CapsuleButton(
                                    text = "Owner Call",
                                    onClick = {
                                        viewModel.simulateIncomingCall(
                                            name = "BOSS",
                                            number = "8795989222",
                                            isOwner = true,
                                            isPriority = true
                                        )
                                        if (NotificationHelper.isDeviceLockedOrScreenOff(context)) {
                                            onNavigateToIncomingCall()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_owner_call_btn")
                                )

                                CapsuleOutlinedButton(
                                    text = "Unknown Call",
                                    accentColor = colors.textPrimary,
                                    onClick = {
                                        viewModel.simulateUnknownCall(
                                            name = "Unknown \"Mine\"",
                                            number = "8073469203"
                                        )
                                        if (NotificationHelper.isDeviceLockedOrScreenOff(context)) {
                                            onNavigateToIncomingCall()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_unknown_call_btn")
                                )
                            }
                        }
                    }
                }
            }
        }

        LyraBottomNavBar(
            currentRoute = LyraDestinations.HOME,
            onNavigate = onNavigateTab,
            unreadMissedCount = unreadMissedCount,
            onReselectTab = {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        )
    }
    }

    pendingPickedMediaUri?.let { pickedUri ->
        DashboardMediaCropDialog(
            pickedUri = pickedUri,
            isVideo = pendingPickedIsVideo,
            hasExistingCustomMedia = dashboardMediaUri != null,
            onApplyImage = { bitmap, crop ->
                viewModel.applyCroppedDashboardImage(bitmap, crop) {
                    pendingPickedMediaUri = null
                }
            },
            onApplyVideo = { uri, crop ->
                viewModel.applyCroppedDashboardVideo(uri, crop) {
                    pendingPickedMediaUri = null
                }
            },
            onResetToOrb = {
                viewModel.resetDashboardMedia()
                pendingPickedMediaUri = null
            },
            onDismiss = { pendingPickedMediaUri = null }
        )
    }
}

@Composable
private fun QuickActionCapsule(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
        modifier = modifier.testTag("quick_action_$label")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PermissionRowItem(
    name: String,
    isAllowed: Boolean,
    requiredLabel: String = "Required"
) {
    val colors = LyraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAllowed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.acceptColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Allowed",
                    color = colors.acceptColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.priorityColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = requiredLabel,
                    color = colors.priorityColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
