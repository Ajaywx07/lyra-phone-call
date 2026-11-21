package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CallerInfo
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SimChooserDialog
import com.example.ui.components.shouldPromptForSim
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel

@Composable
fun CallSummaryScreen(
    viewModel: LyraMainViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    val caller by viewModel.currentCaller.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
    val callNotes by viewModel.currentCallNotes.collectAsStateWithLifecycle()
    val lastSummary by viewModel.lastSummary.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()
    val preferredSim by viewModel.preferredSim.collectAsStateWithLifecycle()
    val fixDefaultSim by viewModel.fixDefaultSim.collectAsStateWithLifecycle()
    var pendingSimCallNumber by remember { mutableStateOf<String?>(null) }

    val defaultCaller = CallerInfo(
        phoneNumber = "+91 98765 43210",
        formattedNumber = "+91 98765 43210",
        displayName = "Ajay",
        isKnown = true,
        isPriority = true,
        isOwner = true
    )
    val activeCaller = caller ?: defaultCaller

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    var isCopied by remember { mutableStateOf(false) }
    var isSavedNote by remember { mutableStateOf(false) }

    val aiSummaryText = lastSummary?.summaryText ?: buildString {
        append("• Call completed with ${activeCaller.displayName} (Duration: $durationFormatted)\n")
        if (callNotes.isNotBlank()) {
            append("• Discussion Notes Captured:\n")
            callNotes.lines().forEach {
                if (it.isNotBlank()) append("  - ${it.trim()}\n")
            }
            append("• AI Detected Action Item: Review captured notes and execute scheduled follow-ups.")
        } else {
            append("• Status: Regular conversation finished cleanly.")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("call_summary_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            LyraTopBar(
                title = "CALL SUMMARY",
                subtitle = "Locally Generated AI Insights",
                statusText = "Saved Locally",
                showBack = true,
                onBackClick = {
                    viewModel.resetCallState()
                    onDone()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Call Overview Glass Card
        item {
            GlassCard(borderColor = colors.glassBorderPurple) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = activeCaller.displayName,
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeCaller.formattedNumber,
                                color = colors.primaryGlow,
                                fontSize = 13.sp
                            )
                        }

                        Surface(
                            color = colors.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Duration: $durationFormatted",
                                color = colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Captured In-Call Notes
        item {
            SectionHeader(title = "In-Call Notes")

            GlassCard {
                if (callNotes.isNotBlank()) {
                    Text(
                        text = callNotes,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        text = "No manual notes were recorded during this call session.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // AI Generated Smart Summary Card
        item {
            SectionHeader(title = "Lyra AI Smart Analysis")

            GlassCard(borderColor = colors.primary.copy(alpha = 0.35f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Summarize,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Executive Summary",
                            color = colors.primaryGlow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = aiSummaryText,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Action Buttons Row: [Save to Notes] [Call Back] [Copy Summary]
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Lyra Call Summary", aiSummaryText)
                        clipboard.setPrimaryClip(clip)
                        isCopied = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.weight(1f).testTag("copy_summary_btn")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (isCopied) EmeraldAccept else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        color = if (isCopied) EmeraldAccept else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (callNotes.isNotBlank()) {
                            viewModel.saveCallNote(
                                callerNumber = activeCaller.phoneNumber,
                                callerName = activeCaller.displayName,
                                text = callNotes
                            )
                            isSavedNote = true
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.weight(1f).testTag("save_notes_btn")
                ) {
                    Icon(
                        imageVector = if (isSavedNote) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = null,
                        tint = if (isSavedNote) EmeraldAccept else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSavedNote) "Saved!" else "Save Notes",
                        color = if (isSavedNote) EmeraldAccept else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        if (shouldPromptForSim(preferredSim, fixDefaultSim, availableSims)) {
                            pendingSimCallNumber = activeCaller.phoneNumber
                        } else {
                            viewModel.placeCall(
                                phoneNumber = activeCaller.phoneNumber,
                                simKeyOverride = preferredSim
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccept),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("callback_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Call Back",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Done / Home Button
            Button(
                onClick = {
                    viewModel.resetCallState()
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("done_summary_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    tint = colors.onPrimaryButton,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Done / Return to Home",
                    color = colors.onPrimaryButton,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    pendingSimCallNumber?.let { targetNumber ->
        SimChooserDialog(
            phoneNumber = targetNumber,
            availableSims = availableSims,
            onSelectSim = { simKey, fixForFuture ->
                if (fixForFuture) {
                    viewModel.setPreferredSim(simKey)
                    viewModel.setFixDefaultSim(true)
                }
                pendingSimCallNumber = null
                viewModel.placeCall(
                    phoneNumber = targetNumber,
                    simKeyOverride = simKey
                )
            },
            onDismiss = { pendingSimCallNumber = null }
        )
    }
}
