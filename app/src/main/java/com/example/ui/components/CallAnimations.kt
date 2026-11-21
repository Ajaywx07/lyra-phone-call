package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.domain.model.CallAnimationStyle
import com.example.ui.theme.LyraTheme

@Composable
fun CallBackgroundAnimation(
    style: CallAnimationStyle,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LyraTheme.colors
    val effectiveAccent = accentColor ?: colors.primary
    val transition = rememberInfiniteTransition(label = "call_bg_anim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.38f)
        val minDim = size.minDimension

        when (style) {
            CallAnimationStyle.ORB_PULSE -> {
                val radius = minDim * 0.55f * breathe
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            effectiveAccent.copy(alpha = 0.24f),
                            colors.secondary.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    center = center,
                    radius = radius
                )
            }

            CallAnimationStyle.RIPPLE_RINGS -> {
                val ringCount = 3
                for (i in 0 until ringCount) {
                    val phase = (progress + i.toFloat() / ringCount) % 1f
                    val radius = minDim * (0.14f + phase * 0.58f)
                    val alpha = (1f - phase) * 0.42f
                    drawCircle(
                        color = effectiveAccent.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }
            }

            CallAnimationStyle.GRADIENT_AURORA -> {
                val offsetShift = (breathe - 0.9f) * size.height * 0.35f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            effectiveAccent.copy(alpha = 0.22f),
                            colors.secondary.copy(alpha = 0.20f),
                            colors.acceptColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        start = Offset(0f, offsetShift),
                        end = Offset(size.width, size.height - offsetShift)
                    )
                )
            }

            CallAnimationStyle.SLIDE_GLOW -> {
                val beamY = size.height * progress
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            effectiveAccent.copy(alpha = 0.22f),
                            colors.secondary.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        startY = (beamY - size.height * 0.22f),
                        endY = (beamY + size.height * 0.22f)
                    )
                )
            }
        }
    }
}
