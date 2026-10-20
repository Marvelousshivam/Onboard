package com.boardsprep.onboard.ui.components.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Grading
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.ai.AiCitation
import com.boardsprep.onboard.core.ai.AiProvider
import com.boardsprep.onboard.core.ai.AiSanitizer
import com.boardsprep.onboard.core.theme.*
import kotlinx.coroutines.launch

data class AiQuickActionChipItem(
    val label: String,
    val icon: ImageVector,
    val prompt: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassAiSheet(
    title: String,
    subtitle: String = "",
    activeProvider: AiProvider,
    activeModel: String,
    streamingContent: String,
    isGenerating: Boolean,
    isWebSearchActive: Boolean,
    citations: List<AiCitation>,
    isCached: Boolean = false,
    errorMessage: String? = null,
    customActions: List<AiQuickActionChipItem>? = null,
    onToggleWebSearch: (Boolean) -> Unit,
    onQuickAction: (String) -> Unit,
    onSendFollowUp: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var followUpInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Breathing pulse for streaming cursor
    val cursorAlphaTransition = rememberInfiniteTransition(label = "cursor_pulse")
    val cursorAlpha by cursorAlphaTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    // Auto-scroll to bottom as new tokens stream in
    LaunchedEffect(streamingContent) {
        if (streamingContent.isNotEmpty() && isGenerating) {
            coroutineScope.launch {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 44.dp, height = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar: Clean single-title with compact clickable model pill (⚡ model ▾) next to ✕ close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Compact clickable model chip
                    val cleanModelName = remember(activeModel) {
                        activeModel.substringAfterLast("/").take(16)
                    }
                    Surface(
                        shape = ExpressivePillSmall,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettings()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ $cleanModelName ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Status Bar: Generating or Cached status
            if (isGenerating || isCached) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Synthesizing answer...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (isCached) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SuccessGreen.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "⚡ Instant Cached Solution (0 Tokens)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            // Content Area (Scrollable Streaming Output)
            Box(modifier = Modifier.weight(1f)) {
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = M3EBentoTileShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI Generation Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = onOpenSettings,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open AI Settings to check API Key")
                            }
                        }
                    }
                } else if (streamingContent.isEmpty() && isGenerating) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Consulting CBSE Master Teacher...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calibrating against 2026-27 rationalized syllabus",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Diagnostic Cards Parser
                        val sections = parseDiagnosticSections(streamingContent)
                        if (sections.isNotEmpty()) {
                            items(sections) { section ->
                                DiagnosticSectionCard(section = section)
                            }
                        } else {
                            // De-slopped Structured Block Renderer
                            item {
                                FormattedExplanationBlock(content = streamingContent)
                            }
                        }

                        // Streaming Cursor
                        if (isGenerating) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 3.dp, height = 18.dp)
                                            .alpha(cursorAlpha)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        }

                        // Citations Chips
                        if (citations.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        text = "Verified Sources & Circulars:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        citations.forEach { citation ->
                                            Surface(
                                                shape = ExpressivePillSmall,
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.clickable {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Source URL", citation.url))
                                                    Toast.makeText(context, "URL copied: ${citation.url}", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Link, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(citation.title, fontSize = 10.5.sp, maxLines = 1)
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

            // Quick Action Chips: Clean LazyRow with contentPadding to prevent clipping on right edge!
            val defaultActions = remember {
                listOf(
                    AiQuickActionChipItem("Simpler Analogy", Icons.Default.Bolt, "Explain this with an intuitive real-world analogy in under 40 words."),
                    AiQuickActionChipItem("Twin Question", Icons.Default.Transform, "Give 1 twin numerical question with identical concept but different numbers."),
                    AiQuickActionChipItem("Step Marking", Icons.AutoMirrored.Filled.Grading, "Show CBSE step-wise marks breakdown: Formula (½m) → Substitution (½m) → Final Unit (1m).")
                )
            }
            val activeActionList = customActions ?: defaultActions

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                // Grounding Chip Toggle inside bottom chip row
                item {
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (isWebSearchActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isWebSearchActive) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleWebSearch(!isWebSearchActive)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isWebSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isWebSearchActive) "Web Verified" else "Verify with Web",
                                fontSize = 11.5.sp,
                                fontWeight = if (isWebSearchActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isWebSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(activeActionList) { action ->
                    QuickActionChip(label = action.label, icon = action.icon) {
                        onQuickAction(action.prompt)
                    }
                }

                item {
                    QuickActionChip(label = "Copy", icon = Icons.Default.ContentCopy) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OnBOARD AI", streamingContent))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Interactive Follow-up Input Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = followUpInput,
                    onValueChange = { followUpInput = it },
                    placeholder = { Text("Ask follow-up doubt or paste draft...", fontSize = 12.5.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (followUpInput.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSendFollowUp(followUpInput.trim())
                            followUpInput = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (followUpInput.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSendFollowUp(followUpInput.trim())
                            followUpInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (followUpInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (followUpInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Sub-Components ─────────────────────────────────────────────────────────

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = ExpressivePillSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

data class DiagnosticSection(
    val title: String,
    val content: String,
    val type: SectionType
) {
    enum class SectionType { CORE_CONCEPT, WHY_WRONG, CBSE_TIP, GENERAL }
}

private fun parseDiagnosticSections(markdown: String): List<DiagnosticSection> {
    if (!markdown.contains("### 1.") && !markdown.contains("### 2.") && !markdown.contains("### 3.")) {
        return emptyList()
    }

    val result = mutableListOf<DiagnosticSection>()
    val lines = markdown.lines()
    var currentTitle = ""
    var currentContent = StringBuilder()
    var currentType = DiagnosticSection.SectionType.GENERAL

    fun flush() {
        if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
            val sanitizedContent = AiSanitizer.sanitizeLatexToUnicode(currentContent.toString().trim())
            result.add(DiagnosticSection(currentTitle, sanitizedContent, currentType))
            currentContent = StringBuilder()
        }
    }

    for (line in lines) {
        when {
            line.contains("### 1.") || line.contains("Core Concept") -> {
                flush()
                currentTitle = "1. Core Concept & Proof"
                currentType = DiagnosticSection.SectionType.CORE_CONCEPT
            }
            line.contains("### 2.") || line.contains("Why Your Pick Was Wrong") -> {
                flush()
                currentTitle = "2. Why Your Pick Was Wrong"
                currentType = DiagnosticSection.SectionType.WHY_WRONG
            }
            line.contains("### 3.") || line.contains("CBSE Board Exam Tip") -> {
                flush()
                currentTitle = "3. CBSE Board Exam Tip"
                currentType = DiagnosticSection.SectionType.CBSE_TIP
            }
            else -> {
                currentContent.append(line).append("\n")
            }
        }
    }
    flush()
    return result
}

@Composable
private fun DiagnosticSectionCard(section: DiagnosticSection) {
    val (accentColor, containerColor) = when (section.type) {
        DiagnosticSection.SectionType.CORE_CONCEPT -> SuccessGreen to SuccessGreen.copy(alpha = 0.08f)
        DiagnosticSection.SectionType.WHY_WRONG -> AccentAmber to AccentAmber.copy(alpha = 0.08f)
        DiagnosticSection.SectionType.CBSE_TIP -> PrimaryBlue to PrimaryBlue.copy(alpha = 0.08f)
        DiagnosticSection.SectionType.GENERAL -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = M3EBentoTileShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (section.type) {
                            DiagnosticSection.SectionType.CORE_CONCEPT -> Icons.Default.Check
                            DiagnosticSection.SectionType.WHY_WRONG -> Icons.Default.WarningAmber
                            DiagnosticSection.SectionType.CBSE_TIP -> Icons.Default.Lightbulb
                            DiagnosticSection.SectionType.GENERAL -> Icons.Default.AutoAwesome
                        }
                        Icon(icon, null, modifier = Modifier.size(13.dp), tint = accentColor)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = section.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FormattedExplanationBlock(content = section.content)
        }
    }
}

sealed class StructuredBlock {
    data class FormulaPill(val formula: String) : StructuredBlock()
    data class BulletPoint(val text: String) : StructuredBlock()
    data class YourTurnCallout(val prompt: String) : StructuredBlock()
    data class Paragraph(val text: String) : StructuredBlock()
}

private fun parseStructuredBlocks(text: String): List<StructuredBlock> {
    val blocks = mutableListOf<StructuredBlock>()
    val lines = text.lines()

    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isBlank()) continue

        when {
            // Formula Detection: Lines stating key formulas or equations
            line.startsWith("Formula:", ignoreCase = true) ||
            line.startsWith("Key Formula:", ignoreCase = true) ||
            line.startsWith("Equation:", ignoreCase = true) ||
            (line.contains("=") && (line.contains("·") || line.contains("×") || line.contains("cos") || line.contains("sin") || line.contains("tan") || line.contains("Δ") || line.contains("ε₀") || line.contains("10⁻")) && line.length < 80) -> {
                val cleanFormula = line
                    .removePrefix("Formula:")
                    .removePrefix("Key Formula:")
                    .removePrefix("Equation:")
                    .trim()
                blocks.add(StructuredBlock.FormulaPill(cleanFormula))
            }

            // Your Turn Callout
            line.startsWith("Your Turn", ignoreCase = true) ||
            line.startsWith("**Your Turn", ignoreCase = true) ||
            line.startsWith("Now calculate", ignoreCase = true) ||
            line.startsWith("Next Step:", ignoreCase = true) -> {
                val cleanPrompt = line
                    .removePrefix("**Your Turn:**")
                    .removePrefix("**Your Turn**:")
                    .removePrefix("Your Turn:")
                    .trim()
                blocks.add(StructuredBlock.YourTurnCallout(cleanPrompt))
            }

            // Bullet Point
            line.startsWith("- ") || line.startsWith("* ") || line.matches(Regex("""^\d+\.\s.*""")) -> {
                val bulletContent = line.replace(Regex("""^[-*]|\d+\.\s*"""), "").trim()
                blocks.add(StructuredBlock.BulletPoint(bulletContent))
            }

            // General Paragraph
            else -> {
                blocks.add(StructuredBlock.Paragraph(line))
            }
        }
    }
    return blocks
}

@Composable
private fun FormattedExplanationBlock(content: String) {
    val sanitized = remember(content) { AiSanitizer.sanitizeLatexToUnicode(content) }
    val blocks = remember(sanitized) { parseStructuredBlocks(sanitized) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is StructuredBlock.FormulaPill -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Functions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = block.formula,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                is StructuredBlock.BulletPoint -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                        )
                        Text(
                            text = block.text,
                            fontSize = 13.sp,
                            lineHeight = 18.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is StructuredBlock.YourTurnCallout -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentAmber.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Your Turn",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AccentAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = block.prompt,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                is StructuredBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        fontSize = 13.sp,
                        lineHeight = 18.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
