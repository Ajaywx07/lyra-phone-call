package com.example.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object LyraMotion {
    val SmoothDecel: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val StandardEase: Easing = FastOutSlowInEasing

    fun areSystemAnimationsEnabled(context: Context): Boolean {
        val animatorsEnabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        if (!animatorsEnabled) return false
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        return scale > 0f
    }

    fun scaledDuration(baseMs: Int, animationsEnabled: Boolean): Int {
        return if (animationsEnabled) baseMs else 0
    }

    fun <T> calmTween(
        durationMs: Int,
        animationsEnabled: Boolean,
        easing: Easing = SmoothDecel
    ): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(durationMs, animationsEnabled),
        easing = easing
    )
}

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { LyraMotion.areSystemAnimationsEnabled(context) }
}
