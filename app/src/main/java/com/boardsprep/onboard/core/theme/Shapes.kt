package com.boardsprep.onboard.core.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ==========================================
// Material You Expressive Shape Tokens (Android 14/15 M3 Expressive)
// Characterized by organic capsules, pills, and asymmetric rounded corners
// ==========================================

// Google Material 3 Expressive Asymmetric Geometry
val AsymmetricLeafHero = RoundedCornerShape(
    topStart = 32.dp,
    topEnd = 12.dp,
    bottomEnd = 32.dp,
    bottomStart = 12.dp
)

val AsymmetricBentoCard = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomEnd = 8.dp,
    bottomStart = 24.dp
)

val AsymmetricOptionCard = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 8.dp,
    bottomEnd = 18.dp,
    bottomStart = 8.dp
)

val ExpressivePill = CircleShape
val ExpressivePillSmall = RoundedCornerShape(100.dp)
val ExpressiveCardLarge = RoundedCornerShape(28.dp)
val ExpressiveCardMedium = RoundedCornerShape(22.dp)
val ExpressiveDockShape = RoundedCornerShape(36.dp)

// Google Material 3 Expressive Bento Geometry Tokens
val M3EBentoHeroShape = RoundedCornerShape(26.dp)
val M3EBentoTileShape = RoundedCornerShape(20.dp)
val M3ESubjectCardShape = RoundedCornerShape(20.dp)
val M3EIconContainerShape = RoundedCornerShape(16.dp)
val M3EFloatingDockShape = RoundedCornerShape(32.dp)
val M3ESquircleBadgeShape = RoundedCornerShape(12.dp)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Micro chips, subtle tags
    small = RoundedCornerShape(14.dp),       // Compact buttons, pills
    medium = M3ESubjectCardShape,            // Cards, dialog containers
    large = M3EBentoHeroShape,               // Prominent feature cards & hero widgets
    extraLarge = M3EFloatingDockShape        // Floating pill menus, docks, capsule FABs
)

