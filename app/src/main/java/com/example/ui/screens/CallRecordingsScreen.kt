package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CallRecording
import com.example.ui.components.CapsuleButton
import com.example.ui.components.CapsuleSearchBar
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.CallRecordingStorage
import com.example.utils.CallRecordingSupportMode
import com.example.utils.CallRecordingSupportStatus
import com.example.utils.PermissionHelper
import com.example.utils.RecordingPlaybackState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallRecordingsScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val recordings by viewModel.allCallRecordings.collectAsStateWithLifecycle()
    val capability by viewModel.callRecordingCapability.collectAsStateWithLifecycle()
    val playbackState by viewModel.recordingPlaybackState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.recordingActionMessage.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDeleteRecording by remember { mutableStateOf<CallRecording?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshRecordingCapability()
        viewModel.loadData()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshRecordingCapability()
    }

    val combinedNotice = actionMessage ?: playbackState.errorMessage
    LaunchedEffect(combinedNotice) {
        if (!combinedNotice.isNullOrBlank()) {
            snackbarHostState.showSnackbar(combinedNotice)
            viewModel.clearRecordingActionMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopRecordingPlayback()
        }
    }

    val filteredRecordings by remember(recordings, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isBlank()) {
                recordings
            } else {
                recordings.filter { item ->
                    item.callerName.contains(q, ignoreCase = true) ||
                        item.callerNumber.contains(q, ignoreCase = true) ||
                        item.fileName.contains(q, ignoreCase = true)
                }
            }
        }
    }

    val storageFolderLabel = remember { CallRecordingStorage.resolvePreferredRelativePath() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("call_recordings_screen")
    ) {
        LyraAmbientBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LyraTopBar(
                        title = "CALL RECORDINGS",
                        subtitle = "MediaStore • $storageFolderLabel",
                        showBack = true,
                        onBackClick = {
                            viewModel.stopRecordingPlayback()
                            onBackClick()
                        },
                        statusText = "${recordings.size} Saved"
                    )
                }

                item {
                    RecordingCapabilityBannerCard(
                        capability = capability,
                        storageFolderLabel = storageFolderLabel,
                        onGrantPermissionClick = {
                            permissionLauncher.launch(PermissionHelper.getCallRecordingRuntimePermissions())
                        }
                    )
                }

                item {
                    CapsuleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search by caller name, number, or file...",
                        testTag = "recordings_search_input"
                    )
                }

                item {
                    SectionHeader(title = "Saved Recordings (${filteredRecordings.size})")
                }

                if (filteredRecordings.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.FiberManualRecord,
                            title = if (searchQuery.isNotBlank()) "No Matching Recordings" else "No Call Recordings Yet",
                            description = if (searchQuery.isNotBlank()) {
                                "No call recordings match \"$searchQuery\"."
                            } else {
                                "Tap Record on the active call screen or in More Call Options during any call. Lyra stops and saves automatically to $storageFolderLabel when the call ends."
                            }
                        )
                    }
                } else {
                    items(
                        items = filteredRecordings,
                        key = { it.id }
                    ) { recording ->
                        CallRecordingItemCard(
                            recording = recording,
                            playbackState = playbackState,
                            onPlayPauseClick = { viewModel.toggleRecordingPlayback(recording) },
                            onStopClick = { viewModel.stopRecordingPlayback() },
                            onSeek = { viewModel.seekRecordingPlayback(it) },
                            onShareClick = { viewModel.shareCallRecording(recording) },
                            onDeleteClick = { pendingDeleteRecording = recording }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }

    pendingDeleteRecording?.let { target ->
        LyraDeleteConfirmationDialog(
            title = "Delete Call Recording?",
            message = "Delete ${target.fileName} (${target.callerName} • ${target.callerNumber}) from shared storage and local database?",
            onConfirm = {
                viewModel.deleteCallRecording(target)
                pendingDeleteRecording = null
            },
            onDismiss = { pendingDeleteRecording = null }
        )
    }
}

@Composable
private fun RecordingCapabilityBannerCard(
    capability: CallRecordingSupportStatus,
    storageFolderLabel: String,
    onGrantPermissionClick: () -> Unit
) {
    val colors = LyraTheme.colors
    val accent = when (capability.mode) {
        CallRecordingSupportMode.FULL_TWO_WAY -> EmeraldAccept
        CallRecordingSupportMode.MIC_ONLY_RESTRICTED -> AmberPriority
        CallRecordingSupportMode.PERMISSION_REQUIRED -> CrimsonDecline
    }
    val statusIcon = when (capability.mode) {
        CallRecordingSupportMode.FULL_TWO_WAY -> Icons.Default.CheckCircle
        CallRecordingSupportMode.MIC_ONLY_RESTRICTED -> Icons.Default.Info
        CallRecordingSupportMode.PERMISSION_REQUIRED -> Icons.Default.Warning
    }

    GlassCard(
        borderColor = accent.copy(alpha = 0.48f),
        modifier = Modifier.testTag("recording_capability_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = capability.headline,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capability.headline,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${capability.deviceLabel} • API ${capability.apiLevel}",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = capability.explanation,
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = colors.primaryGlow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Saved via MediaStore in $storageFolderLabel",
                    color = colors.primaryGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (capability.speakerTip.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = capability.speakerTip,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            if (capability.mode == CallRecordingSupportMode.PERMISSION_REQUIRED) {
                Spacer(modifier = Modifier.height(10.dp))
                CapsuleButton(
                    text = "Grant Recording Permission",
                    onClick = onGrantPermissionClick,
                    modifier = Modifier.testTag("grant_recording_permissions_btn")
                )
            }
        }
    }
}

@Composable
private fun CallRecordingItemCard(
    recording: CallRecording,
    playbackState: RecordingPlaybackState,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = LyraTheme.colors
    val isActiveItem = playbackState.activeRecordingId == recording.id
    val isPlayingItem = isActiveItem && playbackState.isPlaying
    val dateFormatted = remember(recording.timestamp) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(recording.timestamp))
    }
    val durationFormatted = remember(recording.durationSeconds) {
        CallRecordingStorage.formatDuration(recording.durationSeconds)
    }
    val fileSizeFormatted = remember(recording.fileSizeBytes) {
        CallRecordingStorage.formatFileSize(recording.fileSizeBytes)
    }

    GlassCard(
        borderColor = if (isActiveItem) colors.primary.copy(alpha = 0.6f) else colors.glassBorder,
        modifier = Modifier.testTag("recording_card_${recording.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row: Caller Name, Phone Number, Timestamp, & Audio Capture Mode badge
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActiveItem) colors.primary.copy(alpha = 0.22f)
                                else colors.surfaceElevated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Call Recording",
                            tint = if (isActiveItem) colors.primary else colors.primaryGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recording.callerName.ifBlank { "Unknown" },
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = recording.callerNumber.ifBlank { "Unknown" },
                            color = colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateFormatted,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = (if (recording.isTwoWayAudio) EmeraldAccept else AmberPriority)
                            .copy(alpha = 0.16f),
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            (if (recording.isTwoWayAudio) EmeraldAccept else AmberPriority)
                                .copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = if (recording.isTwoWayAudio) "2-Way Audio" else "Mic Only",
                            color = if (recording.isTwoWayAudio) EmeraldAccept else AmberPriority,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$durationFormatted • $fileSizeFormatted",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Exact Filename & MediaStore Path row
            Text(
                text = recording.fileName,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("recording_filename_${recording.id}")
            )
            Text(
                text = "Folder: ${recording.relativePath}",
                color = colors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Active Playback Scrubber when selected
            if (isActiveItem) {
                Spacer(modifier = Modifier.height(8.dp))
                val totalMs = playbackState.totalDurationMs.coerceAtLeast(1)
                val posMs = playbackState.currentPositionMs.coerceIn(0, totalMs)
                val posLabel = CallRecordingStorage.formatDuration((posMs / 1000L))
                val totalLabel = CallRecordingStorage.formatDuration((totalMs / 1000L))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = posLabel,
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = posMs.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..totalMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.glassBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .testTag("recording_seek_slider_${recording.id}")
                    )
                    Text(
                        text = totalLabel,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Controls: Play/Pause, Stop, Share, Delete (48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        color = if (isPlayingItem) colors.primary else colors.primary.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("recording_play_btn_${recording.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlayingItem) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingItem) "Pause recording" else "Play recording",
                                tint = if (isPlayingItem) colors.onPrimaryButton else colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingItem) "Pause" else if (isActiveItem) "Resume" else "Play",
                                color = if (isPlayingItem) colors.onPrimaryButton else colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isActiveItem) {
                        Surface(
                            onClick = onStopClick,
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("recording_stop_btn_${recording.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop playback",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("recording_share_btn_${recording.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share recording ${recording.fileName}",
                            tint = colors.primaryGlow
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("recording_delete_btn_${recording.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete recording ${recording.fileName}",
                            tint = CrimsonDecline
                        )
                    }
                }
            }
        }
    }
}
