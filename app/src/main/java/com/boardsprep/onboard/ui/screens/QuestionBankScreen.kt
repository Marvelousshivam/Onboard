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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankScreen(
    initialSubjectId: String = "physics",
    onBackClick: () -> Unit,
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

    var selectedTypeFilter by remember { mutableStateOf("all") } // "all", "vsa", "sa", "case", "la"
    var selectedStatusFilter by remember { mutableStateOf("all") } // "all", "full", "half", "retry", "unattempted"
    var searchQuery by remember { mutableStateOf("") }

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

    // Filter questions based on UI controls
    val filteredQuestions = remember(
        allQuestions,
        selectedTypeFilter,
        selectedStatusFilter,
        searchQuery,
        evalMap
    ) {
        allQuestions.filter { q ->
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

            val matchesQuery = searchQuery.isBlank() ||
                    q.questionText.contains(searchQuery, ignoreCase = true) ||
                    q.chapterName.contains(searchQuery, ignoreCase = true) ||
                    q.keyTerms.any { it.contains(searchQuery, ignoreCase = true) }

            matchesType && matchesStatus && matchesQuery
        }
    }

    // Statistics
    val totalCount = allQuestions.size
    val evaluatedCount = evaluationsList.size
    val fullCount = evaluationsList.count { it.status == "full" }
    val retryCount = evaluationsList.count { it.status == "retry" }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
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
                    LiquidGlassBackButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    val isGlass = LocalGlassEnabled.current && isGlassSupported()
                    val solvedShape = RoundedCornerShape(100.dp)
                    Surface(
                        shape = solvedShape,
                        color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        border = null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .then(
                                if (isGlass) Modifier.lightweightLiquidGlass(shape = solvedShape)
                                else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isGlass) Color(0xFF00C853) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
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
            // Subject Selection Tabs
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            )

            // Progress Bar & Quick Stats Card
            if (totalCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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

            // Search & Filter Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by concept, law, or keyword...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Marks Filter Chips Segmented Row
                val marksItems = remember {
                    listOf(
                        LiquidGlassSegmentItem("all", "All Marks"),
                        LiquidGlassSegmentItem("vsa", "2m (VSA)"),
                        LiquidGlassSegmentItem("sa", "3m (SA)"),
                        LiquidGlassSegmentItem("case", "4m (Case Study)"),
                        LiquidGlassSegmentItem("la", "5m (Long Answer)")
                    )
                }
                LiquidGlassSegmentedRow(
                    items = marksItems,
                    selectedKey = selectedTypeFilter,
                    onSelect = { selectedTypeFilter = it },
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Status Filter Segmented Row
                val statusItems = remember {
                    listOf(
                        LiquidGlassSegmentItem("all", "All Status"),
                        LiquidGlassSegmentItem("full", "✓ Full Marks"),
                        LiquidGlassSegmentItem("retry", "⟳ Retry Needed"),
                        LiquidGlassSegmentItem("unattempted", "Unattempted")
                    )
                }
                LiquidGlassSegmentedRow(
                    items = statusItems,
                    selectedKey = selectedStatusFilter,
                    onSelect = { selectedStatusFilter = it },
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
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
                        selectedTypeFilter = "all"
                        selectedStatusFilter = "all"
                        searchQuery = ""
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
 * Question Bank Card with Step-Wise Marking Rubric & 3-Tier Self-Assessment Dock.
 */
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                maxLines = 1
                            )
                        }
                    }
                }

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

            // Question Text
            Text(
                text = question.questionText.trim(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            // Evaluator Keywords Chips
            if (question.keyTerms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keywords:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    question.keyTerms.forEach { keyword ->
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
                                            text = question.modelAnswer.trim(),
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
                                                text = "Step ${step.stepNumber}: ${step.criterion}",
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
