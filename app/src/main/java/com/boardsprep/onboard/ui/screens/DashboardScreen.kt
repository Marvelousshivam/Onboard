package com.boardsprep.onboard.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.boardsprep.onboard.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.lightweightLiquidGlass
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionPill
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.models.Chapter
import com.boardsprep.onboard.data.models.Subject
import com.boardsprep.onboard.data.repository.BoardsRepository
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.boardsprep.onboard.core.glass.GLASS_EDGE_WIDTH
import com.boardsprep.onboard.core.glass.GLASS_EDGE_COLOR
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Composable
fun Modifier.springBounceClick(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    subjects: List<Subject>,
    onSubjectClick: (String) -> Unit,
    onHandbookClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    onSamplePapersClick: (String) -> Unit = {},
    onAccountClick: () -> Unit = {},
    onErrorVaultClick: () -> Unit = {},
    onDailyBlitzClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onQuestionBankClick: (String) -> Unit = {},
    onReplayTourClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }
    val syncManager = remember { com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context) }
    val streakInfo by syncManager.streakState.collectAsState()
    val accountInfo by syncManager.accountState.collectAsState()
    val unresolvedErrorCount by repository.getUnresolvedErrorCount().collectAsState(initial = 0)
    val allMastery by repository.getAllMastery().collectAsState(initial = emptyList())
    val recentVideos by repository.getRecentVideos(1).collectAsState(initial = emptyList())
    var showStreakDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    // Contextual Study Resume Engine (Resolves last studied chapter or default high-yield starting point)
    val resumeData = remember(recentVideos, allMastery, subjects) {
        val lastVideo = recentVideos.firstOrNull()
        val chapterId = lastVideo?.chapterId
        var foundSub: Subject? = null
        var foundCh: Chapter? = null

        if (!chapterId.isNullOrBlank()) {
            for (s in subjects) {
                val ch = s.chapters.firstOrNull { it.id == chapterId }
                if (ch != null) {
                    foundSub = s
                    foundCh = ch
                    break
                }
            }
        }

        if (foundSub == null || foundCh == null) {
            val firstSub = subjects.firstOrNull { it.id == "physics" } ?: subjects.firstOrNull()
            val firstCh = firstSub?.chapters?.firstOrNull()
            foundSub = firstSub
            foundCh = firstCh
        }

        val masteryEntity = allMastery.firstOrNull { it.chapterId == foundCh?.id }
        val progressPercent = if (lastVideo != null && lastVideo.durationMillis > 0L) {
            ((lastVideo.positionMillis.toFloat() / lastVideo.durationMillis) * 100).toInt().coerceIn(5, 95)
        } else if (masteryEntity != null) {
            var count = 0
            if (masteryEntity.theoryCompleted) count++
            if (masteryEntity.ncertCompleted) count++
            if (masteryEntity.exemplarCompleted) count++
            if (masteryEntity.pyqCompleted) count++
            count * 25
        } else {
            0 // Truthful initial progress state
        }

        Triple(foundSub, foundCh, progressPercent)
    }
    val (resumeSubject, resumeChapter, resumeProgress) = resumeData

    // Live countdown to CBSE Board Exams 2027 (Feb 15, 2027)
    val isDark = isSystemInDarkTheme()
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val physicsTokens = remember(isDark) { getAdaptiveSubjectTokens("physics", isDark) }
    val chemistryTokens = remember(isDark) { getAdaptiveSubjectTokens("chemistry", isDark) }

    val daysRemaining = remember {
        val examCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2027, Calendar.FEBRUARY, 15, 10, 30, 0)
        }
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val diffMillis = examCal.timeInMillis - nowCal.timeInMillis
        TimeUnit.MILLISECONDS.toDays(diffMillis).coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = "OnBOARD Logo",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "OnBOARD",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Class 12 Preparation Suite",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    LiquidGlassActionPill(modifier = Modifier.padding(end = 8.dp)) {
                        // Global Search Button
                        LiquidGlassActionButton(
                            icon = Icons.Default.Search,
                            contentDescription = "Search & Quick Jump",
                            onClick = { showSearchDialog = true }
                        )

                        // WCAG AA Compliant High-Contrast Compact Streak Pill (Fire + Count)
                        val streakDays = streakInfo.currentStreak
                        val streakText = streakDays.toString()
                        val isToday = streakInfo.isStreakActiveToday
                        val pillBg = if (isToday) (if (isDark) Color(0xFF2C2012) else AccentAmberLight) else (if (isDark) Color(0xFF1A1A1E) else MaterialTheme.colorScheme.surfaceVariant)
                        val pillContent = if (isToday) (if (isDark) AccentAmberDark else AccentAmberOnBg) else MaterialTheme.colorScheme.onSurfaceVariant

                        Surface(
                            onClick = { showStreakDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = pillBg
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Study Streak",
                                    tint = if (isToday) AccentAmberDark else pillContent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = streakText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = pillContent
                                )
                            }
                        }

                        // Account & Cloud Sync Profile Button
                        LiquidGlassActionButton(
                            icon = if (accountInfo.isAnonymous) Icons.Default.CloudSync else Icons.Default.AccountCircle,
                            contentDescription = "Account & Cloud Sync",
                            onClick = onAccountClick
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // 140dp bottom padding ensures the last card is 100% visible ABOVE the floating dock
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
            ) {
                // ── Tier 1: Asymmetric Bento Hero Countdown & Contextual Study Widget ────────────────
                item {
                    val heroShape = RoundedCornerShape(28.dp)
                    val heroColor = if (isDark) Color(0xFF1E1D2A) else Color(0xFFEDE7F6)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isGlass) Modifier.lightweightLiquidGlass(
                                    shape = heroShape,
                                    fallbackColor = if (isDark) Color(0xFF1D1C28) else heroColor
                                ) else Modifier
                            ),
                        shape = heroShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGlass) Color.Transparent else heroColor
                        ),
                        border = null,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .padding(20.dp)
                        ) {
                            // Top Row: Micro-Eyebrow + Oversized Display Countdown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Column: Exam info & micro-eyebrow
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                        Text(
                                            text = "CBSE CLASS 12 • TARGET 95%+",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.2.sp,
                                            fontSize = 10.sp,
                                            color = if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Board Examination",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Starts 15 Feb 2027 • Final Sprint",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Right Column: Oversized Expressive Typography Countdown (Clean Right Aligned & Tightly Stacked)
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$daysRemaining",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontSize = 56.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = (-1.5).sp,
                                            lineHeight = 48.sp
                                        ),
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "DAYS LEFT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.5.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Contextual Resume Study Action Card: Elevated Tactile Player Widget (Pure White, Zero Shadow)
                            Surface(
                                onClick = {
                                    if (resumeSubject != null) {
                                        onSubjectClick(resumeSubject.id)
                                    } else if (subjects.isNotEmpty()) {
                                        onSubjectClick(subjects.first().id)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isGlass) Color.White.copy(alpha = 0.08f) else (if (isDark) Color(0xFF232135) else Color(0xFFFFFBFF)),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                                border = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .springBounceClick {
                                        if (resumeSubject != null) {
                                            onSubjectClick(resumeSubject.id)
                                        } else if (subjects.isNotEmpty()) {
                                            onSubjectClick(subjects.first().id)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Circular progress ring with play button
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { resumeProgress / 100f },
                                            modifier = Modifier.size(42.dp),
                                            color = if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary,
                                            trackColor = (if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary).copy(alpha = 0.18f),
                                            strokeWidth = 3.5.dp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume Study",
                                            tint = if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CONTINUE TODAY'S STUDY",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.5.sp,
                                            letterSpacing = 0.8.sp,
                                            color = if (isDark) PhysicsLavender else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = resumeChapter?.name ?: "Electric Charges & Fields",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (resumeProgress > 0) "${resumeSubject?.name ?: "Physics"} • Ch ${resumeChapter?.number ?: 1} • $resumeProgress% Mastered"
                                            else "${resumeSubject?.name ?: "Physics"} • Ch ${resumeChapter?.number ?: 1} • Ready to Begin",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
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
                            }
                        }
                    }
                }

                // ── Tier 2: Gamified High-Agency Quick Action Strip ──
                item {
                    val quickCardShape = RoundedCornerShape(18.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Recall Blitz (5-Min Daily Spaced Retrieval)
                        Surface(
                            onClick = { onDailyBlitzClick() },
                            shape = quickCardShape,
                            color = if (isGlass) Color.Transparent else (if (isDark) Color(0xFF1B182B) else PhysicsLightBg),
                            border = null, // Zero 1px wireframe border!
                            tonalElevation = if (isGlass) 0.dp else 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .then(
                                    if (isGlass) Modifier.lightweightLiquidGlass(shape = quickCardShape)
                                    else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PhysicsAccent.copy(alpha = if (isDark) 0.25f else 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = PhysicsLavender,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Recall Blitz",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) PhysicsLavender else PhysicsLightOnBg,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "5-MIN DRILL",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.5.sp,
                                        letterSpacing = 0.8.sp,
                                        color = if (isDark) PhysicsLavender.copy(alpha = 0.75f) else PhysicsLightOnBg.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }

                        // 2. Mistake Vault (Anti-Forgetting Recovery)
                        Surface(
                            onClick = { onErrorVaultClick() },
                            shape = quickCardShape,
                            color = if (isGlass) Color.Transparent else (if (isDark) Color(0xFF261522) else EnglishLightBg),
                            border = null, // Zero 1px wireframe border!
                            tonalElevation = if (isGlass) 0.dp else 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .then(
                                    if (isGlass) Modifier.lightweightLiquidGlass(shape = quickCardShape)
                                    else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EnglishAccent.copy(alpha = if (isDark) 0.25f else 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = EnglishRose,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Mistake Vault",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) EnglishRose else EnglishLightOnBg,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = if (unresolvedErrorCount > 0) "$unresolvedErrorCount to Review" else "Zero Flaws",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.5.sp,
                                        letterSpacing = 0.5.sp,
                                        color = if (unresolvedErrorCount > 0) MaterialTheme.colorScheme.error else (if (isDark) EnglishRose.copy(alpha = 0.75f) else EnglishLightOnBg.copy(alpha = 0.75f))
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Tier 3: Academic Subjects Section (Clean Typographic Header) ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Academic Subjects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "CBSE Syllabus 2026-27",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Option A: 2-Column Compact M3 Expressive Subject Grid
                val subjectPairs = subjects.chunked(2)
                items(subjectPairs) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { subject ->
                            Box(modifier = Modifier.weight(1f)) {
                                M3ExpressiveSubjectGridTile(
                                    subject = subject,
                                    allMastery = allMastery,
                                    onClick = { onSubjectClick(subject.id) }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Interactive Streak Psychological Reinforcement Modal
    if (showStreakDialog) {
        StreakBreakdownDialog(streak = streakInfo, onDismiss = { showStreakDialog = false })
    }

    // In-Dashboard Instant Quick Search Sheet
    if (showSearchDialog) {
        QuickSearchDialog(
            subjects = subjects,
            onDismiss = { showSearchDialog = false },
            onSelectSubject = { subjectId ->
                showSearchDialog = false
                onSubjectClick(subjectId)
            }
        )
    }
}

@Composable
fun DockNavigationItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = ExpressivePillSmall,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.springBounceClick(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun M3ExpressiveDeckCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    containerColor: Color,
    onContainerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.springBounceClick(onClick = onClick),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = M3EIconContainerShape,
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = onContainerColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = onContainerColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onContainerColor.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
fun M3ExpressiveSubjectGridTile(
    subject: Subject,
    allMastery: List<ChapterMasteryEntity> = emptyList(),
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val tokens = getAdaptiveSubjectTokens(subject.id, isDark)
    val cardBg = tokens.containerColor
    val onCardColor = tokens.onContainerColor
    val accentColor = tokens.accentColor
    val haptic = LocalHapticFeedback.current

    // Distinct organic M3 shape morphing per subject
    val iconShape = remember(subject.id) {
        when (subject.id.lowercase()) {
            "physics" -> RoundedCornerShape(topStart = 16.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 16.dp)
            "chemistry" -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 16.dp)
            "maths" -> RoundedCornerShape(12.dp)
            "biology" -> RoundedCornerShape(topStart = 14.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 14.dp)
            "english" -> RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 6.dp)
            else -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        }
    }

    // Dynamic mastery calculation strictly aligned with SubjectHubScreen (Requires all 4 pillars for 100% chapter mastery)
    val totalChapters = subject.chapters.size
    val masteredChaptersCount = remember(subject.chapters, allMastery) {
        subject.chapters.count { ch ->
            val m = allMastery.firstOrNull { it.chapterId == ch.id }
            m != null && m.theoryCompleted && m.ncertCompleted && m.exemplarCompleted && m.pyqCompleted
        }
    }
    val inProgressChaptersCount = remember(subject.chapters, allMastery) {
        subject.chapters.count { ch ->
            val m = allMastery.firstOrNull { it.chapterId == ch.id }
            if (m == null) false
            else {
                val hasAny = m.theoryCompleted || m.ncertCompleted || m.exemplarCompleted || m.pyqCompleted
                val hasAll = m.theoryCompleted && m.ncertCompleted && m.exemplarCompleted && m.pyqCompleted
                hasAny && !hasAll
            }
        }
    }
    val masteryPercent = remember(masteredChaptersCount, totalChapters) {
        if (totalChapters > 0) ((masteredChaptersCount.toFloat() / totalChapters) * 100).toInt() else 0
    }

    val tileShape = RoundedCornerShape(22.dp)

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = tileShape,
        color = if (isGlass) Color.Transparent else (if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surface),
        border = null, // Zero 1px wireframe border!
        tonalElevation = if (isGlass) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGlass) Modifier.lightweightLiquidGlass(shape = tileShape)
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: 44dp Squircle Icon + Optional Mastery Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = iconShape,
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
                    modifier = Modifier
                        .size(44.dp)
                        .aspectRatio(1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (subject.id.lowercase()) {
                                "english" -> Icons.AutoMirrored.Filled.MenuBook
                                "physics" -> Icons.Default.Hub
                                "chemistry" -> Icons.Default.Science
                                "maths" -> Icons.Default.Functions
                                "biology" -> Icons.Default.Biotech
                                else -> Icons.Default.SportsBasketball
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (masteredChaptersCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$masteryPercent% Mastered",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (inProgressChaptersCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$inProgressChaptersCount In Progress",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Subject Title & Consolidated Subtitle ("19 Chapters • 80M" + "0%")
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${subject.chapters.size} Chapters • ${subject.totalTheoryMarks}M",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$masteryPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }

            // 6.dp Rounded M3 Linear Progress Indicator with Visible Track Gap & Initial 0% Dot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                val progressFraction = if (totalChapters > 0 && masteredChaptersCount > 0) {
                    (masteredChaptersCount.toFloat() / totalChapters).coerceIn(0.06f, 1f)
                } else 0f
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
fun M3ExpressiveSubjectCard(
    subject: Subject,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val tokens = getAdaptiveSubjectTokens(subject.id, isDark)
    val cardBg = tokens.containerColor
    val onCardColor = tokens.onContainerColor
    val accentColor = tokens.accentColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick),
        shape = M3ESubjectCardShape,
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Saturated Squircle Icon Container
            Surface(
                shape = M3EIconContainerShape,
                color = cardBg,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (subject.id) {
                            "english" -> Icons.AutoMirrored.Filled.MenuBook
                            "physics" -> Icons.Default.Bolt
                            "chemistry" -> Icons.Default.Science
                            "maths" -> Icons.Default.Calculate
                            "biology" -> Icons.Default.Biotech
                            else -> Icons.Default.School
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = ExpressivePillSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Code ${subject.code}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // High-Contrast WCAG AA Compliant Chapter Pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = cardBg
                    ) {
                        Text(
                            text = "${subject.chapters.size} Chapters",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = onCardColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "• ${subject.totalTheoryMarks}M Th + ${subject.totalPracticalMarks}M Pr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Circular Action Chevron
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Subject",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveFeatureBadge(icon: ImageVector, label: String) {
    Surface(
        shape = ExpressivePillSmall,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StreakBreakdownDialog(
    streak: com.boardsprep.onboard.core.sync.StreakInfo = com.boardsprep.onboard.core.sync.StreakInfo(),
    onDismiss: () -> Unit
) {
    val current = streak.currentStreak
    val longest = streak.longestStreak

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = AccentAmberLight,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = AccentAmberOnBg,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = if (current > 0) "$current-Day Study Streak 🔥" else "Start Your Study Streak!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (current > 0) {
                        "You've maintained a $current-day study momentum! Personal best: $longest days. Spaced repetition primes long-term synaptic retention leading up to CBSE 2027."
                    } else {
                        "Complete at least 5 minutes of lectures, 1 DPP Quiz, or master a high-yield derivation today to ignite your study streak!"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                // 7-Day Activity Matrix (Mon - Sun)
                Surface(
                    shape = M3EBentoTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "This Week's Activity",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val todayCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
                        val currentDayOfWeek = todayCal.get(Calendar.DAY_OF_WEEK)
                        // Calendar: Sunday = 1, Monday = 2, Tuesday = 3, Wednesday = 4, Thursday = 5, Friday = 6, Saturday = 7
                        // Map to 0..6 where Monday = 0:
                        val todayIdx = (currentDayOfWeek + 5) % 7

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                                val isToday = index == todayIdx
                                val isCompleted = if (isToday) {
                                    streak.isStreakActiveToday
                                } else {
                                    index < todayIdx && index >= (todayIdx - streak.currentStreak + (if (streak.isStreakActiveToday) 1 else 0))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            isCompleted -> SuccessGreen
                                            isToday -> AccentAmberLight
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        border = if (!isCompleted && !isToday) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            when {
                                                isCompleted -> Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Completed",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                isToday -> Icon(
                                                    imageVector = Icons.Default.LocalFireDepartment,
                                                    contentDescription = "Today",
                                                    tint = AccentAmberOnBg,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = day,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = ExpressivePillSmall
            ) {
                Text("Keep It Up!", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun QuickSearchDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSelectSubject: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allChapters = remember(subjects, searchQuery) {
        val list = mutableListOf<Pair<Chapter, Subject>>()
        subjects.forEach { subj ->
            subj.chapters.forEach { chap ->
                if (searchQuery.isBlank() || chap.name.contains(searchQuery, ignoreCase = true) || subj.name.contains(searchQuery, ignoreCase = true)) {
                    list.add(Pair(chap, subj))
                }
            }
        }
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f),
            shape = M3EBentoHeroShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Global Curriculum Search",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search NCERT chapters, topics...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = ExpressivePillSmall,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Matching Chapters (${allChapters.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(allChapters) { (chapter, subject) ->
                        Surface(
                            shape = M3EIconContainerShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSubject(subject.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${chapter.number}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${subject.name} • ${chapter.weightageMarks} Marks Weightage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
