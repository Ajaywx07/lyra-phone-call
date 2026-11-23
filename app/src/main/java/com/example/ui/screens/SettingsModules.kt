package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AutoReplyMode
import com.example.domain.model.AiProvider
import com.example.domain.model.CallAnimationStyle
import com.example.domain.model.CallScreenStyle
import com.example.domain.model.CallStateEnum
import com.example.ui.components.CapsuleGroupDivider
import com.example.ui.components.CapsuleSettingsItem
import com.example.ui.components.CapsuleStatusChip
import com.example.ui.components.CapsuleToggle
import com.example.ui.components.DashboardHeroGraphic
import com.example.ui.components.liquidGlassPlate
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.LocalInsideGlassSurface
import com.example.ui.theme.LyraTheme
import com.example.ui.theme.themeColorsFor
import com.example.ui.viewmodel.PermissionsState
import com.example.utils.MediaCropTransform
import com.example.utils.PermissionHelper
import com.example.utils.SimManager
import com.example.utils.SimSlotInfo

@Composable
fun ExpandableSettingsModule(
    moduleKey: String,
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LyraTheme.colors.primary,
    statusBadge: String? = null,
    statusBadgeColor: Color = accentColor,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LyraTheme.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "module_chevron_$moduleKey"
    )
    val borderColor = if (expanded) {
        accentColor.copy(alpha = 0.48f)
    } else {
        colors.glassBorder
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_module_$moduleKey"),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface,
        shadowElevation = if (colors.isLight) 2.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("settings_module_header_$moduleKey"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.14f))
                            .border(1.dp, accentColor.copy(alpha = 0.30f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            color = colors.primaryGlow,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!statusBadge.isNullOrBlank()) {
                        CapsuleStatusChip(
                            text = statusBadge,
                            color = statusBadgeColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                            tint = if (expanded) accentColor else colors.textMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(170, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
            ) {
                CompositionLocalProvider(LocalInsideGlassSurface provides true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = colors.glassBorder.copy(alpha = 0.55f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeAndDashboardModuleBody(
    appTheme: AppThemeStyle,
    onSelectAppTheme: (AppThemeStyle) -> Unit,
    dashboardMediaUri: String?,
    dashboardMediaType: String,
    dashboardMediaCrop: MediaCropTransform,
    callState: CallStateEnum,
    onPickDashboardMedia: () -> Unit,
    onResetDashboardMedia: () -> Unit
) {
    val colors = LyraTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("app_theme_selector_row")
        ) {
            items(AppThemeStyle.entries) { style ->
                val preview = themeColorsFor(style)
                val isSelected = appTheme == style
                val cardColor = if (preview.isGlassStyle) {
                    Color(0x66162642)
                } else {
                    preview.surface
                }
                Surface(
                    color = if (preview.isGlassStyle) Color.Transparent else cardColor,
                    shape = RoundedCornerShape(20.dp),
                    border = if (preview.isGlassStyle && !isSelected) {
                        null
                    } else {
                        BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) colors.primary else preview.glassBorder
                        )
                    },
                    modifier = Modifier
                        .width(138.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (preview.isGlassStyle) {
                                Modifier.liquidGlassPlate(
                                    cornerRadius = 20.dp,
                                    tintColor = cardColor,
                                    accentGlow = preview.primary,
                                    rimWidth = if (isSelected) 1.5.dp else 1.1.dp,
                                    subtle = !isSelected,
                                    enableAgslRefraction = false
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelectAppTheme(style) }
                        .testTag("app_theme_card_${style.key}")
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(preview.background)
                                        .border(1.dp, preview.glassBorder, CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(preview.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(preview.secondary)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(preview.acceptColor)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active theme",
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = style.displayName,
                                color = preview.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = style.subtitle,
                                color = preview.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = colors.surfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, colors.glassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardHeroGraphic(
                    mediaUri = dashboardMediaUri,
                    mediaType = dashboardMediaType,
                    mediaCrop = dashboardMediaCrop,
                    callState = callState,
                    customStatusText = "",
                    onClick = onPickDashboardMedia,
                    size = 70.dp,
                    showLabels = false,
                    testTagName = "settings_header_graphic"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dashboard Hero Graphic",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap graphic to customize photo or looping video orb.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    if (dashboardMediaUri != null) {
                        TextButton(
                            onClick = onResetDashboardMedia,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("settings_reset_orb_btn")
                        ) {
                            Text("Reset to Default Orb", color = colors.secondaryGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiEngineModuleBody(
    draftProvider: AiProvider,
    onProviderChange: (AiProvider) -> Unit,
    draftModel: String,
    onModelChange: (String) -> Unit,
    draftApiKey: String,
    onApiKeyChange: (String) -> Unit,
    persistedApiKeyPresent: Boolean,
    draftBaseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    draftGreeting: String,
    onGreetingChange: (String) -> Unit,
    showApiKeyPlaintext: Boolean,
    onToggleShowKey: () -> Unit,
    isTestingAi: Boolean,
    aiConnectionStatus: String?,
    onSaveConfig: () -> Unit,
    onTestConnection: () -> Unit,
    onDeleteApiKey: () -> Unit
) {
    val colors = LyraTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = draftProvider.subtitle,
            color = colors.primaryGlow,
            fontSize = 11.sp
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AiProvider.entries) { provider ->
                val selected = draftProvider == provider
                FilterChip(
                    selected = selected,
                    onClick = { onProviderChange(provider) },
                    label = {
                        Text(
                            text = provider.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                        selectedLabelColor = colors.primary,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = colors.primary,
                        borderColor = colors.glassBorder
                    ),
                    modifier = Modifier.testTag("ai_provider_chip_${provider.key}")
                )
            }
        }

        val presets = when (draftProvider) {
            AiProvider.OPENAI_COMPATIBLE -> listOf("gpt-4o-mini", "gpt-4o-audio-preview", "llama-3.3-70b-versatile")
            else -> listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presets) { preset ->
                val selected = draftModel == preset
                FilterChip(
                    selected = selected,
                    onClick = { onModelChange(preset) },
                    label = { Text(preset, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.secondary.copy(alpha = 0.25f),
                        selectedLabelColor = colors.secondaryGlow,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = colors.secondaryGlow,
                        borderColor = colors.glassBorder
                    )
                )
            }
        }

        OutlinedTextField(
            value = draftModel,
            onValueChange = onModelChange,
            label = { Text("AI Model Name", color = colors.textSecondary, fontSize = 12.sp) },
            placeholder = { Text("gemini-3.8-flash", color = colors.textMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.glassBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_model_input")
        )

        OutlinedTextField(
            value = draftApiKey,
            onValueChange = onApiKeyChange,
            label = {
                Text(
                    text = if (draftProvider == AiProvider.FIREBASE_AI) {
                        "API Key (Optional for Firebase AI)"
                    } else {
                        "API Key (Required for ${draftProvider.displayName})"
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            },
            placeholder = { Text("Paste provider API key here", color = colors.textMuted) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null, tint = colors.primaryGlow, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                Row {
                    if (draftApiKey.isNotEmpty()) {
                        IconButton(onClick = { onApiKeyChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Key", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onToggleShowKey) {
                        Icon(
                            imageVector = if (showApiKeyPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKeyPlaintext) "Hide API Key" else "Show API Key",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            visualTransformation = if (showApiKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.glassBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_api_key_input")
        )

        if (draftProvider == AiProvider.OPENAI_COMPATIBLE) {
            OutlinedTextField(
                value = draftBaseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("API Base URL", color = colors.textSecondary, fontSize = 12.sp) },
                placeholder = { Text("https://api.openai.com/v1", color = colors.textMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.glassBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_base_url_input")
            )
        }

        OutlinedTextField(
            value = draftGreeting,
            onValueChange = onGreetingChange,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Voicemail, contentDescription = null, tint = colors.secondaryGlow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Voicemail Spoken Greeting", color = colors.textSecondary, fontSize = 12.sp)
                }
            },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.secondaryGlow,
                unfocusedBorderColor = colors.glassBorder,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voicemail_greeting_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSaveConfig,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_ai_settings_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.onPrimaryButton, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save AI Config", color = colors.onPrimaryButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isTestingAi,
                border = BorderStroke(1.dp, colors.secondaryGlow.copy(alpha = 0.65f)),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .testTag("test_ai_connection_btn")
            ) {
                if (isTestingAi) {
                    CircularProgressIndicator(color = colors.secondaryGlow, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("Test Model", color = colors.secondaryGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (draftApiKey.isNotEmpty() || persistedApiKeyPresent) {
            OutlinedButton(
                onClick = onDeleteApiKey,
                border = BorderStroke(1.dp, colors.declineColor.copy(alpha = 0.55f)),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_ai_key_btn")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = colors.declineColor, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Saved API Key", color = colors.declineColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        aiConnectionStatus?.let { status ->
            Text(
                text = status,
                color = if (status.startsWith("Test failed")) colors.priorityColor else colors.acceptColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CallScreenStyleModuleBody(
    callScreenStyle: CallScreenStyle,
    onSelectCallScreenStyle: (CallScreenStyle) -> Unit,
    callAnimationStyle: CallAnimationStyle,
    onSelectCallAnimationStyle: (CallAnimationStyle) -> Unit,
    onPreviewIncoming: () -> Unit,
    onPreviewOutgoing: () -> Unit
) {
    val colors = LyraTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Call Screen Theme", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(callScreenStyle.subtitle, color = colors.primaryGlow, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CallScreenStyle.entries) { style ->
                val selected = callScreenStyle == style
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCallScreenStyle(style) },
                    label = {
                        Text(
                            text = style.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                        selectedLabelColor = colors.primary,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = colors.primary,
                        borderColor = colors.glassBorder
                    ),
                    modifier = Modifier.testTag("style_chip_${style.key}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Animation, contentDescription = null, tint = colors.secondaryGlow, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Call Background Animation", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CallAnimationStyle.entries) { anim ->
                val selected = callAnimationStyle == anim
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCallAnimationStyle(anim) },
                    label = {
                        Text(
                            text = anim.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.secondary.copy(alpha = 0.25f),
                        selectedLabelColor = colors.secondaryGlow,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = colors.secondaryGlow,
                        borderColor = colors.glassBorder
                    ),
                    modifier = Modifier.testTag("anim_chip_${anim.key}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPreviewIncoming,
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .testTag("preview_incoming_style_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Preview Incoming", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onPreviewOutgoing,
                border = BorderStroke(1.dp, colors.acceptColor.copy(alpha = 0.6f)),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .testTag("preview_outgoing_style_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.acceptColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Preview Outgoing", color = colors.acceptColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CallingSimAndAlertsModuleBody(
    context: Context,
    availableSims: List<SimSlotInfo>,
    preferredSim: String,
    fixDefaultSim: Boolean,
    onSelectSim: (String) -> Unit,
    onFixDefaultSimChange: (Boolean) -> Unit,
    perms: PermissionsState,
    onLaunchIntent: (Intent) -> Unit,
    onNavigateToPermissions: () -> Unit,
    isFullscreenIncoming: Boolean,
    onFullscreenIncomingChange: (Boolean) -> Unit,
    isIncomingScreenEnabled: Boolean,
    onIncomingScreenEnabledChange: (Boolean) -> Unit,
    isBackgroundCallingEnabled: Boolean,
    onBackgroundCallingEnabledChange: (Boolean) -> Unit,
    isPersistentNotificationEnabled: Boolean,
    onPersistentNotificationEnabledChange: (Boolean) -> Unit,
    showContactPhotoOnCall: Boolean,
    onShowContactPhotoChange: (Boolean) -> Unit,
    isCallVibrationEnabled: Boolean,
    onCallVibrationChange: (Boolean) -> Unit,
    ringtoneUri: String,
    ringtoneTitle: String,
    onLaunchRingtonePicker: (Intent) -> Unit
) {
    val colors = LyraTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SimCard, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Preferred Outgoing SIM", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val selected = preferredSim == SimManager.SIM_ASK
                FilterChip(
                    selected = selected,
                    onClick = {
                        onSelectSim(SimManager.SIM_ASK)
                        onFixDefaultSimChange(false)
                    },
                    label = { Text("Ask Every Time", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                        selectedLabelColor = colors.primary,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textSecondary
                    ),
                    modifier = Modifier.testTag("settings_sim_ask")
                )
            }
            items(availableSims) { sim ->
                val selected = preferredSim == sim.simKey
                FilterChip(
                    selected = selected,
                    onClick = { onSelectSim(sim.simKey) },
                    label = { Text("${sim.label} (${sim.carrierName})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                        selectedLabelColor = colors.primary,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textSecondary
                    ),
                    modifier = Modifier.testTag("settings_sim_${sim.simKey}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text("Fix Default SIM", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (preferredSim == SimManager.SIM_ASK) {
                        "Select SIM 1 or SIM 2 above to lock as fixed default"
                    } else if (fixDefaultSim) {
                        "Locked: Outgoing calls dial via ${preferredSim.replace("_", " ")}"
                    } else {
                        "Optional: Lock chosen SIM without asking"
                    },
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = fixDefaultSim && preferredSim != SimManager.SIM_ASK,
                enabled = preferredSim != SimManager.SIM_ASK,
                onCheckedChange = onFixDefaultSimChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimaryButton,
                    checkedTrackColor = colors.priorityColor
                ),
                modifier = Modifier.testTag("settings_fix_sim_switch")
            )
        }

        CapsuleGroupDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = null,
                    tint = if (perms.isDefaultDialer) colors.acceptColor else colors.priorityColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Default Phone App (Dialer)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (perms.isDefaultDialer) "Active • Incoming & Outgoing UI" else "Required for full call screen handling",
                        color = if (perms.isDefaultDialer) colors.acceptColor else colors.priorityColor,
                        fontSize = 11.sp
                    )
                }
            }
            if (!perms.isDefaultDialer) {
                Button(
                    onClick = {
                        val intent = PermissionHelper.createDefaultDialerIntent(context)
                        if (intent != null) {
                            onLaunchIntent(intent)
                        } else {
                            onLaunchIntent(PermissionHelper.createDefaultAppsSettingsIntent())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("set_default_dialer_btn")
                ) {
                    Text("Set Default", color = colors.onPrimaryButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                CapsuleStatusChip(text = "Active ✓", color = colors.acceptColor)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (perms.isCallScreener) colors.acceptColor else colors.primaryGlow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Caller ID & Screening Role", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (perms.isCallScreener) "Active • System Call Screener" else "Screens incoming calls via Telecom API",
                        color = if (perms.isCallScreener) colors.acceptColor else colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            if (!perms.isCallScreener) {
                OutlinedButton(
                    onClick = {
                        val intent = PermissionHelper.createCallScreeningRoleIntent(context)
                        if (intent != null) {
                            onLaunchIntent(intent)
                        } else {
                            onNavigateToPermissions()
                        }
                    },
                    border = BorderStroke(1.dp, colors.primary),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("set_call_screener_btn")
                ) {
                    Text("Enable", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                CapsuleStatusChip(text = "Active ✓", color = colors.acceptColor)
            }
        }

        OutlinedButton(
            onClick = onNavigateToPermissions,
            border = BorderStroke(1.dp, colors.glassBorder),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("open_max_permissions_btn")
        ) {
            Text(
                text = "Open Full Advanced Permissions Manager",
                color = colors.primaryGlow,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Full-Screen Incoming & Outgoing UI",
            subtitle = "Display Lyra call interface over lock screen and incoming calls",
            icon = Icons.Default.Phone,
            checked = isFullscreenIncoming,
            onCheckedChange = onFullscreenIncomingChange
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Incoming Call Screen",
            subtitle = "Open Lyra incoming screen automatically when ringing on lock screen",
            icon = Icons.Default.PhoneInTalk,
            checked = isIncomingScreenEnabled,
            onCheckedChange = onIncomingScreenEnabledChange,
            testTag = "setting_incoming_call_screen"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Background Call Interception",
            subtitle = "Wake CallActivity reliably when Lyra is closed or over another app",
            icon = Icons.Default.Fullscreen,
            checked = isBackgroundCallingEnabled,
            onCheckedChange = onBackgroundCallingEnabledChange,
            testTag = "setting_background_calling"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Persistent Call Notification",
            subtitle = "Keep ringing & ongoing CallStyle alert pinned against swipe dismissal",
            icon = Icons.Default.NotificationsActive,
            checked = isPersistentNotificationEnabled,
            onCheckedChange = onPersistentNotificationEnabledChange,
            accentColor = colors.acceptColor,
            testTag = "setting_persistent_notification"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Caller Photo in Call UI & Notification",
            subtitle = "Show saved contact photo on call screens and heads-up notifications",
            icon = Icons.Default.PhotoCamera,
            checked = showContactPhotoOnCall,
            onCheckedChange = onShowContactPhotoChange,
            testTag = "setting_show_caller_photo"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Incoming Call Vibration",
            subtitle = "Vibrate alongside ringtone for incoming priority alerts",
            icon = Icons.Default.Vibration,
            checked = isCallVibrationEnabled,
            onCheckedChange = onCallVibrationChange,
            testTag = "setting_call_vibration"
        )
        CapsuleGroupDivider()
        CapsuleSettingsItem(
            title = "Incoming Ringtone",
            subtitle = "Current: $ringtoneTitle • Tap to choose system or custom tone",
            icon = Icons.Default.MusicNote,
            trailingBadge = "Change",
            trailingBadgeColor = colors.secondaryGlow,
            onClick = {
                val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Lyra Incoming Ringtone")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    if (ringtoneUri.isNotBlank()) {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(ringtoneUri))
                    }
                }
                onLaunchRingtonePicker(pickerIntent)
            },
            testTag = "setting_ringtone_picker"
        )
        CapsuleGroupDivider()
        CapsuleSettingsItem(
            title = "Lock-Screen Full-Screen Permission",
            subtitle = if (perms.fullScreenIntentAllowed) {
                "Granted • Incoming UI opens above lock screen"
            } else {
                "Required on Android 14+ to pop incoming call screen when locked"
            },
            icon = Icons.Default.Fullscreen,
            accentColor = if (perms.fullScreenIntentAllowed) colors.acceptColor else colors.priorityColor,
            trailingBadge = if (perms.fullScreenIntentAllowed) "Allowed" else "Allow",
            onClick = { onLaunchIntent(PermissionHelper.createFullScreenIntentSettingsIntent(context)) },
            testTag = "setting_fullscreen_intent_shortcut"
        )
        CapsuleGroupDivider()
        CapsuleSettingsItem(
            title = "Unrestricted Background Battery",
            subtitle = if (perms.batteryOptimizationIgnored) {
                "Unrestricted • System will not kill background call listener"
            } else {
                "Recommended for Xiaomi, Vivo, Oppo & Samsung background calling"
            },
            icon = Icons.Default.BatteryChargingFull,
            accentColor = if (perms.batteryOptimizationIgnored) colors.acceptColor else colors.priorityColor,
            trailingBadge = if (perms.batteryOptimizationIgnored) "Active" else "Allow",
            onClick = { onLaunchIntent(PermissionHelper.createBatteryOptimizationIntent()) },
            testTag = "setting_battery_optimization_shortcut"
        )
    }
}

@Composable
fun AutoReplyModesModuleBody(
    modes: List<AutoReplyMode>,
    masterEnabled: Boolean,
    selectedModeId: String,
    sendSmsAllowed: Boolean,
    onToggleMaster: (Boolean) -> Unit,
    onSelectMode: (String) -> Unit,
    onSaveMessage: (String, String) -> Unit,
    onResetMessage: (String) -> Unit,
    onRequestSmsPermission: () -> Unit
) {
    val colors = LyraTheme.colors
    val selectedMode = remember(modes, selectedModeId) {
        modes.firstOrNull { it.modeId == selectedModeId } ?: modes.firstOrNull()
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var messageDraft by remember(selectedMode?.modeId, selectedMode?.customMessage) {
        mutableStateOf(selectedMode?.customMessage.orEmpty())
    }
    var savedFeedback by remember(selectedMode?.modeId) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = "Auto-Cut & Reply SMS",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (masterEnabled && selectedMode != null) {
                        "Active: ${selectedMode.emoji} ${selectedMode.title} • Incoming calls cut & replied via SMS"
                    } else {
                        "Automatically reject incoming calls and send your active mode SMS"
                    },
                    color = if (masterEnabled) colors.acceptColor else colors.textSecondary,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = masterEnabled,
                onCheckedChange = onToggleMaster,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimaryButton,
                    checkedTrackColor = colors.acceptColor
                ),
                modifier = Modifier.testTag("auto_reply_master_switch")
            )
        }

        if (!sendSmsAllowed) {
            Surface(
                color = colors.priorityColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.priorityColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SMS permission needed for automatic SMS replies.",
                        color = colors.priorityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    TextButton(
                        onClick = onRequestSmsPermission,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("auto_reply_grant_sms_btn")
                    ) {
                        Text("Grant SMS", color = colors.priorityColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        CapsuleGroupDivider()

        if (selectedMode != null) {
            Text(
                text = "Status Mode (${modes.size} presets saved locally)",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { dropdownExpanded = true },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("auto_reply_mode_dropdown")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = selectedMode.emoji, fontSize = 18.sp)
                            Column {
                                Text(
                                    text = selectedMode.title,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to choose from ${modes.size} pre-fed modes",
                                    color = colors.primaryGlow,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Auto-Reply Status Mode",
                            tint = colors.primary
                        )
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .background(colors.surfaceElevated)
                ) {
                    modes.forEach { mode ->
                        val isCurrent = mode.modeId == selectedMode.modeId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = mode.emoji, fontSize = 16.sp)
                                    Text(
                                        text = mode.title,
                                        color = if (isCurrent) colors.primary else colors.textPrimary,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            onClick = {
                                onSelectMode(mode.modeId)
                                dropdownExpanded = false
                                savedFeedback = null
                            },
                            modifier = Modifier.testTag("auto_reply_option_${mode.modeId}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = messageDraft,
                onValueChange = {
                    messageDraft = it
                    savedFeedback = null
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Message, contentDescription = null, tint = colors.primaryGlow, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Custom SMS Reply for ${selectedMode.title}", color = colors.textSecondary, fontSize = 12.sp)
                    }
                },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.glassBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_reply_message_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onSaveMessage(selectedMode.modeId, messageDraft)
                        savedFeedback = "Saved reply for ${selectedMode.title} ✓"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auto_reply_save_btn")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.onPrimaryButton, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Reply", color = colors.onPrimaryButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onResetMessage(selectedMode.modeId)
                        messageDraft = selectedMode.defaultMessage
                        savedFeedback = "Reset ${selectedMode.title} to default"
                    },
                    border = BorderStroke(1.dp, colors.glassBorder),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auto_reply_reset_btn")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Default", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            savedFeedback?.let { note ->
                Text(
                    text = note,
                    color = colors.acceptColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CustomFeaturesModuleBody(
    context: Context,
    showLyraLogoOnIncoming: Boolean,
    onShowLyraLogoChange: (Boolean) -> Unit,
    showMostFrequentContacts: Boolean,
    onShowMostFrequentChange: (Boolean) -> Unit,
    monthlyStatsAutoResetEnabled: Boolean,
    onMonthlyStatsAutoResetChange: (Boolean) -> Unit,
    keypadToneEnabled: Boolean,
    onKeypadToneChange: (Boolean) -> Unit,
    keypadVoiceAnnouncementEnabled: Boolean,
    onKeypadVoiceAnnouncementChange: (Boolean) -> Unit,
    ussdDirectHandlerEnabled: Boolean,
    onUssdDirectHandlerChange: (Boolean) -> Unit,
    accessibilityAutomationEnabled: Boolean,
    onAccessibilityAutomationChange: (Boolean) -> Unit,
    accessibilityServiceConnected: Boolean,
    perms: PermissionsState,
    onLaunchIntent: (Intent) -> Unit,
    onDeactivateDeviceAdmin: () -> Boolean
) {
    val colors = LyraTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        CapsuleToggle(
            title = "Show Lyra Logo on Incoming Call",
            subtitle = "Display the animated Lyra emblem badge on incoming call screens",
            icon = Icons.Default.AutoAwesome,
            checked = showLyraLogoOnIncoming,
            onCheckedChange = onShowLyraLogoChange,
            testTag = "toggle_show_lyra_logo"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Most Frequent Connected Contacts",
            subtitle = "Show top 20 callers ranked by connected calls & talk time below dialpad & in Contacts",
            icon = Icons.Default.Groups,
            checked = showMostFrequentContacts,
            onCheckedChange = onShowMostFrequentChange,
            testTag = "toggle_most_frequent_contacts"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Monthly Counter & Dashboard Auto-Reset",
            subtitle = "Automatically reset Most Frequent & Dashboard counters on 1st of every month at 00:00",
            icon = Icons.Default.CalendarMonth,
            checked = monthlyStatsAutoResetEnabled,
            onCheckedChange = onMonthlyStatsAutoResetChange,
            testTag = "toggle_monthly_auto_reset"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Keypad Spoken Number Voice",
            subtitle = "Speak dialed digits aloud with instant interruption for lag-free fast typing",
            icon = Icons.Default.RecordVoiceOver,
            checked = keypadVoiceAnnouncementEnabled,
            onCheckedChange = onKeypadVoiceAnnouncementChange,
            testTag = "toggle_keypad_voice_announcement"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Keypad Button Press Sounds",
            subtitle = "Play crisp DTMF touch-tone sound on dialpad key press",
            icon = Icons.Default.GraphicEq,
            checked = keypadToneEnabled,
            onCheckedChange = onKeypadToneChange,
            testTag = "toggle_keypad_tone_sound"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Direct Carrier USSD Handler",
            subtitle = "Run *121# / *# codes directly in-app with response dialog",
            icon = Icons.Default.Dialpad,
            checked = ussdDirectHandlerEnabled,
            onCheckedChange = onUssdDirectHandlerChange,
            testTag = "toggle_ussd_handler"
        )
        CapsuleGroupDivider()
        CapsuleToggle(
            title = "Optional Accessibility Automation",
            subtitle = when {
                !accessibilityAutomationEnabled -> "Off by default • Enable only if you want UI automation & auto-response helpers"
                accessibilityServiceConnected -> "Connected • Accessibility automation active"
                else -> "Switch ON • Tap below to allow Lyra in Android Accessibility settings"
            },
            icon = Icons.Default.AccessibilityNew,
            checked = accessibilityAutomationEnabled,
            onCheckedChange = onAccessibilityAutomationChange,
            accentColor = colors.secondaryGlow,
            testTag = "toggle_accessibility_automation"
        )
        if (accessibilityAutomationEnabled) {
            CapsuleSettingsItem(
                title = "Android Accessibility Settings",
                subtitle = if (accessibilityServiceConnected) {
                    "Service connected • Manage or turn off anytime"
                } else {
                    "Grant Accessibility permission to enable screen automation"
                },
                icon = Icons.Default.AccessibilityNew,
                accentColor = if (accessibilityServiceConnected) colors.acceptColor else colors.priorityColor,
                trailingBadge = if (accessibilityServiceConnected) "Connected" else "Open",
                onClick = { onLaunchIntent(PermissionHelper.createAccessibilitySettingsIntent()) },
                testTag = "settings_accessibility_shortcut"
            )
        }
        CapsuleGroupDivider()
        CapsuleSettingsItem(
            title = "Do Not Disturb (DND) Policy Access",
            subtitle = if (perms.dndAllowed) {
                "Granted • Scheduled calls & priority caller alerts bypass silent mode"
            } else {
                "Recommended so priority & scheduled call rings break through DND"
            },
            icon = Icons.Default.DoNotDisturbOn,
            accentColor = if (perms.dndAllowed) colors.acceptColor else colors.priorityColor,
            trailingBadge = if (perms.dndAllowed) "Allowed" else "Allow",
            onClick = { onLaunchIntent(PermissionHelper.createDndSettingsIntent()) },
            testTag = "settings_dnd_policy_shortcut"
        )
        CapsuleGroupDivider()
        CapsuleSettingsItem(
            title = "Uninstall Protection (Device Admin)",
            subtitle = when {
                perms.deviceOwnerActive -> "Device Owner Active • Launcher uninstall blocked"
                perms.deviceAdminActive -> "Device Admin Active • Requires revocation here before uninstall"
                else -> "Protect Lyra from accidental home-screen/launcher uninstallation"
            },
            icon = Icons.Default.AdminPanelSettings,
            accentColor = if (perms.deviceAdminActive || perms.deviceOwnerActive) colors.acceptColor else colors.primary,
            trailingBadge = when {
                perms.deviceOwnerActive -> "Owner"
                perms.deviceAdminActive -> "Revoke"
                else -> "Activate"
            },
            onClick = {
                if (perms.deviceAdminActive && !perms.deviceOwnerActive) {
                    if (!onDeactivateDeviceAdmin()) {
                        onLaunchIntent(PermissionHelper.createSecurityDeviceAdminSettingsIntent())
                    }
                } else {
                    onLaunchIntent(PermissionHelper.createDeviceAdminActivationIntent(context))
                }
            },
            testTag = "settings_device_admin_action"
        )
        CapsuleGroupDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("settings_android15_recording_note"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = if (Build.VERSION.SDK_INT >= 35) colors.priorityColor else colors.primaryGlow,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (Build.VERSION.SDK_INT >= 35) {
                    "Android 15+ Policy: Two-way native call recording is restricted and disabled on this OS level."
                } else {
                    "Call recording follows Android platform policy and is automatically blocked on Android 15+ devices."
                },
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

