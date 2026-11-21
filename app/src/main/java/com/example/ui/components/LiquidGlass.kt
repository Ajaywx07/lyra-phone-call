package com.example.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalInsideGlassSurface
import com.example.ui.theme.LyraTheme
import kotlin.math.min

/**
 * AGSL Signed-Distance-Field (SDF) Liquid Glass shader inspired by iOS 26 / QWEA0/Liquid-Glass-Android.
 * Computes rounded-box SDF distance, inward refraction normals along the curved bevel,
 * subtle RGB chromatic dispersion, and directional Fresnel specular highlights.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader composable;
uniform float2 uSize;
uniform float uCornerRadius;
uniform float uRefraction;
uniform float uDispersion;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 calcSdfNormal(float2 p, float2 b, float r) {
    float eps = 1.5;
    float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
    float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
    float2 grad = float2(dx, dy);
    float len = length(grad);
    return (len > 0.0001) ? (grad / len) : float2(0.0, -1.0);
}

half4 main(float2 fragCoord) {
    float2 halfSize = max(uSize * 0.5, float2(1.0));
    float2 p = fragCoord - halfSize;
    float r = min(uCornerRadius, min(halfSize.x, halfSize.y));
    float d = sdRoundedBox(p, halfSize, r);

    if (d > 1.0) {
        return composable.eval(fragCoord);
    }

    float rimZone = max(14.0, r * 0.65);
    float bevel = smoothstep(-rimZone, 0.0, d);
    float2 normal = calcSdfNormal(p, halfSize, r);

    float2 refractVec = -normal * (bevel * uRefraction);
    float dispScale = bevel * uDispersion;

    half4 base = composable.eval(clamp(fragCoord + refractVec, float2(0.0), uSize));
    half rChan = composable.eval(clamp(fragCoord + refractVec * (1.0 + dispScale), float2(0.0), uSize)).r;
    half bChan = composable.eval(clamp(fragCoord + refractVec * (1.0 - dispScale), float2(0.0), uSize)).b;

    half3 glassRgb = half3(rChan, base.g, bChan);

    // Top-left & bottom-right Fresnel specular catchlight along the SDF lip
    float2 lightDir = normalize(float2(-0.65, -0.76));
    float rimSpec = pow(clamp(dot(-normal, lightDir) * 0.5 + 0.5, 0.0, 1.0), 2.2) * bevel;
    float counterSpec = pow(clamp(dot(normal, lightDir) * 0.5 + 0.5, 0.0, 1.0), 3.0) * bevel;
    glassRgb += half3(0.18, 0.24, 0.30) * rimSpec;
    glassRgb += half3(0.08, 0.14, 0.24) * counterSpec;

    return half4(glassRgb, base.a);
}
"""

@Composable
private fun rememberLiquidGlassRenderEffect(
    size: IntSize,
    cornerRadiusPx: Float,
    refractionPx: Float = 8f,
    dispersion: Float = 0.22f
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (size.width <= 0 || size.height <= 0) return null

    return remember(size.width, size.height, cornerRadiusPx, refractionPx, dispersion) {
        runCatching {
            val shader = RuntimeShader(LIQUID_GLASS_AGSL)
            shader.setFloatUniform("uSize", size.width.toFloat(), size.height.toFloat())
            shader.setFloatUniform("uCornerRadius", cornerRadiusPx)
            shader.setFloatUniform("uRefraction", refractionPx)
            shader.setFloatUniform("uDispersion", dispersion)
            RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
        }.getOrNull()
    }
}

/**
 * Draws multi-layer iOS 26 Liquid Glass surface styling (frosted translucent body,
 * diagonal caustic sheen, inner glass bevel, and chromatic-dispersion prism rim)
 * without altering layout size or blurring child foreground typography.
 */
fun Modifier.liquidGlassPlate(
    cornerRadius: Dp = 24.dp,
    tintColor: Color? = null,
    accentGlow: Color? = null,
    rimWidth: Dp = 1.1.dp,
    enableAgslRefraction: Boolean = true,
    subtle: Boolean = false
): Modifier = composed {
    val colors = LyraTheme.colors
    val insideGlass = LocalInsideGlassSurface.current
    val density = LocalDensity.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val resolvedBase = tintColor ?: when {
        colors.isGlassStyle && insideGlass -> Color(0x881D2E4B)
        colors.isGlassStyle -> Color(0x66132036)
        else -> colors.glassSurface
    }
    val topSheenAlpha = if (colors.isGlassStyle) (if (subtle) 0.12f else 0.22f) else 0.08f
    val primaryPrism = accentGlow ?: colors.primary
    val secondaryPrism = colors.secondary

    val cornerPx = with(density) { cornerRadius.toPx() }
    val renderEffect = if (enableAgslRefraction && colors.isGlassStyle) {
        rememberLiquidGlassRenderEffect(
            size = boxSize,
            cornerRadiusPx = cornerPx,
            refractionPx = if (subtle) 5f else 9f,
            dispersion = if (subtle) 0.14f else 0.24f
        )
    } else {
        null
    }

    this
        .onSizeChanged { boxSize = it }
        .then(
            if (renderEffect != null) {
                Modifier.graphicsLayer {
                    this.renderEffect = renderEffect
                }
            } else {
                Modifier
            }
        )
        .drawWithCache {
            val r = min(cornerRadius.toPx(), min(size.width, size.height) * 0.5f)
            val corner = CornerRadius(r, r)
            val strokePx = rimWidth.toPx()
            val insetStrokePx = 0.85.dp.toPx()

            val bodyBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = topSheenAlpha).compositeOverFast(resolvedBase),
                    0.38f to resolvedBase,
                    0.78f to resolvedBase.copy(alpha = (resolvedBase.alpha * 0.94f).coerceIn(0.18f, 0.96f)),
                    1.0f to primaryPrism.copy(alpha = if (colors.isGlassStyle) 0.14f else 0.06f).compositeOverFast(resolvedBase)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )

            // Diagonal top-band specular caustic reflection across the upper half of the glass
            val causticBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = if (colors.isGlassStyle) (if (subtle) 0.11f else 0.20f) else 0.07f),
                0.35f to Color.White.copy(alpha = if (colors.isGlassStyle) 0.05f else 0.02f),
                0.65f to Color.Transparent,
                1.0f to primaryPrism.copy(alpha = if (colors.isGlassStyle) 0.06f else 0.0f),
                startY = 0f,
                endY = size.height
            )

            // Chromatic-dispersion prism rim (icy white -> sky cyan -> iris violet -> warm prism rose -> sky cyan)
            val rimBrush = if (colors.isGlassStyle) {
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = if (subtle) 0.52f else 0.78f),
                        0.28f to primaryPrism.copy(alpha = if (subtle) 0.42f else 0.68f),
                        0.60f to secondaryPrism.copy(alpha = if (subtle) 0.34f else 0.54f),
                        0.82f to Color(0xFFF472B6).copy(alpha = if (subtle) 0.22f else 0.36f),
                        1.0f to Color(0xFFE0F2FE).copy(alpha = if (subtle) 0.42f else 0.64f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        colors.glassBorder,
                        primaryPrism.copy(alpha = 0.35f)
                    )
                )
            }

            val innerLipBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = if (colors.isGlassStyle) 0.28f else 0.08f),
                0.5f to Color.Transparent,
                1.0f to primaryPrism.copy(alpha = if (colors.isGlassStyle) 0.16f else 0.04f),
                startY = 0f,
                endY = size.height
            )

            onDrawBehind {
                drawRoundRect(
                    brush = bodyBrush,
                    cornerRadius = corner
                )
                drawRoundRect(
                    brush = causticBrush,
                    cornerRadius = corner
                )
                drawRoundRect(
                    brush = innerLipBrush,
                    topLeft = Offset(insetStrokePx, insetStrokePx),
                    size = Size(
                        width = (size.width - insetStrokePx * 2f).coerceAtLeast(1f),
                        height = (size.height - insetStrokePx * 2f).coerceAtLeast(1f)
                    ),
                    cornerRadius = CornerRadius((r - insetStrokePx).coerceAtLeast(0f)),
                    style = Stroke(width = insetStrokePx)
                )
                drawRoundRect(
                    brush = rimBrush,
                    cornerRadius = corner,
                    style = Stroke(width = strokePx)
                )
            }
        }
}

private fun Color.compositeOverFast(background: Color): Color {
    val fgA = alpha
    val invA = 1f - fgA
    val outA = fgA + background.alpha * invA
    if (outA <= 0.0001f) return Color.Transparent
    return Color(
        red = (red * fgA + background.red * background.alpha * invA) / outA,
        green = (green * fgA + background.green * background.alpha * invA) / outA,
        blue = (blue * fgA + background.blue * background.alpha * invA) / outA,
        alpha = outA
    )
}

/**
 * Full-fidelity iOS Liquid Glass container wrapping foreground content inside a crisp clipped
 * shell while executing SDF refraction strictly on the background glass plate.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadius: Dp = 24.dp,
    tintColor: Color? = null,
    accentGlow: Color? = null,
    rimWidth: Dp = 1.1.dp,
    subtle: Boolean = false,
    enableAgslRefraction: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
    ) {
        // Glass plate isolated behind foreground text/icons
        Box(
            modifier = Modifier
                .matchParentSize()
                .liquidGlassPlate(
                    cornerRadius = cornerRadius,
                    tintColor = tintColor,
                    accentGlow = accentGlow,
                    rimWidth = rimWidth,
                    enableAgslRefraction = enableAgslRefraction,
                    subtle = subtle
                )
        )
        CompositionLocalProvider(LocalInsideGlassSurface provides true) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
