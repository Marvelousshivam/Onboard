package com.boardsprep.onboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.boardsprep.onboard.core.theme.*
import kotlinx.coroutines.delay

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
    // Timer state
    var isReadingPeriod by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableIntStateOf(900) } // 15 mins reading = 900s
    var isExamRunning by remember { mutableStateOf(true) }
    val examStartTime = remember { System.currentTimeMillis() }
    var timeTakenSeconds by remember { mutableLongStateOf(0L) }

    // App switch detection
    var appSwitchCount by remember { mutableIntStateOf(0) }
    var showAppSwitchWarning by remember { mutableStateOf(false) }

    // Scratchpad notes
    var scratchpadNotes by remember { mutableStateOf("") }

    // Dialogs
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showCompletionModal by remember { mutableStateOf(false) }

    // Lifecycle observer for anti-distraction app switch detection
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isExamRunning) {
                appSwitchCount++
                showAppSwitchWarning = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Timer countdown loop
    LaunchedEffect(isExamRunning, isReadingPeriod, secondsLeft) {
        if (isExamRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        } else if (isExamRunning && secondsLeft <= 0) {
            if (isReadingPeriod) {
                // Auto transition from 15-min reading period to 3-hour writing period
                isReadingPeriod = false
                secondsLeft = 10800 // 3 hours = 10800s
            } else {
                // Exam time expired
                isExamRunning = false
                timeTakenSeconds = (System.currentTimeMillis() - examStartTime) / 1000
                showCompletionModal = true
            }
        }
    }

    // Format remaining time
    val hours = secondsLeft / 3600
    val minutes = (secondsLeft % 3600) / 60
    val seconds = secondsLeft % 60
    val formattedClock = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    val isUrgent = !isReadingPeriod && secondsLeft < 600 // under 10 minutes

    val subjectTokens = remember(subject) { getAdaptiveSubjectTokens(subject, false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = ExpressivePillSmall,
                            color = subjectTokens.accentColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = subject.ifBlank { "CBSE 2027" },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = subjectTokens.accentColor
                            )
                        }
                        Text(
                            text = paperTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isExamRunning) {
                            showSubmitConfirmDialog = true
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Simulation"
                        )
                    }
                },
                actions = {
                    // Clock Pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (isUrgent) ErrorRed else if (isReadingPeriod) AccentAmber else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isUrgent) Color.White else if (isReadingPeriod) AccentAmberOnBg else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = (if (isReadingPeriod) "Reading: " else "") + formattedClock,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isUrgent) Color.White else if (isReadingPeriod) AccentAmberOnBg else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Early Submit Button
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 15-Minute Reading Period Active Banner
            AnimatedVisibility(visible = isReadingPeriod) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveCardMedium,
                    colors = CardDefaults.cardColors(containerColor = AccentAmberLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = AccentAmberOnBg,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CBSE 15-Minute Reading Period Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AccentAmberOnBg
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Thoroughly inspect the question paper and plan your section sequence. Writing answers in answer booklets is strictly prohibited during reading time.",
                            fontSize = 12.sp,
                            color = AccentAmberOnBg.copy(alpha = 0.85f),
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                isReadingPeriod = false
                                secondsLeft = 10800 // Start 3-hour timer
                            },
                            shape = ExpressivePillSmall,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmberOnBg)
                        ) {
                            Text("Skip to 3h Writing Period →", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }

            // Task Switch Warning Banner
            AnimatedVisibility(visible = showAppSwitchWarning && appSwitchCount > 0) {
                Surface(
                    shape = ExpressiveCardMedium,
                    color = ErrorRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Task switch detected ($appSwitchCount times). Maintain strict CBSE exam room discipline!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ErrorRed
                        )
                    }
                }
            }

            // Primary Question Paper Access Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AsymmetricLeafHero,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = paperTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Official CBSE Board Paper • 3 Hours • 70/80 Marks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onOpenPdf(paperUrl, paperTitle) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveCardMedium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Fullscreen Question Paper PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // CBSE Recommended Section-Wise Time Allocation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveCardLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CBSE 3-Hour Time Allocation Strategy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val timeGuide = listOf(
                        "Section A (16 MCQs / Objective)" to "25 – 30 mins",
                        "Section B (5 Very Short Answer)" to "25 mins",
                        "Section C (7 Short Answer)" to "45 mins",
                        "Section D (2 Case-Based Studies)" to "30 mins",
                        "Section E (3 Long Answer)" to "40 mins",
                        "Final Revision & Unit Checks" to "10 – 15 mins"
                    )

                    timeGuide.forEach { (section, time) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• $section",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = ExpressivePillSmall,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = time,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // In-Exam Scratchpad / Rough Work Notepad
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveCardLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scratchpad / Rough Work",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (scratchpadNotes.isNotBlank()) {
                            TextButton(
                                onClick = { scratchpadNotes = "" },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp, color = ErrorRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = scratchpadNotes,
                        onValueChange = { scratchpadNotes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        placeholder = {
                            Text(
                                "Type rough formulas, question checklists, calculations, or rough notes here during your exam...",
                                fontSize = 12.sp
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        shape = ExpressiveCardMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Finish Examination Button
            Button(
                onClick = { showSubmitConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveCardMedium,
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finish & Submit Examination", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    // Submit Early Confirmation Dialog
    if (showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            title = {
                Text("Submit Examination?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to conclude this 3-hour examination simulation? Your focus integrity and elapsed time will be logged, and the official step-wise marking scheme will be unlocked.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        isExamRunning = false
                        timeTakenSeconds = (System.currentTimeMillis() - examStartTime) / 1000
                        showCompletionModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Yes, Conclude Exam")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text("Keep Writing")
                }
            }
        )
    }

    // Post-Exam Completion & Solution Unlock Modal
    if (showCompletionModal) {
        val totalSecs = timeTakenSeconds.coerceAtLeast(10L)
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        val timeDisplay = if (hrs > 0) "${hrs}h ${mins}m ${secs}s" else "${mins}m ${secs}s"

        Dialog(onDismissRequest = { /* Modal requires deliberate action */ }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = ExpressiveCardLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Examination Completed!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Great job completing this full-length CBSE simulation under strict board examination conditions.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = ExpressiveCardMedium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("TIME TAKEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(timeDisplay, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = ExpressiveCardMedium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("FOCUS INTEGRITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (appSwitchCount == 0) "100% (Zero Tasks)" else "$appSwitchCount Switches",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (appSwitchCount == 0) SuccessGreen else AccentAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (markingSchemeUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                showCompletionModal = false
                                onOpenPdf(markingSchemeUrl, "$paperTitle — Marking Scheme")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveCardMedium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔓 Unlock Official Marking Scheme", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            showCompletionModal = false
                            onBackClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveCardMedium
                    ) {
                        Text("Back to Sample Papers", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
