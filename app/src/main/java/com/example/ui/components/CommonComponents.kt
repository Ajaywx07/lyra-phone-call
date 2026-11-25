package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LocalInsideGlassSurface
import com.example.ui.theme.LyraTheme

@Composable
fun LyraAmbientBackdrop(
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    if (colors.isLight || colors.style == com.example.ui.theme.AppThemeStyle.PURE_AMOLED) return

    val topAlpha = if (colors.isGlassStyle) 0.30f else 0.14f
    val bottomAlpha = if (colors.isGlassStyle) 0.24f else 0.12f

    Canvas(modifier = modifier.fillMaxSize()) {
        if (colors.isGlassStyle) {
            // Soft oceanic mist behind the floating Liquid Glass capsule header
            val headerCenter = Offset(x = size.width * 0.24f, y = 46.dp.toPx())
            val headerRadius = 170.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFF0EA5E9).copy(alpha = 0.22f),
                    0.5f to Color(0xFF38BDF8).copy(alpha = 0.10f),
                    1.0f to Color.Transparent,
                    center = headerCenter,
                    radius = headerRadius
                ),
                radius = headerRadius,
                center = headerCenter
            )
        }

        val topRadius = 210.dp.toPx()
        val topCenter = Offset(x = size.width - 20.dp.toPx(), y = 72.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to colors.primary.copy(alpha = topAlpha),
                0.45f to colors.primary.copy(alpha = topAlpha * 0.48f),
                0.78f to colors.primary.copy(alpha = topAlpha * 0.14f),
                1.0f to Color.Transparent,
                center = topCenter,
                radius = topRadius
            ),
            radius = topRadius,
            center = topCenter
        )

        val bottomRadius = 230.dp.toPx()
        val bottomCenter = Offset(x = 30.dp.toPx(), y = size.height - 60.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to colors.secondary.copy(alpha = bottomAlpha),
                0.45f to colors.secondary.copy(alpha = bottomAlpha * 0.45f),
                0.78f to colors.secondary.copy(alpha = bottomAlpha * 0.14f),
                1.0f to Color.Transparent,
                center = bottomCenter,
                radius = bottomRadius
            ),
            radius = bottomRadius,
            center = bottomCenter
        )

        if (colors.isGlassStyle) {
            val prismCenter = Offset(x = size.width * 0.85f, y = size.height * 0.72f)
            val prismRadius = 155.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFFF472B6).copy(alpha = 0.11f),
                    0.55f to Color(0xFF818CF8).copy(alpha = 0.06f),
                    1.0f to Color.Transparent,
                    center = prismCenter,
                    radius = prismRadius
                ),
                radius = prismRadius,
                center = prismCenter
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = LyraTheme.colors.glassBorder,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = LyraTheme.colors
    val insideGlass = LocalInsideGlassSurface.current
    if (colors.isGlassStyle) {
        val accentHint = if (borderColor != colors.glassBorder) borderColor else colors.primary
        LiquidGlassSurface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            cornerRadius = 24.dp,
            tintColor = if (insideGlass) Color(0x881C2E4A) else Color(0x66132138),
            accentGlow = accentHint,
            subtle = insideGlass,
            enableAgslRefraction = !insideGlass,
            contentPadding = contentPadding
        ) {
            content()
        }
        return
    }
    val resolvedContainer = if (insideGlass && colors.isGlassStyle) {
        colors.surfaceElevated
    } else {
        colors.glassSurface
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = resolvedContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isLight) 2.dp else 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        CompositionLocalProvider(LocalInsideGlassSurface provides true) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

@Composable
fun LyraTopBar(
    title: String = "LYRA",
    subtitle: String = "Personal Calling Assistant",
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    statusText: String = "● Lyra Online",
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    trailingActions: @Composable () -> Unit = {}
) {
    val colors = LyraTheme.colors
    val headerContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        color = if (colors.isGlassStyle) Color(0x40FFFFFF).copy(alpha = 0.14f) else colors.surfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (colors.isGlassStyle) Color.White.copy(alpha = 0.34f) else colors.glassBorder
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("top_bar_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (colors.isGlassStyle) Color.White else colors.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (colors.isGlassStyle) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            colors.primary.copy(alpha = 0.20f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(listOf(colors.surfaceElevated, colors.surfaceElevated))
                                }
                            )
                            .border(
                                1.dp,
                                if (colors.isGlassStyle) Color.White.copy(alpha = 0.45f) else colors.primary.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .padding(5.dp)
                            .testTag("top_bar_lyra_logo"),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lyra_logo),
                            contentDescription = "Lyra Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
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
                if (statusText.isNotBlank()) {
                    Surface(
                        color = if (colors.isGlassStyle) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            colors.primary.copy(alpha = 0.14f)
                        },
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (colors.isGlassStyle) Color.White.copy(alpha = 0.36f) else colors.primary.copy(alpha = 0.42f)
                        )
                    ) {
                        Text(
                            text = statusText,
                            color = if (colors.isGlassStyle) colors.primaryGlow else colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                trailingActions()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
    ) {
        if (colors.isGlassStyle) {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                cornerRadius = 32.dp,
                tintColor = Color(0x6E152540),
                accentGlow = colors.primary,
                rimWidth = 1.25.dp,
                enableAgslRefraction = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                headerContent()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = if (colors.isLight) colors.surface else colors.glassSurface,
                border = BorderStroke(1.dp, colors.glassBorder)
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    headerContent()
                }
            }
        }
    }
}

enum class BadgeType {
    ALLOWED,
    REQUIRED,
    DENIED,
    PRIORITY,
    OWNER,
    UNKNOWN
}

@Composable
fun StatusBadge(
    type: BadgeType,
    customText: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val (bgColor, borderColor, textColor, defaultText, icon) = when (type) {
        BadgeType.ALLOWED -> Tuple5(
            EmeraldAccept.copy(alpha = 0.15f),
            EmeraldAccept.copy(alpha = 0.4f),
            EmeraldAccept,
            "✓ Allowed",
            Icons.Default.CheckCircle
        )
        BadgeType.REQUIRED -> Tuple5(
            AmberPriority.copy(alpha = 0.15f),
            AmberPriority.copy(alpha = 0.4f),
            AmberPriority,
            "⚠ Required",
            Icons.Default.Warning
        )
        BadgeType.DENIED -> Tuple5(
            CrimsonDecline.copy(alpha = 0.15f),
            CrimsonDecline.copy(alpha = 0.4f),
            CrimsonDecline,
            "✕ Denied",
            Icons.Default.Close
        )
        BadgeType.PRIORITY -> Tuple5(
            colors.secondary.copy(alpha = 0.2f),
            colors.secondary.copy(alpha = 0.5f),
            colors.secondary,
            "★ Priority Contact",
            Icons.Default.Star
        )
        BadgeType.OWNER -> Tuple5(
            colors.primary.copy(alpha = 0.2f),
            colors.primary.copy(alpha = 0.6f),
            colors.primary,
            "👑 Owner / Priority Caller",
            Icons.Default.Star
        )
        BadgeType.UNKNOWN -> Tuple5(
            colors.surfaceElevated,
            colors.glassBorder,
            colors.textSecondary,
            "Unknown Caller",
            null
        )
    }

    Surface(
        modifier = modifier,
        color = bgColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = customText ?: defaultText,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    GlassCard(
        modifier = modifier.testTag("permission_card_$title"),
        borderColor = if (isGranted) EmeraldAccept.copy(alpha = 0.35f) else colors.glassBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        type = if (isGranted) BadgeType.ALLOWED else BadgeType.REQUIRED
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("grant_btn_$title")
                ) {
                    Text(
                        text = "Grant",
                        color = colors.onPrimaryButton,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = EmeraldAccept,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun LyraContactAvatar(
    name: String,
    photoUri: String?,
    isOwner: Boolean = false,
    isPriority: Boolean = false,
    size: Dp = 46.dp,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val resolvedAccent = accentColor ?: when {
        isOwner -> colors.primary
        isPriority -> AmberPriority
        else -> when (kotlin.math.abs(name.hashCode()) % 5) {
            0 -> colors.primary
            1 -> colors.secondary
            2 -> EmeraldAccept
            3 -> Color(0xFF0284C7)
            else -> AmberPriority
        }
    }

    var loadFailed by remember(photoUri) { mutableStateOf(false) }
    val showPhoto = !photoUri.isNullOrBlank() && !loadFailed
    val isUnknown = name.isBlank() || name.equals("Unknown Caller", ignoreCase = true)
    val initialChar = if (isUnknown) null else name.firstOrNull { it.isLetter() }?.uppercaseChar()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isOwner) colors.primary.copy(alpha = 0.16f)
                else if (isPriority) AmberPriority.copy(alpha = 0.14f)
                else resolvedAccent.copy(alpha = 0.14f)
            )
            .border(
                width = if (isOwner || isPriority) 2.dp else 1.2.dp,
                color = if (isOwner) colors.primary else if (isPriority) AmberPriority else resolvedAccent.copy(alpha = 0.55f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showPhoto) {
            val context = LocalContext.current
            val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(48) }
            val imageRequest = remember(photoUri, sizePx) {
                ImageRequest.Builder(context)
                    .data(photoUri)
                    .size(sizePx, sizePx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = "$name photo",
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else if (initialChar != null) {
            val textSizeSp = (size.value * 0.42f).coerceIn(14f, 44f).sp
            Text(
                text = initialChar.toString(),
                color = if (isOwner) colors.primary else if (isPriority) AmberPriority else resolvedAccent,
                fontSize = textSizeSp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = name.ifBlank { "Caller photo" },
                tint = if (isOwner) colors.primary else if (isPriority) AmberPriority else colors.textSecondary,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}

@Composable
fun CallerCard(
    caller: CallerInfo,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("caller_profile_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (caller.isOwner) colors.primary.copy(alpha = 0.4f) else colors.secondary.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            LyraContactAvatar(
                name = caller.displayName,
                photoUri = caller.photoUri,
                isOwner = caller.isOwner,
                isPriority = caller.isPriority,
                size = 90.dp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = caller.displayName,
            color = colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = caller.formattedNumber,
            color = colors.primaryGlow,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        when {
            caller.isOwner -> StatusBadge(type = BadgeType.OWNER)
            caller.isPriority -> StatusBadge(type = BadgeType.PRIORITY)
            caller.isKnown -> StatusBadge(type = BadgeType.ALLOWED, customText = "Known Contact")
            else -> StatusBadge(type = BadgeType.UNKNOWN, customText = "Unknown Caller")
        }
    }
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color = Color.White,
    size: Dp = 64.dp,
    iconSize: Dp = 28.dp,
    modifier: Modifier = Modifier,
    testTagOverride: String? = null
) {
    val colors = LyraTheme.colors
    Column(
        modifier = modifier.testTag(testTagOverride ?: "call_action_$label"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = containerColor,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallHistoryItem(
    item: CallHistoryItemData,
    onCallClick: () -> Unit,
    onItemClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val haptic = LocalHapticFeedback.current
    GlassCard(
        modifier = modifier
            .testTag("call_history_item_${item.id}")
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            if (item.isDemoData) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "DEMO DATA",
                        color = AmberPriority,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val (icon, iconColor) = when (item.callType) {
                        CallTypeEnum.INCOMING -> Pair(Icons.Default.CallReceived, EmeraldAccept)
                        CallTypeEnum.OUTGOING -> Pair(Icons.Default.CallMade, colors.primary)
                        CallTypeEnum.MISSED -> Pair(Icons.Default.CallMissed, CrimsonDecline)
                        CallTypeEnum.REJECTED -> Pair(Icons.Default.CallEnd, CrimsonDecline)
                    }

                    Box(modifier = Modifier.size(46.dp)) {
                        LyraContactAvatar(
                            name = item.contactName ?: item.phoneNumber,
                            photoUri = item.photoUri,
                            isOwner = item.isOwner,
                            isPriority = item.isPriority,
                            size = 44.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                                .border(1.dp, iconColor.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = item.callType.name,
                                tint = iconColor,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.contactName ?: item.phoneNumber,
                                color = if (item.callType == CallTypeEnum.MISSED || item.callType == CallTypeEnum.REJECTED) {
                                    CrimsonDecline
                                } else {
                                    colors.textPrimary
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.isOwner) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "👑", fontSize = 12.sp)
                            } else if (item.isPriority) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "★", color = AmberPriority, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.callType.name.lowercase().replaceFirstChar { it.uppercase() }} • ${item.formattedDuration}",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = item.formattedDate,
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(48.dp).testTag("call_btn_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = EmeraldAccept,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactListItem(
    contact: ContactItemData,
    onContactClick: () -> Unit,
    onCallClick: () -> Unit,
    onTogglePriority: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val haptic = LocalHapticFeedback.current
    GlassCard(
        modifier = modifier
            .testTag("contact_item_${contact.id}")
            .combinedClickable(
                onClick = onContactClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            if (contact.isDemoData) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "DEMO DATA",
                        color = AmberPriority,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    LyraContactAvatar(
                        name = contact.name,
                        photoUri = contact.photoUri,
                        isOwner = contact.isOwner,
                        isPriority = contact.isPriority,
                        size = 46.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = contact.name,
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (contact.isOwner) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "👑", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = contact.phoneNumber,
                            color = colors.primaryGlow,
                            fontSize = 12.sp
                        )
                        Text(
                            text = contact.label,
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePriority,
                        modifier = Modifier.size(44.dp).testTag("star_${contact.id}")
                    ) {
                        Icon(
                            imageVector = if (contact.isPriority) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Priority",
                            tint = if (contact.isPriority) AmberPriority else colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier.size(44.dp).testTag("call_contact_${contact.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = EmeraldAccept,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    GlassCard(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("setting_$title"),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = colors.primary,
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

            if (isChecked != null && onCheckedChange != null) {
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimaryButton,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceElevated
                    ),
                    modifier = Modifier.testTag("switch_$title")
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(colors.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = colors.primaryGlow,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.glassSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                    Text(
                        text = buttonText,
                        color = colors.onPrimaryButton,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

@Composable
fun LyraDeleteConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = CrimsonDecline,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("delete_confirmation_title")
            )
        },
        text = {
            Text(
                text = message,
                color = colors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonDecline),
                shape = CircleShape,
                modifier = Modifier.testTag("confirm_delete_btn")
            ) {
                Text(
                    text = confirmText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_btn")
            ) {
                Text(
                    text = "Cancel",
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(22.dp)
    )
}
