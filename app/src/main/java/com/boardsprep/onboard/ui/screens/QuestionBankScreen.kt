package com.boardsprep.onboard.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.sync.FirebaseSyncManager
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.QuestionEvaluationEntity
import com.boardsprep.onboard.data.models.QuestionBankItem
import com.boardsprep.onboard.data.models.StepRubric
import com.boardsprep.onboard.data.models.SubjectQuestionBank
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveEmptyState
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.glassContentColor
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.lightweightLiquidGlass
import com.boardsprep.onboard.core.glass.components.LiquidGlassBackButton
import com.boardsprep.onboard.core.glass.components.LiquidGlassChip
import com.boardsprep.onboard.core.glass.components.LiquidGlassSegmentItem
import com.boardsprep.onboard.core.glass.components.LiquidGlassSegmentedRow
import com.boardsprep.onboard.core.glass.components.GlassSpring
import com.boardsprep.onboard.core.glass.components.GLASS_STRETCH
import com.boardsprep.onboard.core.glass.components.GLASS_SQUASH
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import com.boardsprep.onboard.ui.components.expressiveBounce
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Clean up OCR typos, curly quotes, mid-sentence line breaks from CBSE PDFs,
 * and isolated mark counts before OR choices.
 */
fun sanitizeQuestionText(raw: String): String {
    if (raw.isBlank()) return raw
    val t = raw
        .replace('\u2019', '\'')
        .replace('\u2018', '\'')
        .replace('\u201c', '"')
        .replace('\u201d', '"')
        .replace('\ufffd', '\'')
        .replace("M .", "M.")
        .replace("fir st", "first")
        .replace("fir s ", "first ")
        .replace(Regex("\\n+\\s*\\d+\\s*\\n+\\s*OR\\s*\\n+", RegexOption.IGNORE_CASE), "\n\nOR\n\n")
        .replace(Regex("\\n+\\s*\\d+\\s*$", RegexOption.MULTILINE), "")

    val lines = t.split('\n').map { it.trim() }
    val merged = mutableListOf<String>()
    for (line in lines) {
        if (line.isEmpty()) {
            if (merged.isNotEmpty() && merged.last().isNotEmpty()) {
                merged.add("")
            }
            continue
        }
        if (merged.isEmpty() || merged.last().isEmpty()) {
            merged.add(line)
        } else {
            val prev = merged.last()
            val isSub = line.matches(Regex("^(?:\\([a-z0-9]+\\)|\\[[a-z0-9]+\\]|\\d+[\\.)]|•|-|OR\\b)", RegexOption.IGNORE_CASE))
            val prevEndsSentence = prev.endsWith('.') || prev.endsWith(':') || prev.endsWith('?') || prev.endsWith('!')
            if (!isSub && !prevEndsSentence) {
                merged[merged.lastIndex] = "$prev $line"
            } else {
                merged.add(line)
            }
        }
    }
    return merged.joinToString("\n")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuestionBankScreen(
    initialSubjectId: String = "physics",
    showBackButton: Boolean = true,
    onBackClick: () -> Unit = {},
    onOpenPdf: ((url: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }
    val syncManager = remember { FirebaseSyncManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val subjects = remember {
        listOf(
            "physics" to "Physics",
            "chemistry" to "Chemistry",
            "maths" to "Mathematics",
            "biology" to "Biology",
            "english" to "English Core",
            "physical_education" to "Physical Education"
        )
    }

    val normalizedInitialId = remember(initialSubjectId) {
        when (initialSubjectId.lowercase().trim()) {
            "mathematics", "math" -> "maths"
            "pe", "ped", "physical education" -> "physical_education"
            else -> initialSubjectId.lowercase().trim()
        }
    }

    var selectedSubjectId by remember(normalizedInitialId) {
        mutableStateOf(
            if (subjects.any { it.first.equals(normalizedInitialId, ignoreCase = true) }) {
                normalizedInitialId
            } else "physics"
        )
    }

    var selectedChapterFilter by remember(selectedSubjectId) { mutableStateOf("all") }
    var selectedTypeFilter by remember { mutableStateOf("all") } // "all", "vsa", "sa", "case", "la"
    var selectedStatusFilter by remember { mutableStateOf("all") } // "all", "full", "half", "retry", "unattempted"
    var searchQuery by remember { mutableStateOf("") }

    var isChapterMenuExpanded by remember { mutableStateOf(false) }
    var isStatusMenuExpanded by remember { mutableStateOf(false) }

    var questionBank by remember { mutableStateOf<SubjectQuestionBank?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Observe evaluations from Room DB
    val evaluationsList by repository.getEvaluationsForSubject(selectedSubjectId)
        .collectAsState(initial = emptyList())

    val evalMap = remember(evaluationsList) {
        evaluationsList.associateBy { it.questionId }
    }

    // Load question bank when subject changes
    LaunchedEffect(selectedSubjectId) {
        isLoading = true
        val bank = repository.getQuestionBank(selectedSubjectId)
        questionBank = bank
        isLoading = false
    }

    val allQuestions = questionBank?.questions ?: emptyList()

    val availableChapters = remember(allQuestions) {
        listOf("all") + allQuestions.map { it.chapterName.trim() }.filter { it.isNotBlank() }.distinct()
    }

    // Filter questions based on UI controls
    val filteredQuestions = remember(
        allQuestions,
        selectedChapterFilter,
        selectedTypeFilter,
        selectedStatusFilter,
        searchQuery,
        evalMap
    ) {
        allQuestions.filter { q ->
            val matchesChapter = selectedChapterFilter == "all" ||
                    q.chapterName.equals(selectedChapterFilter, ignoreCase = true)

            val matchesType = when (selectedTypeFilter) {
                "all" -> true
                "vsa" -> q.marks == 2 || q.type.equals("vsa", ignoreCase = true)
                "sa" -> q.marks == 3 || q.type.equals("sa", ignoreCase = true)
                "case" -> q.marks == 4 || q.type.equals("case", ignoreCase = true)
                "la" -> q.marks == 5 || q.type.equals("la", ignoreCase = true)
                else -> true
            }

            val eval = evalMap[q.id]
            val matchesStatus = when (selectedStatusFilter) {
                "all" -> true
                "full" -> eval?.status == "full"
                "half" -> eval?.status == "half"
                "retry" -> eval?.status == "retry"
                "unattempted" -> eval == null
                else -> true
            }

            val cleanStem = sanitizeQuestionText(q.questionText)
            val matchesQuery = searchQuery.isBlank() ||
                    cleanStem.contains(searchQuery, ignoreCase = true) ||
                    q.chapterName.contains(searchQuery, ignoreCase = true) ||
                    q.keyTerms.any { it.contains(searchQuery, ignoreCase = true) }

            matchesChapter && matchesType && matchesStatus && matchesQuery
        }
    }

    // Statistics
    val totalCount = allQuestions.size
    val evaluatedCount = evaluationsList.size
    val fullCount = evaluationsList.count { it.status == "full" }
    val retryCount = evaluationsList.count { it.status == "retry" }

    // Scroll behavior for TopAppBar to maximize viewport on scroll
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text(
                            text = "CBSE Question Bank",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Official Subjective Practice Studio • 2026-27",
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
                actions = {
                    val isGlass = LocalGlassEnabled.current && isGlassSupported()
                    val currentSubjectTitle = subjects.firstOrNull { it.first == selectedSubjectId }?.second ?: "Subject"

                    // Compact Official PDF Button in Top Bar
                    val qbRemote = remember(selectedSubjectId) {
                        repository.getQuestionBankPdf(selectedSubjectId)
                    }
                    if (qbRemote != null && qbRemote.pdfUrl.isNotBlank() && onOpenPdf != null) {
                        val pdfShape = RoundedCornerShape(100.dp)
                        Surface(
                            onClick = {
                                val title = qbRemote.title.ifBlank { "Class 12 $currentSubjectTitle CBSE Official Question Bank" }
                                onOpenPdf(qbRemote.pdfUrl, title)
                            },
                            shape = pdfShape,
                            color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .then(
                                    if (isGlass) Modifier.lightweightLiquidGlass(shape = pdfShape)
                                    else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "View Official PDF",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Solved Counter Pill
                    val solvedShape = RoundedCornerShape(100.dp)
                    Surface(
                        shape = solvedShape,
                        color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .then(
                                if (isGlass) Modifier.lightweightLiquidGlass(shape = solvedShape)
                                else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isGlass) Color(0xFF00C853) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$evaluatedCount / $totalCount Solved",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onPrimaryContainer
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Row 1: Subject Selection Tabs
            val selectedTabIndex = subjects.indexOfFirst { it.first == selectedSubjectId }.coerceAtLeast(0)
            ExpressiveSegmentedTabs(
                tabs = subjects.map { (id, name) ->
                    ExpressiveTabItem(title = name)
                },
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { idx ->
                    selectedSubjectId = subjects[idx].first
                },
                accentColor = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            )

            // Row 2: Sleek 42dp Single-Line Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(42.dp),
                shape = RoundedCornerShape(21.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search questions, chapters, keywords...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Row 3: Consolidated Single Filter Row ([All Chapters ▾] | 2M | 3M | 4M | 5M | [Status: All ▾])
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chapter Filter Dropdown Chip
                item {
                    Box {
                        val isChapterActive = selectedChapterFilter != "all"
                        FilterChip(
                            selected = isChapterActive,
                            onClick = { isChapterMenuExpanded = true },
                            label = {
                                Text(
                                    text = if (isChapterActive) selectedChapterFilter else "All Chapters ▾",
                                    fontSize = 11.sp,
                                    fontWeight = if (isChapterActive) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = if (isChapterActive) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear chapter filter",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { selectedChapterFilter = "all" }
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        DropdownMenu(
                            expanded = isChapterMenuExpanded,
                            onDismissRequest = { isChapterMenuExpanded = false }
                        ) {
                            availableChapters.forEach { ch ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (ch == "all") "All Chapters (${allQuestions.size})" else ch,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedChapterFilter == ch) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedChapterFilter = ch
                                        isChapterMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Marks Chips: All, 2M, 3M, 4M, 5M
                val marksOptions = listOf(
                    "all" to "All Marks",
                    "vsa" to "2M (VSA)",
                    "sa" to "3M (SA)",
                    "case" to "4M (Case)",
                    "la" to "5M (Long)"
                )
                items(marksOptions) { (key, label) ->
                    FilterChip(
                        selected = selectedTypeFilter == key,
                        onClick = { selectedTypeFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTypeFilter == key) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Status Filter Dropdown Chip
                item {
                    Box {
                        val isStatusActive = selectedStatusFilter != "all"
                        val statusLabel = when (selectedStatusFilter) {
                            "full" -> "✓ Full Marks"
                            "half" -> "½ Partial"
                            "retry" -> "⟳ Retry"
                            "unattempted" -> "Unattempted"
                            else -> "Status: All ▾"
                        }
                        FilterChip(
                            selected = isStatusActive,
                            onClick = { isStatusMenuExpanded = true },
                            label = {
                                Text(
                                    text = if (isStatusActive) statusLabel else "Status: All ▾",
                                    fontSize = 11.sp,
                                    fontWeight = if (isStatusActive) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            trailingIcon = if (isStatusActive) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear status filter",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { selectedStatusFilter = "all" }
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        DropdownMenu(
                            expanded = isStatusMenuExpanded,
                            onDismissRequest = { isStatusMenuExpanded = false }
                        ) {
                            listOf(
                                "all" to "All Status",
                                "full" to "✓ Full Marks ($fullCount)",
                                "retry" to "⟳ Retry Needed ($retryCount)",
                                "unattempted" to "Unattempted"
                            ).forEach { (sKey, sName) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sName,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedStatusFilter == sKey) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedStatusFilter = sKey
                                        isStatusMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Question Cards List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filteredQuestions.isEmpty()) {
                ExpressiveEmptyState(
                    icon = Icons.Default.Quiz,
                    title = "No Questions Match Filter",
                    description = "Try resetting filters or search terms to see official CBSE questions.",
                    actionText = "Reset Filters",
                    onActionClick = {
                        selectedChapterFilter = "all"
                        selectedTypeFilter = "all"
                        selectedStatusFilter = "all"
                        searchQuery = ""
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val isImeVisible = WindowInsets.isImeVisible

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 90.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Subject Mastery Card: Scrolls away with the questions, auto-hidden when keyboard opens
                    item(key = "subject_mastery_card") {
                        if (!isImeVisible && totalCount > 0) {
                            SubjectMasteryCard(
                                totalCount = totalCount,
                                evaluatedCount = evaluatedCount,
                                fullCount = fullCount,
                                retryCount = retryCount
                            )
                        }
                    }

                    items(filteredQuestions, key = { it.id }) { question ->
                        val currentEval = evalMap[question.id]
                        QuestionBankCard(
                            question = question,
                            evaluation = currentEval,
                            onSaveEvaluation = { status ->
                                coroutineScope.launch {
                                    repository.saveQuestionEvaluation(
                                        questionId = question.id,
                                        subjectId = selectedSubjectId,
                                        marks = question.marks,
                                        status = status
                                    )
                                    val entity = QuestionEvaluationEntity(
                                        questionId = question.id,
                                        subjectId = selectedSubjectId,
                                        marks = question.marks,
                                        status = status,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    syncManager.syncQuestionEvaluation(entity)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Subject Mastery Progress Card — scrolls away inside LazyColumn to preserve vertical viewport.
 */
@Composable
private fun SubjectMasteryCard(
    totalCount: Int,
    evaluatedCount: Int,
    fullCount: Int,
    retryCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Subject Mastery",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${if (totalCount > 0) (evaluatedCount * 100 / totalCount) else 0}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "✓ $fullCount Full",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00C853)
                    )
                    Text(
                        text = "⟳ $retryCount Retry",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (totalCount > 0) (evaluatedCount.toFloat() / totalCount.toFloat()) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * Question Bank Card with Step-Wise Marking Rubric & 3-Tier Self-Assessment Dock.
 * De-slopped: Mid-sentence line breaks sanitized, keywords moved to expanded section,
 * internal choice OR questions cleanly demarcated with divider badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionBankCard(
    question: QuestionBankItem,
    evaluation: QuestionEvaluationEntity?,
    onSaveEvaluation: (String) -> Unit
) {
    var isRubricExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (evaluation?.status) {
                "full" -> Color(0xFF00C853).copy(alpha = 0.6f)
                "half" -> Color(0xFFFF9100).copy(alpha = 0.6f)
                "retry" -> Color(0xFFFF1744).copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Tags and Marks Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Type Tag
                    val typeLabel = when (question.type.lowercase()) {
                        "vsa" -> "VSA"
                        "sa" -> "Short Answer"
                        "case" -> "Case Study"
                        "la" -> "Long Answer"
                        else -> question.type.uppercase()
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Chapter Tag
                    if (question.chapterName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = question.chapterName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Marks Badge
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${question.marks} Marks",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question Text: Sanitized & split cleanly if there is an internal choice OR
            val cleanStem = remember(question.questionText) {
                sanitizeQuestionText(question.questionText)
            }
            val choiceParts = remember(cleanStem) {
                cleanStem.split(Regex("\\n+\\s*OR\\s*\\n+", RegexOption.IGNORE_CASE))
            }

            if (choiceParts.size > 1) {
                // Choice Question Part 1
                Text(
                    text = choiceParts[0].trim(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                // Elegant Choice Divider Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "OR (Alternative Choice)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // Choice Question Part 2
                Text(
                    text = choiceParts[1].trim(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            } else {
                Text(
                    text = cleanStem,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable CBSE Model Answer & Step Rubric
            val isGlass = LocalGlassEnabled.current && isGlassSupported()
            val rubricShape = RoundedCornerShape(12.dp)
            Surface(
                onClick = { isRubricExpanded = !isRubricExpanded },
                shape = rubricShape,
                color = when {
                    isGlass && isRubricExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    isGlass -> Color.Transparent
                    isRubricExpanded -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isGlass && isRubricExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        isGlass -> Color.White.copy(alpha = 0.15f)
                        isRubricExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    }
                ),
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlass) Modifier.lightweightLiquidGlass(shape = rubricShape)
                        else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRubricExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRubricExpanded) "Hide Model Answer & Rubric" else "Show CBSE Model Answer & Step Rubric",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Icon(
                            imageVector = if (isRubricExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Expanded Content
                    AnimatedVisibility(
                        visible = isRubricExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            // Model Answer
                            if (question.modelAnswer.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "CBSE MODEL ANSWER",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = sanitizeQuestionText(question.modelAnswer),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }

                            // Step-Wise Rubric Breakdown
                            if (question.stepMarking.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "OFFICIAL STEP-BY-STEP MARKING RUBRIC",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    question.stepMarking.forEach { step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.background)
                                                .border(
                                                    0.8.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Step ${step.stepNumber}: ${sanitizeQuestionText(step.criterion)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(100.dp),
                                                color = Color(0xFF00C853).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "+${step.marks}m",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00C853),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Key Value Points (Keywords) — Moved inside expanded rubric
                            val filteredKeywords = remember(question.keyTerms, question.chapterName) {
                                question.keyTerms.filter {
                                    it.isNotBlank() && !it.equals(question.chapterName, ignoreCase = true)
                                }
                            }
                            if (filteredKeywords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "KEY VALUE POINTS TO INCLUDE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    filteredKeywords.forEach { keyword ->
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                0.8.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            )
                                        ) {
                                            Text(
                                                text = keyword,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Tier Self-Assessment Dock with USMUSIC V2 Traveling Liquid Glass Pill
            QuestionAssessmentDock(
                currentStatus = evaluation?.status,
                onSaveEvaluation = { onSaveEvaluation(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QuestionAssessmentDock(
    currentStatus: String?,
    onSaveEvaluation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = LocalGlassEnabled.current && isGlassSupported()
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val tiers = remember {
        listOf(
            Triple("full", "✓ Full Marks", Color(0xFF00C853)),
            Triple("half", "½ Partial", Color(0xFFFF9100)),
            Triple("retry", "⟳ Retry", Color(0xFFFF1744))
        )
    }

    val selectedIndex = tiers.indexOfFirst { it.first == currentStatus }
    val activeColor = if (selectedIndex >= 0) tiers[selectedIndex].third else Color.Transparent

    val pillColor by animateColorAsState(
        targetValue = activeColor,
        animationSpec = tween(220),
        label = "dockPillColor"
    )

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(density) { 8.dp.toPx() }
    val n = tiers.size

    val buttonWidthPx = if (rowWidthPx > 0f) {
        (rowWidthPx - gapPx * (n - 1)) / n
    } else 0f
    val stepPx = if (rowWidthPx > 0f) {
        (rowWidthPx + gapPx) / n
    } else 0f

    val targetOffsetPx = if (selectedIndex >= 0 && stepPx > 0f) {
        selectedIndex * stepPx
    } else 0f

    val animOffset = remember { Animatable(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, targetOffsetPx) {
        if (selectedIndex >= 0) {
            if (!isInitialized) {
                animOffset.snapTo(targetOffsetPx)
                isInitialized = true
            } else {
                animOffset.animateTo(targetOffsetPx, GlassSpring)
            }
        }
    }

    val currentOffset = animOffset.value
    val lag = if (buttonWidthPx > 0f && selectedIndex >= 0) {
        (abs(targetOffsetPx - currentOffset) / buttonWidthPx).coerceIn(0f, 1f)
    } else 0f

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                rowWidthPx = coordinates.size.width.toFloat()
            }
    ) {
        // Gliding Liquid Pill Indicator (Rendered when a tier is selected)
        if (selectedIndex >= 0 && buttonWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(currentOffset.roundToInt(), 0) }
                    .width(with(density) { buttonWidthPx.toDp() })
                    .height(42.dp)
                    .graphicsLayer {
                        scaleX = 1f + lag * GLASS_STRETCH
                        scaleY = 1f - lag * GLASS_STRETCH * GLASS_SQUASH
                    }
                    .clip(shape)
                    .background(
                        if (isGlass) pillColor.copy(alpha = 0.28f)
                        else pillColor.copy(alpha = 0.22f)
                    )
                    .border(
                        width = 1.2.dp,
                        color = pillColor,
                        shape = shape
                    )
            )
        }

        // 3 Tiers Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tiers.forEachIndexed { index, (key, label, color) ->
                val isSelected = selectedIndex == index

                val contentColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> color
                        isGlass -> glassContentColor().copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "evalContentColor"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(shape)
                        .background(
                            // When selected, the gliding selection pill underneath provides the fill!
                            if (isSelected) Color.Transparent
                            else if (isGlass) (if (isDark) Color(0xFF12141C) else Color(0xFFFAF8FE)).copy(alpha = 0.50f)
                            else MaterialTheme.colorScheme.surface,
                            shape
                        )
                        .border(
                            // When selected, the gliding selection pill underneath provides the single crisp border!
                            width = if (isSelected) 0.dp else if (isGlass) 0.5.dp else 1.dp,
                            color = if (isSelected) Color.Transparent else if (isGlass) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                            shape = shape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(
                                bounded = true,
                                color = if (isGlass) Color.White.copy(alpha = 0.55f) else color
                            )
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSaveEvaluation(key)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}
