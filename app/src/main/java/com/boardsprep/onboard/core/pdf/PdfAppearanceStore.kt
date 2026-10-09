package com.boardsprep.onboard.core.pdf

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists reader appearance settings (theme, fit, mode, brightness, spacing)
 * across sessions using a small [SharedPreferences] store. Kept deliberately
 * simple — no DataStore dependency — to match the rest of the app's
 * persistence style and avoid adding a new asynchronous source of truth.
 *
 * Per-document overrides are stored under keys prefixed with the documentId so
 * that appearance "remembers" the last choice per textbook without disturbing
 * the global default.
 */
class PdfAppearanceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("onboard_pdf_appearance", Context.MODE_PRIVATE)

    fun loadGlobalDefault(): PdfAppearanceSettings {
        return PdfAppearanceSettings(
            canvasTheme = runCatching {
                PdfCanvasTheme.valueOf(prefs.getString(KEY_GLOBAL_THEME, DefaultPdfAppearance.canvasTheme.name)!!)
            }.getOrDefault(DefaultPdfAppearance.canvasTheme),
            fitMode = runCatching {
                PdfFitMode.valueOf(prefs.getString(KEY_GLOBAL_FIT, DefaultPdfAppearance.fitMode.name)!!)
            }.getOrDefault(DefaultPdfAppearance.fitMode),
            readingMode = runCatching {
                PdfReadingMode.valueOf(prefs.getString(KEY_GLOBAL_MODE, DefaultPdfAppearance.readingMode.name)!!)
            }.getOrDefault(DefaultPdfAppearance.readingMode),
            fullscreen = prefs.getBoolean(KEY_GLOBAL_FULLSCREEN, DefaultPdfAppearance.fullscreen),
            brightnessOverride = prefs.getFloatOrNull(KEY_GLOBAL_BRIGHTNESS),
            pageSpacingDp = prefs.getInt(KEY_GLOBAL_SPACING, DefaultPdfAppearance.pageSpacingDp)
        )
    }

    fun saveGlobalDefault(settings: PdfAppearanceSettings) {
        prefs.edit()
            .putString(KEY_GLOBAL_THEME, settings.canvasTheme.name)
            .putString(KEY_GLOBAL_FIT, settings.fitMode.name)
            .putString(KEY_GLOBAL_MODE, settings.readingMode.name)
            .putBoolean(KEY_GLOBAL_FULLSCREEN, settings.fullscreen)
            .putFloatOrNull(KEY_GLOBAL_BRIGHTNESS, settings.brightnessOverride)
            .putInt(KEY_GLOBAL_SPACING, settings.pageSpacingDp)
            .apply()
    }

    private fun SharedPreferences.getFloatOrNull(key: String): Float? =
        if (contains(key)) getFloat(key, 0f) else null

    private fun SharedPreferences.Editor.putFloatOrNull(key: String, value: Float?): SharedPreferences.Editor =
        if (value == null) remove(key) else putFloat(key, value)

    companion object {
        private const val KEY_GLOBAL_THEME = "global_canvasTheme"
        private const val KEY_GLOBAL_FIT = "global_fitMode"
        private const val KEY_GLOBAL_MODE = "global_readingMode"
        private const val KEY_GLOBAL_FULLSCREEN = "global_fullscreen"
        private const val KEY_GLOBAL_BRIGHTNESS = "global_brightness"
        private const val KEY_GLOBAL_SPACING = "global_spacing"
    }
}
