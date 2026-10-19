package com.boardsprep.onboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.GLASS_EDGE_COLOR
import com.boardsprep.onboard.core.glass.GLASS_EDGE_WIDTH
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.components.GLASS_SQUASH
import com.boardsprep.onboard.core.glass.components.GLASS_STRETCH
import com.boardsprep.onboard.core.glass.components.GlassSpring
import com.boardsprep.onboard.core.glass.glassContentColor
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.theme.ExpressivePillSmall
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class ExpressiveTabItem(
    val title: String,
    val icon: ImageVector? = null,
    val badge: String? = null
)

/**
 * Standard Material 3 Expressive Segmented Tabs Row.
 * Implements USMUSIC V2 continuous traveling liquid glass indicator with
 * spring momentum physics (squash & stretch, travel spring) and luminous tactile ripples.
 * Harmonizes tabs across Chapter Details, Handbooks, Downloads, and Subject Hubs.
 */
@Composable
fun ExpressiveSegmentedTabs(
    tabs: List<ExpressiveTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val tabOffsets = remember { mutableStateMapOf<Int, Float>() }
    val tabWidths = remember { mutableStateMapOf<Int, Float>() }

    val safeIndex = selectedTabIndex.coerceIn(0, maxOf(0, tabs.lastIndex))
    val targetOffset = tabOffsets[safeIndex] ?: 0f
    val targetWidth = tabWidths[safeIndex] ?: 0f

    val animOffset = remember { Animatable(0f) }
    val animWidth = remember { Animatable(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(safeIndex, targetOffset, targetWidth) {
        if (targetWidth > 0f) {
            if (!isInitialized) {
                animOffset.snapTo(targetOffset)
                animWidth.snapTo(targetWidth)
                isInitialized = true
            } else {
                launch { animOffset.animateTo(targetOffset, GlassSpring) }
                launch { animWidth.animateTo(targetWidth, GlassSpring) }
            }
            // Smoothly auto-scroll into view when selected
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(contentPadding)
    ) {
        // Continuous Traveling Liquid Glass Selection Pill with USMUSIC V2 Momentum Deformation
        if (currentWidth > 0f) {
            val pillShape = ExpressivePillSmall
            Box(
                modifier = Modifier
                    .offset { IntOffset(currentOffset.roundToInt(), 0) }
                    .width(with(density) { currentWidth.toDp() })
                    .height(38.dp)
                    .graphicsLayer {
                        scaleX = 1f + lag * GLASS_STRETCH
                        scaleY = 1f - lag * GLASS_STRETCH * GLASS_SQUASH
                    }
                    .clip(pillShape)
                    .background(
                        if (isGlass) accentColor.copy(alpha = 0.28f)
                        else accentColor
                    )
                    .border(
                        width = if (isGlass) 1.dp else 0.dp,
                        color = if (isGlass) accentColor.copy(alpha = 0.75f) else Color.Transparent,
                        shape = pillShape
                    )
            )
        }

        // Tab Items Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tabItem ->
                val isSelected = safeIndex == index

                val contentColor by animateColorAsState(
                    targetValue = when {
                        isSelected && isGlass -> glassContentColor()
                        isSelected -> Color.White
                        isGlass -> glassContentColor().copy(alpha = 0.70f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(200),
                    label = "tab_content_color"
                )

                val textScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.04f else 1.0f,
                    animationSpec = GlassSpring,
                    label = "tab_text_scale"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInParent()
                            tabOffsets[index] = position.x
                            tabWidths[index] = coordinates.size.width.toFloat()
                        }
                        .clip(ExpressivePillSmall)
                        .background(
                            // When selected, the gliding selection pill underneath provides the fill!
                            if (isSelected) Color.Transparent
                            else if (isGlass) (if (isDark) Color(0xFF12141C) else Color(0xFFFAF8FE)).copy(alpha = 0.50f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                            ExpressivePillSmall
                        )
                        .border(
                            // When selected, the gliding selection pill underneath provides the single crisp border!
                            width = if (isSelected) 0.dp else if (isGlass) GLASS_EDGE_WIDTH else 1.dp,
                            color = if (isSelected) Color.Transparent else if (isGlass) GLASS_EDGE_COLOR else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = ExpressivePillSmall
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
                                onTabSelected(index)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp)
                            .graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (tabItem.icon != null) {
                            Icon(
                                imageVector = tabItem.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = tabItem.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (!tabItem.badge.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = ExpressivePillSmall,
                                color = when {
                                    isSelected && isGlass -> accentColor.copy(alpha = 0.35f)
                                    isSelected -> Color.White.copy(alpha = 0.25f)
                                    isGlass -> Color.White.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                                }
                            ) {
                                Text(
                                    text = tabItem.badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
