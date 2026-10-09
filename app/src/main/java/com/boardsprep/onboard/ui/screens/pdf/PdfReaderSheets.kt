package com.boardsprep.onboard.ui.screens.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.pdf.PdfAnnotation
import com.boardsprep.onboard.core.pdf.PdfAnnotationType
import com.boardsprep.onboard.core.pdf.PdfAppearanceSettings
import com.boardsprep.onboard.core.pdf.PdfCanvasTheme
import com.boardsprep.onboard.core.pdf.PdfErrorLog
import com.boardsprep.onboard.core.pdf.PdfFitMode
import com.boardsprep.onboard.core.pdf.PdfReadingMode
import com.boardsprep.onboard.core.pdf.PdfSearchResult
import com.boardsprep.onboard.core.pdf.PdfTextExtractor
import com.boardsprep.onboard.core.theme.AccentAmber
import com.boardsprep.onboard.core.theme.ExpressivePillSmall
import com.boardsprep.onboard.core.theme.PrimaryBlue
import com.boardsprep.onboard.core.theme.SuccessGreen
import kotlin.math.roundToInt

// ===========================================================================
// Appearance sheet — theme, fit, mode, brightness
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAppearanceSheet(
    appearance: PdfAppearanceSettings,
    onUpdate: (PdfAppearanceSettings.() -> PdfAppearanceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Reading appearance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))

            Text("Canvas theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeChip("Light", appearance.canvasTheme == PdfCanvasTheme.LIGHT) { onUpdate { copy(canvasTheme = PdfCanvasTheme.LIGHT) } }
                ThemeChip("Dark", appearance.canvasTheme == PdfCanvasTheme.DARK_CANVAS) { onUpdate { copy(canvasTheme = PdfCanvasTheme.DARK_CANVAS) } }
                ThemeChip("Invert", appearance.canvasTheme == PdfCanvasTheme.INVERT_PDF) { onUpdate { copy(canvasTheme = PdfCanvasTheme.INVERT_PDF) } }
                ThemeChip("Sepia", appearance.canvasTheme == PdfCanvasTheme.SEPIA) { onUpdate { copy(canvasTheme = PdfCanvasTheme.SEPIA) } }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (appearance.canvasTheme) {
                    PdfCanvasTheme.LIGHT -> "Original PDF on a light canvas."
                    PdfCanvasTheme.DARK_CANVAS -> "Original PDF on a near-black canvas — best for OLED, preserves diagram colors."
                    PdfCanvasTheme.INVERT_PDF -> "Inverts PDF colors. Useful for text but may distort photos and color-coded diagrams."
                    PdfCanvasTheme.SEPIA -> "Warm sepia tint for extended reading comfort."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Text("Reading mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Continuous", appearance.readingMode == PdfReadingMode.CONTINUOUS_VERTICAL) { onUpdate { copy(readingMode = PdfReadingMode.CONTINUOUS_VERTICAL) } }
                ModeChip("Single page", appearance.readingMode == PdfReadingMode.SINGLE_PAGE) { onUpdate { copy(readingMode = PdfReadingMode.SINGLE_PAGE) } }
                ModeChip("Paged", appearance.readingMode == PdfReadingMode.HORIZONTAL_PAGED) { onUpdate { copy(readingMode = PdfReadingMode.HORIZONTAL_PAGED) } }
            }

            Spacer(Modifier.height(20.dp))

            Text("Fit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Fit width", appearance.fitMode == PdfFitMode.FIT_WIDTH) { onUpdate { copy(fitMode = PdfFitMode.FIT_WIDTH) } }
                ModeChip("Fit page", appearance.fitMode == PdfFitMode.FIT_PAGE) { onUpdate { copy(fitMode = PdfFitMode.FIT_PAGE) } }
                ModeChip("Free", appearance.fitMode == PdfFitMode.FREE) { onUpdate { copy(fitMode = PdfFitMode.FREE) } }
            }

            Spacer(Modifier.height(20.dp))

            Text("Brightness", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            var brightness by remember(appearance.brightnessOverride) {
                mutableStateOf(appearance.brightnessOverride ?: -1f)
            }
            Slider(
                value = brightness,
                onValueChange = { brightness = it; onUpdate { copy(brightnessOverride = if (it < 0f) null else it) } },
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
            )
            Text(
                text = if (brightness < 0f) "System brightness" else "${(brightness * 100).roundToInt()}%",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Text("Page spacing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            var spacing by remember(appearance.pageSpacingDp) { mutableStateOf(appearance.pageSpacingDp.toFloat()) }
            Slider(
                value = spacing,
                onValueChange = { spacing = it; onUpdate { copy(pageSpacingDp = it.roundToInt().coerceIn(0, 48)) } },
                valueRange = 0f..48f,
                colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) }, shape = ExpressivePillSmall)
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) }, shape = ExpressivePillSmall)
}

// ===========================================================================
// Search sheet
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSearchSheet(
    query: String,
    result: PdfSearchResult?,
    extractorState: PdfTextExtractor.ExtractionState,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Search document", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Find in this PDF…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                shape = ExpressivePillSmall
            )
            Spacer(Modifier.height(10.dp))
            when {
                extractorState is PdfTextExtractor.ExtractionState.Extracting -> {
                    Text("Extracting text layer…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                result == null && query.isBlank() -> {
                    Text("Type to search the entire document.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                result != null && !result.hasTextLayer -> {
                    Surface(color = AccentAmber.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "This PDF has no text layer (it may be a scanned image). Search isn't available for it.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                result != null && result.matches.isEmpty() -> {
                    Text("No matches for \"${result.query}\".", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                result != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${(currentIndex + 1).coerceAtLeast(1)} of ${result.matches.size} matches",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPrevious) { Text("Prev") }
                        TextButton(onClick = onNext) { Text("Next") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(result.matches.withIndex().toList()) { (idx, match) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (idx == currentIndex) PrimaryBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Page ${match.pageIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(2.dp))
                                    Text(match.matchedText, fontSize = 12.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ===========================================================================
// Bookmarks sheet
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBookmarksSheet(
    bookmarks: List<com.boardsprep.onboard.core.pdf.PdfBookmark>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Bookmarks", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            if (bookmarks.isEmpty()) {
                Text("No bookmarks yet. Tap the bookmark icon on a page to save it for revision.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(bookmarks) { bm ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (bm.pageIndex == currentPage) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmark, null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(bm.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Page ${bm.pageIndex + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (bm.note.isNotBlank()) {
                                        Text(bm.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                                IconButton(onClick = { onJump(bm.pageIndex) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Jump")
                                }
                                IconButton(onClick = { onRemove(bm.id) }) {
                                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ===========================================================================
// Annotations sheet
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotationsSheet(
    annotations: List<PdfAnnotation>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Notes & highlights", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Notes you add on a page are saved against the document and survive rotation, app restart and reopens. Highlight overlays require text selection support, which is being added incrementally.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (annotations.isEmpty()) {
                Text("No notes yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(annotations) { ann ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (ann.pageIndex == currentPage) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (ann.type == PdfAnnotationType.NOTE) Icons.AutoMirrored.Filled.Note else Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ann.noteText.ifBlank { "Untitled note" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text("Page ${ann.pageIndex + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onJump(ann.pageIndex) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Jump")
                                }
                                IconButton(onClick = { onRemove(ann.id) }) {
                                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ===========================================================================
// Outline sheet — honest when no TOC is available
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOutlineSheet(
    outline: List<com.boardsprep.onboard.core.pdf.PdfOutlineEntry>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Table of contents", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            if (outline.isEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No table of contents available for this PDF.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The document didn't include an outline (some NCERT chapters and many scanned papers don't). Use the page jump or search to navigate instead.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(outline) { entry ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (entry.level * 16).dp)
                        ) {
                            Row(Modifier.clickable { onJump(entry.pageIndex) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.title, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("${entry.pageIndex + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ===========================================================================
// Error log sheet — viewable from the overflow menu + error state
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfErrorLogSheet(
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    // Re-fetch entries every time the sheet is composed.
    val entries = PdfErrorLog.getEntriesReversed()
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxHeight(0.85f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Error log", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                // Copy button
                TextButton(onClick = {
                    val text = PdfErrorLog.getLogText()
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OnBOARD Error Log", text))
                    copied = true
                }) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (copied) "Copied!" else "Copy")
                }
                // Clear button
                TextButton(onClick = { onClear(); copied = false }) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No errors logged.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "If something isn't working, try the action again — the error will appear here.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Use weight(1f) so the LazyColumn fills all remaining space
                // in the sheet and scrolls properly.
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(entries) { entry ->
                        val bg = when (entry.level) {
                            PdfErrorLog.Level.ERROR -> androidx.compose.ui.graphics.Color(0x22F56565)
                            PdfErrorLog.Level.WARN -> AccentAmber.copy(alpha = 0.12f)
                            PdfErrorLog.Level.INFO -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                        val fg = when (entry.level) {
                            PdfErrorLog.Level.ERROR -> MaterialTheme.colorScheme.error
                            PdfErrorLog.Level.WARN -> AccentAmber
                            PdfErrorLog.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.level.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fg,
                                        modifier = Modifier
                                            .background(fg.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = entry.tag,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                            .format(java.util.Date(entry.timestamp)),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(entry.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                entry.throwableSummary?.let { summary ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = summary,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
