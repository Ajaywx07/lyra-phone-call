package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CallStateEnum
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.LyraTheme

@Composable
fun LyraOrb(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    state: CallStateEnum = CallStateEnum.IDLE,
    customStatusText: String? = null,
    isOffline: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lyra_orb_anim")

    // Slow pulsing breath animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Outer aura wave animation
    val outerWave by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_wave"
    )

    // Glow alpha animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val colors = LyraTheme.colors

    // Color coordination based on state
    val (corePrimary, coreSecondary, glowColor) = when {
        isOffline -> Triple(Color(0xFF64748B), Color(0xFF475569), Color(0xFF94A3B8))
        state == CallStateEnum.ACTIVE -> Triple(EmeraldAccept, colors.primary, EmeraldGlow)
        state == CallStateEnum.RINGING -> Triple(colors.primary, colors.secondary, colors.primaryGlow)
        state == CallStateEnum.DISCONNECTED -> Triple(colors.secondary, colors.primary, colors.secondaryGlow)
        else -> Triple(colors.primary, colors.secondary, colors.primaryGlow)
    }

    val statusText = customStatusText ?: when {
        isOffline -> "LYRA OFFLINE MODE"
        state == CallStateEnum.RINGING -> "Incoming call detected..."
        state == CallStateEnum.ACTIVE -> "LYRA ASSISTING"
        state == CallStateEnum.DISCONNECTED -> "Call completed."
        else -> "Ready to assist"
    }

    Column(
        modifier = modifier.testTag("lyra_orb_container"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = this.size.minDimension / 2f * 0.70f

                // 1. Outer dynamic ripple aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.25f * glowAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * outerWave * 1.3f
                    ),
                    radius = radius * outerWave * 1.3f,
                    center = center
                )

                // 2. Orbital tech ring
                drawCircle(
                    color = corePrimary.copy(alpha = 0.40f * glowAlpha),
                    radius = radius * 1.15f * pulseScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. Counter orbital dashed ring
                drawCircle(
                    color = coreSecondary.copy(alpha = 0.35f),
                    radius = radius * 1.25f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // 4. Core glowing orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            corePrimary.copy(alpha = 0.85f),
                            coreSecondary.copy(alpha = 0.70f),
                            Color(0xFF080914)
                        ),
                        center = center.copy(y = center.y - radius * 0.15f),
                        radius = radius * pulseScale
                    ),
                    radius = radius * pulseScale,
                    center = center
                )

                // 5. Specular highlight for glass orb depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.Transparent
                        ),
                        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.35f),
                        radius = radius * 0.45f
                    ),
                    radius = radius * 0.45f,
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.35f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "LYRA AI",
            color = corePrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = statusText,
            color = if (isOffline) colors.textMuted else colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
