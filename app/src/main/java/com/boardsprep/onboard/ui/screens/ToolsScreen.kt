package com.boardsprep.onboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBackClick: () -> Unit
) {
    var selectedToolIndex by remember { mutableIntStateOf(0) }

    val toolTabs = listOf(
        ExpressiveTabItem("Best-of-5", icon = Icons.Default.Calculate),
        ExpressiveTabItem("70/30 Passing", icon = Icons.Default.Verified),
        ExpressiveTabItem("Target Sprint", icon = Icons.Default.TrackChanges)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CBSE Class 12 Master Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Official Rule 40.1 • Best-of-5 • Target Gap",
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
            ExpressiveSegmentedTabs(
                tabs = toolTabs,
                selectedTabIndex = selectedToolIndex,
                onTabSelected = { selectedToolIndex = it },
                accentColor = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                when (selectedToolIndex) {
                    0 -> BestOfFiveTool()
                    1 -> PassingCheckerTool()
                    2 -> TargetSprintTool()
                }
            }
        }
    }
}

// ─── 1. CBSE Best-of-5 Calculator ───────────────────────────────────────────
@Composable
private fun BestOfFiveTool() {
    var engScore by remember { mutableStateOf("92") }
    var phyScore by remember { mutableStateOf("88") }
    var chemScore by remember { mutableStateOf("85") }
    var mathBioScore by remember { mutableStateOf("78") }
    var sub5Score by remember { mutableStateOf("94") }
    var opt6Score by remember { mutableStateOf("90") }
    var has6thSub by remember { mutableStateOf(true) }

    fun parse(s: String) = s.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0

    val eng = parse(engScore)
    val phy = parse(phyScore)
    val chem = parse(chemScore)
    val mb = parse(mathBioScore)
    val s5 = parse(sub5Score)
    val s6 = if (has6thSub) parse(opt6Score) else null

    // CBSE Rule: English is mandatory. Best 4 chosen from the remaining 4 (or 5 if 6th subject exists).
    val electiveList = mutableListOf(phy, chem, mb, s5)
    if (s6 != null) electiveList.add(s6)
    val sortedElectives = electiveList.sortedDescending()
    val best4Electives = sortedElectives.take(4)
    val totalBest5 = eng + best4Electives.sum()
    val percentage = totalBest5 / 5.0
    val replacedSubject = if (s6 != null && sortedElectives.size >= 5) sortedElectives.last() else null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AsymmetricLeafHero,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CBSE Best-of-5 Aggregate Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = String.format("%.1f%%", percentage),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${totalBest5.roundToInt()} / 500 Total Marks",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (percentage >= 75.0) SuccessGreen else AccentAmber
                    ) {
                        Text(
                            text = if (percentage >= 75.0) "Distinction" else if (percentage >= 60.0) "1st Division" else "Pass",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                if (has6thSub && replacedSubject != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ℹ️ CBSE Auto-Replacement: Lowest score (${replacedSubject.roundToInt()}m) excluded from Best-of-5 aggregate.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Inputs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveCardLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Subject Marks (out of 100: Theory + Practical)", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                SubjectMarkInput("1. English Core (Compulsory)", engScore) { engScore = it }
                SubjectMarkInput("2. Physics", phyScore) { phyScore = it }
                SubjectMarkInput("3. Chemistry", chemScore) { chemScore = it }
                SubjectMarkInput("4. Mathematics / Biology", mathBioScore) { mathBioScore = it }
                SubjectMarkInput("5. Physical Ed / CS / 5th Subject", sub5Score) { sub5Score = it }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Has 6th Optional Subject?", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(checked = has6thSub, onCheckedChange = { has6thSub = it })
                }

                if (has6thSub) {
                    SubjectMarkInput("6. Optional 6th Subject (e.g. Painting/Music)", opt6Score) { opt6Score = it }
                }
            }
        }
    }
}

@Composable
private fun SubjectMarkInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 3) onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(76.dp),
            shape = ExpressiveCardMedium
        )
    }
}

// ─── 2. CBSE 70/30 & 80/20 Component Passing Checker ────────────────────────
@Composable
private fun PassingCheckerTool() {
    var subjectType by remember { mutableIntStateOf(70) } // 70 or 80
    var theoryScore by remember { mutableStateOf("25") }
    var practicalScore by remember { mutableStateOf("28") }

    val theoryMax = if (subjectType == 70) 70 else 80
    val practicalMax = if (subjectType == 70) 30 else 20
    val theoryCutoff = if (subjectType == 70) 23 else 27 // 33% cutoff
    val practicalCutoff = if (subjectType == 70) 10 else 7

    val theory = theoryScore.toIntOrNull()?.coerceIn(0, theoryMax) ?: 0
    val practical = practicalScore.toIntOrNull()?.coerceIn(0, practicalMax) ?: 0

    val isTheoryPass = theory >= theoryCutoff
    val isPracticalPass = practical >= practicalCutoff
    val isOverallPass = isTheoryPass && isPracticalPass
    val totalScore = theory + practical

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Result Banner
        Surface(
            shape = ExpressiveCardLarge,
            color = if (isOverallPass) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isOverallPass) SuccessGreen.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isOverallPass) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isOverallPass) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isOverallPass) "ELIGIBLE FOR PASS" else if (!isTheoryPass && isPracticalPass) "COMPARTMENT IN THEORY" else "ESSENTIAL REPEAT",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (isOverallPass) SuccessGreen else ErrorRed
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Total: $totalScore / 100 marks (${((totalScore.toDouble()) * 100 / 100).roundToInt()}%)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isOverallPass) {
                        "Congratulations! You meet CBSE Rule 40.1 requirements by passing both Theory ($theory/$theoryMax ≥ $theoryCutoff) and Practical ($practical/$practicalMax ≥ $practicalCutoff) independently."
                    } else if (!isTheoryPass && isPracticalPass) {
                        "Need ${theoryCutoff - theory} more marks in Theory. Eligible for 1-subject Compartment Exam."
                    } else {
                        "CBSE Rule 40.1 requires passing BOTH Theory and Practical independently. Compartment is not permitted if practical is failed."
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveCardLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Subject Format", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = subjectType == 70,
                        onClick = { subjectType = 70 },
                        label = { Text("70/30 (Physics, Chem, Bio, PE)") },
                        shape = ExpressivePillSmall
                    )
                    FilterChip(
                        selected = subjectType == 80,
                        onClick = { subjectType = 80 },
                        label = { Text("80/20 (English, Maths)") },
                        shape = ExpressivePillSmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Theory Score (Max $theoryMax)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Mandatory 33% cutoff: $theoryCutoff marks", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = theoryScore,
                        onValueChange = { theoryScore = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                        shape = ExpressiveCardMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Practical / ALS (Max $practicalMax)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Mandatory 33% cutoff: $practicalCutoff marks", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = practicalScore,
                        onValueChange = { practicalScore = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                        shape = ExpressiveCardMedium
                    )
                }
            }
        }
    }
}

// ─── 3. Target Gap & Sprint Estimator ────────────────────────────────────────
@Composable
private fun TargetSprintTool() {
    var targetPercentage by remember { mutableStateOf("95") }
    var estimatedInternals by remember { mutableStateOf("138") } // out of 140

    val targetPct = targetPercentage.toDoubleOrNull()?.coerceIn(60.0, 100.0) ?: 90.0
    val targetTotal = targetPct * 5.0 // out of 500
    val internals = estimatedInternals.toDoubleOrNull()?.coerceIn(50.0, 140.0) ?: 130.0

    // Remaining theory marks required out of 360/370 (5 theory papers)
    val remainingTheoryNeeded = (targetTotal - internals).coerceAtLeast(0.0)
    val avgTheoryPerPaper = remainingTheoryNeeded / 5.0 // assuming 5 theory papers

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AsymmetricLeafHero,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Sprint Gap Strategy (${targetPercentage}% Target)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveCardMedium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("THEORY NEEDED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${ceil(remainingTheoryNeeded).toInt()} / 360", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveCardMedium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("AVG PER PAPER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("%.1f", avgTheoryPerPaper), fontSize = 16.sp, fontWeight = FontWeight.Black, color = AccentAmber)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "To hit ${targetPercentage}% aggregate, you can afford to lose at most ${(360.0 - remainingTheoryNeeded).coerceAtLeast(0.0).roundToInt()} marks across ALL 5 board theory examinations combined.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
            }
        }

        // Inputs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveCardLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Target Configuration", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Aggregate (%)", fontSize = 13.sp)
                    OutlinedTextField(
                        value = targetPercentage,
                        onValueChange = { targetPercentage = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                        shape = ExpressiveCardMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Practical / ALS Expected", fontSize = 13.sp)
                        Text("Typical school score: 130–138 out of 140", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = estimatedInternals,
                        onValueChange = { estimatedInternals = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                        shape = ExpressiveCardMedium
                    )
                }
            }
        }
    }
}
