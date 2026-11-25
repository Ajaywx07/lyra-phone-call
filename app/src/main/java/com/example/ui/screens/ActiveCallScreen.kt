package com.example.ui.screens

import android.telecom.CallAudioState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.CallScreenStyle
import com.example.domain.model.CallStateEnum
import androidx.compose.foundation.clickable
import com.example.domain.model.CallerInfo
import com.example.service.CallStateManager
import com.example.ui.components.AudioRoutePickerDialog
import com.example.ui.components.CallActionButton
import com.example.ui.components.CallBackgroundAnimation
import com.example.ui.components.ConferenceParticipantsSheet
import com.example.ui.components.FullScreenAddCallOverlay
import com.example.ui.components.InCallDtmfKeypadDialog
import com.example.ui.components.InCallMoreOptionsSheet
import com.example.ui.components.LyraContactAvatar
import com.example.ui.components.LyraOrb
import com.example.ui.components.TopIncomingCallBanner
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.CallProximityManager
import kotlinx.coroutines.delay

@Composable
fun ActiveCallScreen(
    viewModel: LyraMainViewModel,
    onEndCall: () -> Unit,
    onCancelCall: () -> Unit = onEndCall,
    onNavigateBackToApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val caller by viewModel.currentCaller.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val isOutgoing by viewModel.isOutgoingCall.collectAsStateWithLifecycle()
    val activeSimLabel by viewModel.activeSimLabel.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isHold by viewModel.isHold.collectAsStateWithLifecycle()
    val currentAudioRoute by viewModel.currentAudioRoute.collectAsStateWithLifecycle()
    val isBluetoothAvailable by viewModel.isBluetoothAvailable.collectAsStateWithLifecycle()
    val conferenceCount by viewModel.conferenceParticipantCount.collectAsStateWithLifecycle()
    val canMergeConference by viewModel.canMergeConference.collectAsStateWithLifecycle()
    val isConferenceMerged by viewModel.isConferenceMerged.collectAsStateWithLifecycle()
    val callStatusBanner by viewModel.callStatusBanner.collectAsStateWithLifecycle()
    val screenStyle by viewModel.callScreenStyle.collectAsStateWithLifecycle()
    val animationStyle by viewModel.callAnimationStyle.collectAsStateWithLifecycle()
    val showContactPhoto by viewModel.showContactPhotoOnCall.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isNearEar by CallProximityManager.isNearEar.collectAsStateWithLifecycle()

    val defaultCaller = CallerInfo(
        phoneNumber = "+91 98765 43210",
        formattedNumber = "+91 98765 43210",
        displayName = "Ajay",
        isKnown = true,
        isPriority = true,
        isOwner = true
    )
    val activeCaller = caller ?: defaultCaller

    val conferenceParticipants by viewModel.conferenceParticipants.collectAsStateWithLifecycle()
    var showKeypadDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showAddCallScreen by remember { mutableStateOf(false) }
    var showAudioRouteMenu by remember { mutableStateOf(false) }
    var showConferenceParticipantsSheet by remember { mutableStateOf(false) }
    var bgPhotoLoadFailed by remember(activeCaller.photoUri) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshAudioDeviceAvailability()
    }

    val isCallOngoing = callState == CallStateEnum.DIALING || callState == CallStateEnum.ACTIVE
    LaunchedEffect(isCallOngoing, isSpeakerOn) {
        CallProximityManager.updateCallAudioState(
            context = context,
            isCallOngoing = isCallOngoing,
            isSpeakerOn = isSpeakerOn
        )
    }

    LaunchedEffect(callStatusBanner) {
        if (!callStatusBanner.isNullOrBlank()) {
            delay(3500L)
            viewModel.clearCallStatusBanner()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!CallStateManager.isOngoingCall()) {
                CallProximityManager.release()
            }
        }
    }

    val handleEndOrCancel: () -> Unit = {
        CallProximityManager.release()
        if (callState == CallStateEnum.DIALING || callState == CallStateEnum.IDLE) {
            viewModel.cancelDialingCall()
            onCancelCall()
        } else {
            viewModel.endCall()
            onEndCall()
        }
    }

    val noModalOverlayOpen = !showAddCallScreen &&
        !showKeypadDialog &&
        !showMoreSheet &&
        !showAudioRouteMenu &&
        !showConferenceParticipantsSheet

    BackHandler(
        enabled = onNavigateBackToApp != null &&
            (isCallOngoing || callState == CallStateEnum.IDLE) &&
            noModalOverlayOpen
    ) {
        if (isCallOngoing) {
            CallStateManager.setUserMinimizedCallUi(true)
            onNavigateBackToApp?.invoke()
        } else {
            onCancelCall()
        }
    }

    BackHandler(
        enabled = onNavigateBackToApp == null &&
            (callState == CallStateEnum.DIALING || callState == CallStateEnum.IDLE) &&
            !showAddCallScreen
    ) {
        viewModel.cancelDialingCall()
        onCancelCall()
    }

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    val statusText = when {
        isHold -> stringResource(R.string.call_status_on_hold)
        callState == CallStateEnum.DIALING -> stringResource(R.string.call_status_calling_sim, activeSimLabel)
        else -> durationFormatted
    }

    val hasBackgroundPhoto = showContactPhoto && !activeCaller.photoUri.isNullOrBlank() && !bgPhotoLoadFailed
    val accentColor = when (screenStyle) {
        CallScreenStyle.GOOGLE_PIXEL -> Color(0xFF8AB4F8)
        CallScreenStyle.IPHONE -> EmeraldAccept
        CallScreenStyle.NORMAL -> if (hasBackgroundPhoto) Color(0xFF80F0FF) else colors.primaryGlow
        CallScreenStyle.LYRA_AI -> if (hasBackgroundPhoto) Color(0xFF00E5FF) else colors.primary
    }
    val onAccentColor = when (screenStyle) {
        CallScreenStyle.GOOGLE_PIXEL -> Color.Black
        CallScreenStyle.IPHONE -> Color.White
        CallScreenStyle.NORMAL, CallScreenStyle.LYRA_AI -> if (hasBackgroundPhoto) Color.Black else colors.onPrimaryButton
    }
    val overlayTextPrimary = if (hasBackgroundPhoto) Color.White else colors.textPrimary
    val overlayChipSurface = if (hasBackgroundPhoto) Color(0xD9191C2B) else colors.surfaceElevated.copy(alpha = 0.92f)
    val overlayButtonSurface = if (hasBackgroundPhoto) Color(0xE6191C2B) else colors.surfaceElevated.copy(alpha = 0.92f)
    val overlayHintColor = if (hasBackgroundPhoto) Color(0xFF80F0FF) else colors.primaryGlow

    @Suppress("DEPRECATION")
    val isBluetoothActive = isBluetoothAvailable && (currentAudioRoute and CallAudioState.ROUTE_BLUETOOTH) != 0
    val isCustomAudioRouteActive = isSpeakerOn || isBluetoothActive
    val hasExtraActiveState = isHold || isConferenceMerged || canMergeConference

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("active_call_screen")
    ) {
        if (callState == CallStateEnum.RINGING) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TopIncomingCallBanner(
                    caller = activeCaller,
                    activeSimLabel = activeSimLabel,
                    isSilenced = false,
                    onBannerClick = {},
                    onAcceptClick = { viewModel.acceptCall() },
                    onDeclineClick = { viewModel.declineCall() }
                )
            }
        }
        // Dynamic Contact Photo Background (if available) with atmospheric dark scrim
        if (hasBackgroundPhoto) {
            val bgPhotoRequest = remember(activeCaller.photoUri, context) {
                ImageRequest.Builder(context)
                    .data(activeCaller.photoUri)
                    .size(1080, 1920)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = bgPhotoRequest,
                contentDescription = "${activeCaller.displayName} background photo",
                contentScale = ContentScale.Crop,
                onError = { bgPhotoLoadFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("active_call_photo_background")
            )
            val scrimBase = Color(0xFF08090E)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scrimBase.copy(alpha = 0.62f),
                                scrimBase.copy(alpha = 0.74f),
                                scrimBase.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        CallBackgroundAnimation(
            style = animationStyle,
            accentColor = accentColor
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            val compactHeight = maxHeight < 640.dp
            val orbSize = if (compactHeight) 86.dp else 116.dp
            val avatarSize = if (compactHeight) 82.dp else 104.dp
            val actionCircleSize = if (compactHeight) 52.dp else 58.dp
            val gridRowGap = if (compactHeight) 14.dp else 20.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Caller Identity & Status Area (centered cleanly above fixed controls dock)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Direction & SIM Capsule Pill
                    Surface(
                        color = overlayChipSurface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${if (isOutgoing) "OUTGOING CALL" else "ACTIVE CALL"} • $activeSimLabel • ${screenStyle.displayName}",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Active Extra State Indicators (Conference / Status Banner)
                    if (conferenceCount > 1 || !callStatusBanner.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (conferenceCount > 1) {
                            Surface(
                                color = EmeraldAccept.copy(alpha = 0.18f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, EmeraldAccept.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .testTag("active_call_conference_badge")
                                    .clickable { showConferenceParticipantsSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = "Conference call",
                                        tint = EmeraldAccept,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConferenceMerged) "Conference • $conferenceCount" else "$conferenceCount Callers",
                                        color = EmeraldAccept,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (!callStatusBanner.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = overlayChipSurface,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, overlayHintColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = callStatusBanner.orEmpty(),
                                    color = overlayHintColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 18.dp))

                    // AI Orb or Contact Avatar depending on CallScreenStyle
                    if (screenStyle == CallScreenStyle.LYRA_AI) {
                        LyraOrb(
                            size = orbSize,
                            state = if (callState == CallStateEnum.DIALING) CallStateEnum.RINGING else CallStateEnum.ACTIVE,
                            customStatusText = if (isHold) {
                                "ON HOLD"
                            } else if (callState == CallStateEnum.DIALING) {
                                "DIALING..."
                            } else {
                                "LYRA ASSISTING"
                            }
                        )
                    } else {
                        LyraContactAvatar(
                            name = activeCaller.displayName,
                            photoUri = activeCaller.photoUri,
                            isOwner = activeCaller.isOwner,
                            isPriority = activeCaller.isPriority,
                            size = avatarSize,
                            accentColor = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 16.dp))

                    Text(
                        text = activeCaller.displayName,
                        color = overlayTextPrimary,
                        fontSize = if (compactHeight) 22.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = activeCaller.formattedNumber,
                        color = accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = (if (callState == CallStateEnum.DIALING || isHold) AmberPriority else EmeraldAccept)
                            .copy(alpha = 0.16f),
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            (if (callState == CallStateEnum.DIALING || isHold) AmberPriority else EmeraldAccept)
                                .copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = statusText,
                            color = if (callState == CallStateEnum.DIALING || isHold) AmberPriority else EmeraldAccept,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    if (callState == CallStateEnum.DIALING) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { viewModel.acceptCall() },
                            modifier = Modifier.testTag("connect_outgoing_preview_btn")
                        ) {
                            Text(
                                text = "Tap to simulate call answered",
                                color = overlayHintColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Fixed Bottom Controls Dock (Non-Scrolling 2-Row × 3-Column Grid + 70% End Call Pill)
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(bottom = if (compactHeight) 6.dp else 14.dp)
                        .testTag("in_call_controls_dock"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row: Exactly 3 Primary Buttons (Mute, Audio, Keypad)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("in_call_grid_top_row"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CallActionButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) stringResource(R.string.call_action_unmute) else stringResource(R.string.call_action_mute),
                            onClick = { viewModel.toggleMute() },
                            containerColor = if (isMuted) colors.secondary else overlayButtonSurface,
                            contentColor = if (isMuted) colors.onSecondaryButton else overlayTextPrimary,
                            size = actionCircleSize,
                            iconSize = 24.dp,
                            modifier = Modifier.weight(1f),
                            testTagOverride = if (isMuted) "call_action_Unmute" else "call_action_Mute"
                        )

                        CallActionButton(
                            icon = when {
                                isBluetoothActive -> Icons.Default.BluetoothAudio
                                isSpeakerOn -> Icons.Default.VolumeUp
                                else -> Icons.Default.Hearing
                            },
                            label = stringResource(R.string.call_action_audio),
                            onClick = {
                                viewModel.refreshAudioDeviceAvailability()
                                showAudioRouteMenu = true
                            },
                            containerColor = if (isCustomAudioRouteActive) accentColor else overlayButtonSurface,
                            contentColor = if (isCustomAudioRouteActive) onAccentColor else overlayTextPrimary,
                            size = actionCircleSize,
                            iconSize = 24.dp,
                            modifier = Modifier.weight(1f),
                            testTagOverride = "call_action_Audio"
                        )

                        CallActionButton(
                            icon = Icons.Default.Dialpad,
                            label = stringResource(R.string.call_action_keypad),
                            onClick = { showKeypadDialog = true },
                            containerColor = if (showKeypadDialog) accentColor.copy(alpha = 0.25f) else overlayButtonSurface,
                            contentColor = overlayTextPrimary,
                            size = actionCircleSize,
                            iconSize = 24.dp,
                            modifier = Modifier.weight(1f),
                            testTagOverride = "call_action_Keypad"
                        )
                    }

                    Spacer(modifier = Modifier.height(gridRowGap))

                    // Bottom Row: Exactly 2 Buttons (Add Call / Smart Merge, Hold) + 1 'More' Button
                    val showMergeButton = canMergeConference && !isConferenceMerged
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("in_call_grid_bottom_row"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CallActionButton(
                            icon = if (showMergeButton) Icons.Default.CallMerge else Icons.Default.PersonAdd,
                            label = if (showMergeButton) stringResource(R.string.call_action_merge) else stringResource(R.string.call_action_add_call),
                            onClick = {
                                if (showMergeButton) {
                                    viewModel.mergeConferenceCalls()
                                } else {
                                    showAddCallScreen = true
                                }
                            },
                            containerColor = when {
                                showMergeButton -> EmeraldAccept
                                conferenceCount > 1 -> EmeraldAccept.copy(alpha = 0.28f)
                                else -> overlayButtonSurface
                            },
                            contentColor = when {
                                showMergeButton -> Color.White
                                conferenceCount > 1 -> EmeraldAccept
                                else -> overlayTextPrimary
                            },
                            size = actionCircleSize,
                            iconSize = 24.dp,
                            modifier = Modifier.weight(1f),
                            testTagOverride = if (showMergeButton) "call_action_Merge" else "call_action_Add Call"
                        )

                        CallActionButton(
                            icon = if (isHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                            label = if (isHold) stringResource(R.string.call_action_resume) else stringResource(R.string.call_action_hold),
                            onClick = { viewModel.toggleHold() },
                            containerColor = if (isHold) AmberPriority else overlayButtonSurface,
                            contentColor = if (isHold) Color.Black else overlayTextPrimary,
                            size = actionCircleSize,
                            iconSize = 24.dp,
                            modifier = Modifier.weight(1f),
                            testTagOverride = if (isHold) "call_action_Resume" else "call_action_Hold"
                        )

                        CallActionButton(
                            icon = Icons.Default.MoreHoriz,
                            label = stringResource(R.string.call_action_more),
                            onClick = { showMoreSheet = true },
                            containerColor = if (hasExtraActiveState) accentColor.copy(alpha = 0.24f) else overlayButtonSurface,
                            contentColor = if (hasExtraActiveState) accentColor else overlayTextPrimary,
                            size = actionCircleSize,
                            iconSize = 24.dp,
                            modifier = Modifier.weight(1f),
                            testTagOverride = "call_action_More"
                        )
                    }

                    Spacer(modifier = Modifier.height(if (compactHeight) 18.dp else 28.dp))

                    // End / Cancel Call Stretched Pill (~70% width relative to main controls container)
                    val isCanceling = callState == CallStateEnum.DIALING || callState == CallStateEnum.IDLE
                    val endTag = if (isCanceling) "Cancel Call" else "End Call"
                    val endLabel = if (isCanceling) {
                        stringResource(R.string.call_action_cancel_call)
                    } else {
                        stringResource(R.string.call_action_end_call)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = handleEndOrCancel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonDecline,
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.70f)
                                .height(58.dp)
                                .testTag("call_action_$endTag")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = endLabel,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = endLabel,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Add Call / Conference Interface (Dialpad + Contact Search/Picker)
        AnimatedVisibility(
            visible = showAddCallScreen,
            enter = slideInVertically(
                animationSpec = tween(170, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 3 }
            ) + fadeIn(tween(150)),
            exit = slideOutVertically(
                animationSpec = tween(140, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 3 }
            ) + fadeOut(tween(120))
        ) {
            FullScreenAddCallOverlay(
                contacts = contacts,
                activeCallerName = activeCaller.displayName,
                onPlaceSecondaryCall = { number -> viewModel.addCallParticipant(number) },
                onDismiss = { showAddCallScreen = false }
            )
        }

        // Proximity Ear Guard: turns screen black & blocks accidental ear touches when speaker is off
        if (isCallOngoing && !isSpeakerOn && isNearEar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {}
                    .testTag("proximity_screen_off_overlay")
            )
        }
    }

    if (showAudioRouteMenu) {
        AudioRoutePickerDialog(
            currentAudioRoute = currentAudioRoute,
            isBluetoothAvailable = isBluetoothAvailable,
            onSelectAudioRoute = { route -> viewModel.selectAudioRoute(route) },
            onDismiss = { showAudioRouteMenu = false }
        )
    }

    if (showMoreSheet) {
        InCallMoreOptionsSheet(
            isHold = isHold,
            conferenceParticipantCount = conferenceCount,
            canMergeConference = canMergeConference,
            isConferenceMerged = isConferenceMerged,
            currentAudioRoute = currentAudioRoute,
            isBluetoothAvailable = isBluetoothAvailable,
            onToggleHold = { viewModel.toggleHold() },
            onAddCallClick = { showAddCallScreen = true },
            onMergeConference = { viewModel.mergeConferenceCalls() },
            onSelectAudioRoute = { route -> viewModel.selectAudioRoute(route) },
            onDismiss = { showMoreSheet = false }
        )
    }

    if (showKeypadDialog) {
        InCallDtmfKeypadDialog(
            onSendDtmf = { digit -> viewModel.sendDtmf(digit) },
            onDismiss = { showKeypadDialog = false }
        )
    }

    if (showConferenceParticipantsSheet) {
        ConferenceParticipantsSheet(
            participants = conferenceParticipants,
            isConferenceMerged = isConferenceMerged,
            onDisconnectParticipant = { viewModel.disconnectParticipant(it) },
            onToggleHoldParticipant = { id, hold -> viewModel.toggleHoldParticipant(id, hold) },
            onDismiss = { showConferenceParticipantsSheet = false }
        )
    }
}
