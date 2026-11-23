package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.CallScreenStyle
import com.example.domain.model.CallerInfo
import com.example.ui.components.CallActionButton
import com.example.ui.components.CallBackgroundAnimation
import com.example.ui.components.CallerCard
import com.example.ui.components.LyraContactAvatar
import com.example.ui.components.LyraOrb
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.service.CallStateManager
import com.example.service.NotificationHelper
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PermissionHelper
import kotlin.math.roundToInt

@Composable
fun IncomingCallScreen(
    viewModel: LyraMainViewModel,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onVoicemailCall: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeColors = LyraTheme.colors
    val caller by viewModel.currentCaller.collectAsStateWithLifecycle()
    val activeSimLabel by viewModel.activeSimLabel.collectAsStateWithLifecycle()
    val screenStyle by viewModel.callScreenStyle.collectAsStateWithLifecycle()
    val animationStyle by viewModel.callAnimationStyle.collectAsStateWithLifecycle()
    val autoSendQuickSms by viewModel.autoSendQuickSms.collectAsStateWithLifecycle()
    val isSilenced by viewModel.isRingerSilenced.collectAsStateWithLifecycle()
    val showContactPhoto by viewModel.showContactPhotoOnCall.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        CallStateManager.setFullScreenIncomingUiVisible(true)
        if (!CallStateManager.hasTelecomBridge()) {
            NotificationHelper.cancelAllCallNotifications(context)
        }
        onDispose {
            CallStateManager.setFullScreenIncomingUiVisible(false)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setAutoSendQuickSms(granted)
        viewModel.loadData()
    }

    val defaultCaller = CallerInfo(
        phoneNumber = "+91 98765 43210",
        formattedNumber = "+91 98765 43210",
        displayName = "Ajay",
        isKnown = true,
        isPriority = true,
        isOwner = true,
        contactLabel = "Owner / Primary Device"
    )
    val activeCaller = caller ?: defaultCaller
    val callerPhoto = if (showContactPhoto) activeCaller.photoUri else null

    var showQuickMessageDialog by remember { mutableStateOf(false) }
    var isPixelScreening by remember { mutableStateOf(false) }

    val accentColor = when {
        activeCaller.isOwner -> AmberPriority
        screenStyle == CallScreenStyle.GOOGLE_PIXEL -> Color(0xFF8AB4F8)
        screenStyle == CallScreenStyle.IPHONE -> EmeraldAccept
        else -> themeColors.primary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when (screenStyle) {
                    CallScreenStyle.IPHONE -> Brush.verticalGradient(
                        listOf(Color(0xFF141821), Color(0xFF0A0D12))
                    )
                    CallScreenStyle.GOOGLE_PIXEL -> Brush.verticalGradient(
                        listOf(Color(0xFF101824), Color(0xFF0A0E16))
                    )
                    CallScreenStyle.NORMAL -> Brush.verticalGradient(
                        listOf(themeColors.surface, themeColors.background)
                    )
                    CallScreenStyle.LYRA_AI -> Brush.verticalGradient(
                        listOf(themeColors.background, themeColors.surface)
                    )
                }
            )
            .testTag("incoming_call_screen")
    ) {
        // Configurable Background Animation
        CallBackgroundAnimation(
            style = animationStyle,
            accentColor = accentColor
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when (screenStyle) {
                CallScreenStyle.LYRA_AI -> {
                    LyraAiIncomingHeader(
                        caller = activeCaller,
                        simLabel = activeSimLabel,
                        isSilenced = isSilenced
                    )
                    AnimatedVisibility(visible = true, enter = fadeIn(tween(180))) {
                        CallerCard(caller = activeCaller.copy(photoUri = callerPhoto))
                    }
                    StandardIncomingActions(
                        isSilenced = isSilenced,
                        onToggleSilence = { viewModel.silenceRinger() },
                        onOpenMessages = { showQuickMessageDialog = true },
                        onDecline = {
                            viewModel.declineCall()
                            onDeclineCall()
                        },
                        onVoicemail = {
                            viewModel.startVoicemailScreening()
                            onVoicemailCall()
                        },
                        onAccept = {
                            viewModel.acceptCall()
                            onAcceptCall()
                        }
                    )
                }

                CallScreenStyle.IPHONE -> {
                    // Top iOS Typography Block
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 28.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Text(
                                text = buildString {
                                    append("$activeSimLabel • ${if (activeCaller.isPriority) "Priority" else activeCaller.contactLabel}")
                                    if (isSilenced) append(" • Silenced")
                                },
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = activeCaller.displayName,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = activeCaller.formattedNumber,
                            color = Color(0xFFCBD5E1),
                            fontSize = 16.sp
                        )
                    }

                    // Center Caller Photo or Monogram Circle
                    LyraContactAvatar(
                        name = activeCaller.displayName,
                        photoUri = callerPhoto,
                        isOwner = activeCaller.isOwner,
                        isPriority = activeCaller.isPriority,
                        size = 116.dp,
                        accentColor = Color.White
                    )

                    // iOS Bottom Section: Message/Silence + Slide-to-Answer + End Call / Voicemail / Pick Up
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CallActionButton(
                                icon = Icons.AutoMirrored.Filled.VolumeOff,
                                label = if (isSilenced) stringResource(R.string.incoming_silenced) else stringResource(R.string.incoming_silence),
                                onClick = { viewModel.silenceRinger() },
                                containerColor = if (isSilenced) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                                size = 50.dp,
                                iconSize = 22.dp,
                                testTagOverride = if (isSilenced) "call_action_Silenced" else "call_action_Silence"
                            )
                            CallActionButton(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = stringResource(R.string.incoming_message),
                                onClick = { showQuickMessageDialog = true },
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                                size = 50.dp,
                                iconSize = 22.dp,
                                testTagOverride = "call_action_Message"
                            )
                        }

                        // iOS Slide-to-Answer bar
                        IphoneSlideToAnswerBar(
                            onSlideComplete = {
                                viewModel.acceptCall()
                                onAcceptCall()
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CallActionButton(
                                icon = Icons.Default.CallEnd,
                                label = stringResource(R.string.call_action_end_call),
                                onClick = {
                                    viewModel.declineCall()
                                    onDeclineCall()
                                },
                                containerColor = CrimsonDecline,
                                contentColor = Color.White,
                                size = 64.dp,
                                iconSize = 28.dp,
                                modifier = Modifier.weight(1f),
                                testTagOverride = "decline_call_btn"
                            )
                            CallActionButton(
                                icon = Icons.Default.Voicemail,
                                label = stringResource(R.string.incoming_voicemail),
                                onClick = {
                                    viewModel.startVoicemailScreening()
                                    onVoicemailCall()
                                },
                                containerColor = Color(0xFF7C3AED),
                                contentColor = Color.White,
                                size = 64.dp,
                                iconSize = 28.dp,
                                modifier = Modifier.weight(1f),
                                testTagOverride = "voicemail_call_btn"
                            )
                            CallActionButton(
                                icon = Icons.Default.Call,
                                label = stringResource(R.string.incoming_pick_up),
                                onClick = {
                                    viewModel.acceptCall()
                                    onAcceptCall()
                                },
                                containerColor = EmeraldAccept,
                                contentColor = Color.White,
                                size = 64.dp,
                                iconSize = 28.dp,
                                modifier = Modifier.weight(1f),
                                testTagOverride = "accept_call_btn"
                            )
                        }
                    }
                }

                CallScreenStyle.NORMAL -> {
                    // Clean Classic Material Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text(
                            text = "Incoming Call • $activeSimLabel",
                            color = themeColors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        LyraContactAvatar(
                            name = activeCaller.displayName,
                            photoUri = callerPhoto,
                            isOwner = activeCaller.isOwner,
                            isPriority = activeCaller.isPriority,
                            size = 96.dp,
                            accentColor = themeColors.primary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = activeCaller.displayName,
                            color = themeColors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeCaller.formattedNumber,
                            color = themeColors.primaryGlow,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = themeColors.surfaceElevated,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, themeColors.glassBorder)
                        ) {
                            Text(
                                text = buildString {
                                    append(if (activeCaller.isKnown) activeCaller.contactLabel else "Unsaved Number")
                                    if (isSilenced) append(" • Silenced")
                                },
                                color = if (activeCaller.isPriority) AmberPriority else themeColors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StandardIncomingActions(
                        isSilenced = isSilenced,
                        onToggleSilence = { viewModel.silenceRinger() },
                        onOpenMessages = { showQuickMessageDialog = true },
                        onDecline = {
                            viewModel.declineCall()
                            onDeclineCall()
                        },
                        onVoicemail = {
                            viewModel.startVoicemailScreening()
                            onVoicemailCall()
                        },
                        onAccept = {
                            viewModel.acceptCall()
                            onAcceptCall()
                        }
                    )
                }

                CallScreenStyle.GOOGLE_PIXEL -> {
                    val pixelBlue = Color(0xFF8AB4F8)
                    val pixelSurface = Color(0xFF1F2A38)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Surface(
                            color = pixelSurface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, pixelBlue.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = pixelBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = buildString {
                                        append("Incoming call • $activeSimLabel")
                                        if (isSilenced) append(" • Silenced")
                                    },
                                    color = pixelBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        LyraContactAvatar(
                            name = activeCaller.displayName,
                            photoUri = callerPhoto,
                            isOwner = activeCaller.isOwner,
                            isPriority = activeCaller.isPriority,
                            size = 112.dp,
                            accentColor = pixelBlue
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = activeCaller.displayName,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${activeCaller.contactLabel} • ${activeCaller.formattedNumber}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Pixel Call Screen Card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isPixelScreening) {
                            Surface(
                                color = pixelSurface,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, pixelBlue.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                            ) {
                                Text(
                                    text = "Lyra Screen Active: \"Hi, please state your name and reason for calling Ajay.\"",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(14.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 22.dp)
                        ) {
                            Button(
                                onClick = { isPixelScreening = !isPixelScreening },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPixelScreening) pixelBlue else pixelSurface
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.testTag("pixel_screen_call_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isPixelScreening) Color.Black else pixelBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPixelScreening) "Screening..." else "Screen Call",
                                    color = if (isPixelScreening) Color.Black else pixelBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = { showQuickMessageDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = pixelSurface),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Message", color = Color.White, fontSize = 13.sp)
                            }
                        }

                        // Pixel Pill End Call, Voicemail & Pick Up Buttons
                        Button(
                            onClick = {
                                viewModel.startVoicemailScreening()
                                onVoicemailCall()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("voicemail_call_btn")
                        ) {
                            Icon(Icons.Default.Voicemail, contentDescription = "Voicemail", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voicemail • Lyra Screener", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.declineCall()
                                    onDeclineCall()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonDecline),
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .testTag("decline_call_btn")
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("End Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.acceptCall()
                                    onAcceptCall()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccept),
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .testTag("accept_call_btn")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Pick Up", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Up", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Message Dialog
    if (showQuickMessageDialog) {
        val quickMessages = listOf(
            "I'm in a meeting right now. Can I call you back?",
            "Can't talk right now. Please text me.",
            "I'll call you back in 10 minutes.",
            "On my way, will reach shortly."
        )
        val hasSmsPermission = PermissionHelper.hasSendSmsPermission(context)

        AlertDialog(
            onDismissRequest = { showQuickMessageDialog = false },
            title = {
                Text(
                    text = "Quick Response",
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = themeColors.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, themeColors.glassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-Send Directly",
                                    color = themeColors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (autoSendQuickSms && hasSmsPermission) {
                                        "Sends in background without opening SMS app"
                                    } else {
                                        "Opens messaging app with recipient pre-filled"
                                    },
                                    color = themeColors.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = autoSendQuickSms && hasSmsPermission,
                                onCheckedChange = { enable ->
                                    if (enable && !PermissionHelper.hasSendSmsPermission(context)) {
                                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                    } else {
                                        viewModel.setAutoSendQuickSms(enable)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = themeColors.onPrimaryButton,
                                    checkedTrackColor = themeColors.primary
                                ),
                                modifier = Modifier.testTag("incoming_quick_sms_auto_switch")
                            )
                        }
                    }

                    quickMessages.forEachIndexed { idx, msg ->
                        Surface(
                            color = themeColors.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, themeColors.glassBorder),
                            onClick = {
                                viewModel.sendQuickResponseSms(
                                    phoneNumber = activeCaller.phoneNumber,
                                    message = msg,
                                    autoSendOverride = autoSendQuickSms
                                )
                                showQuickMessageDialog = false
                                viewModel.declineCall()
                                onDeclineCall()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_sms_option_$idx")
                        ) {
                            Text(
                                text = msg,
                                color = themeColors.primaryGlow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickMessageDialog = false }) {
                    Text(text = "Cancel", color = themeColors.textSecondary)
                }
            },
            containerColor = themeColors.surfaceElevated
        )
    }
}

@Composable
private fun LyraAiIncomingHeader(
    caller: CallerInfo,
    simLabel: String,
    isSilenced: Boolean
) {
    val themeColors = LyraTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Surface(
            color = themeColors.surfaceElevated,
            shape = CircleShape,
            border = BorderStroke(
                1.dp,
                if (isSilenced) themeColors.primary.copy(alpha = 0.55f) else themeColors.glassBorder
            )
        ) {
            Text(
                text = "CALL INCOMING • $simLabel",
                color = themeColors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSilenced) "Ringtone Silenced • Notification Active" else "Lyra Smart Call Screener",
            color = if (isSilenced) themeColors.primaryGlow else themeColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(14.dp))
        LyraOrb(
            size = 104.dp,
            customStatusText = if (caller.isOwner) "Owner Priority Verified" else "Screening caller ID..."
        )
    }
}

@Composable
private fun StandardIncomingActions(
    isSilenced: Boolean,
    onToggleSilence: () -> Unit,
    onOpenMessages: () -> Unit,
    onDecline: () -> Unit,
    onVoicemail: () -> Unit,
    onAccept: () -> Unit
) {
    val themeColors = LyraTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallActionButton(
                icon = Icons.AutoMirrored.Filled.Message,
                label = stringResource(R.string.incoming_message),
                onClick = onOpenMessages,
                containerColor = themeColors.surfaceElevated,
                contentColor = themeColors.primary,
                size = 52.dp,
                iconSize = 22.dp,
                testTagOverride = "call_action_Message"
            )
            CallActionButton(
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                label = if (isSilenced) stringResource(R.string.incoming_silenced) else stringResource(R.string.incoming_silence),
                onClick = onToggleSilence,
                containerColor = themeColors.surfaceElevated,
                contentColor = if (isSilenced) themeColors.primaryGlow else themeColors.textSecondary,
                size = 52.dp,
                iconSize = 22.dp,
                testTagOverride = if (isSilenced) "call_action_Silenced" else "call_action_Silence"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallActionButton(
                icon = Icons.Default.CallEnd,
                label = stringResource(R.string.call_action_end_call),
                onClick = onDecline,
                containerColor = CrimsonDecline,
                contentColor = Color.White,
                size = 66.dp,
                iconSize = 30.dp,
                modifier = Modifier.weight(1f),
                testTagOverride = "decline_call_btn"
            )
            CallActionButton(
                icon = Icons.Default.Voicemail,
                label = stringResource(R.string.incoming_voicemail),
                onClick = onVoicemail,
                containerColor = Color(0xFF7C3AED),
                contentColor = Color.White,
                size = 66.dp,
                iconSize = 30.dp,
                modifier = Modifier.weight(1f),
                testTagOverride = "voicemail_call_btn"
            )
            CallActionButton(
                icon = Icons.Default.Call,
                label = stringResource(R.string.incoming_pick_up),
                onClick = onAccept,
                containerColor = EmeraldAccept,
                contentColor = Color.White,
                size = 66.dp,
                iconSize = 30.dp,
                modifier = Modifier.weight(1f),
                testTagOverride = "accept_call_btn"
            )
        }
    }
}

@Composable
private fun IphoneSlideToAnswerBar(
    onSlideComplete: () -> Unit
) {
    val thumbSize = 54.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(32.dp))
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val maxOffsetPx = with(density) { (maxWidth - thumbSize - 10.dp).toPx().coerceAtLeast(1f) }
        var dragOffset by remember { mutableFloatStateOf(0f) }

        Text(
            text = "slide to answer",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(EmeraldAccept)
                .pointerInput(maxOffsetPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxOffsetPx)
                        },
                        onDragEnd = {
                            if (dragOffset >= maxOffsetPx * 0.75f) {
                                onSlideComplete()
                            } else {
                                dragOffset = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Slide to Answer",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
