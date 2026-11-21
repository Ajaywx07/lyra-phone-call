package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.service.CallStateManager
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import kotlin.math.hypot

internal val EdgeGlowGradientPalette = listOf(
    Color(0xFF00E5FF), // Cyan edge glow
    Color(0xFF38BDF8), // Soft sky transition
    Color(0xFF8B5CF6), // Violet glow
    Color(0xFFEC4899), // Pink edge highlight
    Color(0xFFFB923C), // Warm coral/amber glow
    Color(0xFFFDE047), // Soft warm sheen
    Color(0xFF22D3EE), // Aqua transition back to cyan
    Color(0xFF00E5FF)  // Seamless cyclic closure
)

/**
 * Reusable capsule container with a smooth, GPU-friendly rotating multi-color edge-glow border
 * and soft outer halo blur. Keeps the inner surface opaque/high-contrast so glow stays along
 * the perimeter and never clutters foreground avatars, text, or action buttons.
 */
@Composable
fun EdgeGlowCapsuleContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    borderWidth: Dp = 2.dp,
    haloMargin: Dp = 4.dp,
    containerColor: Color = if (LyraTheme.colors.isLight) {
        Color(0xFAF8FAFC)
    } else {
        Color(0xF50F172A)
    },
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val callState by CallStateManager.callState.collectAsStateWithLifecycle()
    val isFullScreenIncomingUiVisible by CallStateManager.isFullScreenIncomingUiVisible.collectAsStateWithLifecycle()
    val isCallPopupDismissing = callState != CallStateEnum.RINGING || isFullScreenIncomingUiVisible
    val glowIntensity by animateFloatAsState(
        targetValue = if (isCallPopupDismissing) 0f else 1f,
        animationSpec = tween(durationMillis = 420),
        label = "incoming_popup_glow_fade"
    )
    val haloBlurRadius by animateDpAsState(
        targetValue = if (isCallPopupDismissing) 22.dp else 10.dp,
        animationSpec = tween(durationMillis = 420),
        label = "incoming_popup_glow_blur"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "edge_glow_transition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge_glow_sweep"
    )

    val outerShape = RoundedCornerShape(cornerRadius)
    val haloModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(haloBlurRadius)
    } else {
        Modifier
    }

    Box(modifier = modifier) {
        // Only the glow layers fade and blur when the incoming-call popup is dismissed;
        // the caller details and action buttons remain crisp during the exit animation.
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(haloModifier)
                .drawWithCache {
                    val cornerPx = cornerRadius.toPx()
                    val insetPx = haloMargin.toPx() * 0.55f
                    val sweepBrush = Brush.sweepGradient(
                        colors = EdgeGlowGradientPalette,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                    val radius = hypot(size.width, size.height) * 0.65f
                    val haloStrokeOuter = (borderWidth * 3.5f).toPx()
                    val haloStrokeInner = (borderWidth * 1.8f).toPx()
                    val haloRectTopLeft = Offset(insetPx, insetPx)
                    val haloRectSize = androidx.compose.ui.geometry.Size(
                        width = (size.width - insetPx * 2f).coerceAtLeast(1f),
                        height = (size.height - insetPx * 2f).coerceAtLeast(1f)
                    )
                    val ringPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(haloRectTopLeft, haloRectSize),
                                cornerRadius = CornerRadius(cornerPx, cornerPx)
                            )
                        )
                    }

                    onDrawBehind {
                        clipPath(ringPath) {
                            withTransform({
                                rotate(degrees = sweepAngle, pivot = center)
                            }) {
                                drawCircle(
                                    brush = sweepBrush,
                                    radius = radius,
                                    center = center,
                                    alpha = 0.24f * glowIntensity
                                )
                            }
                        }
                        drawRoundRect(
                            brush = sweepBrush,
                            topLeft = haloRectTopLeft,
                            size = haloRectSize,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            alpha = 0.34f * glowIntensity,
                            style = Stroke(width = haloStrokeOuter)
                        )
                        drawRoundRect(
                            brush = sweepBrush,
                            topLeft = haloRectTopLeft,
                            size = haloRectSize,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            alpha = 0.45f * glowIntensity,
                            style = Stroke(width = haloStrokeInner)
                        )
                    }
                }
        )

        // Crisp running edge-glow perimeter rim + opaque inner surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(haloMargin)
                .clip(outerShape)
                .drawWithCache {
                    val cornerPx = cornerRadius.toPx()
                    val strokePx = borderWidth.toPx()
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val sweepBrush = Brush.sweepGradient(
                        colors = EdgeGlowGradientPalette,
                        center = centerOffset
                    )
                    val circleRadius = hypot(size.width, size.height) * 0.65f

                    val outerPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(Offset.Zero, size),
                                cornerRadius = CornerRadius(cornerPx, cornerPx)
                            )
                        )
                    }
                    val innerCornerPx = (cornerPx - strokePx).coerceAtLeast(0f)
                    val innerPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = strokePx,
                                top = strokePx,
                                right = (size.width - strokePx).coerceAtLeast(strokePx),
                                bottom = (size.height - strokePx).coerceAtLeast(strokePx),
                                cornerRadius = CornerRadius(innerCornerPx, innerCornerPx)
                            )
                        )
                    }
                    val borderRingPath = Path().apply {
                        op(outerPath, innerPath, PathOperation.Difference)
                    }

                    onDrawBehind {
                        clipPath(borderRingPath) {
                            withTransform({
                                rotate(degrees = sweepAngle, pivot = center)
                            }) {
                                drawCircle(
                                    brush = sweepBrush,
                                    radius = circleRadius,
                                    center = center,
                                    alpha = 0.96f * glowIntensity
                                )
                            }
                        }
                    }
                }
                .padding(borderWidth)
        ) {
            val innerCorner = (cornerRadius - borderWidth).coerceAtLeast(8.dp)
            val innerShape = RoundedCornerShape(innerCorner)
            Surface(
                shape = innerShape,
                color = containerColor,
                shadowElevation = 6.dp,
                border = BorderStroke(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = if (LyraTheme.colors.isLight) 0.4f else 0.10f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(innerShape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Box(modifier = Modifier.padding(contentPadding)) {
                    content()
                }
            }
        }
    }
}

/**
 * Unlocked-screen top/heads-up incoming call notification banner.
 * Uses EdgeGlowCapsuleContainer for smooth multi-color edge lighting without overlapping
 * caller photo, caller name, or Accept/Decline action buttons.
 */
@Composable
fun TopIncomingCallBanner(
    caller: CallerInfo,
    activeSimLabel: String,
    isSilenced: Boolean,
    onBannerClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onDeclineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val statusBadgeColor = when {
        caller.isOwner -> AmberPriority
        isSilenced -> colors.secondary
        else -> colors.primary
    }
    val statusLabel = when {
        isSilenced -> "INCOMING • $activeSimLabel • SILENCED"
        caller.isOwner -> "OWNER PRIORITY • $activeSimLabel"
        caller.isPriority -> "PRIORITY CALL • $activeSimLabel"
        else -> "INCOMING CALL • $activeSimLabel"
    }

    EdgeGlowCapsuleContainer(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_incoming_call_banner"),
        cornerRadius = 28.dp,
        borderWidth = 2.dp,
        haloMargin = 4.dp,
        onClick = onBannerClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Caller Photo / Initials Avatar + Identity
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyraContactAvatar(
                    name = caller.displayName,
                    photoUri = caller.photoUri,
                    isOwner = caller.isOwner,
                    isPriority = caller.isPriority,
                    size = 46.dp,
                    modifier = Modifier.testTag("top_banner_caller_photo")
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusBadgeColor)
                        )
                        Text(
                            text = statusLabel,
                            color = statusBadgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = caller.displayName.ifBlank { caller.formattedNumber.ifBlank { "Incoming Caller" } },
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("top_banner_caller_name")
                    )

                    Text(
                        text = buildString {
                            val sub = caller.formattedNumber.ifBlank { caller.phoneNumber }
                            append(sub)
                            if (!caller.isKnown && sub.isNotBlank()) {
                                append(" • Tap to expand")
                            }
                        }.ifBlank { "Tap banner for full screen" },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Decline & Accept circular touch buttons (min 48dp each, clear spacing)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onDeclineClick,
                    shape = CircleShape,
                    color = CrimsonDecline,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .size(48.dp)
                        .testTag("top_banner_decline_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline call",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    onClick = onAcceptClick,
                    shape = CircleShape,
                    color = EmeraldAccept,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .size(48.dp)
                        .testTag("top_banner_accept_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept call",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
