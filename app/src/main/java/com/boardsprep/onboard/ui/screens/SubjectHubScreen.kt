package com.boardsprep.onboard.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.lightweightLiquidGlass
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionPill
import com.boardsprep.onboard.core.glass.components.LiquidGlassBackButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassChip
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.models.Chapter
import com.boardsprep.onboard.data.models.Subject
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import com.boardsprep.onboard.ui.components.expressiveBounce
import kotlinx.coroutines.launch

@Composable
private fun Modifier.hubSpringBounce(onClick: () -> Unit): Modifier = this.expressiveBounce(onClick = onClick)

fun getSubjectThemeTokens(subjectId: String): Triple<Color, Color, Color> {
    val tokens = getAdaptiveSubjectTokens(subjectId, false)
    return Triple(tokens.containerColor, tokens.onContainerColor, tokens.accentColor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectHubScreen(
    subject: Subject,
    onBackClick: () -> Unit,
    onChapterClick: (String) -> Unit,
    onHandbookClick: () -> Unit,
    onPlayLecture: (lectureId: String, url: String, title: String) -> Unit = { _, _, _ -> },
    onOpenPdf: (url: String, title: String) -> Unit = { _, _ -> },
    onSamplePapersClick: (String) -> Unit = {},
    onQuestionBankClick: (String) -> Unit = {}
) {
    var showBlueprintDialog by remember { mutableStateOf(false) }
    var showSubjectPlaylistDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { BoardsRepository(context) }
    val blueprint = remember(subject.id) { repository.getBlueprintForSubject(subject.id) }
    val subjectVideos = remember(subject) { subject.chapters.flatMap { it.videosList }.filter { it.title.isNotBlank() } }
    val subjectSamplePapers = remember(subject.id) { repository.getSamplePapersForSubject(subject.id) }

    val allMastery by repository.getAllMastery().collectAsState(initial = emptyList())
    val masteryMap = remember(allMastery) { allMastery.associateBy { it.chapterId } }

    val isDark = isSystemInDarkTheme()
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val subjectTokens = remember(subject.id, isDark) { getAdaptiveSubjectTokens(subject.id, isDark) }
    val cardBg = subjectTokens.containerColor
    val onCardColor = subjectTokens.onContainerColor
    val accentColor = subjectTokens.accentColor

    // Mastery Metrics for Psychology & Progress Architecture
    val totalChapters = subject.chapters.size
    val masteredChaptersCount = remember(subject.chapters, masteryMap) {
        subject.chapters.count { ch ->
            val m = masteryMap[ch.id]
            m != null && m.theoryCompleted && m.ncertCompleted && m.exemplarCompleted && m.pyqCompleted
        }
    }
    val inProgressChaptersCount = remember(subject.chapters, masteryMap) {
        subject.chapters.count { ch ->
            val m = masteryMap[ch.id]
            if (m == null) false
            else {
                val hasAny = m.theoryCompleted || m.ncertCompleted || m.exemplarCompleted || m.pyqCompleted
                val hasAll = m.theoryCompleted && m.ncertCompleted && m.exemplarCompleted && m.pyqCompleted
                hasAny && !hasAll
            }
        }
    }
    val highYieldChaptersCount = remember(subject.chapters) {
        subject.chapters.count { it.weightageMarks >= 7 }
    }

    val progressFloat = remember(masteredChaptersCount, totalChapters) {
        if (totalChapters > 0) masteredChaptersCount.toFloat() / totalChapters else 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFloat,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "subject_mastery_anim"
    )

    // Filter Logic
    val filteredChapters = remember(subject.chapters, selectedFilter, masteryMap) {
        when (selectedFilter) {
            "High-Yield (>6M)" -> subject.chapters.filter { it.weightageMarks >= 7 }
            "In Progress" -> subject.chapters.filter { ch ->
                val m = masteryMap[ch.id]
                m != null && (m.theoryCompleted || m.ncertCompleted || m.exemplarCompleted || m.pyqCompleted) &&
                        !(m.theoryCompleted && m.ncertCompleted && m.exemplarCompleted && m.pyqCompleted)
            }
            "Mastered" -> subject.chapters.filter { ch ->
                val m = masteryMap[ch.id]
                m != null && m.theoryCompleted && m.ncertCompleted && m.exemplarCompleted && m.pyqCompleted
            }
            else -> subject.chapters
        }
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
                            color = cardBg,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (subject.id.lowercase()) {
                                        "english" -> Icons.AutoMirrored.Filled.MenuBook
                                        "physics" -> Icons.Default.Hub
                                        "chemistry" -> Icons.Default.Science
                                        "maths" -> Icons.Default.Functions
                                        "biology" -> Icons.Default.Biotech
                                        else -> Icons.Default.School
                                    },
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = subject.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Code ${subject.code} • CBSE Board 2027",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    LiquidGlassBackButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
        ) {
            // Tier 1: Dynamic Subject Bento Hero Deck
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isGlass) Modifier.lightweightLiquidGlass(
                                shape = M3EBentoHeroShape,
                                fallbackColor = cardBg
                            ) else Modifier.border(1.dp, accentColor.copy(alpha = 0.25f), M3EBentoHeroShape)
                        ),
                    shape = M3EBentoHeroShape,
                    colors = CardDefaults.cardColors(containerColor = if (isGlass) Color.Transparent else cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Top Meta Micro-Typography (Clean, zero pill containers)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${subject.stream.uppercase()} STREAM • CBSE 2026-27",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                color = onCardColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${subject.totalTheoryMarks}M THEORY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = accentColor
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress Metric & Zeigarnik Effect Anchor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "${(progressFloat * 100).toInt()}% Mastered",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = onCardColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$masteredChaptersCount of $totalChapters Chapters 100% Prepared",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = onCardColor.copy(alpha = 0.8f)
                                )
                            }
                            Text(
                                text = if (masteredChaptersCount == totalChapters) "All Mastered!" else if (inProgressChaptersCount > 0) "$inProgressChaptersCount In Progress" else "Ready to Begin",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (masteredChaptersCount == totalChapters) SuccessGreen else accentColor
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Animated High-Contrast Progress Indicator (Clean track, zero rogue stop dots)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                        ) {
                            if (animatedProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = animatedProgress.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(accentColor)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Action 2x2 Grid (Spacious, no text wrapping mid-word, zero 1px borders)
                        val quickButtonShape = RoundedCornerShape(14.dp)
                        val quickButtonBg = if (isDark) Color(0xFF1E202E) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Blueprint + Handbook/Formulas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = quickButtonShape,
                                    color = quickButtonBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .hubSpringBounce { showBlueprintDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Analytics,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Blueprint",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onCardColor,
                                            maxLines = 1
                                        )
                                    }
                                }

                                val hasHandbook = subject.id == "physics" || subject.id == "chemistry"
                                val handbookTitle = if (subject.id == "physics") "Formulas" else if (subject.id == "chemistry") "Reactions" else "Handbooks"
                                Surface(
                                    shape = quickButtonShape,
                                    color = quickButtonBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .hubSpringBounce(onClick = onHandbookClick)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = handbookTitle,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onCardColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Row 2: One-Shots + SQP 2027
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = quickButtonShape,
                                    color = quickButtonBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .hubSpringBounce { showSubjectPlaylistDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (subjectVideos.isNotEmpty()) "One-Shots (${subjectVideos.size})" else "One-Shots",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onCardColor,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    shape = quickButtonShape,
                                    color = quickButtonBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .hubSpringBounce { onSamplePapersClick(subject.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "SQP 2027",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onCardColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tier 2: M3 Expressive Filter Bar & Curriculum Chapters
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Curriculum Chapters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${filteredChapters.size} of $totalChapters Chapters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val filterTabs = listOf(
                        ExpressiveTabItem("All", badge = "$totalChapters"),
                        ExpressiveTabItem("High-Yield (>6M)", badge = "$highYieldChaptersCount"),
                        ExpressiveTabItem("In Progress", badge = "$inProgressChaptersCount"),
                        ExpressiveTabItem("Mastered", badge = "$masteredChaptersCount")
                    )
                    val selectedIndex = when (selectedFilter) {
                        "High-Yield (>6M)" -> 1
                        "In Progress" -> 2
                        "Mastered" -> 3
                        else -> 0
                    }

                    ExpressiveSegmentedTabs(
                        tabs = filterTabs,
                        selectedTabIndex = selectedIndex,
                        onTabSelected = { idx ->
                            selectedFilter = when (idx) {
                                1 -> "High-Yield (>6M)"
                                2 -> "In Progress"
                                3 -> "Mastered"
                                else -> "All"
                            }
                        },
                        accentColor = accentColor,
                        contentPadding = PaddingValues(0.dp)
                    )
                }
            }

            // Tier 4: Expressive Chapter Bento Cards
            if (filteredChapters.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = M3EBentoTileShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No chapters matching '$selectedFilter'",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = { selectedFilter = "All" }) {
                                Text("Reset to All Chapters", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                items(filteredChapters) { chapter ->
                    val mastery = masteryMap[chapter.id]
                    ExpressiveChapterHubCard(
                        chapter = chapter,
                        mastery = mastery,
                        subjectAccent = accentColor,
                        subjectBg = cardBg,
                        onCardColor = onCardColor,
                        onClick = { onChapterClick(chapter.id) },
                        onQuickTogglePillar = { pillarIndex, isDone ->
                            coroutineScope.launch {
                                val current = mastery ?: ChapterMasteryEntity(chapter.id)
                                val updated = when (pillarIndex) {
                                    0 -> current.copy(theoryCompleted = isDone)
                                    1 -> current.copy(ncertCompleted = isDone)
                                    2 -> current.copy(exemplarCompleted = isDone)
                                    3 -> current.copy(pyqCompleted = isDone)
                                    else -> current
                                }
                                repository.saveMastery(updated)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showBlueprintDialog) {
        AlertDialog(
            onDismissRequest = { showBlueprintDialog = false },
            title = {
                Column {
                    Text(
                        text = "${subject.name} (Code ${subject.code})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Official CBSE Class 12 Blueprint (3 Hours)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (blueprint.competencyBreakdown.isNotEmpty()) {
                        Surface(
                            color = cardBg,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = blueprint.competencyBreakdown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onCardColor,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Text(
                        text = "Question Paper Pattern (${blueprint.totalQuestions} Questions • ${blueprint.totalMarks} Marks):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    blueprint.sections.forEach { sec ->
                        BlueprintRow(
                            section = sec.sectionName,
                            details = "${sec.numberOfQuestions} Qs • ${sec.questionType}",
                            marks = "${sec.totalMarks} M",
                            accentColor = accentColor
                        )
                    }

                    if (blueprint.notes.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = blueprint.notes,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBlueprintDialog = false },
                    shape = ExpressivePillSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Understood", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSubjectPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showSubjectPlaylistDialog = false },
            title = {
                Column {
                    Text(
                        text = "${subject.name} One-Shots",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Curated Board Revision Lectures (${subjectVideos.size} Videos)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(subjectVideos) { lec ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hubSpringBounce {
                                    showSubjectPlaylistDialog = false
                                    onPlayLecture(lec.id, lec.youtubeUrl, lec.title)
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = M3ESquircleBadgeShape,
                                    color = cardBg,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lec.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(lec.durationText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("• ${lec.author}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubjectPlaylistDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun BlueprintRow(section: String, details: String, marks: String, accentColor: Color = PrimaryBlue) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(section, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(marks, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = accentColor)
    }
}

@Composable
fun ExpressiveChapterHubCard(
    chapter: Chapter,
    mastery: ChapterMasteryEntity?,
    subjectAccent: Color,
    subjectBg: Color,
    onCardColor: Color,
    onClick: () -> Unit,
    onQuickTogglePillar: (pillarIndex: Int, isDone: Boolean) -> Unit
) {
    val isFullyMastered = mastery != null &&
            mastery.theoryCompleted && mastery.ncertCompleted && mastery.exemplarCompleted && mastery.pyqCompleted
    val isHighYield = chapter.weightageMarks >= 7
    val isDark = isSystemInDarkTheme()
    val cardBackground = if (isDark) Color(0xFF161722) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hubSpringBounce(onClick = onClick),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Clean Top Row: Muted Chapter Number & Weightage (No pill capsules) + Priority Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHAPTER ${chapter.number}  •  ${chapter.weightageMarks} MARKS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isFullyMastered) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Mastered",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    } else if (isHighYield) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "High Yield",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chapter Title & Forward Affordance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )

                    if (chapter.keyTopics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = chapter.keyTopics.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Pillar Active Recall Progress Ribbon with Quick Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InteractiveMasteryPill(
                    title = "Theory",
                    isCompleted = mastery?.theoryCompleted == true,
                    modifier = Modifier.weight(1f),
                    onToggle = { onQuickTogglePillar(0, !(mastery?.theoryCompleted == true)) }
                )
                InteractiveMasteryPill(
                    title = "NCERT",
                    isCompleted = mastery?.ncertCompleted == true,
                    modifier = Modifier.weight(1f),
                    onToggle = { onQuickTogglePillar(1, !(mastery?.ncertCompleted == true)) }
                )
                InteractiveMasteryPill(
                    title = "Exemplar",
                    isCompleted = mastery?.exemplarCompleted == true,
                    modifier = Modifier.weight(1f),
                    onToggle = { onQuickTogglePillar(2, !(mastery?.exemplarCompleted == true)) }
                )
                InteractiveMasteryPill(
                    title = "PYQs",
                    isCompleted = mastery?.pyqCompleted == true,
                    modifier = Modifier.weight(1f),
                    onToggle = { onQuickTogglePillar(3, !(mastery?.pyqCompleted == true)) }
                )
            }
        }
    }
}

@Composable
fun InteractiveMasteryPill(
    title: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Surface(
        shape = ExpressivePillSmall,
        color = if (isCompleted) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .height(30.dp)
            .hubSpringBounce(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(11.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Medium,
                color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
