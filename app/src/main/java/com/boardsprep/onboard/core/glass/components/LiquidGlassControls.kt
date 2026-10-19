package com.boardsprep.onboard.core.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.GLASS_EDGE_COLOR
import com.boardsprep.onboard.core.glass.GLASS_EDGE_WIDTH
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.backdrop.highlight.Highlight
import com.boardsprep.onboard.core.glass.backdrop.highlight.HighlightElement
import com.boardsprep.onboard.core.glass.backdrop.internal.ShapeProvider
import com.boardsprep.onboard.core.glass.glassContentColor
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.lightweightLiquidGlass
import com.boardsprep.onboard.core.glass.liquidGlass
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Exact USMUSIC V2 spring spec for liquid glass travel and pop.
 * Damping ratio 0.72f settles on the second approach without diminishing vibration,
 * stiffness 320f makes the tap and arrival feel like one cohesive event (~1/3s).
 */
val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
const val GLASS_STRETCH = 0.16f
const val GLASS_SQUASH = 0.5f

/**
 * 44dp Circular Liquid Glass Back Affordance, matching USMUSIC V2 ArtworkPageBackButton.
 * Renders an uninhibited Material 3 circular ripple from exact touch coordinates over the glass lens,
 * with high-contrast ink color and seamless fallback to standard M3 Expressive button when glass mode is OFF.
 */
@Composable
fun LiquidGlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    enabled: Boolean = true
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val shape = CircleShape
    val haptic = LocalHapticFeedback.current
    val contentColor = if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(44.dp)
            .then(
                if (isGlass) Modifier.lightweightLiquidGlass(shape = shape)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f), shape)
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = if (isGlass) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary
                )
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Floating Capsule Pill housing trailing actions and options, inspired by USMUSIC V2 ArtworkPageActions.
 * Employs spring animated content resizing so options expand and collapse fluidly.
 */
@Composable
fun LiquidGlassActionPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val shape = CircleShape

    Row(
        modifier = modifier
            .height(44.dp)
            .then(
                if (isGlass) {
                    Modifier.lightweightLiquidGlass(shape = shape)
                } else {
                    Modifier
                }
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}

/**
 * Standardized 38dp Action Button for use inside [LiquidGlassActionPill] or standalone.
 * Uses native M3 IconButton to let circular ripple bloom freely across the glass surface.
 */
@Composable
fun LiquidGlassActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    badgeCount: Int? = null
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val shape = CircleShape
    val iconTint = tint ?: if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .clip(shape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Floating Liquid Glass Top Bar (Header) inspired by USMUSIC V2 FrostedTopBar.
 * Sits gracefully above scrolling content with circular back button, title, and actions.
 */
@Composable
fun LiquidGlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val titleColor = if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (onBack != null) {
                LiquidGlassBackButton(onClick = onBack)
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = titleColor.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (actions != null) {
            LiquidGlassActionPill {
                actions()
            }
        }
    }
}

/**
 * Interactive Liquid Glass Chip for filters, category chips, or tab options.
 * Provides authentic tactile ripple over glass, optional accent color, and haptic feedback.
 */
@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(100.dp)
    val haptic = LocalHapticFeedback.current

    val effectiveAccent = accentColor ?: MaterialTheme.colorScheme.primary

    val textColor by animateColorAsState(
        targetValue = when {
            selected && isGlass -> glassContentColor()
            selected -> MaterialTheme.colorScheme.onPrimary
            isGlass -> glassContentColor().copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chip_text_color"
    )

    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp
                ),
                color = textColor
            )
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    if (isGlass) {
        // Authentic Liquid Glass Chip with EXACTLY ONE crisp border and uninhibited tactile ripple
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    if (selected) effectiveAccent.copy(alpha = 0.28f)
                    else (if (isDark) Color(0xFF12141C) else Color(0xFFFAF8FE)).copy(alpha = 0.60f),
                    shape
                )
                .border(
                    width = if (selected) 1.dp else GLASS_EDGE_WIDTH,
                    color = if (selected) effectiveAccent.copy(alpha = 0.70f) else GLASS_EDGE_COLOR,
                    shape = shape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(
                        bounded = true,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
        ) {
            chipContent()
        }
    } else {
        // Standard Material 3 Expressive Chip Fallback
        val chipBg = if (selected) effectiveAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            shape = shape,
            color = chipBg,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
            interactionSource = interactionSource,
            modifier = modifier
        ) {
            chipContent()
        }
    }
}

data class LiquidGlassSegmentItem<T>(
    val key: T,
    val label: String,
    val icon: ImageVector? = null,
    val badge: String? = null
)

/**
 * Material 3 Expressive Liquid Glass Segmented Filter / Tab Row.
 * Features USMUSIC V2 traveling liquid glass selection pill with spring momentum
 * squash & stretch and uninhibited tactile ripples.
 */
@Composable
fun <T> LiquidGlassSegmentedRow(
    items: List<LiquidGlassSegmentItem<T>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val itemOffsets = remember { mutableStateMapOf<T, Float>() }
    val itemWidths = remember { mutableStateMapOf<T, Float>() }

    val targetOffset = itemOffsets[selectedKey] ?: 0f
    val targetWidth = itemWidths[selectedKey] ?: 0f

    val animOffset = remember { Animatable(0f) }
    val animWidth = remember { Animatable(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(selectedKey, targetOffset, targetWidth) {
        if (targetWidth > 0f) {
            if (!isInitialized) {
                animOffset.snapTo(targetOffset)
                animWidth.snapTo(targetWidth)
                isInitialized = true
            } else {
                launch { animOffset.animateTo(targetOffset, GlassSpring) }
                launch { animWidth.animateTo(targetWidth, GlassSpring) }
            }
            // Auto-scroll
            val center = targetOffset + targetWidth / 2f
            val halfViewport = (scrollState.viewportSize.toFloat() / 2f).coerceAtLeast(1f)
            val desiredScroll = (center - halfViewport).coerceIn(0f, scrollState.maxValue.toFloat())
            scrollState.animateScrollTo(desiredScroll.roundToInt())
        }
    }

    val currentOffset = animOffset.value
    val currentWidth = animWidth.value
    val lag = if (targetWidth > 0f) {
        (abs(targetOffset - currentOffset) / targetWidth).coerceIn(0f, 1f)
    } else 0f

    val shape = RoundedCornerShape(100.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(contentPadding)
    ) {
        // Continuous traveling liquid pill with USMUSIC V2 Momentum Deformation
        if (currentWidth > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(currentOffset.roundToInt(), 0) }
                    .width(with(density) { currentWidth.toDp() })
                    .height(34.dp)
                    .graphicsLayer {
                        scaleX = 1f + lag * GLASS_STRETCH
                        scaleY = 1f - lag * GLASS_STRETCH * GLASS_SQUASH
                    }
                    .clip(shape)
                    .background(
                        if (isGlass) accentColor.copy(alpha = 0.28f)
                        else accentColor
                    )
                    .border(
                        width = if (isGlass) 1.dp else 0.dp,
                        color = if (isGlass) accentColor.copy(alpha = 0.70f) else Color.Transparent,
                        shape = shape
                    )
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.key == selectedKey

                val textColor by animateColorAsState(
                    targetValue = when {
                        isSelected && isGlass -> glassContentColor()
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isGlass -> glassContentColor().copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(200),
                    label = "segment_text_color"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInParent()
                            itemOffsets[item.key] = position.x
                            itemWidths[item.key] = coordinates.size.width.toFloat()
                        }
                        .clip(shape)
                        .background(
                            // When selected, the gliding selection pill underneath provides the fill!
                            if (isSelected) Color.Transparent
                            else if (isGlass) (if (isDark) Color(0xFF12141C) else Color(0xFFFAF8FE)).copy(alpha = 0.60f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                            shape
                        )
                        .border(
                            // When selected, the gliding selection pill underneath provides the single crisp border!
                            width = if (isSelected) 0.dp else if (isGlass) GLASS_EDGE_WIDTH else 1.dp,
                            color = if (isSelected) Color.Transparent else if (isGlass) GLASS_EDGE_COLOR else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                            shape = shape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(
                                bounded = true,
                                color = if (isGlass) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(item.key)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            color = textColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (!item.badge.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = item.badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

