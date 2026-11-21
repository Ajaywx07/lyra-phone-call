package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.LocalInsideGlassSurface
import com.example.ui.theme.LyraTheme

/**
 * Outer pill-shaped capsule shell used across Double-Capsule controls
 * (Bottom Navigation, Recents/Contacts filter bars, Dual-SIM selector).
 */
@Composable
fun OuterCapsuleContainer(
    modifier: Modifier = Modifier,
    containerColor: Color = LyraTheme.colors.surfaceElevated,
    borderColor: Color = LyraTheme.colors.glassBorder,
    tonalElevation: Dp = if (LyraTheme.colors.isLight) 4.dp else 0.dp,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    content: @Composable () -> Unit
) {
    val colors = LyraTheme.colors
    val insideGlass = LocalInsideGlassSurface.current
    val resolvedColor = if (insideGlass && colors.isGlassStyle && containerColor == colors.glassSurface) {
        colors.surfaceElevated
    } else {
        containerColor
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = resolvedColor,
        shadowElevation = tonalElevation,
        border = BorderStroke(1.dp, borderColor)
    ) {
        CompositionLocalProvider(LocalInsideGlassSurface provides true) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

/**
 * Reusable Double-Capsule segmented filter bar: ONE outer capsule containing N inner capsules.
 */
@Composable
fun <T> CapsuleFilterBar(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "capsule_filter"
) {
    val colors = LyraTheme.colors
    OuterCapsuleContainer(
        modifier = modifier,
        containerColor = colors.surface,
        borderColor = colors.glassBorder,
        contentPadding = PaddingValues(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val label = labelProvider(item)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary.copy(alpha = 0.20f) else Color.Transparent,
                    animationSpec = tween(110, easing = FastOutSlowInEasing),
                    label = "filter_bg"
                )
                val strokeColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary.copy(alpha = 0.55f) else Color.Transparent,
                    animationSpec = tween(110, easing = FastOutSlowInEasing),
                    label = "filter_stroke"
                )
                val txtColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary else colors.textSecondary,
                    animationSpec = tween(110, easing = FastOutSlowInEasing),
                    label = "filter_txt"
                )

                Surface(
                    onClick = { onSelect(item) },
                    shape = CircleShape,
                    color = bgColor,
                    border = if (isSelected) BorderStroke(1.dp, strokeColor) else null,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("${testTagPrefix}_${label.lowercase().replace(" ", "_")}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = txtColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pill-shaped Capsule Search Bar with instant clear action.
 */
@Composable
fun CapsuleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "capsule_search_input"
) {
    val colors = LyraTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp),
        shape = CircleShape,
        color = colors.surfaceElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (query.isNotBlank()) colors.primary.copy(alpha = 0.55f) else colors.glassBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (query.isNotBlank()) colors.primary else colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Capsule-styled screen header with optional back capsule, brand capsule logo, status pill, and trailing controls.
 */
@Composable
fun CapsuleTopHeader(
    title: String,
    subtitle: String,
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    statusText: String? = null,
    trailingActions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (showBack) {
                Surface(
                    onClick = onBackClick,
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("top_bar_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape)
                        .padding(5.dp)
                        .testTag("top_bar_lyra_logo"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lyra_logo),
                        contentDescription = "Lyra Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = colors.primaryGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!statusText.isNullOrBlank()) {
                CapsuleStatusChip(
                    text = statusText,
                    color = colors.primary
                )
            }
            trailingActions()
        }
    }
}

/**
 * Dedicated green capsule button enclosing the Phone Call icon for Recents & Contacts rows.
 */
@Composable
fun GreenCallActionCapsule(
    onClick: () -> Unit,
    contentDescription: String = "Call",
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.acceptColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, colors.acceptColor.copy(alpha = 0.55f)),
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = contentDescription,
                tint = colors.acceptColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Capsule-structured Recent Call row:
 * - Left: circular avatar + call-type badge
 * - Middle: caller name, status/duration pill, and timestamp
 * - Right: dedicated green Call capsule
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentCallCard(
    item: CallHistoryItemData,
    onCallClick: () -> Unit,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
    callCount: Int = 1,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val haptic = LocalHapticFeedback.current

    val (typeIcon, typeColor, typeLabel) = when (item.callType) {
        CallTypeEnum.INCOMING -> Triple(Icons.Default.CallReceived, colors.acceptColor, "Incoming")
        CallTypeEnum.OUTGOING -> Triple(Icons.Default.CallMade, colors.primary, "Outgoing")
        CallTypeEnum.MISSED -> Triple(Icons.Default.CallMissed, colors.declineColor, "Missed")
        CallTypeEnum.REJECTED -> Triple(Icons.Default.CallEnd, colors.declineColor, "Rejected")
    }
    val isMissedOrRejected = item.callType == CallTypeEnum.MISSED || item.callType == CallTypeEnum.REJECTED
    val statusPillText = when (item.callType) {
        CallTypeEnum.MISSED -> "Missed"
        CallTypeEnum.REJECTED -> "Rejected"
        CallTypeEnum.INCOMING, CallTypeEnum.OUTGOING -> {
            if (item.durationSeconds > 0L && item.formattedDuration.isNotBlank()) {
                "$typeLabel • ${item.formattedDuration}"
            } else {
                typeLabel
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .testTag("call_history_item_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isMissedOrRejected) colors.declineColor.copy(alpha = 0.28f) else colors.glassBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Compact Avatar + Call-Type Badge matching ContactCard height
            Box(modifier = Modifier.size(44.dp)) {
                LyraContactAvatar(
                    name = item.contactName ?: item.phoneNumber,
                    photoUri = item.photoUri,
                    isOwner = item.isOwner,
                    isPriority = item.isPriority,
                    size = 42.dp
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, typeColor.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = typeLabel,
                        tint = typeColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle: 2 balanced rows matching ContactCard density
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.contactName?.takeIf { it.isNotBlank() } ?: item.phoneNumber,
                        color = if (isMissedOrRejected) colors.declineColor else colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (callCount > 1) {
                        CapsuleStatusChip(
                            text = "($callCount)",
                            color = colors.secondary,
                            modifier = Modifier.testTag("call_count_badge_${item.id}")
                        )
                    }
                    if (item.isOwner) {
                        CapsuleStatusChip(text = "Owner", color = colors.primary)
                    } else if (item.isPriority) {
                        CapsuleStatusChip(text = "★ VIP", color = AmberPriority)
                    }
                    if (item.isDemoData) {
                        CapsuleStatusChip(text = "DEMO", color = AmberPriority)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        color = typeColor.copy(alpha = 0.14f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = statusPillText,
                            color = typeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                                .testTag("recent_status_${item.id}")
                        )
                    }
                    item.simLabel?.takeIf { it.isNotBlank() }?.let { sim ->
                        Surface(
                            color = colors.surfaceElevated,
                            shape = CircleShape,
                            border = BorderStroke(0.8.dp, colors.glassBorder)
                        ) {
                            Text(
                                text = sim,
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                    .testTag("recent_sim_badge_${item.id}")
                            )
                        }
                    }
                    Text(
                        text = item.formattedDate,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // Right: Dedicated Green Call Capsule
            GreenCallActionCapsule(
                onClick = onCallClick,
                contentDescription = "Call ${item.contactName ?: item.phoneNumber}",
                modifier = Modifier.testTag("call_btn_${item.id}")
            )
        }
    }
}

/**
 * Capsule-structured Contact row matching RecentCallCard visual rhythm.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactCard(
    contact: ContactItemData,
    onContactClick: () -> Unit,
    onCallClick: () -> Unit,
    onTogglePriority: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onContactClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .testTag("contact_item_${contact.id}"),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                contact.isOwner -> colors.primary.copy(alpha = 0.45f)
                contact.isPriority -> AmberPriority.copy(alpha = 0.35f)
                else -> colors.glassBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LyraContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                isOwner = contact.isOwner,
                isPriority = contact.isPriority,
                size = 42.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.name,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (contact.isOwner) {
                        CapsuleStatusChip(text = "Owner", color = colors.primary)
                    } else if (contact.isDemoData) {
                        CapsuleStatusChip(text = "DEMO", color = AmberPriority)
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.phoneNumber,
                        color = colors.primaryGlow,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = colors.surfaceElevated,
                        shape = CircleShape
                    ) {
                        Text(
                            text = contact.label,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onTogglePriority,
                    shape = CircleShape,
                    color = if (contact.isPriority) AmberPriority.copy(alpha = 0.16f) else colors.surfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (contact.isPriority) AmberPriority.copy(alpha = 0.5f) else colors.glassBorder
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("star_${contact.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (contact.isPriority) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Priority",
                            tint = if (contact.isPriority) AmberPriority else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                GreenCallActionCapsule(
                    onClick = onCallClick,
                    contentDescription = "Call ${contact.name}",
                    modifier = Modifier.testTag("call_contact_${contact.id}")
                )
            }
        }
    }
}

@Composable
fun CapsuleStatusChip(
    text: String,
    color: Color = LyraTheme.colors.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CapsuleStatPill(
    count: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Surface(
        modifier = modifier,
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CapsuleSettingsGroup(
    modifier: Modifier = Modifier,
    borderColor: Color = LyraTheme.colors.glassBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LyraTheme.colors
    val insideGlass = LocalInsideGlassSurface.current
    val resolvedColor = if (insideGlass && colors.isGlassStyle) {
        colors.surfaceElevated
    } else {
        colors.glassSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = resolvedColor,
        shadowElevation = if (colors.isLight) 2.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        CompositionLocalProvider(LocalInsideGlassSurface provides true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun CapsuleGroupDivider() {
    HorizontalDivider(
        thickness = 0.8.dp,
        color = LyraTheme.colors.glassBorder.copy(alpha = 0.55f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun CapsuleToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = LyraTheme.colors.primary,
    testTag: String? = null
) {
    val colors = LyraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
            .let { if (testTag != null) it.testTag(testTag) else it.testTag("setting_$title") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.28f), CircleShape),
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
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimaryButton,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceElevated
            ),
            modifier = Modifier.testTag("switch_$title")
        )
    }
}

@Composable
fun CapsuleSettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LyraTheme.colors.primary,
    trailingBadge: String? = null,
    trailingBadgeColor: Color = accentColor,
    testTag: String? = null
) {
    val colors = LyraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .let { if (testTag != null) it.testTag(testTag) else it.testTag("setting_item_$title") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.28f), CircleShape),
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
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        if (!trailingBadge.isNullOrBlank()) {
            CapsuleStatusChip(
                text = trailingBadge,
                color = trailingBadgeColor
            )
        }
    }
}

@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = LyraTheme.colors.primary,
    contentColor: Color = LyraTheme.colors.onPrimaryButton,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CapsuleOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = LyraTheme.colors.primary,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
