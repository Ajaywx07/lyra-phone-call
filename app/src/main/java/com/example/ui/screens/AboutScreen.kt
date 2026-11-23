package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    var guideExpanded by rememberSaveable { mutableStateOf(false) }

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
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.about_subtitle),
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
                    text = stringResource(R.string.about_brand_title),
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.about_tagline),
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Expandable Framework, Guide & App Details Box
            item {
                GlassCard(
                    borderColor = colors.primary.copy(alpha = 0.45f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { guideExpanded = !guideExpanded }
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("about_guide_expandable_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = stringResource(R.string.about_guide_card_title),
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(R.string.about_guide_card_title),
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.about_guide_card_subtitle),
                                        color = colors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                    Text(
                                        text = if (guideExpanded) {
                                            stringResource(R.string.about_guide_tap_collapse)
                                        } else {
                                            stringResource(R.string.about_guide_tap_expand)
                                        },
                                        color = colors.primaryGlow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (guideExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (guideExpanded) {
                                    stringResource(R.string.about_guide_tap_collapse)
                                } else {
                                    stringResource(R.string.about_guide_tap_expand)
                                },
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = guideExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .testTag("about_guide_expanded_content"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = colors.glassBorder)
                                AboutGuideSection(
                                    title = stringResource(R.string.about_guide_section_how_to_use),
                                    body = stringResource(R.string.about_guide_body_how_to_use)
                                )
                                AboutGuideSection(
                                    title = stringResource(R.string.about_guide_section_settings),
                                    body = stringResource(R.string.about_guide_body_settings)
                                )
                                AboutGuideSection(
                                    title = stringResource(R.string.about_guide_section_recommended),
                                    body = stringResource(R.string.about_guide_body_recommended)
                                )
                                AboutGuideSection(
                                    title = stringResource(R.string.about_guide_section_dos_donts),
                                    body = stringResource(R.string.about_guide_body_dos_donts)
                                )
                                AboutGuideSection(
                                    title = stringResource(R.string.about_guide_section_framework),
                                    body = stringResource(R.string.about_guide_body_framework)
                                )
                                AboutGuideSection(
                                    title = stringResource(R.string.about_guide_section_app_details),
                                    body = stringResource(
                                        R.string.about_guide_body_app_details,
                                        BuildConfig.VERSION_NAME,
                                        BuildConfig.VERSION_CODE
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // GitHub Releases In-App Update Checker
            if (viewModel != null) {
                item {
                    SectionHeader(title = stringResource(R.string.about_updates_header))

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
                                        text = stringResource(R.string.about_updates_channel),
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.about_updates_installed,
                                            BuildConfig.VERSION_NAME,
                                            BuildConfig.VERSION_CODE
                                        ),
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = repoDraft,
                                onValueChange = { repoDraft = it },
                                label = { Text(stringResource(R.string.about_updates_repo_label)) },
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
                                    Text(
                                        text = stringResource(R.string.about_updates_save_repo),
                                        color = colors.textPrimary,
                                        fontSize = 12.sp
                                    )
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
                                        text = if (isCheckingUpdates) {
                                            stringResource(R.string.about_updates_checking)
                                        } else {
                                            stringResource(R.string.about_updates_check)
                                        },
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
                SectionHeader(title = stringResource(R.string.about_ownership_header))

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
                SectionHeader(title = stringResource(R.string.about_privacy_header))

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
                                text = stringResource(R.string.about_privacy_zero_cloud),
                                color = EmeraldAccept,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.about_privacy_points),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Open Source Technologies
            item {
                SectionHeader(title = stringResource(R.string.about_frameworks_header))

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
                                text = stringResource(R.string.about_native_stack),
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
private fun AboutGuideSection(title: String, body: String) {
    val colors = LyraTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            color = colors.primaryGlow,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            color = colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
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
