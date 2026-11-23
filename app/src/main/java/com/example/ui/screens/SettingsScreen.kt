package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.domain.model.AiProvider
import com.example.ui.components.CapsuleGroupDivider
import com.example.ui.components.CapsuleToggle
import com.example.ui.components.DashboardMediaCropDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PermissionHelper
import com.example.utils.ProfileMediaStorage
import com.example.utils.SimManager

@Composable
fun SettingsScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onPreviewIncomingCall: () -> Unit = {},
    onPreviewOutgoingCall: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLyraEnabled by viewModel.isLyraEnabled.collectAsStateWithLifecycle()
    val ownerName by viewModel.ownerName.collectAsStateWithLifecycle()
    val ownerPhone by viewModel.ownerPhone.collectAsStateWithLifecycle()
    val isCallerIdEnabled by viewModel.isCallerIdEnabled.collectAsStateWithLifecycle()
    val isUnknownDetectionEnabled by viewModel.isUnknownCallerDetectionEnabled.collectAsStateWithLifecycle()
    val isSummaryEnabled by viewModel.isCallSummaryEnabled.collectAsStateWithLifecycle()
    val isNotesEnabled by viewModel.isCallNotesEnabled.collectAsStateWithLifecycle()
    val isVoiceGreetingEnabled by viewModel.isVoiceGreetingEnabled.collectAsStateWithLifecycle()
    val isLocalOnly by viewModel.isLocalOnlyMode.collectAsStateWithLifecycle()
    val isFullscreenIncoming by viewModel.isFullscreenIncomingEnabled.collectAsStateWithLifecycle()
    val isIncomingScreenEnabled by viewModel.isIncomingScreenEnabled.collectAsStateWithLifecycle()
    val isPersistentNotificationEnabled by viewModel.isPersistentNotificationEnabled.collectAsStateWithLifecycle()
    val isBackgroundCallingEnabled by viewModel.isBackgroundCallingEnabled.collectAsStateWithLifecycle()
    val isCallVibrationEnabled by viewModel.isCallVibrationEnabled.collectAsStateWithLifecycle()
    val showContactPhotoOnCall by viewModel.showContactPhotoOnCall.collectAsStateWithLifecycle()
    val ringtoneUri by viewModel.ringtoneUri.collectAsStateWithLifecycle()
    val ringtoneTitle by viewModel.ringtoneTitle.collectAsStateWithLifecycle()
    val callScreenStyle by viewModel.callScreenStyle.collectAsStateWithLifecycle()
    val callAnimationStyle by viewModel.callAnimationStyle.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()
    val preferredSim by viewModel.preferredSim.collectAsStateWithLifecycle()
    val fixDefaultSim by viewModel.fixDefaultSim.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val cloudContacts by viewModel.cloudContacts.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val perms by viewModel.permissionsState.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val dashboardMediaUri by viewModel.dashboardMediaUri.collectAsStateWithLifecycle()
    val dashboardMediaType by viewModel.dashboardMediaType.collectAsStateWithLifecycle()
    val dashboardMediaCrop by viewModel.dashboardMediaCrop.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val autoSendQuickSms by viewModel.autoSendQuickSms.collectAsStateWithLifecycle()
    val blockUnknownCalls by viewModel.blockUnknownCalls.collectAsStateWithLifecycle()
    val blockCommercialPrefixes by viewModel.blockCommercialPrefixes.collectAsStateWithLifecycle()
    val colors = LyraTheme.colors

    // Modular collapsible section states (AI Provider & Call Screen start cleanly collapsed)
    var appearanceExpanded by rememberSaveable { mutableStateOf(true) }
    var blockerExpanded by rememberSaveable { mutableStateOf(true) }
    var aiEngineExpanded by rememberSaveable { mutableStateOf(false) }
    var callScreenExpanded by rememberSaveable { mutableStateOf(false) }
    var callingAlertsExpanded by rememberSaveable { mutableStateOf(false) }
    var assistantPrivacyExpanded by rememberSaveable { mutableStateOf(false) }
    var ownerBackupExpanded by rememberSaveable { mutableStateOf(false) }

    val allExpanded = appearanceExpanded &&
        blockerExpanded &&
        aiEngineExpanded &&
        callScreenExpanded &&
        callingAlertsExpanded &&
        assistantPrivacyExpanded &&
        ownerBackupExpanded

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

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri != null) {
                val title = runCatching {
                    RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context)
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Custom Ringtone"
                viewModel.setRingtone(pickedUri.toString(), title)
            } else {
                viewModel.setRingtone("", "Silent")
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.loadData()
        if (granted) {
            viewModel.setAutoSendQuickSms(true)
        }
    }

    val persistedAiSettings by viewModel.persistedAiSettings.collectAsStateWithLifecycle()
    val aiConnectionStatus by viewModel.aiConnectionStatus.collectAsStateWithLifecycle()
    val isTestingAi by viewModel.isTestingAiConnection.collectAsStateWithLifecycle()

    var draftProvider by remember { mutableStateOf(persistedAiSettings.provider) }
    var draftModel by remember { mutableStateOf(persistedAiSettings.modelName) }
    var draftApiKey by remember { mutableStateOf(persistedAiSettings.apiKey) }
    var draftBaseUrl by remember { mutableStateOf(persistedAiSettings.customBaseUrl) }
    var draftGreeting by remember { mutableStateOf(persistedAiSettings.voicemailGreeting) }
    var showApiKeyPlaintext by remember { mutableStateOf(false) }
    var aiFieldsInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(persistedAiSettings) {
        if (persistedAiSettings.isLoaded && !aiFieldsInitialized) {
            draftProvider = persistedAiSettings.provider
            draftModel = persistedAiSettings.modelName
            draftApiKey = persistedAiSettings.apiKey
            draftBaseUrl = persistedAiSettings.customBaseUrl
            draftGreeting = persistedAiSettings.voicemailGreeting
            aiFieldsInitialized = true
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.loadData()
    }

    var showEditOwnerDialog by remember { mutableStateOf(false) }
    var editOwnerName by remember { mutableStateOf(ownerName) }
    var editOwnerPhone by remember { mutableStateOf(ownerPhone) }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var restoreResult by remember { mutableStateOf<String?>(null) }
    var showDeleteApiKeyConfirm by remember { mutableStateOf(false) }

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
                .padding(horizontal = 16.dp)
                .testTag("settings_screen"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LyraTopBar(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_subtitle),
                    showBack = true,
                    onBackClick = onBackClick,
                    trailingActions = {
                        Surface(
                            onClick = {
                                val next = !allExpanded
                                appearanceExpanded = next
                                blockerExpanded = next
                                aiEngineExpanded = next
                                callScreenExpanded = next
                                callingAlertsExpanded = next
                                assistantPrivacyExpanded = next
                                ownerBackupExpanded = next
                            },
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.testTag("settings_toggle_all_btn")
                        ) {
                            Text(
                                text = if (allExpanded) {
                                    stringResource(R.string.settings_collapse_all)
                                } else {
                                    stringResource(R.string.settings_expand_all)
                                },
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                )
            }

            // Module 1: Appearance & Dashboard Theme
            item(key = "module_appearance") {
                ExpandableSettingsModule(
                    moduleKey = "appearance",
                    title = "App UI Theme & Header",
                    summary = "${appTheme.displayName} • ${AppThemeStyle.entries.size} styles",
                    icon = Icons.Default.Palette,
                    expanded = appearanceExpanded,
                    onToggleExpand = { appearanceExpanded = !appearanceExpanded },
                    accentColor = colors.primary,
                    statusBadge = appTheme.displayName
                ) {
                    ThemeAndDashboardModuleBody(
                        appTheme = appTheme,
                        onSelectAppTheme = { viewModel.setAppTheme(it) },
                        dashboardMediaUri = dashboardMediaUri,
                        dashboardMediaType = dashboardMediaType,
                        dashboardMediaCrop = dashboardMediaCrop,
                        callState = callState,
                        onPickDashboardMedia = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onResetDashboardMedia = { viewModel.resetDashboardMedia() }
                    )
                }
            }

            // Module 2: Call Screening & Blocker Switches
            item(key = "module_blocker") {
                val activeFilters = (if (blockUnknownCalls) 1 else 0) + (if (blockCommercialPrefixes) 1 else 0)
                ExpandableSettingsModule(
                    moduleKey = "blocker",
                    title = "Call Screening & Blocker",
                    summary = "Unknown & Commercial (140/180) spam filters",
                    icon = Icons.Default.Shield,
                    expanded = blockerExpanded,
                    onToggleExpand = { blockerExpanded = !blockerExpanded },
                    accentColor = colors.declineColor,
                    statusBadge = if (activeFilters == 0) "Off" else "$activeFilters Active",
                    statusBadgeColor = if (activeFilters > 0) colors.declineColor else colors.textMuted
                ) {
                    CapsuleToggle(
                        title = stringResource(R.string.settings_unknown_blocker_title),
                        subtitle = "Automatically block incoming calls from numbers not saved in contacts",
                        icon = Icons.Default.Block,
                        checked = blockUnknownCalls,
                        onCheckedChange = { viewModel.setBlockUnknownCalls(it) },
                        accentColor = colors.declineColor,
                        testTag = "setting_block_unknown_calls"
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "Commercial & Telemarketing Blocker",
                        subtitle = "Block telemarketing prefixes (140, 180, 14, 18) while allowing personal numbers (6–9)",
                        icon = Icons.Default.Campaign,
                        checked = blockCommercialPrefixes,
                        onCheckedChange = { viewModel.setBlockCommercialPrefixes(it) },
                        accentColor = colors.priorityColor,
                        testTag = "setting_block_commercial_prefixes"
                    )
                }
            }

            // Module 3: AI Provider & Voicemail Engine (Modular Collapsible Box)
            item(key = "module_ai_engine") {
                val keyBadge = when {
                    draftApiKey.isNotBlank() || persistedAiSettings.apiKey.isNotBlank() -> "Key Set"
                    draftProvider == AiProvider.FIREBASE_AI -> "Managed"
                    else -> "No Key"
                }
                ExpandableSettingsModule(
                    moduleKey = "ai_engine",
                    title = "AI Provider & Voicemail Engine",
                    summary = "${draftProvider.displayName} • ${draftModel.ifBlank { "gemini-3.8-flash" }}",
                    icon = Icons.Default.AutoAwesome,
                    expanded = aiEngineExpanded,
                    onToggleExpand = { aiEngineExpanded = !aiEngineExpanded },
                    accentColor = colors.primary,
                    statusBadge = keyBadge,
                    statusBadgeColor = if (keyBadge == "No Key") colors.priorityColor else colors.acceptColor
                ) {
                    AiEngineModuleBody(
                        draftProvider = draftProvider,
                        onProviderChange = { provider ->
                            draftProvider = provider
                            if (provider == AiProvider.OPENAI_COMPATIBLE && draftModel.startsWith("gemini")) {
                                draftModel = "gpt-4o-mini"
                            } else if (provider != AiProvider.OPENAI_COMPATIBLE && !draftModel.startsWith("gemini")) {
                                draftModel = "gemini-3.8-flash"
                            }
                        },
                        draftModel = draftModel,
                        onModelChange = { draftModel = it },
                        draftApiKey = draftApiKey,
                        onApiKeyChange = { draftApiKey = it },
                        persistedApiKeyPresent = persistedAiSettings.apiKey.isNotEmpty(),
                        draftBaseUrl = draftBaseUrl,
                        onBaseUrlChange = { draftBaseUrl = it },
                        draftGreeting = draftGreeting,
                        onGreetingChange = { draftGreeting = it },
                        showApiKeyPlaintext = showApiKeyPlaintext,
                        onToggleShowKey = { showApiKeyPlaintext = !showApiKeyPlaintext },
                        isTestingAi = isTestingAi,
                        aiConnectionStatus = aiConnectionStatus,
                        onSaveConfig = {
                            viewModel.saveAiEngineSettings(
                                provider = draftProvider,
                                modelName = draftModel,
                                apiKey = draftApiKey,
                                customBaseUrl = draftBaseUrl,
                                greetingScript = draftGreeting
                            )
                        },
                        onTestConnection = {
                            viewModel.testAiConnection(
                                provider = draftProvider,
                                modelName = draftModel,
                                apiKey = draftApiKey,
                                customBaseUrl = draftBaseUrl
                            )
                        },
                        onDeleteApiKey = {
                            showDeleteApiKeyConfirm = true
                        }
                    )
                }
            }

            // Module 4: Call Screen Style & Animations (Modular Collapsible Box)
            item(key = "module_call_screen") {
                ExpandableSettingsModule(
                    moduleKey = "call_screen",
                    title = "Call Screen Style & Animations",
                    summary = "${callScreenStyle.displayName} • ${callAnimationStyle.displayName}",
                    icon = Icons.Default.Wallpaper,
                    expanded = callScreenExpanded,
                    onToggleExpand = { callScreenExpanded = !callScreenExpanded },
                    accentColor = colors.secondary,
                    statusBadge = callScreenStyle.displayName,
                    statusBadgeColor = colors.secondaryGlow
                ) {
                    CallScreenStyleModuleBody(
                        callScreenStyle = callScreenStyle,
                        onSelectCallScreenStyle = { viewModel.setCallScreenStyle(it) },
                        callAnimationStyle = callAnimationStyle,
                        onSelectCallAnimationStyle = { viewModel.setCallAnimationStyle(it) },
                        onPreviewIncoming = {
                            viewModel.simulateIncomingCall(
                                name = ownerName,
                                number = ownerPhone,
                                isOwner = true,
                                isPriority = true
                            )
                            onPreviewIncomingCall()
                        },
                        onPreviewOutgoing = {
                            viewModel.simulateOutgoingCall(ownerPhone)
                            onPreviewOutgoingCall()
                        }
                    )
                }
            }

            // Module 5: Dual-SIM, Telecom Roles & Call Alerts
            item(key = "module_calling_alerts") {
                val simLabel = if (preferredSim == SimManager.SIM_ASK) "Ask SIM" else preferredSim.replace("_", " ")
                ExpandableSettingsModule(
                    moduleKey = "calling_alerts",
                    title = "Dual-SIM, Telecom & Call Alerts",
                    summary = "$simLabel • Ringtone: $ringtoneTitle",
                    icon = Icons.Default.SimCard,
                    expanded = callingAlertsExpanded,
                    onToggleExpand = { callingAlertsExpanded = !callingAlertsExpanded },
                    accentColor = colors.primary,
                    statusBadge = if (perms.isDefaultDialer) "Dialer Ready" else "Setup",
                    statusBadgeColor = if (perms.isDefaultDialer) colors.acceptColor else colors.priorityColor
                ) {
                    CallingSimAndAlertsModuleBody(
                        context = context,
                        availableSims = availableSims,
                        preferredSim = preferredSim,
                        fixDefaultSim = fixDefaultSim,
                        onSelectSim = { viewModel.setPreferredSim(it) },
                        onFixDefaultSimChange = { viewModel.setFixDefaultSim(it) },
                        perms = perms,
                        onLaunchIntent = { intent ->
                            runCatching { roleLauncher.launch(intent) }
                                .onFailure { PermissionHelper.openAppSettings(context) }
                        },
                        onNavigateToPermissions = onNavigateToPermissions,
                        isFullscreenIncoming = isFullscreenIncoming,
                        onFullscreenIncomingChange = { viewModel.setFullscreenIncoming(it) },
                        isIncomingScreenEnabled = isIncomingScreenEnabled,
                        onIncomingScreenEnabledChange = { viewModel.setIncomingScreenEnabled(it) },
                        isBackgroundCallingEnabled = isBackgroundCallingEnabled,
                        onBackgroundCallingEnabledChange = { viewModel.setBackgroundCallingEnabled(it) },
                        isPersistentNotificationEnabled = isPersistentNotificationEnabled,
                        onPersistentNotificationEnabledChange = { viewModel.setPersistentNotificationEnabled(it) },
                        showContactPhotoOnCall = showContactPhotoOnCall,
                        onShowContactPhotoChange = { viewModel.setShowContactPhotoOnCall(it) },
                        isCallVibrationEnabled = isCallVibrationEnabled,
                        onCallVibrationChange = { viewModel.setCallVibrationEnabled(it) },
                        ringtoneUri = ringtoneUri,
                        ringtoneTitle = ringtoneTitle,
                        onLaunchRingtonePicker = { pickerIntent ->
                            runCatching { ringtonePickerLauncher.launch(pickerIntent) }
                                .onFailure { PermissionHelper.openAppSettings(context) }
                        }
                    )
                }
            }

            // Module 6: Assistant Behavior & Local Privacy
            item(key = "module_assistant_privacy") {
                ExpandableSettingsModule(
                    moduleKey = "assistant_privacy",
                    title = "Assistant Behavior & Local Privacy",
                    summary = "Lyra ${if (isLyraEnabled) "On" else "Off"} • ${if (isLocalOnly) "Local-Only Mode" else "Standard Sync"}",
                    icon = Icons.Default.PhoneInTalk,
                    expanded = assistantPrivacyExpanded,
                    onToggleExpand = { assistantPrivacyExpanded = !assistantPrivacyExpanded },
                    accentColor = colors.acceptColor,
                    statusBadge = if (isLocalOnly) "Offline" else "Active",
                    statusBadgeColor = if (isLocalOnly) colors.priorityColor else colors.acceptColor
                ) {
                    CapsuleToggle(
                        title = "Enable Lyra Assistant",
                        subtitle = "Master switch for AI call screening and assistance",
                        icon = Icons.Default.Security,
                        checked = isLyraEnabled,
                        onCheckedChange = { viewModel.setLyraEnabled(it) }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "Auto-Send Quick SMS Replies",
                        subtitle = if (perms.sendSmsAllowed) {
                            "Send quick call replies in the background without opening Messaging app"
                        } else {
                            "Requires SEND_SMS permission to auto-send quick call replies"
                        },
                        icon = Icons.AutoMirrored.Filled.Send,
                        checked = autoSendQuickSms && perms.sendSmsAllowed,
                        onCheckedChange = { enable ->
                            if (enable && !perms.sendSmsAllowed) {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            } else {
                                viewModel.setAutoSendQuickSms(enable)
                            }
                        }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "Caller Identification",
                        subtitle = "Identify incoming numbers against local contacts and owner",
                        icon = Icons.Default.AccountCircle,
                        checked = isCallerIdEnabled,
                        onCheckedChange = { viewModel.setCallerIdEnabled(it) }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "Unknown Caller Detection",
                        subtitle = "Highlight unsaved phone numbers with caution indicators",
                        icon = Icons.Default.QuestionMark,
                        checked = isUnknownDetectionEnabled,
                        onCheckedChange = { viewModel.setUnknownCallerDetection(it) }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "AI Call Summaries",
                        subtitle = "Auto-generate structured bullet points after call concludes",
                        icon = Icons.Default.Summarize,
                        checked = isSummaryEnabled,
                        onCheckedChange = { viewModel.setCallSummaryEnabled(it) }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "In-Call Notes Assistant",
                        subtitle = "Display live note pad with smart action chip suggestions",
                        icon = Icons.AutoMirrored.Filled.Note,
                        checked = isNotesEnabled,
                        onCheckedChange = { viewModel.setCallNotesEnabled(it) }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "Voice Greeting (Experimental)",
                        subtitle = "Synthesize local greeting announcement on connect",
                        icon = Icons.Default.Mic,
                        checked = isVoiceGreetingEnabled,
                        onCheckedChange = { viewModel.setVoiceGreetingEnabled(it) }
                    )
                    CapsuleGroupDivider()
                    CapsuleToggle(
                        title = "Local-Only Operation Mode",
                        subtitle = "Keep calls & contacts strictly offline unless you tap Cloud Backup",
                        icon = Icons.Default.Lock,
                        checked = isLocalOnly,
                        onCheckedChange = { viewModel.setLocalOnlyMode(it) },
                        accentColor = colors.priorityColor
                    )
                }
            }

            // Module 7: Owner Profile & Cloud Backup
            item(key = "module_owner_backup") {
                ExpandableSettingsModule(
                    moduleKey = "owner_backup",
                    title = "Owner Profile & Cloud Backup",
                    summary = "$ownerName • ${if (currentUser != null) "${cloudContacts.size} cloud contacts" else "Offline JSON backup"}",
                    icon = Icons.Default.AccountCircle,
                    expanded = ownerBackupExpanded,
                    onToggleExpand = { ownerBackupExpanded = !ownerBackupExpanded },
                    accentColor = colors.primary,
                    statusBadge = if (currentUser != null) "Connected" else "Local",
                    statusBadgeColor = if (currentUser != null) colors.acceptColor else colors.textSecondary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Owner: $ownerName", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Number: $ownerPhone", color = colors.primaryGlow, fontSize = 12.sp)
                            Text("Calls from this number receive priority alerts.", color = colors.textMuted, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = {
                                editOwnerName = ownerName
                                editOwnerPhone = ownerPhone
                                showEditOwnerDialog = true
                            },
                            modifier = Modifier.testTag("edit_owner_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Owner", tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                    }

                    CapsuleGroupDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (currentUser != null) colors.acceptColor else colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentUser?.displayName ?: "Google Cloud Contact Backup",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        isLocalOnly -> "Local-Only Operation Mode is active — cloud sync disabled."
                                        currentUser != null -> "${currentUser?.email ?: "Signed in"} • ${cloudContacts.size} contacts"
                                        else -> "Optional sign-in for encrypted cloud backup & restore"
                                    },
                                    color = when {
                                        isLocalOnly -> colors.priorityColor
                                        currentUser != null -> colors.acceptColor
                                        else -> colors.textSecondary
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (currentUser == null) {
                            Button(
                                onClick = { viewModel.signInWithGoogle(context) },
                                enabled = !isSigningIn && !isLocalOnly,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = CircleShape,
                                modifier = Modifier.testTag("google_sign_in_btn")
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.onPrimaryButton, strokeWidth = 2.dp)
                                } else {
                                    Text("Google Login", color = colors.onPrimaryButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.signOutFromGoogle() },
                                border = BorderStroke(1.dp, colors.declineColor.copy(alpha = 0.5f)),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("google_sign_out_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = colors.declineColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign Out", color = colors.declineColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (currentUser != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.backupContactsToCloud() },
                                enabled = !isCloudSyncing && !isLocalOnly,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f).testTag("cloud_backup_btn")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = colors.onPrimaryButton, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup Cloud", color = colors.onPrimaryButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.restoreContactsFromCloud() },
                                enabled = !isCloudSyncing && !isLocalOnly,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f).testTag("cloud_restore_btn")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore Cloud", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.exportBackup() },
                            border = BorderStroke(1.dp, colors.glassBorder),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).testTag("export_backup_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = colors.primaryGlow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Local Export", color = colors.primaryGlow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            border = BorderStroke(1.dp, colors.glassBorder),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).testTag("restore_backup_btn")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Local Restore", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    backupStatus?.let { statusText ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val statusColor = when {
                            statusText.contains("failed", ignoreCase = true) ||
                                statusText.contains("error", ignoreCase = true) ||
                                statusText.contains("cancelled", ignoreCase = true) ||
                                statusText.contains("no google account", ignoreCase = true) ||
                                statusText.contains("not configured", ignoreCase = true) ||
                                statusText.contains("unavailable", ignoreCase = true) -> colors.declineColor
                            statusText.endsWith("...") -> colors.primary
                            else -> colors.acceptColor
                        }
                        Text(text = statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Compact About Lyra Footer
            item(key = "about_footer") {
                GlassCard(
                    modifier = Modifier.testTag("about_card"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.settings_footer_version), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.settings_footer_owner), color = colors.primaryGlow, fontSize = 11.sp)
                        }
                        TextButton(onClick = onNavigateToAbout) {
                            Text(stringResource(R.string.action_details), color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showEditOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showEditOwnerDialog = false },
            title = { Text("Owner Profile Settings", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editOwnerName,
                        onValueChange = { editOwnerName = it },
                        label = { Text("Owner Name", color = colors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editOwnerPhone,
                        onValueChange = { editOwnerPhone = it },
                        label = { Text("Owner Phone Number", color = colors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editOwnerName.isNotBlank()) viewModel.setOwnerName(editOwnerName)
                        if (editOwnerPhone.isNotBlank()) viewModel.setOwnerPhone(editOwnerPhone)
                        showEditOwnerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditOwnerDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Contacts from JSON", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Paste contact backup JSON content below:", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        placeholder = { Text("{\"contacts\": [...]}", color = colors.textMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    restoreResult?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = it, color = colors.acceptColor, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            viewModel.restoreBackup(restoreJsonInput) { count ->
                                restoreResult = "Successfully imported $count priority contacts!"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Restore", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
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

    if (showDeleteApiKeyConfirm) {
        LyraDeleteConfirmationDialog(
            title = "Delete Saved API Key?",
            message = "Are you sure you want to delete your saved AI provider API key from secure local storage?",
            onConfirm = {
                draftApiKey = ""
                viewModel.deleteAiApiKey()
                showDeleteApiKeyConfirm = false
            },
            onDismiss = { showDeleteApiKeyConfirm = false }
        )
    }
}
