package com.boardsprep.onboard.core.glass

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Liquid Glass physics tuning model inspired by ClearPDF & Kyant's backdrop engine.
 * Every value is a multiplier or normalized float:
 * 1.0f = baseline calibrated look.
 */
@Immutable
data class GlassStyle(
    val blur: Float = 1.0f,
    val refractionHeight: Float = 1.0f,
    val refractionAmount: Float = 1.0f,
    val tintAlpha: Float = 1.0f,
    val saturation: Float = 1.5f,
    val brightness: Float = 0.0f,
    val highlight: Float = 1.0f,
    val corners: Float = 1.0f,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false
) {
    companion object {
        val Default = GlassStyle()

        /** Heavier frost & denser tint: pristine legibility over study notes & syllabi. */
        val Readable = GlassStyle(
            blur = 2.4f,
            refractionHeight = 0.8f,
            refractionAmount = 0.6f,
            tintAlpha = 1.8f,
            saturation = 1.3f,
            highlight = 0.85f,
            corners = 1.0f
        )

        /** Clear, strongly bent glass with a prismatic RGB dispersion sheen. */
        val Crystal = GlassStyle(
            blur = 0.35f,
            refractionHeight = 1.5f,
            refractionAmount = 1.6f,
            tintAlpha = 0.65f,
            saturation = 1.8f,
            highlight = 1.0f,
            corners = 1.1f,
            depthEffect = true,
            chromaticAberration = true
        )

        /** Zero blur or lens passes; ultra-lightweight translucent acrylic for battery efficiency. */
        val Performance = GlassStyle(
            blur = 0.0f,
            refractionHeight = 0.0f,
            refractionAmount = 0.0f,
            tintAlpha = 1.4f,
            saturation = 1.0f,
            highlight = 0.7f,
            corners = 1.0f,
            depthEffect = false,
            chromaticAberration = false
        )
    }
}

/**
 * App-wide Glass Settings held in Compose snapshot state for instant live updates.
 * Default is STRICTLY DISABLED (Normal Material 3 Expressive UI is default).
 */
object GlassSettings {
    private const val PREFS_NAME = "boards_glass_prefs"
    private const val KEY_ENABLED = "glass_enabled"
    private const val KEY_BLUR = "glass_blur"
    private const val KEY_REFR_H = "glass_refr_h"
    private const val KEY_REFR_A = "glass_refr_a"
    private const val KEY_TINT = "glass_tint"
    private const val KEY_SAT = "glass_sat"
    private const val KEY_BRIGHT = "glass_bright"
    private const val KEY_HIGHLIGHT = "glass_highlight"
    private const val KEY_CORNERS = "glass_corners"
    private const val KEY_DEPTH = "glass_depth"
    private const val KEY_CHROMATIC = "glass_chromatic"

    // Default: false (Normal UI is default)
    var isGlassEnabled by mutableStateOf(false)
    var style by mutableStateOf(GlassStyle.Default)

    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        isLoaded = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val d = GlassStyle.Default

        isGlassEnabled = prefs.getBoolean(KEY_ENABLED, false)
        style = GlassStyle(
            blur = prefs.getFloat(KEY_BLUR, d.blur),
            refractionHeight = prefs.getFloat(KEY_REFR_H, d.refractionHeight),
            refractionAmount = prefs.getFloat(KEY_REFR_A, d.refractionAmount),
            tintAlpha = prefs.getFloat(KEY_TINT, d.tintAlpha),
            saturation = prefs.getFloat(KEY_SAT, d.saturation),
            brightness = prefs.getFloat(KEY_BRIGHT, d.brightness),
            highlight = prefs.getFloat(KEY_HIGHLIGHT, d.highlight),
            corners = prefs.getFloat(KEY_CORNERS, d.corners),
            depthEffect = prefs.getBoolean(KEY_DEPTH, d.depthEffect),
            chromaticAberration = prefs.getBoolean(KEY_CHROMATIC, d.chromaticAberration)
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        isGlassEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun updateStyle(context: Context, newStyle: GlassStyle) {
        style = newStyle
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_BLUR, newStyle.blur)
            .putFloat(KEY_REFR_H, newStyle.refractionHeight)
            .putFloat(KEY_REFR_A, newStyle.refractionAmount)
            .putFloat(KEY_TINT, newStyle.tintAlpha)
            .putFloat(KEY_SAT, newStyle.saturation)
            .putFloat(KEY_BRIGHT, newStyle.brightness)
            .putFloat(KEY_HIGHLIGHT, newStyle.highlight)
            .putFloat(KEY_CORNERS, newStyle.corners)
            .putBoolean(KEY_DEPTH, newStyle.depthEffect)
            .putBoolean(KEY_CHROMATIC, newStyle.chromaticAberration)
            .apply()
    }

    fun resetToDefaults(context: Context) {
        updateStyle(context, GlassStyle.Default)
    }
}

val LocalGlassTheme = compositionLocalOf { GlassSettings.style }
val LocalGlassEnabled = compositionLocalOf { GlassSettings.isGlassEnabled }
