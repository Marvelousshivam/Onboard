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
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.models.Chapter
import com.boardsprep.onboard.data.models.Subject
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.expressiveBounce
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Composable
fun Modifier.springBounceClick(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier = this.expressiveBounce(scaleOnPress = 0.95f, onClick = onClick)

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
    onToolsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }
    val syncManager = remember { com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context) }
    val streakInfo by syncManager.streakState.collectAsState()
    val accountInfo by syncManager.accountState.collectAsState()
    val unresolvedErrorCount by repository.getUnresolvedErrorCount().collectAsState(initial = 0)
    var showStreakDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }


    // Live countdown to CBSE Board Exams 2027 (Feb 15, 2027)
    val isDark = isSystemInDarkTheme()
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
                            shape = M3EIconContainerShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(40.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
                    // Global Search & Command Quick Jump Trigger
                    IconButton(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier.padding(end = 4.dp).size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search & Quick Jump",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // WCAG AA Compliant High-Contrast Compact Streak Pill (Fire + Count)
                    val streakDays = streakInfo.currentStreak
                    val streakText = streakDays.toString()
                    val isToday = streakInfo.isStreakActiveToday
                    val pillBg = if (isToday) AccentAmber else AccentAmberLight
                    val pillContent = if (isToday) Color.White else AccentAmberOnBg

                    Surface(
                        onClick = { showStreakDialog = true },
                        shape = ExpressivePillSmall,
                        color = pillBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Study Streak",
                                tint = pillContent,
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
                    Surface(
                        onClick = onAccountClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 12.dp).size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (accountInfo.isAnonymous) Icons.Default.CloudSync else Icons.Default.AccountCircle,
                                contentDescription = "Account & Cloud Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                verticalArrangement = Arrangement.spacedBy(18.dp),
                // 130dp bottom padding ensures the last card is 100% visible ABOVE the floating dock
                contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp)
            ) {
                // ── Tier 1: Google Pixel At-A-Glance Hero Widget ────────────────
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(28.dp)
                            ),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "CBSE Class 12 • 2027",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Board Examination",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Target: 95%+ Aggregate • 15 Feb 2027",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Google Clock-Style Countdown Pill
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 4.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$daysRemaining",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text(
                                            text = "DAYS LEFT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // One prominent, confident Google CTA
                            Button(
                                onClick = {
                                    if (subjects.isNotEmpty()) {
                                        onSubjectClick(subjects.first().id)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ExpressivePillSmall,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Resume Today's Study",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ── Tier 2: Board Prep Hub (Google M3 Expressive 2x2 Bento) ─────
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Board Prep Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = ExpressivePillSmall,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "High-Agency",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 1 of 2x2 Bento: 3h Exam Sim + Daily Recall Blitz
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            M3ExpressiveDeckCard(
                                title = "3h Exam Sim",
                                subtitle = "Official SQP & Timing",
                                badge = "Sim",
                                icon = Icons.Default.Bolt,
                                containerColor = if (isDark) MdSurfaceVariantDark else AccentAmberLight,
                                onContainerColor = if (isDark) AccentAmber else AccentAmberOnBg,
                                accentColor = AccentAmber,
                                modifier = Modifier.weight(1f),
                                onClick = { onSamplePapersClick("") }
                            )

                            M3ExpressiveDeckCard(
                                title = "Recall Blitz",
                                subtitle = "5-Min Memory Drill",
                                badge = "Daily",
                                icon = Icons.Default.Timer,
                                containerColor = if (isDark) MdSurfaceVariantDark else PhysicsLightBg,
                                onContainerColor = if (isDark) PhysicsLavender else PhysicsLightOnBg,
                                accentColor = PhysicsAccent,
                                modifier = Modifier.weight(1f),
                                onClick = onDailyBlitzClick
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 2 of 2x2 Bento: Mistake Vault + Board Tools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            M3ExpressiveDeckCard(
                                title = "Mistake Vault",
                                subtitle = if (unresolvedErrorCount > 0) "$unresolvedErrorCount to Review" else "All Clean",
                                badge = if (unresolvedErrorCount > 0) "$unresolvedErrorCount Open" else "Clean",
                                icon = Icons.Default.AutoFixHigh,
                                containerColor = if (isDark) MdSurfaceVariantDark else EnglishLightBg,
                                onContainerColor = if (isDark) EnglishRose else EnglishLightOnBg,
                                accentColor = EnglishAccent,
                                modifier = Modifier.weight(1f),
                                onClick = onErrorVaultClick
                            )

                            M3ExpressiveDeckCard(
                                title = "Board Tools",
                                subtitle = "Rule 40.1 & Best 5",
                                badge = "Calc",
                                icon = Icons.Default.Calculate,
                                containerColor = if (isDark) MdSurfaceVariantDark else ChemistryLightBg,
                                onContainerColor = if (isDark) ChemistryMint else ChemistryLightOnBg,
                                accentColor = ChemistryAccent,
                                modifier = Modifier.weight(1f),
                                onClick = onToolsClick
                            )
                        }
                    }
                }

                // Tier 2: Academic Subjects Header (Option A: 2-Column M3 Expressive Grid)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Academic Subjects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = ExpressivePillSmall,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${subjects.size} Curricula",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
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

            // Adaptive Bottom Gradient Scrim (Content softly fades behind the dock)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            // Google M3 Expressive Floating Navigation Dock
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = M3EFloatingDockShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Home (Active Tab)
                        DockNavigationItem(
                            icon = Icons.Default.Home,
                            label = "Home",
                            isSelected = true,
                            onClick = { /* Already on Home */ }
                        )

                        // 2. CBSE Sample Papers & 3h Exam Sim
                        DockNavigationItem(
                            icon = Icons.Default.Description,
                            label = "Papers",
                            isSelected = false,
                            onClick = { onSamplePapersClick("") }
                        )

                        // 3. High-Yield Handbooks
                        DockNavigationItem(
                            icon = Icons.Default.Bookmarks,
                            label = "Handbook",
                            isSelected = false,
                            onClick = { onHandbookClick("physics") }
                        )

                        // 4. Offline Library & Downloads
                        DockNavigationItem(
                            icon = Icons.Default.CloudDownload,
                            label = "Offline",
                            isSelected = false,
                            onClick = onDownloadsClick
                        )
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
        modifier = modifier
            .springBounceClick(onClick = onClick)
            .border(1.dp, accentColor.copy(alpha = 0.25f), M3EBentoTileShape),
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
                    shape = ExpressivePillSmall,
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
            .springBounceClick(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = M3EBentoTileShape
            ),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                // Saturated Squircle Icon Container
                Surface(
                    shape = M3EIconContainerShape,
                    color = cardBg,
                    modifier = Modifier.size(42.dp)
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    shape = ExpressivePillSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Code ${subject.code}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${subject.chapters.size} Ch • ${subject.totalTheoryMarks}M Th",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Quick Status Pill
            Surface(
                shape = ExpressivePillSmall,
                color = cardBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "4 Pillars Ready",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = onCardColor,
                        fontSize = 10.sp
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
            .springBounceClick(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = M3ESubjectCardShape
            ),
        shape = M3ESubjectCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
