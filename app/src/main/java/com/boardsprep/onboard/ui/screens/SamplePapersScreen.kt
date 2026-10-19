package com.boardsprep.onboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.lightweightLiquidGlass
import com.boardsprep.onboard.core.glass.glassContentColor
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassActionPill
import com.boardsprep.onboard.core.glass.components.LiquidGlassBackButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassChip
import com.boardsprep.onboard.core.glass.components.LiquidGlassSegmentItem
import com.boardsprep.onboard.core.glass.components.LiquidGlassSegmentedRow
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

data class UnifiedPaperSet(
    val setId: String,
    val subject: String,
    val subjectId: String,
    val category: String,
    val displayTitle: String,
    val subtitle: String,
    val sqpPaper: SamplePaper?,
    val msPaper: SamplePaper?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplePapersScreen(
    initialSubjectId: String = "",
    showBackButton: Boolean = false,
    onOpenPdf: OnOpenPdfWithPair,
    onStartExamSimulation: (paperTitle: String, subject: String, paperUrl: String, msUrl: String) -> Unit = { _, _, _, _ -> },
    onQuestionBankClick: (String) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }

    val normalizedInitialId = remember(initialSubjectId) {
        when (initialSubjectId.lowercase().trim()) {
            "mathematics", "math" -> "maths"
            "pe", "ped", "physical education" -> "physical_education"
            else -> initialSubjectId.lowercase().trim()
        }
    }

    var selectedSubjectId by remember(normalizedInitialId) { mutableStateOf(normalizedInitialId) }
    var selectedTypeFilter by remember { mutableStateOf("all") } // "all", "official", "practice"
    var searchQuery by remember { mutableStateOf("") }
    var isPatternExpanded by remember { mutableStateOf(false) }

    val allPapers = remember { repository.getSamplePapers() }
    val allExamAttempts by repository.getAllExamAttempts().collectAsState(initial = emptyList())

    // Unified paper sets: Groups papers strictly by setId / pairFilename so SQPs and Practice Sets are never jumbled!
    val unifiedPaperSets = remember(allPapers, selectedSubjectId, selectedTypeFilter, searchQuery) {
        val groups = allPapers.groupBy { paper ->
            if (paper.setId.isNotBlank()) paper.setId
            else {
                val fn = paper.filename.lowercase()
                val sub = paper.subjectId
                when {
                    fn.contains("pq2") -> "${sub}_pq2"
                    fn.contains("pq") -> "${sub}_pq1"
                    else -> "${sub}_sqp"
                }
            }
        }

        groups.mapNotNull { (setIdKey, papers) ->
            val sqp = papers.firstOrNull { it.type.equals("sqp", ignoreCase = true) }
            val ms = papers.firstOrNull { it.type.equals("ms", ignoreCase = true) }
                ?: allPapers.firstOrNull { it.filename.equals(sqp?.pairFilename, ignoreCase = true) }
            val primaryPaper = sqp ?: ms ?: return@mapNotNull null

            val subjId = primaryPaper.subjectId
            val matchesSubject = selectedSubjectId.isBlank() || subjId.equals(selectedSubjectId, ignoreCase = true)
            if (!matchesSubject) return@mapNotNull null

            val category = primaryPaper.paperCategory.ifBlank {
                val fn = primaryPaper.filename.lowercase()
                when {
                    fn.contains("pq2") -> "Practice Paper Set 2"
                    fn.contains("pq") -> "Practice Paper Set 1"
                    else -> "Official SQP 2027"
                }
            }

            val matchesCategory = when (selectedTypeFilter) {
                "official" -> category.contains("Official", ignoreCase = true) || !primaryPaper.filename.contains("PQ", ignoreCase = true)
                "practice" -> category.contains("Practice", ignoreCase = true) || primaryPaper.filename.contains("PQ", ignoreCase = true)
                else -> true
            }
            if (!matchesCategory) return@mapNotNull null

            val displaySubject = when (primaryPaper.subject.trim().lowercase()) {
                "englishcore", "english" -> "English Core"
                "physicaleducation", "pe" -> "Physical Education"
                "maths" -> "Mathematics"
                else -> primaryPaper.subject
            }

            val cleanSetTitle = when {
                category.contains("Set 2", ignoreCase = true) || primaryPaper.filename.contains("PQ2", ignoreCase = true) ->
                    "$displaySubject Additional Practice Paper (Set 2)"
                category.contains("Set 1", ignoreCase = true) || primaryPaper.filename.contains("PQ", ignoreCase = true) ->
                    "$displaySubject Additional Practice Paper (Set 1)"
                else ->
                    "$displaySubject Official Sample Question Paper 2026-27"
            }

            val subtitle = when {
                category.contains("Set 2", ignoreCase = true) || primaryPaper.filename.contains("PQ2", ignoreCase = true) ->
                    "CBSE Additional Practice Set 2 • With Full Solutions"
                category.contains("Set 1", ignoreCase = true) || primaryPaper.filename.contains("PQ", ignoreCase = true) ->
                    "CBSE Additional Practice Set 1 • With Full Solutions"
                else ->
                    "Official Board 3-Hour SQP • Full Step-Wise Marking Scheme"
            }

            val matchesQuery = searchQuery.isBlank() ||
                    cleanSetTitle.contains(searchQuery, ignoreCase = true) ||
                    displaySubject.contains(searchQuery, ignoreCase = true) ||
                    category.contains(searchQuery, ignoreCase = true)

            if (!matchesQuery) return@mapNotNull null

            UnifiedPaperSet(
                setId = setIdKey,
                subject = displaySubject,
                subjectId = subjId,
                category = category,
                displayTitle = cleanSetTitle,
                subtitle = subtitle,
                sqpPaper = sqp,
                msPaper = ms
            )
        }.sortedWith(
            compareBy<UnifiedPaperSet> {
                when {
                    it.category.contains("Official", ignoreCase = true) || (!it.displayTitle.contains("Practice", ignoreCase = true)) -> 0
                    it.category.contains("Set 1", ignoreCase = true) || it.displayTitle.contains("Set 1", ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenBy { it.subject }
        )
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
                    if (showBackButton) {
                        LiquidGlassBackButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(start = 8.dp)
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
                shape = RoundedCornerShape(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Paper Type Segmented Tabs (All Papers, Official SQP, Practice Sets)
            val allMatchingSubject = remember(allPapers, selectedSubjectId) {
                val groups = allPapers.groupBy { paper ->
                    if (paper.setId.isNotBlank()) paper.setId
                    else {
                        val fn = paper.filename.lowercase()
                        val sub = paper.subjectId
                        when {
                            fn.contains("pq2") -> "${sub}_pq2"
                            fn.contains("pq") -> "${sub}_pq1"
                            else -> "${sub}_sqp"
                        }
                    }
                }
                groups.filter { (_, papers) ->
                    val primary = papers.firstOrNull() ?: return@filter false
                    selectedSubjectId.isBlank() || primary.subjectId.equals(selectedSubjectId, ignoreCase = true)
                }
            }

            val officialSetsCount = remember(allMatchingSubject) {
                allMatchingSubject.count { (_, papers) ->
                    val primary = papers.firstOrNull() ?: return@count false
                    primary.paperCategory.contains("Official", ignoreCase = true) || !primary.filename.contains("PQ", ignoreCase = true)
                }
            }

            val practiceSetsCount = remember(allMatchingSubject) {
                allMatchingSubject.count { (_, papers) ->
                    val primary = papers.firstOrNull() ?: return@count false
                    primary.paperCategory.contains("Practice", ignoreCase = true) || primary.filename.contains("PQ", ignoreCase = true)
                }
            }

            val typeTabs = listOf(
                ExpressiveTabItem("All Papers", badge = "${officialSetsCount + practiceSetsCount}"),
                ExpressiveTabItem("Official SQP", badge = "$officialSetsCount"),
                ExpressiveTabItem("Practice Sets", badge = "$practiceSetsCount")
            )
            val selectedTypeIndex = when (selectedTypeFilter) {
                "official" -> 1
                "practice" -> 2
                else -> 0
            }

            ExpressiveSegmentedTabs(
                tabs = typeTabs,
                selectedTabIndex = selectedTypeIndex,
                onTabSelected = {
                    selectedTypeFilter = when (it) {
                        1 -> "official"
                        2 -> "practice"
                        else -> "all"
                    }
                },
                accentColor = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            )

            // Subject Filter Chips (Edge-to-Edge with ContentPadding = 16.dp, Zero Edge Clipping!)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjects) { (id, label) ->
                    val isSelected = selectedSubjectId == id
                    val chipColor = when (id) {
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
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.2f),
                            selectedLabelColor = chipColor,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) chipColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            borderWidth = if (isSelected) 1.5.dp else 1.dp
                        )
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
            ) {
                // Official CBSE Pattern Guidance (Collapsible M3 Expressive Banner)
                item {
                    val isGlass = LocalGlassEnabled.current && isGlassSupported()
                    val bannerShape = RoundedCornerShape(18.dp)
                    Surface(
                        onClick = { isPatternExpanded = !isPatternExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isGlass) Modifier.lightweightLiquidGlass(shape = bannerShape)
                                else Modifier
                            ),
                        shape = bannerShape,
                        color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = if (isGlass) null
                        else androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "CBSE 2026-27 Pattern Breakdown",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "50% Competency • 20% MCQ • 30% Theory",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isPatternExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isPatternExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (isPatternExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
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
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("20%", fontWeight = FontWeight.Black, fontSize = 13.sp, color = AccentAmber)
                                            Text("MCQ / Obj", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
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
                }

                if (unifiedPaperSets.isEmpty()) {
                    item {
                        ExpressiveEmptyState(
                            icon = Icons.Default.Description,
                            title = "No Papers Found",
                            description = "No sample papers match your current filters. Try selecting 'All Subjects' or clearing the search keyword.",
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
                    items(unifiedPaperSets, key = { it.setId }) { paperSet ->
                        UnifiedSamplePaperCard(
                            paperSet = paperSet,
                            allExamAttempts = allExamAttempts,
                            selectedSubjectId = selectedSubjectId,
                            selectedTypeFilter = selectedTypeFilter,
                            onOpenPdf = onOpenPdf,
                            onStartExamSimulation = onStartExamSimulation
                        )
                    }

                    // Question Bank navigation shortcut banner
                    item {
                        Card(
                            onClick = { onQuestionBankClick(selectedSubjectId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Quiz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Need Chapter-Wise Subjective Practice?",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Practice questions with step-by-step marking rubrics in CBSE Question Bank →",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun UnifiedSamplePaperCard(
    paperSet: UnifiedPaperSet,
    allExamAttempts: List<com.boardsprep.onboard.data.local.entities.ExamAttemptEntity>,
    selectedSubjectId: String,
    selectedTypeFilter: String,
    onOpenPdf: OnOpenPdfWithPair,
    onStartExamSimulation: (paperTitle: String, subject: String, paperUrl: String, msUrl: String) -> Unit
) {
    val context = LocalContext.current

    val subjectAccent = when (paperSet.subjectId) {
        "physics" -> PhysicsLavender
        "chemistry" -> ChemistryMint
        "maths" -> MathsPeach
        "biology" -> BiologySky
        "english" -> EnglishRose
        "physical_education" -> AccentAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val latestAttempt = remember(allExamAttempts, paperSet.displayTitle, paperSet.sqpPaper?.title) {
        allExamAttempts.firstOrNull {
            it.paperTitle.equals(paperSet.displayTitle, ignoreCase = true) ||
            (paperSet.sqpPaper != null && it.paperTitle.equals(paperSet.sqpPaper.title, ignoreCase = true))
        }
    }

    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    Surface(
        onClick = {
            // Default card tap opens SQP with MS paired
            val sqp = paperSet.sqpPaper ?: paperSet.msPaper
            if (sqp != null) {
                val ms = paperSet.msPaper
                val sqpLocal = LocalDocumentScanner.findSamplePaper(context, sqp.filename)
                val sqpUrl = sqpLocal?.absolutePath ?: sqp.url
                val msLocal = ms?.let { LocalDocumentScanner.findSamplePaper(context, it.filename) }
                val msUrl = msLocal?.absolutePath ?: (ms?.url ?: "")
                onOpenPdf(
                    sqpUrl,
                    "${paperSet.displayTitle} — SQP",
                    msUrl,
                    "${paperSet.displayTitle} — Marking Scheme",
                    PdfPairedRole.QUESTION_PAPER
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGlass) Modifier.lightweightLiquidGlass(shape = ExpressiveCardLarge)
                else Modifier
            ),
        shape = ExpressiveCardLarge,
        color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        border = null
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Row: Subject pill (hidden when subject filter is active) + Status/Score badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Only show subject pill if subject is not already filtered!
                if (selectedSubjectId.isBlank()) {
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
                                text = paperSet.subject,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Compact Score / Board Status Badge
                if (latestAttempt != null) {
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (latestAttempt.selfScore >= 0) AccentAmberLight else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = AccentAmberOnBg,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (latestAttempt.selfScore >= 0)
                                    "Score: ${latestAttempt.selfScore}/${latestAttempt.maxMarks} • ${(latestAttempt.timeSpentSeconds / 60)}m"
                                else
                                    "Attempted • ${(latestAttempt.timeSpentSeconds / 60)}m",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmberOnBg
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = ExpressivePillSmall,
                        color = when {
                            paperSet.category.contains("Official", ignoreCase = true) || (!paperSet.displayTitle.contains("Practice", ignoreCase = true)) ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            paperSet.category.contains("Set 2", ignoreCase = true) || paperSet.displayTitle.contains("Set 2", ignoreCase = true) ->
                                AccentAmberLight
                            else ->
                                SuccessGreenLight
                        }
                    ) {
                        Text(
                            text = when {
                                paperSet.category.contains("Official", ignoreCase = true) || (!paperSet.displayTitle.contains("Practice", ignoreCase = true)) ->
                                    "Official SQP • 2026-27"
                                paperSet.category.contains("Set 2", ignoreCase = true) || paperSet.displayTitle.contains("Set 2", ignoreCase = true) ->
                                    "Practice Paper • Set 2"
                                else ->
                                    "Practice Paper • Set 1"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                paperSet.category.contains("Official", ignoreCase = true) || (!paperSet.displayTitle.contains("Practice", ignoreCase = true)) ->
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                paperSet.category.contains("Set 2", ignoreCase = true) || paperSet.displayTitle.contains("Set 2", ignoreCase = true) ->
                                    AccentAmberOnBg
                                else ->
                                    SuccessGreenOnBg
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = paperSet.displayTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Details subtitle
            Text(
                text = paperSet.subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Hero Action: Start 3-Hour Exam Simulation
            Button(
                onClick = {
                    val sqp = paperSet.sqpPaper ?: paperSet.msPaper!!
                    val ms = paperSet.msPaper
                    val sqpLocal = LocalDocumentScanner.findSamplePaper(context, sqp.filename)
                    val sqpUrl = sqpLocal?.absolutePath ?: sqp.url
                    val msUrl = ms?.let { m ->
                        val mLocal = LocalDocumentScanner.findSamplePaper(context, m.filename)
                        mLocal?.absolutePath ?: m.url
                    } ?: ""
                    onStartExamSimulation(paperSet.displayTitle, paperSet.subject, sqpUrl, msUrl)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveCardMedium,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Icon(
                    imageVector = if (latestAttempt != null) Icons.Default.Replay else Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AccentAmberOnBg
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (latestAttempt != null) "Retake 3-Hour Exam Simulation" else "Start 3-Hour Exam Simulation",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmberOnBg
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Actions Row (50% / 50%): Read Paper (PDF) & Marking Scheme (MS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val sqp = paperSet.sqpPaper ?: return@OutlinedButton
                        val ms = paperSet.msPaper
                        val sqpLocal = LocalDocumentScanner.findSamplePaper(context, sqp.filename)
                        val sqpUrl = sqpLocal?.absolutePath ?: sqp.url
                        val msLocal = ms?.let { LocalDocumentScanner.findSamplePaper(context, it.filename) }
                        val msUrl = msLocal?.absolutePath ?: (ms?.url ?: "")
                        onOpenPdf(
                            sqpUrl,
                            "${paperSet.displayTitle} — SQP",
                            msUrl,
                            "${paperSet.displayTitle} — Marking Scheme",
                            PdfPairedRole.QUESTION_PAPER
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = ExpressiveCardMedium
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Read Paper",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        val ms = paperSet.msPaper ?: return@OutlinedButton
                        val sqp = paperSet.sqpPaper
                        val msLocal = LocalDocumentScanner.findSamplePaper(context, ms.filename)
                        val msUrl = msLocal?.absolutePath ?: ms.url
                        val sqpLocal = sqp?.let { LocalDocumentScanner.findSamplePaper(context, it.filename) }
                        val sqpUrl = sqpLocal?.absolutePath ?: (sqp?.url ?: "")
                        onOpenPdf(
                            msUrl,
                            "${paperSet.displayTitle} — Marking Scheme",
                            sqpUrl,
                            "${paperSet.displayTitle} — SQP",
                            PdfPairedRole.MARKING_SCHEME
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = ExpressiveCardMedium
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Marking Scheme",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
