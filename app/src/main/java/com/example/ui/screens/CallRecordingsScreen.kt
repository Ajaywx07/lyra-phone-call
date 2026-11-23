package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.CallRecording
import com.example.domain.model.CallRecordingCapability
import com.example.domain.model.RecordingPlaybackState
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraTopBar
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallRecordingsScreen(
    viewModel: LyraMainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val recordings by viewModel.allCallRecordings.collectAsStateWithLifecycle()
    val capability by viewModel.callRecordingCapability.collectAsStateWithLifecycle()
    val playbackState by viewModel.recordingPlaybackState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.recordingActionMessage.collectAsStateWithLifecycle()

    var recordingToDelete by remember { mutableStateOf<CallRecording?>(null) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearRecordingActionMessage()
        }
    }

    val colors = LyraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LyraAmbientBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .testTag("call_recordings_screen")
        ) {
            LyraTopBar(
                title = stringResource(R.string.title_call_recordings),
                subtitle = if (capability.isSupported) "Saved Local Audio" else "Android 15+ Policy Restricted",
                showBack = true,
                onBackClick = onBackClick,
                statusText = if (recordings.isNotEmpty()) "${recordings.size} Saved" else ""
            )

            // Android restriction / capability notice card
            CapabilityNoticeCard(capability = capability)

            Spacer(modifier = Modifier.height(8.dp))

            if (recordings.isEmpty()) {
                EmptyRecordingsPlaceholder()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("call_recordings_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = recordings,
                        key = { it.id }
                    ) { recording ->
                        CallRecordingItemCard(
                            recording = recording,
                            playbackState = playbackState,
                            onPlay = { viewModel.playRecording(recording) },
                            onPause = { viewModel.pauseRecording() },
                            onResume = { viewModel.resumeRecording() },
                            onSeek = { viewModel.seekRecordingPlayback(it) },
                            onShare = { viewModel.shareRecording(context, recording) },
                            onDelete = { recordingToDelete = recording }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    recordingToDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.recording_delete_confirm),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.recording_delete_message) + "\n\n${recording.callerName} (${recording.fileName})"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecording(recording)
                        recordingToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.declineColor)
                ) {
                    Text(stringResource(R.string.recording_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CapabilityNoticeCard(capability: CallRecordingCapability) {
    val colors = LyraTheme.colors
    val noticeBorder = if (capability.isSupported) colors.glassBorder else colors.declineColor.copy(alpha = 0.55f)
    val noticeTint = if (capability.isSupported) colors.primary else colors.declineColor
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("recordings_capability_notice"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, noticeBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = noticeTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = capability.notice.ifBlank { stringResource(R.string.recording_disclaimer_notice) },
                fontSize = 11.sp,
                color = colors.textSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun EmptyRecordingsPlaceholder() {
    val colors = LyraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = stringResource(R.string.no_recordings_found),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.recording_empty_subtitle),
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun CallRecordingItemCard(
    recording: CallRecording,
    playbackState: RecordingPlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LyraTheme.colors
    val isCurrent = playbackState.recordingId == recording.id
    val isPlaying = isCurrent && playbackState.isPlaying

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val formattedDate = remember(recording.timestamp) { dateFormat.format(Date(recording.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_recording_item_${recording.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.glassBorder, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause round action button
                IconButton(
                    onClick = {
                        if (isCurrent) {
                            if (isPlaying) onPause() else onResume()
                        } else {
                            onPlay()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) colors.primary else colors.primary.copy(alpha = 0.18f))
                        .testTag("recording_play_btn_${recording.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.recording_pause) else stringResource(R.string.recording_play),
                        tint = if (isPlaying) Color.Black else colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.callerName.ifEmpty { "Unknown" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${recording.callerNumber} • $formattedDate",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Share Button
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("recording_share_btn_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.recording_share),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("recording_delete_btn_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.recording_delete),
                        tint = colors.declineColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Progress Bar or Chips
            if (isCurrent) {
                val totalMs = if (playbackState.durationMs > 0) playbackState.durationMs else recording.durationSeconds * 1000L
                val currentMs = playbackState.currentPositionMs.coerceIn(0L, totalMs.coerceAtLeast(1L))
                val fraction = if (totalMs > 0) (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

                Slider(
                    value = fraction,
                    onValueChange = { frac ->
                        onSeek((frac * totalMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatSeconds(currentMs / 1000L),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = formatSeconds(totalMs / 1000L),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipBadge(text = formatSeconds(recording.durationSeconds))
                        ChipBadge(text = formatFileSize(recording.fileSizeBytes))
                    }
                    Text(
                        text = recording.fileName,
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipBadge(text: String) {
    val colors = LyraTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface.copy(alpha = 0.8f))
            .border(0.5.dp, colors.glassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
    }
}

private fun formatSeconds(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
    }
}
