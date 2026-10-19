package com.boardsprep.onboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.GlassSettings
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.lightweightLiquidGlass
import com.boardsprep.onboard.core.glass.glassContentColor
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionPill
import com.boardsprep.onboard.core.glass.components.LiquidGlassStudioSheet
import com.boardsprep.onboard.core.sync.FirebaseSyncManager
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.repository.BoardsRepository

/**
 * Consolidated Library & Study Utilities Hub for OnBoard CBSE Prep.
 * Serves as the 4th top-level pillar in the main navigation dock.
 * Houses Handbooks, Offline Storage, Error Vault, Daily Blitz,
 * CBSE Calculation Tools, and System Settings/Tour Replay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHubScreen(
    onHandbookClick: (subjectId: String) -> Unit,
    onDownloadsClick: () -> Unit,
    onErrorVaultClick: () -> Unit,
    onDailyBlitzClick: () -> Unit,
    onToolsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onReplayTourClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }
    val syncManager = remember { FirebaseSyncManager.getInstance(context) }
    val accountInfo by syncManager.accountState.collectAsState()
    val unresolvedErrorCount by repository.getUnresolvedErrorCount().collectAsState(initial = 0)
    val isDark = isSystemInDarkTheme()
    val isUserLoggedIn = !accountInfo.isAnonymous && accountInfo.uid.isNotBlank()
    var showGlassStudio by remember { mutableStateOf(false) }

    if (showGlassStudio) {
        LiquidGlassStudioSheet(
            onDismiss = { showGlassStudio = false }
        )
    }

    val surfaceCardColor = if (isDark) MdSurfaceVariantDark else MaterialTheme.colorScheme.surface
    val borderStrokeColor = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Library & Utilities",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Reference handbooks, mistake vault & tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    LiquidGlassActionPill(modifier = Modifier.padding(end = 8.dp)) {
                        // Quick Liquid Glass Toggle Button
                        LiquidGlassActionButton(
                            icon = Icons.Default.AutoAwesome,
                            contentDescription = "Toggle Liquid Glass",
                            onClick = {
                                val newState = !GlassSettings.isGlassEnabled
                                GlassSettings.setEnabled(context, newState)
                            },
                            tint = if (GlassSettings.isGlassEnabled) Color(0xFFAB47BC) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Cloud Sync Button
                        LiquidGlassActionButton(
                            icon = if (isUserLoggedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            contentDescription = "Cloud Sync",
                            onClick = onAccountClick,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            // ── Section 1: Quick Handbooks Carousel / Grid ───────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "High-Yield Handbooks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Formulas & Derivations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 1: Physics + Chemistry (Subject-matched icons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HandbookChipTile(
                            title = "Physics",
                            subtitle = "Derivations & Formulas",
                            icon = Icons.Default.Hub,
                            tint = Color(0xFF9C27B0),
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = { onHandbookClick("physics") }
                        )
                        HandbookChipTile(
                            title = "Chemistry",
                            subtitle = "Reactions & Reagents",
                            icon = Icons.Default.Science,
                            tint = Color(0xFF00B0FF),
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = { onHandbookClick("chemistry") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Mathematics + Biology (Subject-matched icons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HandbookChipTile(
                            title = "Mathematics",
                            subtitle = "Key Formulae & Rules",
                            icon = Icons.Default.Functions,
                            tint = Color(0xFFFF9800),
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = { onHandbookClick("maths") }
                        )
                        HandbookChipTile(
                            title = "Biology",
                            subtitle = "Diagrams & Summary",
                            icon = Icons.Default.Biotech,
                            tint = Color(0xFF4CAF50),
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = { onHandbookClick("biology") }
                        )
                    }
                }
            }

            // ── Section 2: Memory & Mastery Bento (Vault + Blitz) ───────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Spaced Recall & Recovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Error Vault Card
                        LibraryFeatureCard(
                            title = "Mistake Vault",
                            subtitle = if (unresolvedErrorCount > 0) "$unresolvedErrorCount questions to fix" else "Zero pending errors",
                            badge = if (unresolvedErrorCount > 0) "$unresolvedErrorCount PENDING" else "",
                            badgeColor = MaterialTheme.colorScheme.error,
                            icon = Icons.Default.AutoFixHigh,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.weight(1f),
                            onClick = onErrorVaultClick
                        )

                        // Recall Blitz Card (Harmonized with Home screen)
                        LibraryFeatureCard(
                            title = "Recall Blitz",
                            subtitle = "5-Min rapid retrieval drill",
                            badge = "",
                            badgeColor = Color(0xFFAB47BC),
                            icon = Icons.Default.Bolt,
                            tint = Color(0xFFAB47BC),
                            modifier = Modifier.weight(1f),
                            onClick = onDailyBlitzClick
                        )
                    }
                }
            }

            // ── Section 3: Offline Storage & CBSE Tools ─────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Offline Storage & Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LibraryFeatureCard(
                            title = "Offline Library",
                            subtitle = "Saved PDFs & Lectures",
                            badge = "",
                            badgeColor = Color(0xFF00B0FF),
                            icon = Icons.Default.CloudDownload,
                            tint = Color(0xFF00B0FF),
                            modifier = Modifier.weight(1f),
                            onClick = onDownloadsClick
                        )

                        LibraryFeatureCard(
                            title = "CBSE Tools",
                            subtitle = "Rule 40.1 & Best 5 Calc",
                            badge = "",
                            badgeColor = Color(0xFFFFB300),
                            icon = Icons.Default.Calculate,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.weight(1f),
                            onClick = onToolsClick
                        )
                    }
                }
            }

            // ── Section 4: System, Account Sync & Tour Replay ───────────────
            item {
                val isGlass = LocalGlassEnabled.current && isGlassSupported()
                val prefsShape = RoundedCornerShape(24.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isGlass) Modifier.lightweightLiquidGlass(shape = prefsShape)
                            else Modifier
                        ),
                    shape = prefsShape,
                    colors = CardDefaults.cardColors(containerColor = if (isGlass) Color.Transparent else (if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surface)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Preferences & Experience",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Account Sync Tile
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onAccountClick() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isUserLoggedIn) (accountInfo.email ?: "Cloud Account") else "Cloud Sync & Progress",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isUserLoggedIn) "Live streak & mastery synchronized" else "Sign in to keep streak across devices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Liquid Glass Mode Direct Toggle Tile (USMUSIC V2 Mode)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val newState = !GlassSettings.isGlassEnabled
                                    GlassSettings.setEnabled(context, newState)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (GlassSettings.isGlassEnabled) Color(0xFFAB47BC).copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (GlassSettings.isGlassEnabled) Color(0xFFAB47BC)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Liquid Glass UI Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = if (GlassSettings.isGlassEnabled) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        ) {
                                            Text(
                                                text = if (GlassSettings.isGlassEnabled) "ACTIVE" else "OFF (DEFAULT)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (GlassSettings.isGlassEnabled) Color(0xFF2E7D32)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (GlassSettings.isGlassEnabled) "Optical refraction, blurs & specular highlights active"
                                        else "Standard Material 3 Expressive UI active (Zero overhead)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = GlassSettings.isGlassEnabled,
                                onCheckedChange = { newState ->
                                    GlassSettings.setEnabled(context, newState)
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Replay Onboarding Animation Tour Tile
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onReplayTourClick() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF9100).copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9100),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replay Onboarding Tour",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "6-Slide interactive 3D physics demonstration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // App Version & CBSE Syllabus Calibration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CBSE 2026-27 Blueprint Calibrated",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "v2.4.0",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Trailing spacer so bottom preferences tile fully clears floating dock
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private data class HandbookItem(
    val title: String,
    val id: String,
    val subtitle: String,
    val tint: Color
)

@Composable
private fun HandbookChipTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val shape = RoundedCornerShape(16.dp)
    val cardBg = if (isGlass) tint.copy(alpha = 0.15f) else (if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    Surface(
        onClick = onClick,
        shape = shape,
        color = cardBg,
        border = if (isGlass) androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else null,
        modifier = modifier
            .height(58.dp)
            .then(
                if (isGlass) Modifier.lightweightLiquidGlass(shape = shape)
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.2f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibraryFeatureCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val cardBg = if (isGlass) Color.Transparent else (if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surface)
    Surface(
        onClick = onClick,
        shape = M3EBentoTileShape,
        color = cardBg,
        border = if (isGlass) androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else null,
        modifier = modifier
            .then(
                if (isGlass) Modifier.lightweightLiquidGlass(shape = M3EBentoTileShape)
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tint.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (badge.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
