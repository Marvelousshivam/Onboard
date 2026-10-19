package com.boardsprep.onboard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Standard Material 3 Expressive tactile modifier with native bounded ripple and USMUSIC V2 spring micro-response.
 * Enforces Android accessibility minimum touch target area (48dp) and delivers silky 120Hz ripple bloom.
 */
@Composable
fun Modifier.expressiveBounce(
    scaleOnPress: Float = 0.98f,
    enabled: Boolean = true,
    enforceMinTouchTarget: Boolean = false,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleOnPress else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 320f
        ),
        label = "expressive_bounce"
    )

    var modifier = this.scale(scale)
    if (enforceMinTouchTarget) {
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    }

    return modifier.clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true),
        enabled = enabled,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
}
