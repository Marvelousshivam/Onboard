package com.boardsprep.onboard.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.ErrorVaultEntity
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveEmptyState
import com.boardsprep.onboard.ui.components.expressiveBounce
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorVaultScreen(
    repository: BoardsRepository,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val allErrors by repository.getAllErrors().collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("UNRESOLVED") } // UNRESOLVED, ALL, CONCEPTUAL, CALCULATION, FORMULA, RESOLVED

    val unresolvedCount = allErrors.count { !it.isResolved }
    val resolvedCount = allErrors.count { it.isResolved }
    val totalCount = allErrors.size
    val recoveryRate = if (totalCount > 0) ((resolvedCount * 100) / totalCount) else 100

    val filteredList = remember(allErrors, selectedFilter) {
        when (selectedFilter) {
            "UNRESOLVED" -> allErrors.filter { !it.isResolved }
            "RESOLVED" -> allErrors.filter { it.isResolved }
            "CONCEPTUAL" -> allErrors.filter { it.mistakeCategory.equals("conceptual", ignoreCase = true) }
            "CALCULATION" -> allErrors.filter { it.mistakeCategory.equals("calculation", ignoreCase = true) }
            "FORMULA" -> allErrors.filter { it.mistakeCategory.contains("formula", ignoreCase = true) }
            else -> allErrors
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Mistake Notebook",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = ExpressivePillSmall,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "$unresolvedCount Open",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Text(
                            text = "CBSE Board Defense • Convert Errors into +10 Marks",
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            // Tier 1: Bento Stats Runway
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AsymmetricLeafHero,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "VAULT DEFENSE RATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$recoveryRate%",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (recoveryRate >= 70) SuccessGreen else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Resolved",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Circular icon badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatCell(label = "Total Logged", value = "$totalCount")
                            StatCell(label = "Unresolved", value = "$unresolvedCount", color = ErrorRed)
                            StatCell(label = "Conquered", value = "$resolvedCount", color = SuccessGreen)
                        }
                    }
                }
            }

            // Tier 2: Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "UNRESOLVED",
                        onClick = { selectedFilter = "UNRESOLVED" },
                        label = { Text("Open ($unresolvedCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All ($totalCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == "CONCEPTUAL",
                        onClick = { selectedFilter = "CONCEPTUAL" },
                        label = { Text("Conceptual") }
                    )
                    FilterChip(
                        selected = selectedFilter == "CALCULATION",
                        onClick = { selectedFilter = "CALCULATION" },
                        label = { Text("Calculation") }
                    )
                    FilterChip(
                        selected = selectedFilter == "FORMULA",
                        onClick = { selectedFilter = "FORMULA" },
                        label = { Text("Formula") }
                    )
                    FilterChip(
                        selected = selectedFilter == "RESOLVED",
                        onClick = { selectedFilter = "RESOLVED" },
                        label = { Text("Conquered ($resolvedCount)") }
                    )
                }
            }

            // Tier 3: Error List Items
            if (filteredList.isEmpty()) {
                item {
                    ExpressiveEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = if (unresolvedCount == 0) "All Mistakes Conquered!" else "No Mistakes in this category",
                        description = if (unresolvedCount == 0)
                            "You have zero pending errors in your Galti Register. You're defending every mark for CBSE 2027!"
                        else
                            "Select another filter tab or re-attempt questions in Chapter DPPs.",
                        actionText = "Back to Study",
                        onActionClick = onBackClick
                    )

                }
            } else {
                items(filteredList, key = { it.id }) { mistake ->
                    ErrorVaultItemCard(
                        item = mistake,
                        onResolve = {
                            coroutineScope.launch {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                repository.resolveMistake(mistake.id)
                                Toast.makeText(context, "Conquered! Error marked resolved 🎉", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteMistake(mistake.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorVaultItemCard(
    item: ErrorVaultEntity,
    onResolve: () -> Unit,
    onDelete: () -> Unit
) {
    var isReattemptMode by remember { mutableStateOf(false) }
    var selectedReattemptIndex by remember { mutableStateOf<Int?>(null) }
    var showResult by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val categoryColor = when (item.mistakeCategory.lowercase()) {
        "calculation" -> AccentAmber
        "conceptual" -> PrimaryBlue
        "formula_miss" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(
            containerColor = if (item.isResolved)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Category Badge + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ExpressivePillSmall,
                    color = categoryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = item.mistakeCategory.replace("_", " ").uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                }

                if (item.isResolved) {
                    Surface(
                        shape = ExpressivePillSmall,
                        color = SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Conquered", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }
                } else {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question Text
            Text(
                text = item.questionText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Options List
            val options = listOf(item.optionA, item.optionB, item.optionC, item.optionD)

            if (!isReattemptMode) {
                // Read / Review Mode: highlight what was selected vs correct
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEachIndexed { index, opt ->
                        if (opt.isNotBlank()) {
                            val isCorrect = index == item.correctOptionIndex
                            val isUserWrongPick = index == item.userSelectedOptionIndex && !isCorrect

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isCorrect -> SuccessGreen.copy(alpha = 0.12f)
                                    isUserWrongPick -> ErrorRed.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        isCorrect -> SuccessGreen.copy(alpha = 0.5f)
                                        isUserWrongPick -> ErrorRed.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${('A' + index)}. ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            isCorrect -> SuccessGreen
                                            isUserWrongPick -> ErrorRed
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCorrect) {
                                        Text("(Correct)", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                    } else if (isUserWrongPick) {
                                        Text("(Your Pick)", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (item.explanation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "💡 EXPLANATION & EXAM DEFENSE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Re-attack Action
                if (!item.isResolved) {
                    Button(
                        onClick = { isReattemptMode = true },
                        shape = ExpressivePillSmall,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Attack This Question (Prove Mastery)")
                    }
                }
            } else {
                // Interactive Re-attempt Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEachIndexed { index, opt ->
                        if (opt.isNotBlank()) {
                            val isSelected = selectedReattemptIndex == index
                            val isCorrect = index == item.correctOptionIndex

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    showResult && isCorrect -> SuccessGreen.copy(alpha = 0.15f)
                                    showResult && isSelected && !isCorrect -> ErrorRed.copy(alpha = 0.15f)
                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        showResult && isCorrect -> SuccessGreen
                                        showResult && isSelected && !isCorrect -> ErrorRed
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !showResult) {
                                        selectedReattemptIndex = index
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${('A' + index)}. ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!showResult) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isReattemptMode = false },
                            shape = ExpressivePillSmall,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (selectedReattemptIndex != null) {
                                    showResult = true
                                    if (selectedReattemptIndex == item.correctOptionIndex) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onResolve()
                                    }
                                }
                            },
                            enabled = selectedReattemptIndex != null,
                            shape = ExpressivePillSmall,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Check Answer")
                        }
                    }
                } else {
                    val gotItRight = selectedReattemptIndex == item.correctOptionIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (gotItRight) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (gotItRight) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (gotItRight) SuccessGreen else ErrorRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (gotItRight) "Correct! This question has been conquered." else "Incorrect. Don't worry, keep practicing this concept.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (gotItRight) SuccessGreen else ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}
