package com.boardsprep.onboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.ai.*
import com.boardsprep.onboard.core.glass.components.LiquidGlassBackButton
import com.boardsprep.onboard.core.theme.AccentAmber
import com.boardsprep.onboard.core.theme.ErrorRed
import com.boardsprep.onboard.core.theme.PrimaryBlue
import com.boardsprep.onboard.core.theme.SuccessGreen
import com.boardsprep.onboard.data.models.Quiz
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ai.LiquidGlassAiSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quizFile: String,
    title: String,
    repository: BoardsRepository,
    onBackClick: () -> Unit,
    onOpenAiSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefsManager = remember { AiPreferencesManager.getInstance(context) }
    val responseCache = remember { AiResponseCache.getInstance(context) }
    val aiEngine = remember { AiProviderEngine() }

    var quiz by remember { mutableStateOf<Quiz?>(null) }
    var currentIndex by remember { mutableStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<Int, Int>() } // questionIndex -> selectedOptionIndex
    var isPracticeMode by remember { mutableStateOf(true) }
    var isQuizCompleted by remember { mutableStateOf(false) }
    var isReviewMode by remember { mutableStateOf(false) }
    var reviewFilter by remember { mutableStateOf("ALL") } // ALL, INCORRECT, CORRECT, UNATTEMPTED
    var elapsedSeconds by remember { mutableStateOf(0L) }

    // AI Socratic & Diagnostic State
    var showAiSheet by remember { mutableStateOf(false) }
    var aiSheetTitle by remember { mutableStateOf("Socratic Study Companion") }
    var aiStreamingText by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }
    var isAiResponseCached by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var aiCitations by remember { mutableStateOf<List<AiCitation>>(emptyList()) }
    var aiCurrentContext by remember { mutableStateOf<DppQuestionContext?>(null) }
    var isWebSearchActive by remember { mutableStateOf(prefsManager.isWebSearchEnabled()) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    val conversationHistory = remember { mutableStateListOf<AiMessage>() }

    fun startAiStreaming(dppCtx: DppQuestionContext, isHintOnly: Boolean, customQuery: String? = null, forceRefresh: Boolean = false) {
        aiJob?.cancel()
        aiErrorMessage = null
        showAiSheet = true
        aiCurrentContext = dppCtx

        val provider = prefsManager.getSelectedProvider()
        val apiKey = prefsManager.getApiKey(provider)
        val model = prefsManager.getSelectedModel(provider)
        val baseUrl = prefsManager.getBaseUrl(provider)

        aiSheetTitle = if (isHintOnly) "Socratic Study Companion" else "DPP Diagnostic Breakdown"

        // Local Response Caching check: re-opening previously answered hints/solutions costs 0 tokens!
        val actionType = when {
            customQuery != null -> "custom_${customQuery.hashCode()}"
            isHintOnly -> "hint"
            else -> "diagnostic"
        }
        val cacheKey = AiResponseCache.generateKey(dppCtx.questionText, actionType, provider.id, model)

        if (!forceRefresh) {
            val cached = responseCache.get(cacheKey)
            if (cached != null) {
                aiStreamingText = cached.content
                aiCitations = cached.citations
                isAiGenerating = false
                isAiResponseCached = true
                return
            }
        }

        isAiResponseCached = false
        aiStreamingText = ""
        aiCitations = emptyList()
        isAiGenerating = true

        if (apiKey.isBlank() && provider != AiProvider.CUSTOM) {
            isAiGenerating = false
            aiErrorMessage = "API Key is missing for ${provider.displayName}. Tap below to configure your key in AI Settings."
            return
        }

        val systemPrompt = CbsePromptBuilder.buildSystemPrompt(
            subject = dppCtx.subject,
            chapter = dppCtx.chapter,
            webSearchEnabled = isWebSearchActive
        )

        val userPrompt = if (customQuery != null) {
            customQuery
        } else if (isHintOnly) {
            CbsePromptBuilder.buildSocraticHintPrompt(dppCtx)
        } else {
            CbsePromptBuilder.buildDppDiagnosticPrompt(dppCtx)
        }

        // Hard Token Cap: 150 for Hint (covers reasoning + concise hint), 280 for Diagnostic
        val tokenCap = if (isHintOnly) 150 else 280

        conversationHistory.clear()
        conversationHistory.add(AiMessage("system", systemPrompt))
        conversationHistory.add(AiMessage("user", userPrompt))

        aiJob = coroutineScope.launch {
            try {
                aiEngine.streamChat(
                    messages = conversationHistory.toList(),
                    provider = provider,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    enableWebSearch = isWebSearchActive,
                    maxTokens = tokenCap
                ).collect { chunk ->
                    if (chunk.errorMessage != null) {
                        aiErrorMessage = chunk.errorMessage
                        isAiGenerating = false
                    } else {
                        if (chunk.deltaText.isNotEmpty()) {
                            aiStreamingText += chunk.deltaText
                        }
                        if (chunk.citations.isNotEmpty()) {
                            aiCitations = chunk.citations
                        }
                        if (chunk.isComplete) {
                            isAiGenerating = false
                            conversationHistory.add(AiMessage("assistant", aiStreamingText))
                            // Cache completed response locally
                            responseCache.put(cacheKey, aiStreamingText, aiCitations)
                        }
                    }
                }
            } catch (e: Exception) {
                aiErrorMessage = e.localizedMessage ?: "Failed to generate AI response"
                isAiGenerating = false
            }
        }
    }

    fun sendFollowUp(query: String) {
        val currentCtx = aiCurrentContext ?: return
        val provider = prefsManager.getSelectedProvider()
        val apiKey = prefsManager.getApiKey(provider)
        val model = prefsManager.getSelectedModel(provider)
        val baseUrl = prefsManager.getBaseUrl(provider)

        // Token Optimization: Strip entire multi-turn history bloat!
        // Resend only System Prompt + Compact Question Context + Specific Follow-Up Prompt.
        val systemPrompt = CbsePromptBuilder.buildSystemPrompt(
            subject = currentCtx.subject,
            chapter = currentCtx.chapter,
            webSearchEnabled = isWebSearchActive
        )
        val contextPrompt = "Context Question: \"${currentCtx.questionText}\"\nOption (${currentCtx.correctOptionLetter}) is correct."
        val strippedMessages = listOf(
            AiMessage("system", systemPrompt),
            AiMessage("user", "$contextPrompt\n\nTask: $query")
        )

        aiStreamingText += "\n\n**Action:** $query\n\n"
        isAiGenerating = true
        isAiResponseCached = false

        val tokenCap = if (query.contains("analogy", ignoreCase = true) || query.contains("twin", ignoreCase = true)) 140 else 280

        aiJob?.cancel()
        aiJob = coroutineScope.launch {
            try {
                aiEngine.streamChat(
                    messages = strippedMessages,
                    provider = provider,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    enableWebSearch = isWebSearchActive,
                    maxTokens = tokenCap
                ).collect { chunk ->
                    if (chunk.errorMessage != null) {
                        aiErrorMessage = chunk.errorMessage
                        isAiGenerating = false
                    } else {
                        if (chunk.deltaText.isNotEmpty()) {
                            aiStreamingText += chunk.deltaText
                        }
                        if (chunk.citations.isNotEmpty()) {
                            aiCitations = chunk.citations
                        }
                        if (chunk.isComplete) {
                            isAiGenerating = false
                        }
                    }
                }
            } catch (e: Exception) {
                aiErrorMessage = e.localizedMessage ?: "Failed to stream follow-up"
                isAiGenerating = false
            }
        }
    }

    fun requestAiExplanation(qIndex: Int, isHintOnly: Boolean, customQuery: String? = null) {
        val activeQuiz = quiz ?: return
        val q = activeQuiz.questions.getOrNull(qIndex) ?: return
        val dppCtx = DppQuestionContext(
            subject = title.substringBefore("—").trim().ifBlank { "CBSE Class 12" },
            chapter = title.substringAfter("—").trim().ifBlank { "Current Chapter" },
            questionText = q.question,
            options = q.options,
            correctOptionIndex = q.correctOptionIndex,
            selectedOptionIndex = userAnswers[qIndex],
            officialExplanation = q.explanation,
            isPracticeMode = isPracticeMode
        )
        startAiStreaming(dppCtx, isHintOnly, customQuery)
    }

    LaunchedEffect(quizFile) {
        quiz = repository.getQuizForFile(quizFile)
    }

    // Timer active while taking the quiz
    LaunchedEffect(isQuizCompleted) {
        while (!isQuizCompleted) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val currentQuiz = quiz

    if (currentQuiz == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        LiquidGlassBackButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                )
            }
        ) { innerPadding ->
            com.boardsprep.onboard.ui.components.QuizAsymmetricLoadingView(
                modifier = Modifier.padding(innerPadding)
            )
        }
        return
    }


    val totalQ = (if (currentQuiz.totalQuestions > 0) currentQuiz.totalQuestions else currentQuiz.questions.size).coerceAtLeast(1)
    val correctCount = currentQuiz.questions.indices.count { i ->
        userAnswers[i] == currentQuiz.questions.getOrNull(i)?.correctOptionIndex
    }
    val incorrectCount = currentQuiz.questions.indices.count { i ->
        userAnswers.containsKey(i) && userAnswers[i] != currentQuiz.questions.getOrNull(i)?.correctOptionIndex
    }
    val unattemptedCount = totalQ - userAnswers.size

    val formatTimer = { sec: Long ->
        val m = sec / 60
        val s = sec % 60
        "%02d:%02d".format(m, s)
    }

    fun finishQuiz() {
        coroutineScope.launch {
            repository.recordQuizAttempt(quizFile, correctCount, totalQ, elapsedSeconds)
            // Auto-ingest missed questions into Error Vault (Mistake Notebook)
            currentQuiz?.questions?.forEachIndexed { idx, q ->
                val userPick = userAnswers[idx]
                if (userPick != null && userPick != q.correctOptionIndex) {
                    val opts = q.options
                    repository.saveQuizMistake(
                        questionId = "${quizFile}_q$idx",
                        chapterId = quizFile,
                        subjectId = "",
                        questionText = q.question,
                        optionA = opts.getOrNull(0) ?: "",
                        optionB = opts.getOrNull(1) ?: "",
                        optionC = opts.getOrNull(2) ?: "",
                        optionD = opts.getOrNull(3) ?: "",
                        correctOptionIndex = q.correctOptionIndex,
                        userSelectedOptionIndex = userPick,
                        explanation = q.explanation,
                        mistakeCategory = "conceptual"
                    )

                }
            }
        }
        isQuizCompleted = true
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isReviewMode) "Detailed Solutions Review" else if (isPracticeMode) "Practice Mode • Instant Explanations" else "Exam Mode • Timed (${formatTimer(elapsedSeconds)})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    LiquidGlassBackButton(
                        onClick = {
                            if (isReviewMode) {
                                isReviewMode = false
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    IconButton(onClick = onOpenAiSettings) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!isQuizCompleted) {
                        // Switch between Practice and Exam Mode
                        FilterChip(
                            selected = isPracticeMode,
                            onClick = { isPracticeMode = !isPracticeMode },
                            label = {
                                Text(if (isPracticeMode) "⚡ Practice" else "⏱️ Exam", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        if (!isPracticeMode) {
                            FilledTonalButton(
                                onClick = { finishQuiz() },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("Submit", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (!isQuizCompleted && !isReviewMode) {
                // Fixed Bottom Navigation Bar - ALWAYS VISIBLE!
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Button
                        OutlinedButton(
                            onClick = {
                                if (currentIndex > 0) currentIndex--
                            },
                            enabled = currentIndex > 0,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }

                        // Status indicator
                        val isAnswered = userAnswers.containsKey(currentIndex)
                        Text(
                            text = if (isAnswered) "Answered" else "Unanswered",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAnswered) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Next or Finish Button
                        if (currentIndex < totalQ - 1) {
                            Button(
                                onClick = { currentIndex++ },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Button(
                                onClick = { finishQuiz() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Finish DPP")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isQuizCompleted && !isReviewMode) {
            // RESULT SUMMARY SCORECARD
            val percentage = if (totalQ > 0) (correctCount * 100) / totalQ else 0
            val gradeRating = when {
                percentage >= 90 -> "Outstanding! Board Topper Performance 🌟"
                percentage >= 75 -> "Very Good! Strong Board Foundation 🎯"
                percentage >= 50 -> "Decent Attempt! Review Missed Concepts 📖"
                else -> "Needs Revision! Focus on Chapter Explanations 💡"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (percentage >= 75) SuccessGreen.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (percentage >= 75) Icons.Default.EmojiEvents else Icons.Default.AssignmentTurnedIn,
                            contentDescription = null,
                            tint = if (percentage >= 75) SuccessGreen else AccentAmber,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("DPP Attempt Summary", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(gradeRating, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))

                // Score banner card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("FINAL SCORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$correctCount",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (percentage >= 75) SuccessGreen else PrimaryBlue
                            )
                            Text(
                                text = " / $totalQ",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Text("$percentage% Accuracy • Time: ${formatTimer(elapsedSeconds)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ScoreStatBadge("Correct", "$correctCount", SuccessGreen)
                            ScoreStatBadge("Incorrect", "$incorrectCount", ErrorRed)
                            ScoreStatBadge("Skipped", "$unattemptedCount", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (incorrectCount > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-Saved to Mistake Notebook",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "$incorrectCount missed question(s) saved to Error Vault for re-attack.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons

                Button(
                    onClick = { isReviewMode = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Review All Answers & Solutions", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        userAnswers.clear()
                        currentIndex = 0
                        isQuizCompleted = false
                        isReviewMode = false
                        elapsedSeconds = 0L
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry DPP")
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onBackClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Chapter Hub", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (isReviewMode) {
            // COMPREHENSIVE REVIEW MODE
            val filteredQuestions = currentQuiz.questions.mapIndexed { idx, q -> idx to q }.filter { (idx, q) ->
                when (reviewFilter) {
                    "CORRECT" -> userAnswers[idx] == q.correctOptionIndex
                    "INCORRECT" -> userAnswers.containsKey(idx) && userAnswers[idx] != q.correctOptionIndex
                    "UNATTEMPTED" -> !userAnswers.containsKey(idx)
                    else -> true
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = reviewFilter == "ALL",
                        onClick = { reviewFilter = "ALL" },
                        label = { Text("All ($totalQ)") }
                    )
                    FilterChip(
                        selected = reviewFilter == "INCORRECT",
                        onClick = { reviewFilter = "INCORRECT" },
                        label = { Text("Incorrect ($incorrectCount)") }
                    )
                    FilterChip(
                        selected = reviewFilter == "CORRECT",
                        onClick = { reviewFilter = "CORRECT" },
                        label = { Text("Correct ($correctCount)") }
                    )
                    FilterChip(
                        selected = reviewFilter == "UNATTEMPTED",
                        onClick = { reviewFilter = "UNATTEMPTED" },
                        label = { Text("Skipped ($unattemptedCount)") }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(filteredQuestions) { _, (origIdx, q) ->
                        val selectedOpt = userAnswers[origIdx]
                        val isUserCorrect = selectedOpt == q.correctOptionIndex

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Q${origIdx + 1}",
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        fontSize = 14.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            selectedOpt == null -> MaterialTheme.colorScheme.outlineVariant
                                            isUserCorrect -> SuccessGreen.copy(alpha = 0.2f)
                                            else -> ErrorRed.copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = when {
                                                selectedOpt == null -> "Skipped"
                                                isUserCorrect -> "Correct (+1)"
                                                else -> "Incorrect (0)"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                selectedOpt == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                isUserCorrect -> SuccessGreen
                                                else -> ErrorRed
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(q.question, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(12.dp))

                                q.options.forEachIndexed { optIdx, optText ->
                                    val optLetter = listOf("A", "B", "C", "D").getOrElse(optIdx) { "" }
                                    val isCorrectOpt = optIdx == q.correctOptionIndex
                                    val isChosenOpt = optIdx == selectedOpt

                                    val optBg = when {
                                        isCorrectOpt -> SuccessGreen.copy(alpha = 0.2f)
                                        isChosenOpt && !isCorrectOpt -> ErrorRed.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val optBorder = when {
                                        isCorrectOpt -> SuccessGreen
                                        isChosenOpt && !isCorrectOpt -> ErrorRed
                                        else -> Color.Transparent
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = optBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, optBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "($optLetter) $optText",
                                                fontSize = 13.sp,
                                                fontWeight = if (isCorrectOpt || isChosenOpt) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCorrectOpt) SuccessGreen else if (isChosenOpt) ErrorRed else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                if (q.explanation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Official Solution & CBSE Concept:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryBlue)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(q.explanation, fontSize = 12.sp, lineHeight = 17.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        requestAiExplanation(origIdx, isHintOnly = false)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!isUserCorrect && selectedOpt != null) "Diagnose Why My Pick Was Wrong (AI)" else "Deep Concept Breakdown (AI)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ACTIVE QUESTION VIEW
            val q = currentQuiz.questions.getOrNull(currentIndex)
            if (q != null) {
                val selectedOption = userAnswers[currentIndex]
                val hasAnsweredCurrent = selectedOption != null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Question Palette Chips Row (1, 2, 3... N)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 0 until totalQ) {
                            val isCurrent = i == currentIndex
                            val isAnswered = userAnswers.containsKey(i)
                            val isAnsCorrect = isPracticeMode && isAnswered && userAnswers[i] == currentQuiz.questions.getOrNull(i)?.correctOptionIndex
                            val isAnsWrong = isPracticeMode && isAnswered && userAnswers[i] != currentQuiz.questions.getOrNull(i)?.correctOptionIndex

                            val chipBg = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isAnsCorrect -> SuccessGreen
                                isAnsWrong -> ErrorRed
                                isAnswered -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }

                            val textColor = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isAnsCorrect || isAnsWrong -> Color.White
                                isAnswered -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(chipBg)
                                    .clickable { currentIndex = i },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textColor
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        // Header progress info
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Question ${currentIndex + 1} of $totalQ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "CBSE Board MCQ • +1 Mark",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Question card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = q.question,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(18.dp)
                                )
                            }
                        }

                        // Options
                        items(q.options.size) { optIdx ->
                            val optText = q.options[optIdx]
                            val optLetter = listOf("A", "B", "C", "D").getOrElse(optIdx) { "" }
                            val isSelected = selectedOption == optIdx
                            val isCorrectOpt = q.correctOptionIndex == optIdx

                            val optionBg = when {
                                isPracticeMode && hasAnsweredCurrent && isCorrectOpt -> SuccessGreen.copy(alpha = 0.2f)
                                isPracticeMode && hasAnsweredCurrent && isSelected && !isCorrectOpt -> ErrorRed.copy(alpha = 0.2f)
                                isSelected -> PrimaryBlue.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surface
                            }

                            val optionBorder = when {
                                isPracticeMode && hasAnsweredCurrent && isCorrectOpt -> SuccessGreen
                                isPracticeMode && hasAnsweredCurrent && isSelected && !isCorrectOpt -> ErrorRed
                                isSelected -> PrimaryBlue
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, optionBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isPracticeMode) {
                                            // In practice mode, record once
                                            if (!hasAnsweredCurrent) {
                                                userAnswers[currentIndex] = optIdx
                                            }
                                        } else {
                                            // In exam mode, can change answer anytime
                                            userAnswers[currentIndex] = optIdx
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = optionBg)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "($optLetter)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = optText,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isPracticeMode && hasAnsweredCurrent) {
                                        if (isCorrectOpt) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                        } else if (isSelected) {
                                            Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }

                        // Socratic Hint trigger (Practice Mode, before answering)
                        if (isPracticeMode && !hasAnsweredCurrent) {
                            item {
                                OutlinedButton(
                                    onClick = {
                                        requestAiExplanation(currentIndex, isHintOnly = true)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = AccentAmber
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Need a Socratic Hint? Ask AI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }

                        // Instant Explanation Card (Practice Mode)
                        if (isPracticeMode && hasAnsweredCurrent) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedOption == q.correctOptionIndex) SuccessGreen.copy(alpha = 0.1f) else AccentAmber.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (selectedOption == q.correctOptionIndex) Icons.Default.CheckCircle else Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (selectedOption == q.correctOptionIndex) SuccessGreen else AccentAmber
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Correct Answer: Option (${q.correctAnswerLetter})",
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedOption == q.correctOptionIndex) SuccessGreen else AccentAmber,
                                                fontSize = 14.sp
                                            )
                                        }
                                        if (q.explanation.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = q.explanation,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                requestAiExplanation(currentIndex, isHintOnly = false)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (selectedOption != q.correctOptionIndex) "Diagnose Why My Pick Was Wrong (AI)" else "Deep Concept Proof & Marking Scheme (AI)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
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
    }

    if (showAiSheet) {
        LiquidGlassAiSheet(
            title = if (aiCurrentContext?.isPracticeMode == true) "Socratic Study Companion" else "DPP Diagnostic Breakdown",
            activeProvider = prefsManager.getSelectedProvider(),
            activeModel = prefsManager.getSelectedModel(prefsManager.getSelectedProvider()),
            streamingContent = aiStreamingText,
            isGenerating = isAiGenerating,
            isWebSearchActive = isWebSearchActive,
            citations = aiCitations,
            isCached = isAiResponseCached,
            errorMessage = aiErrorMessage,
            onToggleWebSearch = { newState ->
                isWebSearchActive = newState
                prefsManager.setWebSearchEnabled(newState)
                aiCurrentContext?.let { ctx ->
                    startAiStreaming(ctx, isHintOnly = false, forceRefresh = true)
                }
            },
            onQuickAction = { promptText ->
                sendFollowUp(promptText)
            },
            onSendFollowUp = { query ->
                sendFollowUp(query)
            },
            onOpenSettings = {
                showAiSheet = false
                onOpenAiSettings()
            },
            onDismissRequest = {
                showAiSheet = false
                aiJob?.cancel()
                isAiGenerating = false
            }
        )
    }
}

@Composable
fun ScoreStatBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
