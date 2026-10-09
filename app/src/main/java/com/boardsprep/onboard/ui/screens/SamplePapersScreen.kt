package com.boardsprep.onboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.pdf.LocalDocumentScanner
import com.boardsprep.onboard.core.pdf.PdfPairedRole
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.models.SamplePaper
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveEmptyState
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import com.boardsprep.onboard.ui.components.expressiveBounce

/**
 * Callback for opening a PDF. Carries optional paired-document info so the
 * in-reader question-paper <-> marking-scheme switcher can be wired up.
 */
typealias OnOpenPdfWithPair = (
    url: String,
    title: String,
    pairedUrl: String,
    pairedTitle: String,
    pairedRole: PdfPairedRole
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplePapersScreen(
    initialSubjectId: String = "",
    onOpenPdf: OnOpenPdfWithPair,
    onStartExamSimulation: (paperTitle: String, subject: String, paperUrl: String, msUrl: String) -> Unit = { _, _, _, _ -> },
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }

    var selectedSubjectId by remember { mutableStateOf(initialSubjectId) }
    var selectedTypeFilter by remember { mutableStateOf("all") } // "all", "sqp", "ms"
    var searchQuery by remember { mutableStateOf("") }

    val allPapers = remember { repository.getSamplePapers() }

    val filteredPapers = remember(allPapers, selectedSubjectId, selectedTypeFilter, searchQuery) {
        allPapers.filter { paper ->
            val matchesSubject = selectedSubjectId.isBlank() || paper.subjectId.equals(selectedSubjectId, ignoreCase = true)
            val matchesType = selectedTypeFilter == "all" || paper.type.equals(selectedTypeFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    paper.title.contains(searchQuery, ignoreCase = true) ||
                    paper.subject.contains(searchQuery, ignoreCase = true)
            matchesSubject && matchesType && matchesQuery
        }
    }

    val subjects = listOf(
        "" to "All Subjects",
        "physics" to "Physics",
        "chemistry" to "Chemistry",
        "maths" to "Mathematics",
        "biology" to "Biology",
        "english" to "English Core",
        "physical_education" to "Physical Education"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CBSE Sample Papers 2027",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Official SQP & Marking Schemes • Class 12",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search sample papers, marking schemes...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = ExpressiveCardMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Paper Type Segmented Tabs (All, SQP, MS)
            val typeTabs = listOf(
                ExpressiveTabItem("All Papers", badge = "${allPapers.size}"),
                ExpressiveTabItem("Question Papers (SQP)", icon = Icons.Default.Description),
                ExpressiveTabItem("Marking Schemes (MS)", icon = Icons.Default.CheckCircle)
            )
            val selectedTypeIndex = when (selectedTypeFilter) {
                "sqp" -> 1
                "ms" -> 2
                else -> 0
            }

            ExpressiveSegmentedTabs(
                tabs = typeTabs,
                selectedTabIndex = selectedTypeIndex,
                onTabSelected = {
                    selectedTypeFilter = when (it) {
                        1 -> "sqp"
                        2 -> "ms"
                        else -> "all"
                    }
                },
                accentColor = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            )

            // Subject Filter Horizontal Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                subjects.forEach { (id, label) ->
                    val isSelected = selectedSubjectId == id
                    val subjectColor = when (id) {
                        "physics" -> PhysicsLavender
                        "chemistry" -> ChemistryMint
                        "maths" -> MathsPeach
                        "biology" -> BiologySky
                        "english" -> EnglishRose
                        "physical_education" -> AccentAmber
                        else -> MaterialTheme.colorScheme.primary
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSubjectId = id },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = subjectColor.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = ExpressivePillSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Content List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                // Official CBSE Pattern Guidance Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AsymmetricLeafHero,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Official CBSE 2026-27 Assessment Pattern",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Class 12 Board Examination SQP & Solutions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("50%", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Competency", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("20%", fontWeight = FontWeight.Black, fontSize = 13.sp, color = AccentAmber)
                                        Text("MCQ / Objective", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("30%", fontWeight = FontWeight.Black, fontSize = 13.sp, color = SuccessGreen)
                                        Text("Short & Long", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredPapers.isEmpty()) {
                    item {
                        ExpressiveEmptyState(
                            icon = Icons.Default.Description,
                            title = "No Papers Found",
                            description = "No sample papers match your current filters. Try selecting 'All Papers' or clearing the search keyword.",
                            tintColor = MaterialTheme.colorScheme.primary,
                            actionText = "Reset Filters",
                            onActionClick = {
                                selectedTypeFilter = "all"
                                selectedSubjectId = ""
                                searchQuery = ""
                            }
                        )
                    }
                } else {
                    items(filteredPapers) { paper ->
                        SamplePaperCard(
                            paper = paper,
                            allPapers = allPapers,
                            onOpenPdf = onOpenPdf,
                            onStartExamSimulation = onStartExamSimulation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplePaperCard(
    paper: SamplePaper,
    allPapers: List<SamplePaper>,
    onOpenPdf: OnOpenPdfWithPair,
    onStartExamSimulation: (paperTitle: String, subject: String, paperUrl: String, msUrl: String) -> Unit
) {
    val context = LocalContext.current
    val isSqp = paper.type.equals("sqp", ignoreCase = true)

    val subjectAccent = when (paper.subjectId) {
        "physics" -> PhysicsLavender
        "chemistry" -> ChemistryMint
        "maths" -> MathsPeach
        "biology" -> BiologySky
        "english" -> EnglishRose
        "physical_education" -> AccentAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val subjectContainer = when (paper.subjectId) {
        "physics" -> PhysicsLavenderContainer
        "chemistry" -> ChemistryMintContainer
        "maths" -> MathsPeachContainer
        "biology" -> BiologySkyContainer
        "english" -> EnglishRoseContainer
        "physical_education" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    // Find paired paper (e.g. if this is SQP, find MS, and vice versa)
    val pairedType = if (isSqp) "ms" else "sqp"
    val pairedPaper = remember(paper.subjectId, pairedType) {
        allPapers.find { it.subjectId == paper.subjectId && it.type.equals(pairedType, ignoreCase = true) }
    }

    val displaySubject = remember(paper.subject) {
        when (paper.subject.trim().lowercase()) {
            "englishcore", "english" -> "English Core"
            "physicaleducation", "pe" -> "Physical Education"
            "maths" -> "Mathematics"
            else -> paper.subject
        }
    }

    val displayTitle = remember(paper.title) {
        paper.title
            .replace("EnglishCore", "English Core")
            .replace("PhysicalEducation", "Physical Education")
    }

    val localCached = remember(paper.filename) {
        LocalDocumentScanner.findSamplePaper(context, paper.filename)
    }

    /**
     * Compute the paired document (url, title, role) for the *current* paper.
     * Used when the user opens this paper directly — the in-reader switcher
     * can then jump to the sibling SQP/MS in one tap.
     */
    fun pairedInfo(): Triple<String, String, PdfPairedRole> {
        val p = pairedPaper ?: return Triple("", "", PdfPairedRole.GENERAL)
        val pairedLocal = LocalDocumentScanner.findSamplePaper(context, p.filename)
        val pUrl = pairedLocal?.absolutePath ?: p.url
        val pTitle = p.title
            .replace("EnglishCore", "English Core")
            .replace("PhysicalEducation", "Physical Education")
        val role = if (isSqp) PdfPairedRole.MARKING_SCHEME else PdfPairedRole.QUESTION_PAPER
        return Triple(pUrl, pTitle, role)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveCardLarge)
            .clickable {
                val openUrl = localCached?.absolutePath ?: paper.url
                val (pairUrl, pairTitle, pairRole) = pairedInfo()
                onOpenPdf(openUrl, displayTitle, pairUrl, pairTitle, pairRole)
            },
        shape = ExpressiveCardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Row: Subject pill + Paper Type badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ExpressivePillSmall,
                    color = subjectAccent.copy(alpha = 0.2f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(subjectAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = displaySubject,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Surface(
                    shape = ExpressivePillSmall,
                    color = if (isSqp) PrimaryBlue.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isSqp) "Sample Question Paper (SQP)" else "Marking Scheme (MS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSqp) PrimaryBlue else SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = displayTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Details info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isSqp) "Official 3-Hour Board Paper • 70/80 Marks" else "Step-Wise Marking Breakdown & Model Solutions",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (localCached != null) {
                    Surface(
                        shape = ExpressivePillSmall,
                        color = SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Downloaded",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SQP 3-Hour Exam Simulation Quick Action
            if (isSqp) {
                Button(
                    onClick = {
                        val openUrl = localCached?.absolutePath ?: paper.url
                        val pairedMsUrl = pairedPaper?.let { p ->
                            val local = LocalDocumentScanner.findSamplePaper(context, p.filename)
                            local?.absolutePath ?: p.url
                        } ?: ""
                        onStartExamSimulation(displayTitle, displaySubject, openUrl, pairedMsUrl)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveCardMedium,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AccentAmberOnBg
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ Start 3-Hour Exam Simulation",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmberOnBg
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Secondary Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val openUrl = localCached?.absolutePath ?: paper.url
                        val (pairUrl, pairTitle, pairRole) = pairedInfo()
                        onOpenPdf(openUrl, displayTitle, pairUrl, pairTitle, pairRole)
                    },
                    modifier = Modifier.weight(1f),
                    shape = ExpressiveCardMedium
                ) {
                    Icon(
                        imageVector = if (isSqp) Icons.Default.MenuBook else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSqp) "Open in Reader" else "View Marking",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (pairedPaper != null) {
                    OutlinedButton(
                        onClick = {
                            val pairedLocal = LocalDocumentScanner.findSamplePaper(context, pairedPaper.filename)
                            val openUrl = pairedLocal?.absolutePath ?: pairedPaper.url
                            val pairedTitleText = pairedPaper.title
                                .replace("EnglishCore", "English Core")
                                .replace("PhysicalEducation", "Physical Education")
                            val originalRole = if (isSqp) PdfPairedRole.QUESTION_PAPER else PdfPairedRole.MARKING_SCHEME
                            val originalUrl = localCached?.absolutePath ?: paper.url
                            onOpenPdf(openUrl, pairedTitleText, originalUrl, displayTitle, originalRole)
                        },
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveCardMedium
                    ) {
                        Icon(
                            imageVector = if (isSqp) Icons.Default.CheckCircle else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSqp) "Marking Scheme" else "Question Paper",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
