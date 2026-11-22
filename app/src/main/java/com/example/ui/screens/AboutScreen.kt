package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.local.preferences.LyraPreferences
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.LyraMainViewModel

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    viewModel: LyraMainViewModel? = null
) {
    val colors = LyraTheme.colors
    val persistedRepo by viewModel?.githubUpdateRepo?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(LyraPreferences.DEFAULT_GITHUB_UPDATE_REPO) }
    val isCheckingUpdates by viewModel?.isCheckingUpdates?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val updateStatus by viewModel?.updateCheckStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }
    var repoDraft by remember(persistedRepo) { mutableStateOf(persistedRepo) }

    LaunchedEffect(persistedRepo) {
        if (repoDraft.isBlank()) {
            repoDraft = persistedRepo
        }
    }

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
                .padding(16.dp)
                .testTag("about_screen"),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LyraTopBar(
                    title = "ABOUT LYRA",
                    subtitle = "Intelligence & Specifications",
                    showBack = true,
                    onBackClick = onBackClick
                )
            }

            // Official Brand Logo Header
            item {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.secondary.copy(alpha = 0.28f),
                                    colors.primary.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                        .testTag("about_lyra_logo"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lyra_logo),
                        contentDescription = "Lyra Official Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "LYRA CALLING ASSISTANT",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "\"Your calls. Your privacy. Your assistant.\"",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // GitHub Releases In-App Update Checker
            if (viewModel != null) {
                item {
                    SectionHeader(title = "App Updates (GitHub Releases)")

                    GlassCard(borderColor = colors.primary.copy(alpha = 0.4f)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "GitHub Release Channel",
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = repoDraft,
                                onValueChange = { repoDraft = it },
                                label = { Text("GitHub Repo (owner/repo)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("github_update_repo_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.setGitHubUpdateRepo(repoDraft) },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_github_repo_btn")
                                ) {
                                    Text("Save Repo", color = colors.textPrimary, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        viewModel.setGitHubUpdateRepo(repoDraft)
                                        viewModel.checkForAppUpdates(manual = true)
                                    },
                                    enabled = !isCheckingUpdates,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("check_updates_btn")
                                ) {
                                    if (isCheckingUpdates) {
                                        CircularProgressIndicator(
                                            color = colors.onPrimaryButton,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = colors.onPrimaryButton,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCheckingUpdates) "Checking..." else "Check Updates",
                                        color = colors.onPrimaryButton,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            updateStatus?.let { msg ->
                                Text(
                                    text = msg,
                                    color = if (msg.contains("up to date", ignoreCase = true) ||
                                        msg.contains("available", ignoreCase = true) ||
                                        msg.contains("saved", ignoreCase = true)
                                    ) {
                                        EmeraldAccept
                                    } else {
                                        colors.primaryGlow
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Developer & Ownership Card
            item {
                SectionHeader(title = "Ownership & Architecture")

                GlassCard(borderColor = colors.glassBorderPurple) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutRow(label = "Application", value = "Lyra Calling Assistant")
                        AboutRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (Production Release)")
                        AboutRow(label = "Developer / Owner", value = "Ajay")
                        AboutRow(label = "Package Name", value = "com.ajay.lyra")
                        AboutRow(label = "Architecture", value = "MVVM + Clean Repository")
                        AboutRow(label = "Local Storage", value = "Room Database + DataStore")
                        AboutRow(label = "Telecom Engine", value = "Android InCallService + Telephony")
                    }
                }
            }

            // Privacy Pledge Card
            item {
                SectionHeader(title = "Strict Privacy Pledge")

                GlassCard(borderColor = EmeraldAccept.copy(alpha = 0.35f)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EmeraldAccept,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Zero Cloud Leakage Guarantee",
                                color = EmeraldAccept,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "1. Lyra operates strictly offline and locally by default.\n" +
                                   "2. Never uploads private contacts to external servers.\n" +
                                   "3. Never records or streams call audio without explicit permission.\n" +
                                   "4. All notes, summaries, and priority tags stay on this device.\n" +
                                   "5. Complies with Google Play policies and least-privilege standards.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Open Source Technologies
            item {
                SectionHeader(title = "Frameworks & Tooling")

                GlassCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Native Android Stack",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "• Kotlin 2.0 & Jetpack Compose (Material 3)\n" +
                                   "• Android Telecom InCallService & BroadcastReceiver\n" +
                                   "• Room Database & Kotlin Coroutines Flow\n" +
                                   "• AndroidX Preferences DataStore\n" +
                                   "• Coil Image Loading Library\n" +
                                   "• Robolectric JVM Testing Suite",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = LyraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 12.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
