package com.boardsprep.onboard.core.glass

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boardsprep.onboard.core.glass.backdrop.Backdrop
import com.boardsprep.onboard.core.glass.backdrop.drawBackdrop
import com.boardsprep.onboard.core.glass.backdrop.effects.blur
import com.boardsprep.onboard.core.glass.backdrop.effects.colorControls
import com.boardsprep.onboard.core.glass.backdrop.effects.lens
import com.boardsprep.onboard.core.glass.backdrop.highlight.Highlight
import com.boardsprep.onboard.core.glass.backdrop.highlight.HighlightElement
import com.boardsprep.onboard.core.glass.backdrop.internal.ShapeProvider
import com.boardsprep.onboard.core.glass.backdrop.shadow.Shadow

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Hardware RenderEffect support available from Android 12 (API 31). */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Specular hairline edge constants matching USMUSIC V2 / ClearPDF aesthetic. */
val GLASS_EDGE_WIDTH = 0.5.dp
val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.14f)

/**
 * High-contrast ink color for glyphs and labels sitting on glass surfaces.
 * Evaluates background luminance so icons/text never wash out against dark or light content.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFF1D1B20) else Color(0xFFF3F4F6)

/**
 * Inverted selection indicator scrim color for glass surfaces.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f)

/**
 * Full-fidelity Liquid Glass surface:
 * If [LocalAppBackdrop] is available, optical backdrop sampling with AGSL lens refraction,
 * gaussian blur, vibrancy, directional highlight, and inner depth shadow is applied.
 * When disabled (default), renders lightweight, zero-overhead standard Material 3 Expressive surfaces.
 */
fun Modifier.liquidGlass(
    shape: CornerBasedShape = RoundedCornerShape(20.dp),
    containerColorOverride: Color? = null,
    borderWidth: Dp = GLASS_EDGE_WIDTH,
    enabled: Boolean? = null,
    styleOverride: GlassStyle? = null
): Modifier = composed {
    val isEnabled = (enabled ?: LocalGlassEnabled.current) && isGlassSupported()
    val style = styleOverride ?: LocalGlassTheme.current
    val isDark = isSystemInDarkTheme()

    if (!isEnabled) {
        // Zero-overhead standard M3 fallback
        val defaultBg = containerColorOverride ?: MaterialTheme.colorScheme.surface
        return@composed this
            .clip(shape)
            .background(defaultBg, shape)
            .border(
                borderWidth,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.40f),
                shape
            )
    }

    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val blurRadiusDp = (10f * style.blur).coerceIn(1f, 40f)
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * 0.33f
    val lensHeightPx = with(density) { (style.refractionHeight * 48f).dp.toPx() } * 0.33f
    val lensAmountPx = with(density) { (style.refractionAmount * 48f).dp.toPx() } * 0.33f

    val surfaceTintColor = containerColorOverride ?: if (isDark) {
        Color(0xFF10121A)
    } else {
        Color(0xFFFAF8FE)
    }
    val surfaceAlpha = (0.55f * style.tintAlpha).coerceIn(0.12f, 0.95f)

    if (backdrop != null) {
        return@composed this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = 1f + 0.5f * style.saturation)
                blur(blurPx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && style.refractionAmount > 0.05f) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = style.depthEffect,
                        chromaticAberration = style.chromaticAberration,
                    )
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow.Default },
            onDrawSurface = {
                drawRect(color = surfaceTintColor.copy(alpha = surfaceAlpha), size = size)
            },
            backdropScale = 0.33f,
        ).border(borderWidth, GLASS_EDGE_COLOR, shape)
    }

    // Direct RenderEffect Fallback if backdrop provider is absent
    val topSheen = Color.White.copy(alpha = (0.50f * style.highlight).coerceIn(0f, 1f))
    val midSheen = (if (isDark) Color.White else Color(0xFF6750A4)).copy(alpha = (0.18f * style.highlight).coerceIn(0f, 1f))
    val bottomDepth = Color.Black.copy(alpha = (0.22f * style.highlight).coerceIn(0f, 1f))
    val specularBrush = Brush.verticalGradient(listOf(topSheen, midSheen, bottomDepth))

    var modifier: Modifier = this.clip(shape)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style.blur > 0.05f) {
        val blurPxDirect = (14f * style.blur).coerceIn(1f, 80f)
        modifier = modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                blurPxDirect,
                blurPxDirect,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }

    modifier
        .background(surfaceTintColor.copy(alpha = surfaceAlpha), shape)
        .border(borderWidth, specularBrush, shape)
}

/**
 * Lightweight Liquid Glass surface designed for interactive controls (Back buttons,
 * action capsules, filter chips, options). Retains directional specular highlight,
 * translucent tint, and hairline rim with zero heavy blur overhead.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color = MaterialTheme.colorScheme.surface,
    styleOverride: GlassStyle? = null
): Modifier {
    val useGlass = LocalGlassEnabled.current && isGlassSupported()
    val style = styleOverride ?: LocalGlassTheme.current
    val isDark = isSystemInDarkTheme()

    if (!useGlass) {
        return this
            .clip(shape)
            .background(fallbackColor, shape)
            .border(
                GLASS_EDGE_WIDTH,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.20f else 0.35f),
                shape
            )
    }

    val glassTint = if (isDark) Color(0xFF12141C) else Color(0xFFFAF8FE)
    val alpha = (0.60f * style.tintAlpha).coerceIn(0.15f, 0.95f)
    val shapeProvider = ShapeProvider { shape }

    return this
        .clip(shape)
        .background(glassTint.copy(alpha = alpha), shape)
        .then(
            HighlightElement(
                shapeProvider = shapeProvider,
                highlight = { Highlight.Default },
            )
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Renders the blurred translucent background layer for floating docks and cards,
 * ensuring foreground icons and typography remain completely razor-sharp.
 */
fun Modifier.liquidGlassDockBackground(
    shape: Shape = RoundedCornerShape(32.dp),
    styleOverride: GlassStyle? = null
): Modifier = composed {
    val style = styleOverride ?: LocalGlassTheme.current
    val isDark = isSystemInDarkTheme()

    val dockBaseAlpha = if (isDark) 0.52f else 0.62f
    val computedDockAlpha = (dockBaseAlpha * style.tintAlpha).coerceIn(0.15f, 0.90f)
    val dockColor = (if (isDark) Color(0xFF0F1118) else Color(0xFFFBF9FF)).copy(alpha = computedDockAlpha)

    var modifier: Modifier = this.clip(shape)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style.blur > 0.05f) {
        val blurRadiusPx = (16f * style.blur).coerceIn(1f, 90f)
        modifier = modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                blurRadiusPx,
                blurRadiusPx,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }

    modifier.background(dockColor, shape)
}
