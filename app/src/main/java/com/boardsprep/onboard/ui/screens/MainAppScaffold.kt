package com.boardsprep.onboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.LocalGlassTheme
import com.boardsprep.onboard.core.glass.glassContentColor
import com.boardsprep.onboard.core.glass.glassIndicatorColor
import com.boardsprep.onboard.core.glass.components.GlassSpring
import com.boardsprep.onboard.core.glass.components.GLASS_STRETCH
import com.boardsprep.onboard.core.glass.components.GLASS_SQUASH
import com.boardsprep.onboard.core.glass.liquidGlassDockBackground
import com.boardsprep.onboard.core.pdf.PdfPairedRole
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.models.Subject
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Top-Level Application Scaffold hosting the 4 Primary Tabs:
 * 0 -> Home (DashboardScreen)
 * 1 -> Q-Bank (QuestionBankScreen)
 * 2 -> Papers (SamplePapersScreen)
 * 3 -> Library (LibraryHubScreen)
 *
 * Hosts a unified, persistent ClearPDF-inspired Liquid Glass Floating Dock.
 * Provides instant 1-tap switching and handles Back navigation cleanly.
 */
@Composable
fun MainAppScaffold(
    initialTab: Int = 0,
    subjects: List<Subject>,
    onSubjectClick: (String) -> Unit,
    onHandbookClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onErrorVaultClick: () -> Unit,
    onDailyBlitzClick: () -> Unit,
    onToolsClick: () -> Unit,
    onReplayTourClick: () -> Unit,
    onOpenPdfWithPair: (url: String, title: String, pairedUrl: String, pairedTitle: String, pairedRole: PdfPairedRole) -> Unit,
    onOpenPdfSimple: (url: String, title: String) -> Unit,
    onStartExamSimulation: (paperTitle: String, subject: String, paperUrl: String, msUrl: String) -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onCheckForUpdates: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var activeQbSubject by rememberSaveable { mutableStateOf("physics") }
    var activePapersSubject by rememberSaveable { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()

    // Back press returns to Home (Tab 0) if on any other tab
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Tab Content Switching
        Crossfade(
            targetState = selectedTabIndex,
            animationSpec = tween(220),
            label = "tab_crossfade"
        ) { currentTab ->
            when (currentTab) {
                0 -> {
                    DashboardScreen(
                        subjects = subjects,
                        onSubjectClick = onSubjectClick,
                        onHandbookClick = onHandbookClick,
                        onDownloadsClick = onDownloadsClick,
                        onSamplePapersClick = { subId ->
                            if (subId.isNotBlank()) activePapersSubject = subId
                            selectedTabIndex = 2
                        },
                        onAccountClick = onAccountClick,
                        onErrorVaultClick = onErrorVaultClick,
                        onDailyBlitzClick = onDailyBlitzClick,
                        onToolsClick = onToolsClick,
                        onQuestionBankClick = { subId ->
                            if (subId.isNotBlank()) activeQbSubject = subId
                            selectedTabIndex = 1
                        },
                        onReplayTourClick = onReplayTourClick
                    )
                }
                1 -> {
                    QuestionBankScreen(
                        initialSubjectId = activeQbSubject,
                        showBackButton = false,
                        onBackClick = { selectedTabIndex = 0 },
                        onOpenPdf = onOpenPdfSimple,
                        onOpenAiSettings = onOpenAiSettings
                    )
                }
                2 -> {
                    SamplePapersScreen(
                        initialSubjectId = activePapersSubject,
                        showBackButton = false,
                        onOpenPdf = onOpenPdfWithPair,
                        onStartExamSimulation = onStartExamSimulation,
                        onQuestionBankClick = { subId ->
                            if (subId.isNotBlank()) activeQbSubject = subId
                            selectedTabIndex = 1
                        },
                        onBackClick = { selectedTabIndex = 0 }
                    )
                }
                3 -> {
                    LibraryHubScreen(
                        onHandbookClick = onHandbookClick,
                        onDownloadsClick = onDownloadsClick,
                        onErrorVaultClick = onErrorVaultClick,
                        onDailyBlitzClick = onDailyBlitzClick,
                        onToolsClick = onToolsClick,
                        onAccountClick = onAccountClick,
                        onReplayTourClick = onReplayTourClick,
                        onOpenAiSettings = onOpenAiSettings,
                        onCheckForUpdates = onCheckForUpdates
                    )
                }
            }
        }

        // Adaptive Bottom Gradient Scrim behind the floating dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(116.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // USMUSIC V2-Matched Liquid Glass Floating Navigation Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val isGlassEnabled = LocalGlassEnabled.current
            val glassStyle = LocalGlassTheme.current

            val topRim = Color.White.copy(alpha = (0.65f * glassStyle.highlight).coerceIn(0f, 1f))
            val bottomRim = (if (isDark) Color.Black else Color(0xFF6750A4)).copy(alpha = (0.25f * glassStyle.highlight).coerceIn(0f, 1f))
            val rimBrush = Brush.verticalGradient(listOf(topRim, topRim.copy(alpha = 0.20f), bottomRim))

            data class NavDockTab(val icon: ImageVector, val label: String)
            val dockTabs = remember {
                listOf(
                    NavDockTab(Icons.Default.Dashboard, "Home"),
                    NavDockTab(Icons.Default.Quiz, "Q-Bank"),
                    NavDockTab(Icons.Default.HistoryEdu, "Papers"),
                    NavDockTab(Icons.Default.LocalLibrary, "Library")
                )
            }

            val density = LocalDensity.current
            val haptic = LocalHapticFeedback.current
            var rowSize by remember { mutableStateOf(IntSize.Zero) }
            val gapPx = with(density) { 4.dp.toPx() }
            val n = dockTabs.size

            val tabWidthPx = if (rowSize.width > 0 && n > 0) {
                (rowSize.width - gapPx * (n - 1)) / n
            } else 0f
            val tabStepPx = if (rowSize.width > 0 && n > 0) {
                (rowSize.width + gapPx) / n
            } else 0f

            var dragOffset by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(selectedTabIndex) { dragOffset = 0f }

            val pillTargetPx = if (tabStepPx > 0f) {
                selectedTabIndex * tabStepPx + dragOffset
            } else 0f

            val animatedPillOffset by animateFloatAsState(
                targetValue = pillTargetPx,
                animationSpec = GlassSpring,
                label = "dockPillOffset"
            )

            val lag = if (tabStepPx > 0f) {
                (abs(pillTargetPx - animatedPillOffset) / tabStepPx).coerceIn(0f, 1f)
            } else 0f

            Box(contentAlignment = Alignment.Center) {
                // Blurred Translucent Backdrop Layer (Only rendered when Glass Mode is active)
                if (isGlassEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .liquidGlassDockBackground(shape = RoundedCornerShape(26.dp))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = if (isGlassEnabled) (if (isDark) Color(0xFF10121A) else Color(0xFFFAF8FE)).copy(alpha = (0.80f * glassStyle.tintAlpha).coerceIn(0.20f, 0.95f))
                    else if (isDark) Color(0xFF161722).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.95f),
                    border = if (isGlassEnabled) androidx.compose.foundation.BorderStroke(1.2.dp, rimBrush)
                    else androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF28253A) else Color(0xFFE6E0EC)),
                    tonalElevation = 0.dp,
                    shadowElevation = if (isGlassEnabled) 3.dp else 3.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        // Continuous Travelling Selection Pill with USMUSIC V2 Momentum Deformation
                        if (tabWidthPx > 0f) {
                            val pillShape = RoundedCornerShape(18.dp)
                            Box(
                                modifier = Modifier
                                    .width(with(density) { tabWidthPx.toDp() })
                                    .height(with(density) { rowSize.height.toDp() })
                                    .graphicsLayer {
                                        translationX = animatedPillOffset
                                        scaleX = 1f + lag * GLASS_STRETCH
                                        scaleY = 1f - lag * GLASS_STRETCH * GLASS_SQUASH
                                    }
                                    .clip(pillShape)
                                    .background(
                                        if (isGlassEnabled) {
                                            glassIndicatorColor().copy(alpha = 0.22f)
                                        } else {
                                            if (isDark) Color(0xFF28253A) else MaterialTheme.colorScheme.primaryContainer
                                        }
                                    )
                                    .border(
                                        width = if (isGlassEnabled) 0.5.dp else 0.dp,
                                        color = if (isGlassEnabled) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = pillShape
                                    )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .onSizeChanged { rowSize = it }
                                .pointerInput(Unit) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDrag = 0f },
                                        onDragCancel = { dragOffset = 0f },
                                        onDragEnd = {
                                            if (tabStepPx > 0f) {
                                                val ratio = totalDrag / tabStepPx
                                                val shift = when {
                                                    ratio > 0.35f -> max(1, ratio.roundToInt())
                                                    ratio < -0.35f -> min(-1, ratio.roundToInt())
                                                    else -> 0
                                                }
                                                val newIndex = (selectedTabIndex + shift).coerceIn(0, dockTabs.lastIndex)
                                                if (newIndex != selectedTabIndex) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedTabIndex = newIndex
                                                }
                                            }
                                            dragOffset = 0f
                                        },
                                        onHorizontalDrag = { _, delta ->
                                            totalDrag += delta
                                            val rawTarget = selectedTabIndex * tabStepPx + totalDrag
                                            val minTarget = 0f
                                            val maxTarget = dockTabs.lastIndex * tabStepPx
                                            dragOffset = when {
                                                rawTarget < minTarget -> (rawTarget - minTarget) * 0.25f
                                                rawTarget > maxTarget -> (rawTarget - maxTarget) * 0.25f
                                                else -> totalDrag
                                            }
                                        }
                                    )
                                },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dockTabs.forEachIndexed { index, tab ->
                                val isSelected = selectedTabIndex == index
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.10f else 1f,
                                    animationSpec = GlassSpring,
                                    label = "dockIconScale"
                                )
                                val contentColor by animateColorAsState(
                                    targetValue = when {
                                        isSelected && isGlassEnabled -> glassContentColor()
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isGlassEnabled -> glassContentColor().copy(alpha = 0.65f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(200),
                                    label = "dockContentColor"
                                )

                                Column(
                                    modifier = Modifier
                                        .width(with(density) { if (tabWidthPx > 0f) tabWidthPx.toDp() else 68.dp })
                                        .clip(RoundedCornerShape(percent = 50))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(
                                                bounded = true,
                                                color = if (isGlassEnabled) glassContentColor() else MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            if (!isSelected) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedTabIndex = index
                                            }
                                        }
                                        .padding(vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = contentColor,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        color = contentColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
