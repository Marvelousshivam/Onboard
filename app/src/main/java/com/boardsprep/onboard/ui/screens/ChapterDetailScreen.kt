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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.models.Chapter
import com.boardsprep.onboard.data.models.Lecture
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveEmptyState
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import com.boardsprep.onboard.ui.components.expressiveBounce

@Composable
private fun Modifier.detailSpringBounce(onClick: () -> Unit): Modifier = this.expressiveBounce(onClick = onClick)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDetailScreen(
    chapter: Chapter,
    lectures: List<Lecture>,
    mastery: ChapterMasteryEntity?,
    onBackClick: () -> Unit,
    onLectureClick: (Lecture) -> Unit,
    onDownloadLectureClick: (Lecture) -> Unit,
    onOpenPdf: (url: String, title: String) -> Unit,
    onOpenQuiz: (quizFile: String, title: String) -> Unit,
    onMasteryChange: (ChapterMasteryEntity) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val isDark = isSystemInDarkTheme()
    val subjectTokens = remember(chapter.subjectId, isDark) {
        getAdaptiveSubjectTokens(chapter.subjectId, isDark)
    }
    val cardBg = subjectTokens.containerColor
    val onCardColor = subjectTokens.onContainerColor
    val accentColor = subjectTokens.accentColor

    // Counts for M3 Expressive Tabs
    val validLectures = remember(lectures) { lectures.filter { it.title.isNotBlank() } }
    val notesCount = remember(chapter) {
        var count = 1 // NCERT Textbook
        if (chapter.notesList.isNotEmpty()) count += chapter.notesList.size
        else if (chapter.notesPdfUrl.isNotBlank()) count += 1
        if (chapter.exemplarPdfUrl.isNotBlank()) count += 1
        if (chapter.subjectId == "english") count += 1
        count
    }
    val quizzesCount = remember(chapter) {
        if (chapter.quizzesList.isNotEmpty()) chapter.quizzesList.size else 2
    }
    val completedPillars = remember(mastery) {
        var count = 0
        if (mastery?.theoryCompleted == true) count++
        if (mastery?.ncertCompleted == true) count++
        if (mastery?.exemplarCompleted == true) count++
        if (mastery?.pyqCompleted == true) count++
        count
    }

    val tabs = listOf(
        ExpressiveTabItem("Lectures", Icons.Default.PlayCircle, "${validLectures.size}"),
        ExpressiveTabItem("NCERT & Notes", Icons.AutoMirrored.Filled.MenuBook, "$notesCount"),
        ExpressiveTabItem("DPPs", Icons.Default.Quiz, "$quizzesCount"),
        ExpressiveTabItem("Mastery", Icons.Default.CheckCircle, "$completedPillars/4")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chapter.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Chapter ${chapter.number} • ${chapter.weightageMarks} Marks CBSE 2027",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = ExpressivePillSmall,
                            color = if (completedPillars == 4) SuccessGreen.copy(alpha = 0.15f) else cardBg,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (completedPillars == 4) Icons.Default.CheckCircle else Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = if (completedPillars == 4) SuccessGreen else accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$completedPillars/4 Pillars",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (completedPillars == 4) SuccessGreen else onCardColor
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // M3 Expressive Pill Tabs Row
            ExpressiveSegmentedTabs(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                accentColor = accentColor
            )


            // Tab Content
            when (selectedTabIndex) {
                0 -> ExpressiveLecturesTab(
                    lectures = validLectures,
                    chapterName = chapter.name,
                    accentColor = accentColor,
                    cardBg = cardBg,
                    onCardColor = onCardColor,
                    onLectureClick = onLectureClick,
                    onDownloadLectureClick = onDownloadLectureClick
                )
                1 -> ExpressiveNcertNotesTab(
                    chapter = chapter,
                    accentColor = accentColor,
                    onOpenPdf = onOpenPdf
                )
                2 -> ExpressiveDppQuizzesTab(
                    chapter = chapter,
                    accentColor = accentColor,
                    onOpenQuiz = onOpenQuiz,
                    onOpenPdf = onOpenPdf
                )
                3 -> ExpressiveMasteryChecklistTab(
                    chapter = chapter,
                    mastery = mastery ?: ChapterMasteryEntity(chapter.id),
                    cardBg = cardBg,
                    onCardColor = onCardColor,
                    accentColor = accentColor,
                    onMasteryChange = onMasteryChange
                )
            }
        }
    }
}

private data class TabItem(val icon: ImageVector, val title: String, val badge: String)

@Composable
fun ExpressiveLecturesTab(
    lectures: List<Lecture>,
    chapterName: String,
    accentColor: Color,
    cardBg: Color,
    onCardColor: Color,
    onLectureClick: (Lecture) -> Unit,
    onDownloadLectureClick: (Lecture) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Helpful Offline / Quality Banner
        item {
            Card(
                shape = M3EBentoTileShape,
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = M3ESquircleBadgeShape,
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tip: Download with resolution options or place custom .mp4 files into the 'lectures/' folder for zero-data offline playback.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = onCardColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (lectures.isEmpty()) {
            item {
                ExpressiveEmptyState(
                    icon = Icons.Default.SmartDisplay,
                    title = "No Video Lectures Linked Yet",
                    description = "Add YouTube links in chapter manifest or place offline .mp4 video files in the 'lectures/' directory.",
                    tintColor = accentColor
                )
            }
        } else {
            items(lectures) { lecture ->
                val displayTitle = lecture.title.ifBlank { "$chapterName — Video Lecture" }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .detailSpringBounce { onLectureClick(lecture) }
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), M3EBentoTileShape),
                    shape = M3EBentoTileShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = M3ESquircleBadgeShape,
                            color = if (lecture.isUserAdded) AccentAmber.copy(alpha = 0.15f) else cardBg,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (lecture.isUserAdded) Icons.Default.FolderZip else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (lecture.isUserAdded) AccentAmberOnBg else accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (lecture.durationText.isNotBlank()) {
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = accentColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = lecture.durationText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = lecture.author,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (lecture.isDownloaded) {
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = SuccessGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Downloaded",
                                            fontSize = 9.sp,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Play Action Pill Button
                        Surface(
                            shape = ExpressivePillSmall,
                            color = accentColor,
                            modifier = Modifier
                                .height(36.dp)
                                .detailSpringBounce { onLectureClick(lecture) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(onClick = { onDownloadLectureClick(lecture) }) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download Lecture",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveNcertNotesTab(
    chapter: Chapter,
    accentColor: Color,
    onOpenPdf: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val localNotes = remember(chapter.name) { com.boardsprep.onboard.core.pdf.LocalDocumentScanner.findNotesForChapter(context, chapter.name) }
    val localNcert = remember(chapter.name) { com.boardsprep.onboard.core.pdf.LocalDocumentScanner.findNcertForChapter(context, chapter.name, chapter.ncertPdfUrl) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Official NCERT Textbook Chapter
        item {
            ExpressivePdfCard(
                title = "NCERT Textbook Chapter",
                subtitle = if (localNcert != null) "Available Offline • Official Textbook" else "Official CBSE Textbook PDF with back exercise questions",
                tag = "Textbook",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                color = PrimaryBlue,
                onClick = {
                    val targetUrl = localNcert?.absolutePath ?: chapter.ncertPdfUrl
                    onOpenPdf(targetUrl, "${chapter.name} — NCERT")
                }
            )
        }

        // 2. Dynamic Revision Notes from GitHub
        if (chapter.notesList.isNotEmpty()) {
            items(chapter.notesList) { note ->
                ExpressivePdfCard(
                    title = note.title,
                    subtitle = "Key formulas, summary & board points (Cloud Sync)",
                    tag = "Revision Notes",
                    icon = Icons.Default.EditNote,
                    color = SuccessGreen,
                    onClick = { onOpenPdf(note.url, "${chapter.name} — ${note.title}") }
                )
            }
        } else if (chapter.notesPdfUrl.isNotEmpty() || localNotes != null) {
            item {
                ExpressivePdfCard(
                    title = "Chapter Revision Notes",
                    subtitle = if (localNotes != null) "Available Offline • Handwritten Board Notes" else "Key formulas, summary & board points (GitHub Sync)",
                    tag = "Revision Notes",
                    icon = Icons.Default.EditNote,
                    color = SuccessGreen,
                    onClick = {
                        val targetUrl = localNotes?.absolutePath ?: chapter.notesPdfUrl
                        onOpenPdf(targetUrl, "${chapter.name} — Revision Notes")
                    }
                )
            }
        }

        // 3. Exemplar Problems
        if (chapter.exemplarPdfUrl.isNotEmpty()) {
            item {
                ExpressivePdfCard(
                    title = "NCERT Exemplar Problems",
                    subtitle = "High-order thinking MCQs, case studies and board level problems",
                    tag = "HOTS / MCQs",
                    icon = Icons.Default.Quiz,
                    color = AccentAmber,
                    onClick = { onOpenPdf(chapter.exemplarPdfUrl, "${chapter.name} — Exemplar") }
                )
            }
        }

        // 4. Writing Section Notes (for English)
        if (chapter.subjectId == "english") {
            item {
                val writingNotes = remember { com.boardsprep.onboard.core.pdf.LocalDocumentScanner.getWritingSectionNotes(context) }
                val firstWritingNote = writingNotes.firstOrNull()
                ExpressivePdfCard(
                    title = "Writing Section Hand Written Notes",
                    subtitle = if (firstWritingNote != null) "Available Offline • Notices, Letters, Reports & Articles" else "CBSE Official Formats, Notices, Letters & Reports",
                    tag = "Formats & Notes",
                    icon = Icons.Default.Description,
                    color = AccentAmber,
                    onClick = {
                        val targetUrl = firstWritingNote?.absolutePath ?: "${BoardsRepository.GITHUB_RAW_BASE}/notes/english/writing_section/class_12th_writing_section_formats.pdf"
                        onOpenPdf(targetUrl, "Class 12 Writing Section Formats & Notes")
                    }
                )
            }
        }

        // 5. Offline storage guide card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = M3EBentoTileShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto Cloud & Offline Storage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PDFs are cached locally on first view for offline access. You can also place custom .pdf documents into the app storage folder.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressivePdfCard(
    title: String,
    subtitle: String,
    tag: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .detailSpringBounce(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), M3EBentoTileShape),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = M3ESquircleBadgeShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = ExpressivePillSmall,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Read PDF",
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveDppQuizzesTab(
    chapter: Chapter,
    accentColor: Color,
    onOpenQuiz: (quizFile: String, title: String) -> Unit,
    onOpenPdf: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val localDpp = remember(chapter.name) { com.boardsprep.onboard.core.pdf.LocalDocumentScanner.findDppForChapter(context, chapter.name) }
    val dppPdfTarget = localDpp?.absolutePath ?: chapter.dppPdfUrl

    val fallbackQuizFiles = when (chapter.name) {
        "The Last Lesson" -> listOf("flamingo_ch01_dpp01.json" to "Part 1 DPP (10 MCQs)", "flamingo_ch01_dpp02.json" to "Part 2 DPP (10 MCQs)")
        "Lost Spring" -> listOf("flamingo_ch02_dpp01.json" to "Saheb-e-Alam DPP (10 MCQs)", "flamingo_ch02_dpp02.json" to "Mukesh & Firozabad DPP (10 MCQs)")
        "Deep Water" -> listOf("flamingo_ch03_dpp01.json" to "YMCA Pool DPP (10 MCQs)", "flamingo_ch03_dpp02.json" to "Overcoming Fear DPP (10 MCQs)")
        "The Rattrap" -> listOf("flamingo_ch04_dpp01.json" to "Crofter & Ironworks DPP (10 MCQs)", "flamingo_ch04_dpp02.json" to "Edla & Transformation DPP (10 MCQs)")
        "My Mother at Sixty-Six" -> listOf("flamingo_poem01_dpp01.json" to "Poem DPP (10 MCQs)")
        "Keeping Quiet" -> listOf("flamingo_poem02_dpp01.json" to "Poem DPP (10 MCQs)")
        "A Thing of Beauty" -> listOf("flamingo_poem03_dpp01.json" to "Poem DPP (10 MCQs)")
        "A Roadside Stand" -> listOf("flamingo_poem04_dpp01.json" to "Poem DPP (10 MCQs)")
        else -> listOf(
            "${chapter.id}_dpp01.json" to "CBSE 2027 DPP 01: Core Concepts & Formulae",
            "${chapter.id}_dpp02.json" to "CBSE 2027 DPP 02: Board PYQs & High-Yield MCQs"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header: Interactive Timed Quizzes
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Interactive Timed Quizzes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real-time timer • Instant step explanations & score analytics",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Dynamic Quizzes from GitHub or Fallback
        if (chapter.quizzesList.isNotEmpty()) {
            items(chapter.quizzesList) { quizItem ->
                QuizExpressiveCard(
                    title = quizItem.title,
                    subtitle = "${quizItem.totalQuestions} Questions • Timed Mode with Instant Solutions",
                    onClick = { onOpenQuiz(quizItem.url.ifEmpty { quizItem.filename }, "${chapter.name} — ${quizItem.title}") }
                )
            }
        } else {
            items(fallbackQuizFiles) { (filename, title) ->
                QuizExpressiveCard(
                    title = title,
                    subtitle = "10 Questions • Instant Solutions & Board Exam Timer",
                    onClick = { onOpenQuiz(filename, "${chapter.name} — $title") }
                )
            }
        }

        // Section Header: DPP Practice Sheets (PDF)
        if (chapter.dppsList.isNotEmpty() || dppPdfTarget.isNotBlank()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    Text(
                        text = "Official DPP Practice Sheets (PDF)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Handwriting & step-marking simulation on paper",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (chapter.dppsList.isNotEmpty()) {
                items(chapter.dppsList) { dppItem ->
                    DppPdfExpressiveCard(
                        title = dppItem.title,
                        subtitle = "Official Practice Sheet PDF • Paper Practice",
                        onClick = { onOpenPdf(dppItem.url, "${chapter.name} — ${dppItem.title}") }
                    )
                }
            } else if (dppPdfTarget.isNotBlank()) {
                item {
                    DppPdfExpressiveCard(
                        title = "Official DPP Question Sheet (PDF)",
                        subtitle = if (localDpp != null) "Available Offline • Full Practice Sheet" else "Teacher DPP Sheet with answers • Practice on paper",
                        onClick = { onOpenPdf(dppPdfTarget, "${chapter.name} — Teacher DPP Sheet") }
                    )
                }
            }
        }
    }
}

@Composable
fun QuizExpressiveCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .detailSpringBounce(onClick = onClick)
            .border(1.dp, AccentAmber.copy(alpha = 0.35f), M3EBentoTileShape),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = M3ESquircleBadgeShape,
                color = AccentAmber.copy(alpha = 0.15f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = AccentAmberOnBg, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = ExpressivePillSmall,
                color = AccentAmber,
                modifier = Modifier
                    .height(34.dp)
                    .detailSpringBounce(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start Quiz", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun DppPdfExpressiveCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .detailSpringBounce(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), M3EBentoTileShape),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = M3ESquircleBadgeShape,
                color = PrimaryBlue.copy(alpha = 0.15f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = ExpressivePillSmall,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .height(34.dp)
                    .detailSpringBounce(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Open PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun ExpressiveMasteryChecklistTab(
    chapter: Chapter,
    mastery: ChapterMasteryEntity,
    cardBg: Color,
    onCardColor: Color,
    accentColor: Color,
    onMasteryChange: (ChapterMasteryEntity) -> Unit
) {
    var completedCount = 0
    if (mastery.theoryCompleted) completedCount++
    if (mastery.ncertCompleted) completedCount++
    if (mastery.exemplarCompleted) completedCount++
    if (mastery.pyqCompleted) completedCount++

    val progressRatio = completedCount.toFloat() / 4f
    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "mastery_checklist_anim"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gamified Mastery Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (completedCount == 4) SuccessGreen.copy(alpha = 0.4f) else accentColor.copy(alpha = 0.25f),
                        M3EBentoHeroShape
                    ),
                shape = M3EBentoHeroShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (completedCount == 4) SuccessGreenLight else cardBg
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(68.dp),
                            color = if (completedCount == 4) SuccessGreen else accentColor,
                            trackColor = (if (completedCount == 4) SuccessGreen else accentColor).copy(alpha = 0.18f),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = if (completedCount == 4) SuccessGreen else onCardColor
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (completedCount == 4) "100% Board Mastered!" else "Preparation Progress",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (completedCount == 4) SuccessGreen else onCardColor
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = when (completedCount) {
                                0 -> "Start with Theory & Video Lectures to build core concepts."
                                1 -> "1 of 4 Done! Next, solve NCERT in-text & back exercises."
                                2 -> "Halfway there! Tackle NCERT Exemplar for high-yield MCQs."
                                3 -> "Almost ready! Solve Last 10 Years PYQs for exam simulation."
                                else -> "🎉 Fantastic! You have completed all 4 pillars for Chapter ${chapter.number}."
                            },
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (completedCount == 4) SuccessGreen else onCardColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "The 4 Pillars of CBSE Board Success",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Pillar 1: Theory
        item {
            ExpressiveChecklistItem(
                pillarNumber = "Pillar 1",
                title = "Theory & Core Concepts",
                description = "Watched one-shot video lecture and thoroughly studied notes/formula sheet.",
                whyItMatters = "Builds 100% conceptual clarity for both assertion-reason and long derivation questions.",
                checked = mastery.theoryCompleted,
                onCheckedChange = { onMasteryChange(mastery.copy(theoryCompleted = it)) }
            )
        }

        // Pillar 2: NCERT
        item {
            ExpressiveChecklistItem(
                pillarNumber = "Pillar 2",
                title = "NCERT Exercise Questions",
                description = "Solved all in-text examples, box points, and end-of-chapter textbook problems.",
                whyItMatters = "Over 60-70% of CBSE board questions are framed directly from NCERT back exercises.",
                checked = mastery.ncertCompleted,
                onCheckedChange = { onMasteryChange(mastery.copy(ncertCompleted = it)) }
            )
        }

        // Pillar 3: Exemplar
        item {
            ExpressiveChecklistItem(
                pillarNumber = "Pillar 3",
                title = "NCERT Exemplar Problems",
                description = "Practiced high-order thinking MCQs, multi-concept problems, and case studies.",
                whyItMatters = "Targets Section A MCQs and 4-mark Case Study questions with tricky conceptual traps.",
                checked = mastery.exemplarCompleted,
                onCheckedChange = { onMasteryChange(mastery.copy(exemplarCompleted = it)) }
            )
        }

        // Pillar 4: PYQs
        item {
            ExpressiveChecklistItem(
                pillarNumber = "Pillar 4",
                title = "Last 10 Years CBSE PYQs",
                description = "Solved past board questions with official CBSE step-marking rubrics.",
                whyItMatters = "Familiarizes with recurring board patterns and guarantees maximum step-wise marks.",
                checked = mastery.pyqCompleted,
                onCheckedChange = { onMasteryChange(mastery.copy(pyqCompleted = it)) }
            )
        }

        // Section: CBSE Step-Marking Rubric Assistant ("Examiner Mode")
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CBSE Examiner Step-Marking Rubrics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = ExpressivePillSmall,
                        color = AccentAmber.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Examiner Mode",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "See exactly where examiners award or deduct marks for Chapter ${chapter.number} questions.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            val sampleRubric = remember(chapter.name) {
                com.boardsprep.onboard.ui.components.CbseQuestionRubric(
                    questionTitle = "High-Yield Derivation / Core Problem: ${chapter.name}",
                    totalMarks = 3.0f,
                    subject = chapter.subjectId,
                    steps = listOf(
                        com.boardsprep.onboard.ui.components.CbseRubricStep(
                            stepNumber = 1,
                            title = "Formula Statement & Labeled Diagram",
                            marks = 1.0f,
                            description = "State governing laws, definitions, or draw standard circuit/ray diagram with directional arrows.",
                            examinerKeyNote = "Deducts 0.5M if arrows on rays or magnetic fields are omitted."
                        ),
                        com.boardsprep.onboard.ui.components.CbseRubricStep(
                            stepNumber = 2,
                            title = "Algebraic Substitution & Integration",
                            marks = 1.5f,
                            description = "Perform mathematical derivation or numerical substitution with explicit intermediate algebraic steps.",
                            examinerKeyNote = "Do not skip directly to final answer; intermediate working must be visible."
                        ),
                        com.boardsprep.onboard.ui.components.CbseRubricStep(
                            stepNumber = 3,
                            title = "Final Result in Box with SI Units",
                            marks = 0.5f,
                            description = "Enclose final numerical answer or formula in standard rectangle and write full SI units.",
                            examinerKeyNote = "Mandatory -0.5M penalty if SI unit is omitted or incorrect."
                        )
                    ),
                    commonExaminerTrap = "In CBSE evaluations, over 40% of students lose 0.5M by omitting units (e.g. 'm/s' or 'T') or drawing diagrams without direction arrows, even with 100% correct calculations!"
                )
            }
            com.boardsprep.onboard.ui.components.CbseRubricAccordion(rubric = sampleRubric)
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}


@Composable
fun ExpressiveChecklistItem(
    pillarNumber: String,
    title: String,
    description: String,
    whyItMatters: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .detailSpringBounce { onCheckedChange(!checked) }
            .border(
                1.dp,
                if (checked) SuccessGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                M3EBentoTileShape
            ),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(
            containerColor = if (checked) SuccessGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = SuccessGreen,
                    checkmarkColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (checked) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (checked) "Done" else pillarNumber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (checked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Why: $whyItMatters",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    lineHeight = 14.sp
                )
            }
        }
    }
}
