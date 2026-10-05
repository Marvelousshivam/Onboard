package com.boardsprep.onboard.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = MdPrimaryLight,
    onPrimary = MdOnPrimaryLight,
    primaryContainer = MdPrimaryContainerLight,
    onPrimaryContainer = MdOnPrimaryContainerLight,
    secondary = MdSecondaryLight,
    onSecondary = MdOnSecondaryLight,
    secondaryContainer = MdSecondaryContainerLight,
    onSecondaryContainer = MdOnSecondaryContainerLight,
    tertiary = MdTertiaryLight,
    onTertiary = MdOnTertiaryLight,
    tertiaryContainer = MdTertiaryContainerLight,
    onTertiaryContainer = MdOnTertiaryContainerLight,
    error = MdErrorLight,
    onError = MdOnErrorLight,
    errorContainer = MdErrorContainerLight,
    onErrorContainer = MdOnErrorContainerLight,
    background = MdBackgroundLight,
    onBackground = MdOnBackgroundLight,
    surface = MdSurfaceLight,
    onSurface = MdOnSurfaceLight,
    surfaceVariant = MdSurfaceVariantLight,
    onSurfaceVariant = MdOnSurfaceVariantLight,
    outline = MdOutlineLight,
    outlineVariant = MdOutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = MdPrimaryDark,
    onPrimary = MdOnPrimaryDark,
    primaryContainer = MdPrimaryContainerDark,
    onPrimaryContainer = MdOnPrimaryContainerDark,
    secondary = MdSecondaryDark,
    onSecondary = MdOnSecondaryDark,
    secondaryContainer = MdSecondaryContainerDark,
    onSecondaryContainer = MdOnSecondaryContainerDark,
    tertiary = MdTertiaryDark,
    onTertiary = MdOnTertiaryDark,
    tertiaryContainer = MdTertiaryContainerDark,
    onTertiaryContainer = MdOnTertiaryContainerDark,
    error = MdErrorDark,
    onError = MdOnErrorDark,
    errorContainer = MdErrorContainerDark,
    onErrorContainer = MdOnErrorContainerDark,
    background = MdBackgroundDark,
    onBackground = MdOnBackgroundDark,
    surface = MdSurfaceDark,
    onSurface = MdOnSurfaceDark,
    surfaceVariant = MdSurfaceVariantDark,
    onSurfaceVariant = MdOnSurfaceVariantDark,
    outline = MdOutlineDark,
    outlineVariant = MdOutlineVariantDark
)

@Composable
fun OnboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Curated aesthetic brand palette by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
