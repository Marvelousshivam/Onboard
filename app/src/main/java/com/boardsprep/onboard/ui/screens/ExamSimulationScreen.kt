package com.boardsprep.onboard.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.boardsprep.onboard.core.glass.components.LiquidGlassBackButton
import com.boardsprep.onboard.core.sync.FirebaseSyncManager
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.ExamAttemptEntity
import com.boardsprep.onboard.data.local.entities.FocusSessionEntity
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.screens.pdf.PdfContinuousCanvas
import com.boardsprep.onboard.ui.screens.pdf.PdfLoadingState
import com.boardsprep.onboard.ui.screens.pdf.rememberPdfReaderState
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

data class SubjectSectionSpec(
    val key: String,
    val name: String,
    val maxMarks: Int,
    val questionSummary: String,
    val suggestedMinutes: Int,
    val examinerTip: String
)

fun getSubjectSectionSpecs(subject: String): List<SubjectSectionSpec> {
    val s = subject.lowercase().trim()
    return when {
        s.contains("english") -> listOf(
            SubjectSectionSpec("A", "Reading Skills", 22, "2 Passages (12 + 10 Marks)", 45, "Read questions first, then scan passages to locate target answers quickly."),
            SubjectSectionSpec("B", "Creative Writing Skills", 18, "Notice, Invitation, Letter, Article/Report", 50, "Strictly follow CBSE formats and word limits (50 words for short writing, 120-150 words for long writing)."),
            SubjectSectionSpec("C", "Literature Textbooks", 40, "Flamingo & Vistas (Extracts + Short & Long Answers)", 70, "Include character motivations and central themes. Underline key quotes or expressions.")
        )
        s.contains("physics") || s.contains("chemistry") -> listOf(
            SubjectSectionSpec("A", "Objective / MCQs", 16, "16 Questions (12 MCQs + 4 Assertion-Reason)", 25, "Eliminate incorrect options first. For Assertion-Reason, ensure Reason correctly explains Assertion."),
            SubjectSectionSpec("B", "Very Short Answer (VSA)", 10, "5 Questions (2 marks each)", 25, "Answer precisely in 30–50 words. Write standard definitions and formula statements."),
            SubjectSectionSpec("C", "Short Answer (SA)", 21, "7 Questions (3 marks each)", 45, "Break derivations into steps. Always include neat labelled diagrams and SI units."),
            SubjectSectionSpec("D", "Case Studies", 8, "2 Case Studies (4 marks each)", 30, "Carefully read experimental data before answering sub-questions. High scoring!"),
            SubjectSectionSpec("E", "Long Answer (LA)", 15, "3 Questions with internal choice (5 marks each)", 40, "Select internal choice questions with care. Clearly underline final formulas and answers.")
        )
        s.contains("math") -> listOf(
            SubjectSectionSpec("A", "Objective / MCQs", 20, "20 Questions (18 MCQs + 2 Assertion-Reason)", 30, "Solve rough work on scratchpad. Do not spend >1.5 mins per MCQ."),
            SubjectSectionSpec("B", "Very Short Answer (VSA)", 10, "5 Questions (2 marks each)", 25, "State theorems and formulas used before substituting values."),
            SubjectSectionSpec("C", "Short Answer (SA)", 18, "6 Questions (3 marks each)", 40, "Show clear step-by-step algebraic manipulation. Write +C for indefinite integrals!"),
            SubjectSectionSpec("D", "Long Answer (LA)", 20, "4 Questions (5 marks each)", 45, "Check matrices/determinants and calculus steps twice. Step marks apply."),
            SubjectSectionSpec("E", "Case Studies", 12, "3 Case Studies (4 marks each)", 25, "Apply concept directly from the given practical scenario.")
        )
        s.contains("bio") -> listOf(
            SubjectSectionSpec("A", "Objective / MCQs", 16, "16 Questions (12 MCQs + 4 Assertion-Reason)", 25, "Pay attention to 'Except' or 'Incorrect' keywords."),
            SubjectSectionSpec("B", "Very Short Answer (VSA)", 10, "5 Questions (2 marks each)", 25, "Write precise technical terms and biological nomenclature."),
            SubjectSectionSpec("C", "Short Answer (SA)", 21, "7 Questions (3 marks each)", 45, "Draw clean labelled pencil diagrams wherever relevant."),
            SubjectSectionSpec("D", "Case Studies", 8, "2 Case Studies (4 marks each)", 30, "Analyze pedigree charts, crosses, or graph data step-wise."),
            SubjectSectionSpec("E", "Long Answer (LA)", 15, "3 Questions (5 marks each)", 40, "Structure answers with headings, bullet points, and neat diagrams.")
        )
        s.contains("pe") || s.contains("physical") -> listOf(
            SubjectSectionSpec("A", "Objective / MCQs", 18, "18 Questions (1 mark each)", 25, "Focus on tournament formulas, BMI calculations, and yoga asanas."),
            SubjectSectionSpec("B", "Very Short Answer (VSA)", 10, "5 Questions (2 marks each)", 25, "Answer in 60–90 words with key points."),
            SubjectSectionSpec("C", "Short Answer (SA)", 15, "5 Questions (3 marks each)", 40, "Explain physiological factors and biomechanical principles."),
            SubjectSectionSpec("D", "Case Studies", 12, "3 Case Studies (4 marks each)", 35, "Read fixture tables or test battery scenarios carefully."),
            SubjectSectionSpec("E", "Long Answer (LA)", 15, "3 Questions (5 marks each)", 40, "Draw neat fixture tournament diagrams and step-by-step procedures.")
        )
        else -> listOf(
            SubjectSectionSpec("A", "Section A", 16, "Objective questions", 25, "Read questions carefully."),
            SubjectSectionSpec("B", "Section B", 10, "Short Answer I", 25, "Concise answers."),
            SubjectSectionSpec("C", "Section C", 21, "Short Answer II", 45, "Detailed solutions."),
            SubjectSectionSpec("D", "Section D", 8, "Case-Based", 30, "Context-based questions."),
            SubjectSectionSpec("E", "Section E", 15, "Long Answer", 40, "Comprehensive answers.")
        )
    }
}

/**
 * CBSE 3-Hour Exam Simulation Studio.
 *
 * Implements:
 * 1. Cognitive load reduction & 4-7-8 Mindful Reset (Psychology Framework)
 * 2. Integrated 4-tab docked workspace (Question Paper, Section Pacing, Scratchpad, Mindful Reset)
 * 3. Activity-level true focus tracking (eliminates false task-switch penalties)
 * 4. Step-wise CBSE Marking Scheme Self-Scoring & Solution Walkthrough
 * 5. Full Room database truth persistence (exam_attempts, focus_sessions, FirebaseSync)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSimulationScreen(
    paperTitle: String,
    subject: String,
    paperUrl: String,
    markingSchemeUrl: String,
    onOpenPdf: (url: String, title: String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { BoardsRepository(context) }

    // Persistent timer & simulation state (survives screen rotation)
    var isReadingPeriod by rememberSaveable { mutableStateOf(true) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(900) } // 15 mins reading = 900s
    var isExamRunning by rememberSaveable { mutableStateOf(true) }
    val examStartTime = rememberSaveable { System.currentTimeMillis() }
    var timeTakenSeconds by rememberSaveable { mutableLongStateOf(0L) }

    // Active Tab: 0 = Question Paper, 1 = Section Pacing, 2 = Scratchpad, 3 = Mindful Reset
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Completed Sections tracking (Section A to E)
    var completedSectionsList by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Activity-level true app switch detection
    var appSwitchCount by rememberSaveable { mutableIntStateOf(0) }
    var showAppSwitchNotice by rememberSaveable { mutableStateOf(false) }

    // Scratchpad notes (survives rotation & tabs)
    var scratchpadNotes by rememberSaveable { mutableStateOf("") }

    // Dialog & Modal states
    var showSubmitConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showCompletionStudio by rememberSaveable { mutableStateOf(false) }

    // Activity Lifecycle Observer: ONLY fires ON_STOP when app is genuinely backgrounded
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isExamRunning) {
                appSwitchCount++
                showAppSwitchNotice = true
            }
        }
        (activity as? ComponentActivity)?.lifecycle?.addObserver(observer)
        onDispose {
            (activity as? ComponentActivity)?.lifecycle?.removeObserver(observer)
        }
    }

    // Intercept hardware / gesture back during active exam
    BackHandler(enabled = isExamRunning) {
        showSubmitConfirmDialog = true
    }

    // Continuous 1-second exam clock
    LaunchedEffect(isExamRunning, isReadingPeriod, secondsLeft) {
        if (isExamRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        } else if (isExamRunning && secondsLeft <= 0) {
            if (isReadingPeriod) {
                // Auto-transition to 3-hour writing phase
                isReadingPeriod = false
                secondsLeft = 10800 // 3 hours = 10800s
            } else {
                // Time up! Auto-conclude exam
                isExamRunning = false
                timeTakenSeconds = (System.currentTimeMillis() - examStartTime) / 1000
                showCompletionStudio = true
            }
        }
    }

    // Timer formatting
    val hours = secondsLeft / 3600
    val minutes = (secondsLeft % 3600) / 60
    val seconds = secondsLeft % 60
    val formattedClock = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    // Anxiety-managed timer styling
    val isUrgent = !isReadingPeriod && secondsLeft < 300 // under 5 minutes
    val isWarning = !isReadingPeriod && secondsLeft in 300..1800 // under 30 minutes

    val timerBgColor = when {
        isReadingPeriod -> AccentAmberLight
        isUrgent -> ErrorRed.copy(alpha = 0.9f)
        isWarning -> AccentAmber.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val timerTextColor = when {
        isReadingPeriod -> AccentAmberOnBg
        isUrgent -> Color.White
        isWarning -> AccentAmberOnBg
        else -> MaterialTheme.colorScheme.primary
    }

    val subjectTokens = remember(subject) { getAdaptiveSubjectTokens(subject, false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val sectionSpecs = remember(subject) { getSubjectSectionSpecs(subject) }
                    Column {
                        Text(
                            text = subject.ifBlank { "Exam Simulation" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${completedSectionsList.size}/${sectionSpecs.size} Sections Done",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    LiquidGlassBackButton(
                        onClick = {
                            if (isExamRunning) {
                                showSubmitConfirmDialog = true
                            } else {
                                onBackClick()
                            }
                        },
                        contentDescription = "Exit Simulation",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    // CBSE Live Clock Pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = timerBgColor,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isReadingPeriod) Icons.Default.MenuBook else Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = timerTextColor
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = (if (isReadingPeriod) "Read " else "") + formattedClock,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = timerTextColor
                            )
                        }
                    }

                    // Conclude / Submit Action
                    FilledTonalButton(
                        onClick = { showSubmitConfirmDialog = true },
                        shape = ExpressivePillSmall,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Submit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // 4-Tab Dock Navigation
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.height(64.dp)
            ) {
                val tabs = listOf(
                    Triple(0, "Question Paper", Icons.Default.Description),
                    Triple(1, "Section Pacing", Icons.Default.Speed),
                    Triple(2, "Scratchpad", Icons.Default.EditNote),
                    Triple(3, "Mindful Reset", Icons.Default.SelfImprovement)
                )

                tabs.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 15-Minute Reading Period Active Alert - shown only on Question Paper tab to avoid keyboard collision
                AnimatedVisibility(visible = isReadingPeriod && selectedTab == 0) {
                    Surface(
                        color = AccentAmberLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = AccentAmberOnBg,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reading Period: Scan questions & section choices.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmberOnBg
                                )
                            }
                            Button(
                                onClick = {
                                    isReadingPeriod = false
                                    secondsLeft = 10800 // Start 3-hour timer
                                },
                                shape = ExpressivePillSmall,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmberOnBg)
                            ) {
                                Text("Start Writing →", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                // Respectful Focus Monitor Banner (non-accusatory)
                AnimatedVisibility(visible = showAppSwitchNotice && appSwitchCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Focus Monitor: $appSwitchCount app background event(s) logged.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showAppSwitchNotice = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Active Tab Content
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> QuestionPaperTab(
                            paperUrl = paperUrl,
                            paperTitle = paperTitle,
                            onOpenFullscreen = { onOpenPdf(paperUrl, paperTitle) }
                        )
                        1 -> SectionPacingTab(
                            subject = subject,
                            completedSections = completedSectionsList.toSet(),
                            onToggleSection = { sectionKey ->
                                completedSectionsList = if (completedSectionsList.contains(sectionKey)) {
                                    completedSectionsList - sectionKey
                                } else {
                                    completedSectionsList + sectionKey
                                }
                            },
                            secondsElapsed = (if (isReadingPeriod) 0 else 10800 - secondsLeft).toLong()
                        )
                        2 -> ScratchpadTab(
                            subject = subject,
                            notes = scratchpadNotes,
                            onNotesChange = { scratchpadNotes = it },
                            isReadingPeriod = isReadingPeriod,
                            onStartWritingEarly = {
                                isReadingPeriod = false
                                secondsLeft = 10800
                            }
                        )
                        3 -> MindfulResetTab(
                            onReturnToExam = { selectedTab = 0 }
                        )
                    }
                }
            }
        }
    }

    // Exit / Submit Confirmation Dialog
    if (showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            title = {
                Text("Conclude Exam Simulation?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to conclude this 3-hour CBSE simulation? Your focus integrity, section progress, and time elapsed will be saved to your dashboard, and the official step-wise marking scheme will be unlocked for self-evaluation.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showSubmitConfirmDialog = false
                            isExamRunning = false
                            timeTakenSeconds = (System.currentTimeMillis() - examStartTime) / 1000
                            showCompletionStudio = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressivePillSmall
                    ) {
                        Text("Yes, Conclude & Grade", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { showSubmitConfirmDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressivePillSmall
                    ) {
                        Text("Continue Writing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            dismissButton = null
        )
    }

    // Post-Exam Completion & Step-Wise Self-Scoring Studio Modal
    if (showCompletionStudio) {
        ExamCompletionStudioDialog(
            paperTitle = paperTitle,
            subject = subject,
            timeTakenSeconds = timeTakenSeconds.coerceAtLeast(15L),
            appSwitchCount = appSwitchCount,
            completedSections = completedSectionsList,
            scratchpadNotes = scratchpadNotes,
            markingSchemeUrl = markingSchemeUrl,
            onOpenMarkingScheme = { msUrl ->
                onOpenPdf(msUrl, "$paperTitle — Official Marking Scheme")
            },
            onSaveAndExit = { attempt ->
                coroutineScope.launch {
                    try {
                        repository.saveExamAttempt(attempt)
                        repository.database.focusSessionDao().recordSession(
                            FocusSessionEntity(
                                sessionType = "cbse_mock_180",
                                targetSubjectId = subject,
                                durationMinutes = 180,
                                completedMinutes = (attempt.timeSpentSeconds / 60).toInt().coerceAtLeast(1),
                                wasInterrupted = attempt.appSwitchCount > 0,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        FirebaseSyncManager.getInstance(context).syncExamSimulation(
                            id = "mock_${System.currentTimeMillis()}",
                            paperId = paperTitle,
                            paperTitle = paperTitle,
                            subject = subject,
                            durationSeconds = 10800,
                            timeSpentSeconds = attempt.timeSpentSeconds.toInt(),
                            tabSwitches = attempt.appSwitchCount,
                            status = "completed"
                        )
                    } catch (_: Exception) {}
                    showCompletionStudio = false
                    onBackClick()
                }
            }
        )
    }
}

// =============================================================================
// TAB 1: INTEGRATED QUESTION PAPER PDF VIEWER
// =============================================================================

@Composable
private fun QuestionPaperTab(
    paperUrl: String,
    paperTitle: String,
    onOpenFullscreen: () -> Unit
) {
    if (paperUrl.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = ExpressiveCardMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Question Paper Ready",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = paperTitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenFullscreen, shape = ExpressivePillSmall) {
                        Text("Open Fullscreen PDF")
                    }
                }
            }
        }
        return
    }

    val state = rememberPdfReaderState(source = paperUrl, title = paperTitle, paired = null)
    val ui by state.uiState.collectAsState()

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            ui.isLoading -> {
                val progress = (ui.loadState as? com.boardsprep.onboard.core.pdf.PdfLoadState.Loading)?.progress
                PdfLoadingState(progress)
            }
            ui.isError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Unable to render inline preview", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onOpenFullscreen, shape = ExpressivePillSmall) {
                            Text("Open in Fullscreen Reader")
                        }
                    }
                }
            }
            ui.isReady -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (zoomScale > 1.2f) {
                                        zoomScale = 1f
                                        zoomOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.2f
                                        zoomOffset = Offset(
                                            x = (size.width / 2f - tapOffset.x) * 1.2f,
                                            y = (size.height / 2f - tapOffset.y) * 1.2f
                                        )
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                if (newScale == 1f) {
                                    zoomOffset = Offset.Zero
                                } else {
                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                    zoomOffset = Offset(
                                        x = (zoomOffset.x + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (zoomOffset.y + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                }
                                zoomScale = newScale
                            }
                        }
                ) {
                    PdfContinuousCanvas(
                        stateHolder = state,
                        onToggleChrome = { /* Integrated in tab */ },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                            }
                    )
                }

                // Upgraded Compact 3-Action Floating Pill: [ Page 1/16 | 100% | ⛶ ]
                Surface(
                    shape = ExpressivePillSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Page Indicator
                        Text(
                            text = "${ui.currentPage + 1} / ${ui.totalPages.coerceAtLeast(1)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        VerticalDivider(
                            modifier = Modifier.height(14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // 2. Zoom Preset Cycle (100% -> 150% -> 200% -> 100%)
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val nextScale = when {
                                    zoomScale < 1.3f -> 1.5f
                                    zoomScale < 1.8f -> 2.0f
                                    else -> 1.0f
                                }
                                zoomScale = nextScale
                                if (nextScale == 1.0f) zoomOffset = Offset.Zero
                            }
                        )

                        VerticalDivider(
                            modifier = Modifier.height(14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // 3. Fullscreen (icon only)
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(onClick = onOpenFullscreen),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 2: CBSE SECTION PACING & STRATEGY
// =============================================================================

@Composable
private fun SectionPacingTab(
    subject: String,
    completedSections: Set<String>,
    onToggleSection: (String) -> Unit,
    secondsElapsed: Long
) {
    val sections = remember(subject) { getSubjectSectionSpecs(subject) }
    val minutesElapsed = (secondsElapsed / 60).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Strategy Overview Card
        Card(
            shape = ExpressiveCardMedium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CBSE 180-Minute Pacing Blueprint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val writingMinutes = sections.sumOf { it.suggestedMinutes }
                val bufferMinutes = (180 - writingMinutes).coerceAtLeast(10)
                Text(
                    text = "Total Writing Time: $writingMinutes minutes allocated across ${sections.size} sections + $bufferMinutes minutes reserved for final proofreading, units, and formula verification.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                )
            }
        }

        // Section Cards
        sections.forEach { section ->
            val isDone = completedSections.contains(section.key)
            Card(
                shape = ExpressiveCardLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (isDone) SuccessGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isDone) androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Section ${section.key} — ${section.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${section.questionSummary} • ${section.maxMarks} Marks",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = ExpressivePillSmall,
                            color = if (isDone) SuccessGreen else MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = if (isDone) "✓ Completed" else "~${section.suggestedMinutes} mins",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDone) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "💡 Examiner Tip: ${section.examinerTip}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onToggleSection(section.key) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressivePillSmall,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDone) SuccessGreen else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDone) "Mark as In-Progress" else "Mark Section ${section.key} Done",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Buffer Revision Card
        Card(
            shape = ExpressiveCardMedium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RateReview,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Final 15-Minute Revision Buffer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Check question numbers, verify SI units on all numericals, and ensure supplement sheets are tied.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =============================================================================
// TAB 3: IN-EXAM SCRATCHPAD & FORMULA PAD
// =============================================================================

@Composable
private fun ScratchpadTab(
    subject: String,
    notes: String,
    onNotesChange: (String) -> Unit,
    isReadingPeriod: Boolean = false,
    onStartWritingEarly: () -> Unit = {}
) {
    val s = subject.lowercase()
    val isChemistry = s.contains("chem")
    val isPhysics = s.contains("phys")
    val isMath = s.contains("math")
    val isBiology = s.contains("bio")

    // Subject-tailored quick helpers
    val quickChips = remember(subject) {
        when {
            isChemistry -> listOf(
                "⇌" to " ⇌ ",
                "→" to " → ",
                "Δ" to "Δ",
                "×10ⁿ" to " × 10^",
                "log" to "log(",
                "[ ]" to "[ ]",
                "°C" to "°C",
                "K" to " K",
                "mol/L" to " mol/L",
                "E°" to "E°"
            )
            isPhysics -> listOf(
                "π" to "π",
                "√" to "√",
                "θ" to "θ",
                "Δ" to "Δ",
                "×10ⁿ" to " × 10^",
                "Ω" to " Ω",
                "λ" to "λ",
                "±" to "±",
                "∫" to "∫",
                "μ" to "μ",
                "ε₀" to "ε₀",
                "ħ" to "ħ"
            )
            isMath -> listOf(
                "π" to "π",
                "√" to "√",
                "θ" to "θ",
                "Δ" to "Δ",
                "∫" to "∫ ",
                "dx" to " dx",
                "dy/dx" to "dy/dx",
                "Σ" to "Σ",
                "lim" to "lim ",
                "∞" to "∞",
                "±" to "±",
                "×10ⁿ" to " × 10^"
            )
            isBiology -> listOf(
                "♀" to "♀",
                "♂" to "♂",
                "DNA" to "DNA",
                "RNA" to "RNA",
                "ATP" to "ATP",
                "CO₂" to "CO₂",
                "O₂" to "O₂",
                "pH" to "pH"
            )
            else -> listOf(
                "• Bullet" to "\n• ",
                "1. Number" to "\n1. ",
                "Format: Notice" to "\n[ORGANIZATION NAME]\nNOTICE\nDate: DD/MM/YYYY\n[HEADING]\n[Body of Notice - 50 words]\nName\nDesignation",
                "Format: Letter" to "\nSender's Address:\nDate:\nReceiver's Address:\n\nSubject: \n\nSir/Madam,\n[Body]\n\nYours faithfully,\n[Name]"
            )
        }
    }

    val headerTitle = when {
        isChemistry -> "Chemistry Reactions & Formula Pad"
        isPhysics -> "Physics Calculations & Formula Pad"
        isMath -> "Mathematics Rough Work & Calculations"
        isBiology -> "Biology Rough Diagrams & Notes"
        else -> "English Format Outline & Rough Notes"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Subtle hint if reading period is still active
        if (isReadingPeriod) {
            Surface(
                shape = ExpressivePillSmall,
                color = AccentAmberLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "💡 Reading period active • Typing starts 3-hr clock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmberOnBg
                    )
                    TextButton(
                        onClick = onStartWritingEarly,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Start Now →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAmberOnBg)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = headerTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${notes.length} characters • Autosaved offline",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (notes.isNotBlank()) {
                TextButton(
                    onClick = { onNotesChange("") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clear All", fontSize = 11.sp, color = ErrorRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Subject-Specific Symbol / Formatting Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickChips.forEach { (label, insertText) ->
                Surface(
                    shape = ExpressivePillSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        if (isReadingPeriod) onStartWritingEarly()
                        onNotesChange(notes + insertText)
                    }
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = {
                if (isReadingPeriod) onStartWritingEarly()
                onNotesChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    text = if (isChemistry)
                        "Type reaction equations, molar mass steps, equilibrium expressions, or rough notes here..."
                    else if (isPhysics || isMath)
                        "Type rough calculations, formula checks, order of questions, or steps here during your exam..."
                    else
                        "Draft essay points, character sketch keywords, notice outlines, or word counts here...",
                    fontSize = 12.sp
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (isPhysics || isMath || isChemistry) FontFamily.Monospace else FontFamily.Default,
                fontSize = 13.sp
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        )
    }
}

// =============================================================================
// TAB 4: MINDFUL RESET (4-7-8 ANXIETY PACER)
// =============================================================================

@Composable
private fun MindfulResetTab(
    onReturnToExam: () -> Unit
) {
    // 4-7-8 Breathing Cycle (4s Inhale, 7s Hold, 8s Exhale = 19s loop)
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")

    // Progress 0f..1f over 19 seconds
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 19f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_timeline"
    )

    val (phaseText, circleScale, phaseColor) = when {
        progress < 4f -> Triple(
            "Breathe In Slowly (4s)...",
            0.6f + (progress / 4f) * 0.4f,
            MaterialTheme.colorScheme.primary
        )
        progress < 11f -> Triple(
            "Hold Breath Gently (7s)...",
            1.0f,
            AccentAmber
        )
        else -> Triple(
            "Exhale Completely (8s)...",
            1.0f - ((progress - 11f) / 8f) * 0.4f,
            SuccessGreen
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "30-Second Mindful Reset",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Acute exam stress blocks working memory. Slow, rhythmic breathing activates the parasympathetic nervous system, resetting clarity in 30 seconds.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Animated Breathing Bubble
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = phaseColor.copy(alpha = 0.15f),
                modifier = Modifier.size((180 * circleScale).dp)
            ) {}

            Surface(
                shape = CircleShape,
                color = phaseColor.copy(alpha = 0.35f),
                modifier = Modifier.size((130 * circleScale).dp)
            ) {}

            Surface(
                shape = CircleShape,
                color = phaseColor,
                modifier = Modifier.size(70.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = phaseText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = phaseColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            shape = ExpressiveCardMedium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "💬 Self-Talk Anchor: \"I have prepared for this. I don't need to know every answer at once. I only need to solve one step at a time.\"",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onReturnToExam,
            shape = ExpressivePillSmall,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Back to Question Paper →", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// POST-EXAM COMPLETION & STEP-WISE SELF-SCORING STUDIO
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamCompletionStudioDialog(
    paperTitle: String,
    subject: String,
    timeTakenSeconds: Long,
    appSwitchCount: Int,
    completedSections: List<String>,
    scratchpadNotes: String,
    markingSchemeUrl: String,
    onOpenMarkingScheme: (String) -> Unit,
    onSaveAndExit: (ExamAttemptEntity) -> Unit
) {
    val specs = remember(subject) { getSubjectSectionSpecs(subject) }
    val maxMarks = remember(specs) { specs.sumOf { it.maxMarks } }

    // Section marks inputs: clean empty string by default, no default numbers, no stray dots!
    val sectionScores = remember { mutableStateMapOf<String, String>() }

    val calculatedScore = sectionScores.values.sumOf { it.toIntOrNull() ?: 0 }

    val hrs = timeTakenSeconds / 3600
    val mins = (timeTakenSeconds % 3600) / 60
    val secs = timeTakenSeconds % 60
    val timeDisplay = if (hrs > 0) "${hrs}h ${mins}m ${secs}s" else "${mins}m ${secs}s"

    Dialog(onDismissRequest = { /* Modal requires deliberate save */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = ExpressiveCardLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Examination Concluded!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = paperTitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Row with IntrinsicSize.Min to ensure equal height and no text wrapping bugs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = ExpressiveCardMedium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(timeDisplay, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = ExpressiveCardMedium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("INTEGRITY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (appSwitchCount == 0) "100%" else "${(100 - appSwitchCount * 10).coerceAtLeast(0)}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (appSwitchCount == 0) SuccessGreen else AccentAmber
                            )
                            Text(
                                text = if (appSwitchCount == 0) "Focused" else "$appSwitchCount Switches",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = ExpressiveCardMedium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("SECTIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${completedSections.size}/${specs.size}", fontSize = 13.sp, fontWeight = FontWeight.Black)
                            Text("Done", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CBSE Step-Wise Self-Scoring Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveCardMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Section-Wise Self-Grading",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = ExpressivePillSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "Score: $calculatedScore / $maxMarks",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    softWrap = false,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Inspect your answer booklet against the official marking scheme and record section marks:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic Section Inputs Row (3 for English, 5 for Physics/Chemistry/Maths)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specs.forEach { spec ->
                                SectionMarkField(
                                    label = "Sec ${spec.key}",
                                    maxMarksPlaceholder = "/${spec.maxMarks}",
                                    value = sectionScores[spec.key] ?: "",
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(2)
                                        val num = digits.toIntOrNull()
                                        if (digits.isEmpty() || (num != null && num <= spec.maxMarks)) {
                                            if (digits.isEmpty()) {
                                                sectionScores.remove(spec.key)
                                            } else {
                                                sectionScores[spec.key] = digits
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Marking Scheme Unlock Button - Single flat vector icon, no emojis!
                if (markingSchemeUrl.isNotBlank()) {
                    Button(
                        onClick = { onOpenMarkingScheme(markingSchemeUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveCardMedium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Official Marking Scheme (PDF)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Final Save & Return Button
                Button(
                    onClick = {
                        val attempt = ExamAttemptEntity(
                            paperTitle = paperTitle,
                            subjectId = subject,
                            durationSeconds = 10800,
                            timeSpentSeconds = timeTakenSeconds,
                            readingTimeSeconds = 900,
                            appSwitchCount = appSwitchCount,
                            completedSectionsJson = Gson().toJson(completedSections),
                            selfScore = calculatedScore,
                            maxMarks = maxMarks,
                            scratchpadNotes = scratchpadNotes,
                            completedAt = System.currentTimeMillis()
                        )
                        onSaveAndExit(attempt)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveCardMedium,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Attempt to Dashboard & Exit", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionMarkField(
    label: String,
    maxMarksPlaceholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            maxLines = 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = maxMarksPlaceholder,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
