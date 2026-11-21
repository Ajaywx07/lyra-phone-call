package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.VoicemailEntry
import com.example.domain.model.VoicemailPhase
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoicemailScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val caller by viewModel.currentCaller.collectAsStateWithLifecycle()
    val phase by viewModel.voicemailPhase.collectAsStateWithLifecycle()
    val amplitude by viewModel.voicemailAmplitude.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.voicemailElapsedSeconds.collectAsStateWithLifecycle()
    val maxSeconds by viewModel.voicemailMaxSeconds.collectAsStateWithLifecycle()
    val greetingText by viewModel.voicemailGreeting.collectAsStateWithLifecycle()
    val activeEntry by viewModel.activeVoicemailEntry.collectAsStateWithLifecycle()
    val voicemailError by viewModel.voicemailError.collectAsStateWithLifecycle()
    val allVoicemails by viewModel.allVoicemails.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val playingId by viewModel.playingVoicemailId.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val aiModelName by viewModel.aiModelName.collectAsStateWithLifecycle()
    var pendingDeleteVoicemail by remember { mutableStateOf<VoicemailEntry?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.loadData()
        if (granted) {
            viewModel.startVoicemailScreening()
        }
    }

    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasMicrophonePermission(context) &&
            phase == VoicemailPhase.ERROR
        ) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler {
        if (phase == VoicemailPhase.SPEAKING_GREETING ||
            phase == VoicemailPhase.PLAYING_BEEP ||
            phase == VoicemailPhase.RECORDING_MESSAGE
        ) {
            viewModel.cancelVoicemailScreening()
        } else {
            viewModel.stopVoicemailPlayback()
        }
        onBackClick()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .testTag("voicemail_screen"),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LyraTopBar(
                title = "LYRA VOICEMAIL",
                subtitle = "${aiProvider.displayName} • $aiModelName",
                showBack = true,
                onBackClick = {
                    if (phase == VoicemailPhase.SPEAKING_GREETING ||
                        phase == VoicemailPhase.PLAYING_BEEP ||
                        phase == VoicemailPhase.RECORDING_MESSAGE
                    ) {
                        viewModel.cancelVoicemailScreening()
                    } else {
                        viewModel.stopVoicemailPlayback()
                    }
                    onBackClick()
                },
                trailingActions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("voicemail_open_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI Model & API Settings",
                            tint = colors.primary
                        )
                    }
                }
            )
        }

        // Live Voicemail Screening Card (Active when screening a call or viewing active result)
        if (phase != VoicemailPhase.IDLE || activeEntry != null) {
            item {
                LiveVoicemailHudCard(
                    callerName = activeEntry?.callerName ?: caller?.displayName ?: "Incoming Caller",
                    callerNumber = activeEntry?.callerNumber ?: caller?.formattedNumber ?: "",
                    phase = phase,
                    greetingText = greetingText,
                    amplitude = amplitude,
                    elapsedSeconds = elapsedSeconds,
                    maxSeconds = maxSeconds,
                    providerLabel = "${aiProvider.displayName} ($aiModelName)",
                    activeEntry = activeEntry,
                    errorText = voicemailError,
                    isPlaying = activeEntry != null && playingId == activeEntry?.id,
                    onStopAndSummarize = { viewModel.stopVoicemailRecordingAndSummarize() },
                    onCancelScreening = {
                        viewModel.cancelVoicemailScreening()
                        onDone()
                    },
                    onTogglePlay = { activeEntry?.let { viewModel.toggleVoicemailPlayback(it) } },
                    onReanalyze = { activeEntry?.let { viewModel.reanalyzeVoicemail(it) } },
                    onCallBack = {
                        val number = activeEntry?.callerNumber ?: caller?.phoneNumber.orEmpty()
                        if (number.isNotBlank()) {
                            viewModel.placeCall(number)
                        }
                    },
                    onGrantMicAndRetry = {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenSettings = onOpenSettings,
                    onDone = {
                        viewModel.stopVoicemailPlayback()
                        onDone()
                    }
                )
            }
        }

        // Saved Voicemails Inbox
        item {
            SectionHeader(title = "Local Voicemails (${allVoicemails.size})")
        }

        if (allVoicemails.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Voicemail,
                    title = "No Local Voicemails Yet",
                    description = "Tap Voicemail on any incoming call. Lyra will greet the caller, play a beep, record locally, and generate a 1–2 line actionable summary.",
                    buttonText = "Simulate Screening Test",
                    onButtonClick = {
                        viewModel.simulateUnknownCall()
                        if (PermissionHelper.hasMicrophonePermission(context)) {
                            viewModel.startVoicemailScreening()
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        } else {
            items(allVoicemails, key = { it.id }) { entry ->
                val isPriorityContact = remember(contacts, entry.callerNumber) {
                    contacts.any { c ->
                        (c.isPriority || c.isOwner) &&
                            PhoneNumberUtils.areNumbersEqual(c.phoneNumber, entry.callerNumber)
                    }
                }
                SavedVoicemailCard(
                    entry = entry,
                    canDelete = !isPriorityContact,
                    isPlaying = playingId == entry.id,
                    isSelected = activeEntry?.id == entry.id,
                    onSelect = { viewModel.selectVoicemailForViewing(entry) },
                    onPlayToggle = { viewModel.toggleVoicemailPlayback(entry) },
                    onReanalyze = { viewModel.reanalyzeVoicemail(entry) },
                    onCallBack = { viewModel.placeCall(entry.callerNumber) },
                    onDelete = { pendingDeleteVoicemail = entry }
                )
            }
        }
    }

    pendingDeleteVoicemail?.let { target ->
        LyraDeleteConfirmationDialog(
            title = "Delete Voicemail?",
            message = "Are you sure you want to delete the voicemail from ${target.callerName.ifBlank { target.callerNumber }}?",
            onConfirm = {
                viewModel.deleteVoicemail(target)
                pendingDeleteVoicemail = null
            },
            onDismiss = { pendingDeleteVoicemail = null }
        )
    }
}

@Composable
private fun LiveVoicemailHudCard(
    callerName: String,
    callerNumber: String,
    phase: VoicemailPhase,
    greetingText: String,
    amplitude: Float,
    elapsedSeconds: Long,
    maxSeconds: Int,
    providerLabel: String,
    activeEntry: VoicemailEntry?,
    errorText: String?,
    isPlaying: Boolean,
    onStopAndSummarize: () -> Unit,
    onCancelScreening: () -> Unit,
    onTogglePlay: () -> Unit,
    onReanalyze: () -> Unit,
    onCallBack: () -> Unit,
    onGrantMicAndRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit
) {
    val colors = LyraTheme.colors
    val pulseTransition = rememberInfiniteTransition(label = "vm_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val borderColor = when (phase) {
        VoicemailPhase.RECORDING_MESSAGE -> CrimsonDecline.copy(alpha = 0.7f)
        VoicemailPhase.READY -> EmeraldAccept.copy(alpha = 0.65f)
        VoicemailPhase.ERROR -> AmberPriority.copy(alpha = 0.75f)
        else -> colors.secondary.copy(alpha = 0.65f)
    }

    GlassCard(
        borderColor = borderColor,
        modifier = Modifier.testTag("live_voicemail_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Caller + Routing Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callerName,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (callerNumber.isNotBlank()) {
                        Text(
                            text = callerNumber,
                            color = colors.primaryGlow,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = colors.secondary.copy(alpha = 0.22f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Silent Internal Route",
                        color = colors.secondaryGlow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phase-specific Display
            when (phase) {
                VoicemailPhase.SPEAKING_GREETING -> {
                    Surface(
                        color = colors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = pulseAlpha * 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Lyra Speaking",
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lyra Speaking Custom Greeting...",
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"$greetingText\"",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelScreening,
                            border = BorderStroke(1.dp, CrimsonDecline),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).testTag("cancel_voicemail_btn")
                        ) {
                            Text("End Call", color = CrimsonDecline, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                VoicemailPhase.PLAYING_BEEP -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Beep",
                            tint = AmberPriority,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Playing Beep Tone...",
                            color = AmberPriority,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Preparing local WAV recorder",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                VoicemailPhase.RECORDING_MESSAGE -> {
                    Surface(
                        color = colors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CrimsonDecline.copy(alpha = pulseAlpha))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CrimsonDecline.copy(alpha = pulseAlpha))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recording Message Locally • ${formatMmSs(elapsedSeconds)} / ${formatMmSs(maxSeconds.toLong())}",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Real-time RMS amplitude meter bars
                            Row(
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val weights = listOf(0.45f, 0.7f, 0.95f, 1.0f, 0.85f, 0.65f, 0.9f, 0.75f, 0.5f)
                                weights.forEach { w ->
                                    val barHeight = (6f + 26f * (amplitude * w).coerceIn(0.05f, 1f)).dp
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(colors.primary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Saved locally on device, then processed by $providerLabel",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelScreening,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).testTag("cancel_voicemail_btn")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = null, tint = CrimsonDecline, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Discard", color = CrimsonDecline, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStopAndSummarize,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = CircleShape,
                            modifier = Modifier.weight(1.4f).testTag("stop_voicemail_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = colors.onPrimaryButton, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop & Summarize", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                VoicemailPhase.PROCESSING_AI -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = colors.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Extracting Key Points & Action Prompts...",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Using $providerLabel",
                            color = colors.primaryGlow,
                            fontSize = 12.sp
                        )
                    }
                }

                VoicemailPhase.READY -> {
                    activeEntry?.let { entry ->
                        VoicemailAnalysisDetails(
                            entry = entry,
                            isPlaying = isPlaying,
                            onTogglePlay = onTogglePlay,
                            onReanalyze = onReanalyze,
                            onCallBack = onCallBack,
                            onDone = onDone
                        )
                    }
                }

                VoicemailPhase.ERROR -> {
                    Surface(
                        color = colors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AmberPriority.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = AmberPriority,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Voicemail Attention Needed",
                                    color = AmberPriority,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorText ?: "Unable to complete AI processing.",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeEntry != null) {
                            OutlinedButton(
                                onClick = onTogglePlay,
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPlaying) "Pause" else "Play Local", color = colors.primary, fontSize = 11.sp)
                            }
                            Button(
                                onClick = onReanalyze,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f).testTag("retry_voicemail_ai_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.onPrimaryButton, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry AI", color = colors.onPrimaryButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onGrantMicAndRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f).testTag("grant_mic_retry_btn")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = colors.onPrimaryButton, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Grant Mic & Retry", color = colors.onPrimaryButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenSettings,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            shape = CircleShape
                        ) {
                            Text("AI Settings", color = colors.textPrimary, fontSize = 11.sp)
                        }
                    }
                }

                VoicemailPhase.IDLE -> Unit
            }
        }
    }
}

@Composable
private fun VoicemailAnalysisDetails(
    entry: VoicemailEntry,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onReanalyze: () -> Unit,
    onCallBack: () -> Unit,
    onDone: () -> Unit
) {
    val colors = LyraTheme.colors
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Concise 1-2 Line Actionable Summary Highlight
        Surface(
            color = colors.surfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIONABLE SUMMARY (1–2 LINES)",
                        color = colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.summary.ifBlank { "Voicemail recorded locally." },
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                )
                if (entry.actionPrompt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AmberPriority.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberPriority.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Action Prompt: ${entry.actionPrompt}",
                            color = AmberPriority,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Extracted Key Points
        val points = entry.keyPoints.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (points.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "KEY POINTS",
                    color = colors.secondaryGlow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                points.forEach { pt ->
                    Text(
                        text = "• $pt",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Full Transcript
        if (entry.transcript.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "TRANSCRIPT (${entry.durationSeconds}s • ${entry.modelUsed})",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.transcript,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        // Audio Playback & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTogglePlay,
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)),
                shape = CircleShape,
                modifier = Modifier.weight(1f).testTag("play_voicemail_btn")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause Audio" else "Play Audio",
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPlaying) "Pause" else "Play Audio", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onReanalyze,
                border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.6f)),
                shape = CircleShape,
                modifier = Modifier.weight(1f).testTag("reanalyze_voicemail_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.secondaryGlow, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Re-analyze", color = colors.secondaryGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCallBack,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccept),
                shape = CircleShape,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Call Back", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                border = BorderStroke(1.dp, colors.glassBorder),
                shape = CircleShape,
                modifier = Modifier.weight(1f).testTag("voicemail_done_btn")
            ) {
                Text("Done", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SavedVoicemailCard(
    entry: VoicemailEntry,
    canDelete: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlayToggle: () -> Unit,
    onReanalyze: () -> Unit,
    onCallBack: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LyraTheme.colors
    GlassCard(
        borderColor = if (isSelected) colors.primary.copy(alpha = 0.7f) else colors.glassBorder,
        modifier = Modifier
            .clickable(onClick = onSelect)
            .testTag("saved_voicemail_${entry.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.callerName,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${entry.callerNumber} • ${entry.durationSeconds}s • ${formatTimestamp(entry.timestamp)}",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayToggle,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play Voicemail",
                            tint = colors.primary
                        )
                    }
                    IconButton(
                        onClick = onCallBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Back",
                            tint = EmeraldAccept
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_voicemail_btn_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Voicemail",
                                tint = colors.textMuted
                            )
                        }
                    }
                }
            }

            if (entry.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.summary,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else if (entry.errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI pending: ${entry.errorMessage}",
                        color = AmberPriority,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Retry",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onReanalyze)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatMmSs(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(ts))
}
