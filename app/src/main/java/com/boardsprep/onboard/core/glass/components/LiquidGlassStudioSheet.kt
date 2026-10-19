package com.boardsprep.onboard.core.glass.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.GlassSettings
import com.boardsprep.onboard.core.glass.GlassStyle

/**
 * Interactive Liquid Glass Customization Studio.
 * Allows fine-tuning of optics, surface tints, specular rims, and presets
 * matching ClearPDF's physical glass framework.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassStudioSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isEnabled = GlassSettings.isGlassEnabled
    val currentStyle = GlassSettings.style
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Liquid Glass Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "CLEARPDF OPTICS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Physics-based optical tuning & presets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Master Enable Toggle
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(
                    1.dp,
                    if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Liquid Glass UI Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnabled) "Active across Dock & Surfaces" else "Normal M3 UI Active (Default)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            GlassSettings.setEnabled(context, checked)
                        }
                    )
                }
            }

            // Interactive Live Preview Swatch
            GlassPreviewSwatch(
                style = currentStyle,
                isGlassActive = isEnabled
            )

            // Tuning Controls (Visible when Glass is enabled)
            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "OPTICAL PRESETS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip(
                                label = "Default",
                                isSelected = currentStyle == GlassStyle.Default,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    GlassSettings.updateStyle(context, GlassStyle.Default)
                                }
                            )
                            PresetChip(
                                label = "Ultra-Readable",
                                isSelected = currentStyle == GlassStyle.Readable,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    GlassSettings.updateStyle(context, GlassStyle.Readable)
                                }
                            )
                            PresetChip(
                                label = "Prismatic Crystal",
                                isSelected = currentStyle == GlassStyle.Crystal,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    GlassSettings.updateStyle(context, GlassStyle.Crystal)
                                }
                            )
                            PresetChip(
                                label = "Performance",
                                isSelected = currentStyle == GlassStyle.Performance,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    GlassSettings.updateStyle(context, GlassStyle.Performance)
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Optics Sliders
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "FINE-TUNED CONTROLS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        GlassSliderRow(
                            label = "Frost Blur Intensity",
                            value = currentStyle.blur,
                            range = 0f..3f,
                            formatter = { "${"%.2f".format(it)}x" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(blur = it)) }
                        )

                        GlassSliderRow(
                            label = "Refraction Height",
                            value = currentStyle.refractionHeight,
                            range = 0f..2f,
                            formatter = { "${"%.2f".format(it)}x" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(refractionHeight = it)) }
                        )

                        GlassSliderRow(
                            label = "Refraction Amount",
                            value = currentStyle.refractionAmount,
                            range = 0f..2f,
                            formatter = { "${"%.2f".format(it)}x" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(refractionAmount = it)) }
                        )

                        GlassSliderRow(
                            label = "Surface Tint Opacity",
                            value = currentStyle.tintAlpha,
                            range = 0f..2.5f,
                            formatter = { "${"%.2f".format(it)}x" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(tintAlpha = it)) }
                        )

                        GlassSliderRow(
                            label = "Vibrancy & Saturation",
                            value = currentStyle.saturation,
                            range = 0.5f..2.5f,
                            formatter = { "${"%.2f".format(it)}x" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(saturation = it)) }
                        )

                        GlassSliderRow(
                            label = "Specular Highlight Rim",
                            value = currentStyle.highlight,
                            range = 0f..1f,
                            formatter = { "${(it * 100).toInt()}%" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(highlight = it)) }
                        )

                        GlassSliderRow(
                            label = "Corner Curvature",
                            value = currentStyle.corners,
                            range = 0.4f..1.4f,
                            formatter = { "${"%.2f".format(it)}x" },
                            onValueChange = { GlassSettings.updateStyle(context, currentStyle.copy(corners = it)) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Advanced Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ADVANCED OPTICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        GlassToggleRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Chromatic Aberration",
                            desc = "Prismatic RGB color dispersion at refractive corners",
                            checked = currentStyle.chromaticAberration,
                            onCheckedChange = { GlassSettings.updateStyle(context, currentStyle.copy(chromaticAberration = it)) }
                        )

                        GlassToggleRow(
                            icon = Icons.Rounded.Layers,
                            title = "3D Depth Lens Effect",
                            desc = "Simulates lens curvature distortion on backdrop content",
                            checked = currentStyle.depthEffect,
                            onCheckedChange = { GlassSettings.updateStyle(context, currentStyle.copy(depthEffect = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Reset to Defaults Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            GlassSettings.resetToDefaults(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Glass to Defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun GlassSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatter: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatter(value),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GlassToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
