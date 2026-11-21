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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.hypot
import kotlin.math.min

internal val EdgeGlowGradientPalette = listOf(
    Color(0xFF22D3EE), // Luminous ice cyan
    Color(0xFF38BDF8), // Electric sky blue
    Color(0xFF8B5CF6), // Neon violet
    Color(0xFFA855F7), // Vivid orchid purple
    Color(0xFFEC4899), // Hot neon magenta-pink
    Color(0xFFFB7185), // Radiant coral-rose
    Color(0xFFA855F7), // Smooth violet return
    Color(0xFF22D3EE)  // Seamless cyclic cyan closure
)

private val SmokedObsidianCore = Color(0xEC0D1017)
private val SmokedObsidianEdge = Color(0xF2151924)

/**
 * Draws a wrapped arc segment along a closed [PathMeasure] without allocating new [Path] instances.
 */
private fun DrawScope.drawWrappedSurgeSegment(
    pathMeasure: PathMeasure,
    totalLength: Float,
    scratchPath: Path,
    startDist: Float,
    endDist: Float,
    color: Color,
    strokeWidth: Float
) {
    if (totalLength <= 1f) return
    val s = ((startDist % totalLength) + totalLength) % totalLength
    val e = ((endDist % totalLength) + totalLength) % totalLength
    val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    if (s < e) {
        scratchPath.reset()
        if (pathMeasure.getSegment(s, e, scratchPath, startWithMoveTo = true)) {
            drawPath(scratchPath, color = color, style = style)
        }
    } else if (s > e) {
        scratchPath.reset()
        if (pathMeasure.getSegment(s, totalLength, scratchPath, startWithMoveTo = true)) {
            drawPath(scratchPath, color = color, style = style)
        }
        scratchPath.reset()
        if (pathMeasure.getSegment(0f, e, scratchPath, startWithMoveTo = true)) {
            drawPath(scratchPath, color = color, style = style)
        }
    }
}

/**
 * Reusable pill-shaped smoked-glass capsule with:
 * - High corner radius forming a true semi-circular pill silhouette.
 * - Vivid rotating multi-color neon border + incandescent inner filament core.
 * - Traveling light-tube surges (`PathMeasure`) chasing smoothly along the capsule perimeter.
 * - Multi-layer outer neon bloom and downward drop shadow synchronized with the border colors,
 *   feathered cleanly at the outermost boundary so all four sides glow evenly without clipping.
 * - Subtle blurred smoked-glass interior with a sleek diagonal specular reflection.
 */
@Composable
fun EdgeGlowCapsuleContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 48.dp,
    borderWidth: Dp = 2.5.dp,
    haloMargin: Dp = 16.dp,
    containerColor: Color = SmokedObsidianCore,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
    val outerBloomBlur by animateDpAsState(
        targetValue = if (isCallPopupDismissing) 24.dp else 13.dp,
        animationSpec = tween(durationMillis = 420),
        label = "incoming_popup_glow_blur"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "edge_glow_transition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge_glow_sweep"
    )

    val pillShape = RoundedCornerShape(cornerRadius)
    val outerBloomBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(outerBloomBlur)
    } else {
        Modifier
    }
    val innerAuraBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(5.dp)
    } else {
        Modifier
    }
    val subtleSurfaceBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(6.dp)
    } else {
        Modifier
    }

    Box(modifier = modifier) {
        // Layer 1: Wide outer neon bloom + synchronized downward drop shadow, feathered around the
        // outermost margin fringe (DstOut) so every side—including curved pill ends—glows evenly
        // and fades seamlessly into light or dark surroundings without rectangular edges.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (size.width > 4f && size.height > 4f) {
                        val marginPx = haloMargin.toPx()
                        val featherSteps = 4
                        val maxFeather = (marginPx * 0.34f).coerceAtLeast(2f)
                        for (step in 0 until featherSteps) {
                            val frac = step.toFloat() / featherSteps
                            val bandInset = (maxFeather * (1f - frac)) * 0.5f
                            val bandWidth = (maxFeather / featherSteps).coerceAtLeast(1f)
                            val w = (size.width - bandInset * 2f).coerceAtLeast(1f)
                            val h = (size.height - bandInset * 2f).coerceAtLeast(1f)
                            val r = min(cornerRadius.toPx() + marginPx - bandInset, h / 2f)
                            drawRoundRect(
                                color = Color.White.copy(alpha = (1f - frac) * 0.85f),
                                topLeft = Offset(bandInset, bandInset),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(r, r),
                                style = Stroke(width = bandWidth),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    }
                }
        ) {
            // Wide diffuse aurora bloom & subtle drop shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(outerBloomBlurModifier)
                    .drawWithCache {
                        val marginPx = haloMargin.toPx()
                        val shadowShiftY = 3.5.dp.toPx()
                        val pillLeft = marginPx
                        val pillTop = marginPx
                        val pillW = (size.width - marginPx * 2f).coerceAtLeast(1f)
                        val pillH = (size.height - marginPx * 2f).coerceAtLeast(1f)
                        val pillCorner = min(cornerRadius.toPx(), pillH / 2f)

                        val shadowTop = (pillTop + shadowShiftY).coerceAtMost(size.height - pillH)
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)
                        val sweepBrush = Brush.sweepGradient(
                            colors = EdgeGlowGradientPalette,
                            center = centerOffset
                        )
                        val circleRadius = hypot(size.width, size.height) * 0.7f

                        val wideBloomWidth = (borderWidth * 5.2f).toPx()
                        val outerBloomRing = Path().apply {
                            val outerLeft = (pillLeft - wideBloomWidth * 0.45f).coerceAtLeast(1f)
                            val outerTop = (pillTop - wideBloomWidth * 0.45f).coerceAtLeast(1f)
                            val outerW = (size.width - outerLeft * 2f).coerceAtLeast(1f)
                            val outerH = (size.height - outerTop * 2f).coerceAtLeast(1f)
                            val outerR = min(pillCorner + wideBloomWidth * 0.45f, outerH / 2f)

                            val innerInset = wideBloomWidth * 0.35f
                            val innerLeft = pillLeft + innerInset
                            val innerTop = pillTop + innerInset
                            val innerW = (pillW - innerInset * 2f).coerceAtLeast(1f)
                            val innerH = (pillH - innerInset * 2f).coerceAtLeast(1f)
                            val innerR = (pillCorner - innerInset).coerceAtLeast(1f)

                            val outer = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(Offset(outerLeft, outerTop), Size(outerW, outerH)),
                                        cornerRadius = CornerRadius(outerR, outerR)
                                    )
                                )
                            }
                            val inner = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(Offset(innerLeft, innerTop), Size(innerW, innerH)),
                                        cornerRadius = CornerRadius(innerR, innerR)
                                    )
                                )
                            }
                            op(outer, inner, PathOperation.Difference)
                        }

                        val dropShadowPill = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(Offset(pillLeft - 2f, shadowTop), Size(pillW + 4f, pillH)),
                                    cornerRadius = CornerRadius(pillCorner, pillCorner)
                                )
                            )
                        }

                        val centerLinePath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(Offset(pillLeft, pillTop), Size(pillW, pillH)),
                                    cornerRadius = CornerRadius(pillCorner, pillCorner)
                                )
                            )
                        }
                        val pathMeasure = PathMeasure().apply {
                            setPath(centerLinePath, forceClosed = true)
                        }
                        val totalLength = pathMeasure.length.coerceAtLeast(1f)
                        val scratchPath = Path()

                        onDrawBehind {
                            // Subtle neutral anchor shadow so the dark pill lifts off light screens cleanly
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.24f),
                                topLeft = Offset(pillLeft, shadowTop),
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(pillCorner, pillCorner)
                            )

                            // Synchronized directional colored drop shadow matching border rotation
                            clipPath(dropShadowPill) {
                                withTransform({
                                    rotate(degrees = sweepAngle, pivot = center)
                                }) {
                                    drawCircle(
                                        brush = sweepBrush,
                                        radius = circleRadius,
                                        center = center,
                                        alpha = 0.44f * glowIntensity
                                    )
                                }
                            }

                            // Wide uniform perimeter aurora glow matching rotating border colors
                            clipPath(outerBloomRing) {
                                withTransform({
                                    rotate(degrees = sweepAngle, pivot = center)
                                }) {
                                    drawCircle(
                                        brush = sweepBrush,
                                        radius = circleRadius,
                                        center = center,
                                        alpha = 0.78f * glowIntensity
                                    )
                                }
                            }

                            // Synchronized blurred bloom boost for the two traveling light surges
                            if (glowIntensity > 0.02f) {
                                val head1 = (sweepAngle / 360f) * totalLength
                                val head2 = head1 + totalLength * 0.5f
                                val surgeSpan = totalLength * 0.14f
                                val bloomStroke = (borderWidth * 3.8f).toPx()
                                listOf(head1, head2).forEach { head ->
                                    drawWrappedSurgeSegment(
                                        pathMeasure = pathMeasure,
                                        totalLength = totalLength,
                                        scratchPath = scratchPath,
                                        startDist = head - surgeSpan,
                                        endDist = head,
                                        color = Color.White.copy(alpha = 0.36f * glowIntensity),
                                        strokeWidth = bloomStroke
                                    )
                                }
                            }
                        }
                    }
            )

            // Tight luminous neon aura hugging right outside the pill outline
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(innerAuraBlurModifier)
                    .drawWithCache {
                        val marginPx = haloMargin.toPx()
                        val auraSpread = (borderWidth * 1.4f).toPx()
                        val outerLeft = (marginPx - auraSpread).coerceAtLeast(1f)
                        val outerTop = (marginPx - auraSpread).coerceAtLeast(1f)
                        val outerW = (size.width - outerLeft * 2f).coerceAtLeast(1f)
                        val outerH = (size.height - outerTop * 2f).coerceAtLeast(1f)
                        val outerR = min(cornerRadius.toPx() + auraSpread, outerH / 2f)

                        val innerInset = marginPx + borderWidth.toPx() * 0.5f
                        val innerW = (size.width - innerInset * 2f).coerceAtLeast(1f)
                        val innerH = (size.height - innerInset * 2f).coerceAtLeast(1f)
                        val innerR = min(cornerRadius.toPx(), innerH / 2f)

                        val auraRingPath = Path().apply {
                            val outer = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(Offset(outerLeft, outerTop), Size(outerW, outerH)),
                                        cornerRadius = CornerRadius(outerR, outerR)
                                    )
                                )
                            }
                            val inner = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(Offset(innerInset, innerInset), Size(innerW, innerH)),
                                        cornerRadius = CornerRadius(innerR, innerR)
                                    )
                                )
                            }
                            op(outer, inner, PathOperation.Difference)
                        }

                        val sweepBrush = Brush.sweepGradient(
                            colors = EdgeGlowGradientPalette,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        val circleRadius = hypot(size.width, size.height) * 0.68f

                        onDrawBehind {
                            clipPath(auraRingPath) {
                                withTransform({
                                    rotate(degrees = sweepAngle, pivot = center)
                                }) {
                                    drawCircle(
                                        brush = sweepBrush,
                                        radius = circleRadius,
                                        center = center,
                                        alpha = 0.90f * glowIntensity
                                    )
                                }
                            }
                        }
                    }
            )
        }

        // Layer 2: Crisp glowing neon border ring + traveling incandescent light surges
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(haloMargin)
                .clip(pillShape)
                .drawWithCache {
                    val cornerPx = min(cornerRadius.toPx(), size.height / 2f)
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

                    // Thin inner incandescent filament inside the colored border
                    val filamentCenterInset = strokePx * 0.48f
                    val filamentPath = Path().apply {
                        val w = (size.width - filamentCenterInset * 2f).coerceAtLeast(1f)
                        val h = (size.height - filamentCenterInset * 2f).coerceAtLeast(1f)
                        val r = min(cornerPx - filamentCenterInset, h / 2f).coerceAtLeast(0f)
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    Offset(filamentCenterInset, filamentCenterInset),
                                    Size(w, h)
                                ),
                                cornerRadius = CornerRadius(r, r)
                            )
                        )
                    }
                    val pathMeasure = PathMeasure().apply {
                        setPath(filamentPath, forceClosed = true)
                    }
                    val totalLength = pathMeasure.length.coerceAtLeast(1f)
                    val scratchPath = Path()

                    onDrawBehind {
                        // Saturated rotating neon perimeter border
                        clipPath(borderRingPath) {
                            withTransform({
                                rotate(degrees = sweepAngle, pivot = center)
                            }) {
                                drawCircle(
                                    brush = sweepBrush,
                                    radius = circleRadius,
                                    center = center,
                                    alpha = glowIntensity
                                )
                            }
                        }

                        // Bright glowing inner filament line + running tapered light pulses
                        if (glowIntensity > 0.02f) {
                            drawPath(
                                path = filamentPath,
                                color = Color.White.copy(alpha = 0.42f * glowIntensity),
                                style = Stroke(width = (strokePx * 0.42f).coerceAtLeast(1f))
                            )

                            val head1 = (sweepAngle / 360f) * totalLength
                            val head2 = head1 + totalLength * 0.5f
                            val surgeLength = totalLength * 0.16f
                            val steps = 6
                            listOf(head1, head2).forEach { head ->
                                for (step in 0 until steps) {
                                    val t0 = step.toFloat() / steps
                                    val t1 = (step + 1).toFloat() / steps
                                    val segStart = head - surgeLength * (1f - t0)
                                    val segEnd = head - surgeLength * (1f - t1)
                                    val rampAlpha = (t1 * t1) * 0.72f * glowIntensity
                                    drawWrappedSurgeSegment(
                                        pathMeasure = pathMeasure,
                                        totalLength = totalLength,
                                        scratchPath = scratchPath,
                                        startDist = segStart,
                                        endDist = segEnd,
                                        color = Color.White.copy(alpha = rampAlpha),
                                        strokeWidth = strokePx * (0.45f + 0.35f * t1)
                                    )
                                }
                            }
                        }
                    }
                }
                .padding(borderWidth)
        ) {
            // Layer 3: Sleek dark smoked-glass inner capsule with subtle blur, radial depth,
            // and diagonal specular glass reflection matching the reference look.
            val innerCorner = (cornerRadius - borderWidth).coerceAtLeast(12.dp)
            val innerShape = RoundedCornerShape(innerCorner)
            Surface(
                shape = innerShape,
                color = Color.Transparent,
                shadowElevation = 0.dp,
                border = BorderStroke(
                    width = 0.6.dp,
                    color = Color.White.copy(alpha = 0.20f)
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtly blurred dark smoked-glass radial gradient + diagonal glass highlight
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(subtleSurfaceBlurModifier)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        SmokedObsidianEdge,
                                        containerColor,
                                        Color(0xF5090B10)
                                    )
                                )
                            )
                            .drawWithCache {
                                val sheenBrush = Brush.linearGradient(
                                    0.0f to Color.White.copy(alpha = 0.14f),
                                    0.42f to Color.White.copy(alpha = 0.06f),
                                    0.52f to Color.White.copy(alpha = 0.01f),
                                    0.56f to Color.Transparent,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width * 0.66f, size.height * 1.1f)
                                )
                                onDrawBehind {
                                    drawRect(brush = sheenBrush)
                                }
                            }
                    )

                    // Crisp foreground caller identity & glowing circular action buttons
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Unlocked-screen top/heads-up incoming call notification banner.
 * Uses EdgeGlowCapsuleContainer for a dark smoked-glass pill with luminous running neon
 * perimeter glow and high-contrast crisp white typography regardless of theme.
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
    val statusBadgeColor = when {
        caller.isOwner -> AmberPriority
        isSilenced -> Color(0xFF94A3B8)
        else -> Color(0xFF67E8F9)
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
        cornerRadius = 48.dp,
        borderWidth = 2.5.dp,
        haloMargin = 16.dp,
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
                        color = Color.White,
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
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Luminous Decline & Accept circular touch buttons (min 48dp each)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlowingCallActionCircle(
                    baseColor = CrimsonDecline,
                    highlightColor = Color(0xFFFF6B81),
                    onClick = onDeclineClick,
                    modifier = Modifier.testTag("top_banner_decline_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline call",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                GlowingCallActionCircle(
                    baseColor = EmeraldAccept,
                    highlightColor = Color(0xFF4ADE80),
                    onClick = onAcceptClick,
                    modifier = Modifier.testTag("top_banner_accept_btn")
                ) {
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

@Composable
private fun GlowingCallActionCircle(
    baseColor: Color,
    highlightColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.background(
                brush = Brush.radialGradient(
                    colors = listOf(highlightColor, baseColor, baseColor.copy(alpha = 0.92f)),
                    center = Offset(18f, 16f),
                    radius = 90f
                )
            )
        ) {
            iconContent()
        }
    }
}
