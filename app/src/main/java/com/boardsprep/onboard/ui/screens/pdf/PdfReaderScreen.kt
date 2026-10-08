package com.boardsprep.onboard.ui.screens.pdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.pdf.PdfCanvasTheme
import com.boardsprep.onboard.core.pdf.PdfErrorLog
import com.boardsprep.onboard.core.pdf.PdfPairedDocument
import com.boardsprep.onboard.core.pdf.PdfPairedRole
import com.boardsprep.onboard.core.pdf.PdfReadingMode
import com.boardsprep.onboard.core.theme.AccentAmber

/** Find the hosting Activity from a Compose context. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Public entry point that the existing app navigation calls. Preserves the
 * signature of the original PdfViewerScreen so callers don't need to change,
 * and adds optional paired-document support for the question-paper /
 * marking-scheme switcher.
 */
@Composable
fun PdfViewerScreen(
    url: String,
    title: String,
    onBackClick: () -> Unit,
    pairedSource: String? = null,
    pairedTitle: String? = null,
    pairedRole: PdfPairedRole = PdfPairedRole.GENERAL,
    onNavigateToPairedOverride: ((String, String) -> Unit)? = null
) {
    val paired = remember(pairedSource, pairedTitle) {
        if (pairedSource.isNullOrBlank() || pairedTitle.isNullOrBlank()) null
        else PdfPairedDocument(
            documentId = pairedSource,
            source = pairedSource,
            title = pairedTitle,
            role = pairedRole
        )
    }
    PdfReaderScreen(
        source = url,
        title = title,
        paired = paired,
        onBackClick = onBackClick,
        onNavigateToPaired = onNavigateToPairedOverride ?: { _, _ -> onBackClick() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    source: String,
    title: String,
    paired: PdfPairedDocument?,
    onBackClick: () -> Unit,
    onNavigateToPaired: (String, String) -> Unit
) {
    val context = LocalContext.current
    val state = rememberPdfReaderState(source = source, title = title, paired = paired)
    val ui by state.uiState.collectAsState()

    var chromeVisible by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showErrorLog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showOfflineModePrompt by remember { mutableStateOf(false) }

    // Online/Offline mode selection dialog — shown once when the PDF first
    // becomes ready, before the user sees the document.
    var showModeSelectionDialog by remember { mutableStateOf(true) }

    val isFullscreen = ui.appearance.fullscreen

    // Apply brightness to the window whenever it changes.
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(ui.appearance.brightnessOverride, activity) {
        activity?.let { state.applyBrightness(it) }
    }
    // Restore system brightness when leaving the reader.
    DisposableEffect(activity) {
        onDispose { activity?.let { state.restoreBrightness(it) } }
    }

    // Back button: exit fullscreen / close sheets before leaving the document.
    BackHandler(enabled = isFullscreen || showAppearance || showBookmarks || showAnnotations || showOutline || showJump || showErrorLog || showNoteDialog || showOverflow || showSearch || showOfflineModePrompt) {
        when {
            isFullscreen -> state.toggleFullscreen()
            showAppearance -> showAppearance = false
            showBookmarks -> showBookmarks = false
            showAnnotations -> showAnnotations = false
            showOutline -> showOutline = false
            showJump -> showJump = false
            showErrorLog -> showErrorLog = false
            showNoteDialog -> showNoteDialog = false
            showSearch -> showSearch = false
            showOfflineModePrompt -> showOfflineModePrompt = false
            showOverflow -> showOverflow = false
        }
    }

    val canvasColor = canvasBackgroundColor(ui.appearance.canvasTheme)

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = chromeVisible && !isFullscreen, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (ui.totalPages > 0) {
                                Text(
                                    text = "OnBOARD Reader • Page ${ui.currentPage + 1} of ${ui.totalPages}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        // --- Minimal always-visible icons to prevent overlap ---
                        // Top bar icons: Offline, Sketch, Appearance, More.
                        // Search & Bookmark are in the overflow menu (not the top bar)
                        // to prevent overlap. Sketch stays visible per user request.

                        // Offline mode toggle
                        IconButton(onClick = {
                            if (ui.offlineModeEnabled) {
                                state.disableOfflineMode()
                            } else {
                                showModeSelectionDialog = true
                            }
                        }) {
                            Icon(
                                imageVector = if (ui.offlineModeEnabled) Icons.Default.CloudOff else Icons.Default.CloudDownload,
                                contentDescription = if (ui.offlineModeEnabled) "Offline mode ON" else "Offline mode OFF",
                                tint = if (ui.offlineModeEnabled) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sketch (freehand pen) toggle — visible in top bar
                        IconButton(onClick = { state.toggleSketchMode() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Sketch",
                                tint = if (ui.sketchMode) AccentAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Appearance
                        IconButton(onClick = { showAppearance = true }) {
                            Icon(Icons.Default.Tune, "Appearance")
                        }

                        // More (overflow) — contains Search, Highlight, Bookmark, etc.
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }

                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            // Search (opens search sheet if offline mode is on,
                            // otherwise shows the offline mode prompt)
                            DropdownMenuItem(
                                text = { Text("Search in document") },
                                onClick = {
                                    showOverflow = false
                                    if (ui.offlineModeEnabled) {
                                        showSearch = true
                                    } else {
                                        showOfflineModePrompt = true
                                    }
                                }
                            )
                            // Highlight toggle
                            DropdownMenuItem(
                                text = { Text(if (ui.highlightMode) "✓ Highlight mode" else "Highlight mode") },
                                onClick = { showOverflow = false; state.toggleHighlightMode() }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            // Bookmark current page
                            DropdownMenuItem(
                                text = { Text(if (remember(ui.bookmarks, ui.currentPage) { ui.bookmarks.any { it.pageIndex == ui.currentPage } }) "✓ Bookmark this page" else "Bookmark this page") },
                                onClick = { showOverflow = false; state.toggleBookmarkOnCurrentPage() }
                            )
                            // Bookmarks & revision list
                            DropdownMenuItem(
                                text = { Text("Bookmarks & revision") },
                                onClick = { showOverflow = false; showBookmarks = true }
                            )
                            // Notes & highlights list
                            DropdownMenuItem(
                                text = { Text("Notes & highlights") },
                                onClick = { showOverflow = false; showAnnotations = true }
                            )
                            // Add note on this page
                            DropdownMenuItem(
                                text = { Text("Add note on this page") },
                                onClick = { showOverflow = false; showNoteDialog = true }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Open in external viewer") },
                                onClick = { showOverflow = false; state.openInExternalViewer(context) }
                            )
                            DropdownMenuItem(
                                text = { Text("View error log") },
                                onClick = { showOverflow = false; showErrorLog = true }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                .background(canvasColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                ui.isLoading -> {
                    val progress = (ui.loadState as? com.boardsprep.onboard.core.pdf.PdfLoadState.Loading)?.progress
                    PdfLoadingState(progress)
                }
                ui.isError -> {
                    val err = ui.loadState as com.boardsprep.onboard.core.pdf.PdfLoadState.Error
                    PdfErrorState(
                        kind = err.kind,
                        detail = err.detail,
                        onRetry = { state.start() },
                        onOpenExternal = { state.openInExternalViewer(context) },
                        onViewErrorLog = { showErrorLog = true }
                    )
                }
                ui.isReady -> {
                    // Online mode = single-page (fast, simple, like the original app).
                    // Offline mode = continuous vertical (full document available
                    // for search + scrolling). The user can still switch modes
                    // from the appearance sheet.
                    val effectiveMode = if (ui.offlineModeEnabled) {
                        ui.appearance.readingMode
                    } else {
                        // Online: always single-page for speed and stability.
                        PdfReadingMode.SINGLE_PAGE
                    }
                    when (effectiveMode) {
                        PdfReadingMode.CONTINUOUS_VERTICAL -> PdfContinuousCanvas(
                            stateHolder = state,
                            onToggleChrome = { chromeVisible = !chromeVisible }
                        )
                        PdfReadingMode.SINGLE_PAGE -> PdfSinglePageCanvas(
                            stateHolder = state,
                            onToggleChrome = { chromeVisible = !chromeVisible }
                        )
                        PdfReadingMode.HORIZONTAL_PAGED -> PdfHorizontalPagedCanvas(
                            stateHolder = state,
                            onToggleChrome = { chromeVisible = !chromeVisible }
                        )
                    }
                }
            }

            // Floating bottom dock (auto-hide while reading).
            AnimatedVisibility(
                visible = chromeVisible && ui.isReady,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PdfBottomDock(
                    currentPage = ui.currentPage,
                    totalPages = ui.totalPages,
                    zoom = ui.zoom,
                    readingMode = ui.appearance.readingMode,
                    canUndo = ui.canUndo,
                    canRedo = ui.canRedo,
                    onUndo = { state.undo() },
                    onRedo = { state.redo() },
                    onPrev = { state.previousPage() },
                    onNext = { state.nextPage() },
                    onJump = { showJump = true },
                    onZoomIn = { state.setZoom(ui.zoom + 0.5f) },
                    onZoomOut = { state.setZoom(ui.zoom - 0.5f) },
                    onZoomReset = { state.resetZoom() },
                    onToggleAppearance = { showAppearance = true },
                    onToggleFullscreen = { state.toggleFullscreen() }
                )
            }

            // Floating exit-fullscreen button when immersive.
            if (isFullscreen) {
                IconButton(
                    onClick = { state.toggleFullscreen() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Exit fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // Sheets
    if (showAppearance) {
        PdfAppearanceSheet(
            appearance = ui.appearance,
            onUpdate = { transform -> state.updateAppearance(transform) },
            onDismiss = { showAppearance = false }
        )
    }
    if (showSearch) {
        PdfSearchSheet(
            query = ui.searchQuery,
            result = ui.search,
            extractorState = ui.extractorState,
            currentIndex = ui.searchIndex,
            onQueryChange = { state.runSearch(it) },
            onNext = { state.nextSearchMatch() },
            onPrevious = { state.previousSearchMatch() },
            onDismiss = { showSearch = false }
        )
    }
    if (showBookmarks) {
        PdfBookmarksSheet(
            bookmarks = ui.bookmarks,
            currentPage = ui.currentPage,
            onJump = { state.jumpToPage(it); showBookmarks = false },
            onRemove = { state.removeBookmark(it) },
            onDismiss = { showBookmarks = false }
        )
    }
    if (showAnnotations) {
        PdfAnnotationsSheet(
            annotations = ui.annotations,
            currentPage = ui.currentPage,
            onJump = { state.jumpToPage(it); showAnnotations = false },
            onRemove = { state.deleteAnnotation(it) },
            onDismiss = { showAnnotations = false }
        )
    }
    if (showOutline) {
        PdfOutlineSheet(
            outline = emptyList(),
            onJump = { state.jumpToPage(it); showOutline = false },
            onDismiss = { showOutline = false }
        )
    }
    if (showJump) {
        PdfPageJumpDialog(
            currentPage = ui.currentPage,
            totalPages = ui.totalPages,
            onJump = { state.jumpToPage(it) },
            onDismiss = { showJump = false }
        )
    }
    if (showErrorLog) {
        PdfErrorLogSheet(
            onClear = { PdfErrorLog.clear() },
            onDismiss = { showErrorLog = false }
        )
    }

    // Offline mode prompt — shown when the user taps Search while offline mode is off.
    if (showOfflineModePrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOfflineModePrompt = false },
            icon = { Icon(Icons.Default.CloudDownload, null) },
            title = { Text("Enable Offline Mode?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Text search requires the full document to be parsed locally. " +
                    "This downloads the text layer (a few seconds for large textbooks) " +
                    "and enables word search across the entire PDF.\n\n" +
                    "You can turn it off later from the top bar cloud icon.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showOfflineModePrompt = false
                        state.enableOfflineMode()
                        showSearch = true
                    }
                ) { Text("Enable & Search") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOfflineModePrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Offline mode loading overlay.
    if (ui.offlineModeLoading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Preparing offline mode…", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color(0xFFD0BCFF),
                        modifier = Modifier.size(40.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                    Text(
                        "Extracting text layer from the document.\nThis happens once per document.",
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Online/Offline mode selection dialog — shown when the PDF is ready
    // and the user hasn't chosen yet.
    if (ui.isReady && showModeSelectionDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                // Dismissing = Online mode (safe default).
                showModeSelectionDialog = false
            },
            title = { Text("Open this PDF", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Online mode opens quickly with basic reading (zoom, colors, " +
                    "fullscreen, bookmarks).\n\n" +
                    "Offline mode downloads the full text layer for word search " +
                    "and continuous scrolling — takes a few extra seconds for " +
                    "large textbooks.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showModeSelectionDialog = false
                        state.enableOfflineMode()
                    }
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text("Offline")
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showModeSelectionDialog = false }
                ) {
                    Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text("Online")
                }
            }
        )
    }

    // Note dialog — opens a text input for the user to type a note.
    if (showNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add note on page ${ui.currentPage + 1}", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type your note here…") },
                    minLines = 3
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            state.addNoteAnnotation(ui.currentPage, noteText, AccentAmber.toArgb())
                        }
                        showNoteDialog = false
                    }
                ) { Text("Save note") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sketch color picker — shown when sketch mode is on.
    if (ui.sketchMode) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colors = listOf(
                            0xFFFF1744.toInt() to "Red",
                            0xFF2979FF.toInt() to "Blue",
                            0xFF00E676.toInt() to "Green",
                            0xFFFFD600.toInt() to "Yellow",
                            0xFF000000.toInt() to "Black"
                        )
                        colors.forEach { (color, _) ->
                            val isSelected = ui.sketchColor == color
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(if (isSelected) 32.dp else 28.dp)
                                    .background(
                                        color = androidx.compose.ui.graphics.Color(color),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .clickable { state.setSketchColor(color) }
                            )
                        }
                    }
                }
            }
        }
    }
}
