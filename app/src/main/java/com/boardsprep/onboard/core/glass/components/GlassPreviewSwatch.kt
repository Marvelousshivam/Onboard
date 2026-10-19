package com.boardsprep.onboard.core.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.GlassStyle
import com.boardsprep.onboard.core.glass.liquidGlass
import com.boardsprep.onboard.core.glass.liquidGlassDockBackground

/**
 * Live interactive preview swatch showing real-time Liquid Glass refraction,
 * frost blur, specular highlight, and tint over a rich educational backdrop.
 */
@Composable
fun GlassPreviewSwatch(
    style: GlassStyle,
    isGlassActive: Boolean,
    modifier: Modifier = Modifier
) {
    val cornerRadius = (24.dp * style.corners).coerceIn(12.dp, 36.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        // Vivid Multi-Gradient Educational Backdrop (Simulating complex app content behind glass)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6750A4), // M3 Primary Purple
                            Color(0xFF00B0FF), // Vivid Electric Cyan
                            Color(0xFFFF4081), // Neon Pink
                            Color(0xFFFF9100), // Amber Gold
                            Color(0xFF00C853)  // Vibrant Mint
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CBSE CLASS 12 • PHYSICS & CHEMISTRY • 2026-27",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    text = "Maxwell's Equations • Wave Optics • Biomolecules",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Text(
                    text = "SQP 2027 Blueprint • 3D Derivations • One-Shots",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.80f)
                )
            }
        }

        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val chipInk = if (isDark) Color.White else Color(0xFF1C1B1F)
        val chipSubInk = if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF49454E)
        val iconBg = if (isDark) Color.White.copy(alpha = 0.20f) else Color(0xFF6750A4).copy(alpha = 0.16f)
        val iconTint = if (isDark) Color.White else Color(0xFF6750A4)

        val chipShape = RoundedCornerShape((18.dp * style.corners).coerceIn(8.dp, 28.dp))

        val topRim = Color.White.copy(alpha = (0.75f * style.highlight).coerceIn(0f, 1f))
        val bottomRim = (if (isDark) Color.Black else Color(0xFF6750A4)).copy(alpha = (0.25f * style.highlight).coerceIn(0f, 1f))
        val rimBrush = Brush.verticalGradient(listOf(topRim, topRim.copy(alpha = 0.25f), bottomRim))

        // Live Floating Glass Chip (Dual-Layer: frosted backing + razor-sharp foreground text)
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isGlassActive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .liquidGlassDockBackground(shape = chipShape, styleOverride = style)
                )
            }

            Surface(
                shape = chipShape,
                color = if (isGlassActive) (if (isDark) Color(0xFF1E172B) else Color(0xFFFAF8FE)).copy(alpha = (0.75f * style.tintAlpha).coerceIn(0.20f, 0.95f))
                else MaterialTheme.colorScheme.surface,
                border = if (isGlassActive) androidx.compose.foundation.BorderStroke(1.2.dp, rimBrush)
                else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = if (isGlassActive) 0.dp else 4.dp,
                shadowElevation = if (isGlassActive) 8.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = iconBg,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isGlassActive) Icons.Rounded.AutoAwesome else Icons.Rounded.TouchApp,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isGlassActive) "Liquid Glass Active" else "Normal M3 Preview",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = chipInk
                        )
                        Text(
                            text = if (isGlassActive) "Blur: ${"%.1f".format(style.blur)}x • Tint: ${"%.1f".format(style.tintAlpha)}x" else "Glass Mode Disabled",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = chipSubInk
                        )
                    }
                }
            }
        }
    }
}
