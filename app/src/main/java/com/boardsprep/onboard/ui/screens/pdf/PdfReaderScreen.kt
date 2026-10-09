package com.boardsprep.onboard.ui.screens.pdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.boardsprep.onboard.core.theme.ExpressivePillSmall
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
    var showNoteSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    val isFullscreen = ui.appearance.fullscreen
    val haptic = LocalHapticFeedback.current

    // Apply brightness to the window whenever it changes.
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(ui.appearance.brightnessOverride, activity) {
        activity?.let { state.applyBrightness(it) }
    }
    // Restore system brightness when leaving the reader.
    DisposableEffect(activity) {
        onDispose { activity?.let { state.restoreBrightness(it) } }
    }

    // Back button: exit annotation studio / exit fullscreen / close sheets before leaving the document.
    val isAnnotating = ui.sketchMode || ui.highlightMode || ui.eraserMode
    BackHandler(enabled = isFullscreen || isAnnotating || showAppearance || showBookmarks || showAnnotations || showOutline || showJump || showErrorLog || showNoteSheet || showOverflow || showSearch) {
        when {
            isAnnotating -> state.exitAnnotationStudio()
            isFullscreen -> state.toggleFullscreen()
            showAppearance -> showAppearance = false
            showBookmarks -> showBookmarks = false
            showAnnotations -> showAnnotations = false
            showOutline -> showOutline = false
            showJump -> showJump = false
            showErrorLog -> showErrorLog = false
            showNoteSheet -> showNoteSheet = false
            showSearch -> showSearch = false
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
                                    text = "CBSE Class 12 • Page ${ui.currentPage + 1} of ${ui.totalPages}",
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
                        // 1. Paired Document Switcher Pill (Question Paper ↔ Marking Scheme)
                        val pairedDoc = ui.pairedDocument
                        if (pairedDoc != null) {
                            val isMs = pairedDoc.role == PdfPairedRole.MARKING_SCHEME
                            Surface(
                                shape = ExpressivePillSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clickable { state.switchToPaired(onNavigateToPaired) }
                                    .padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Switch Document",
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (isMs) "View MS" else "View QP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // 2. Direct 1-Tap Search
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in document")
                        }

                        // 3. Direct 1-Tap Bookmark with Celebratory Haptic Pulse
                        val isBookmarked = remember(ui.bookmarks, ui.currentPage) {
                            ui.bookmarks.any { it.pageIndex == ui.currentPage }
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            state.toggleBookmarkOnCurrentPage()
                        }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Bookmarked" else "Bookmark this page",
                                tint = if (isBookmarked) AccentAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 4. Sketch / Pen / Annotation Studio toggle
                        IconButton(onClick = {
                            if (isAnnotating) {
                                state.exitAnnotationStudio()
                            } else {
                                state.toggleSketchMode()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Sketch & Highlight",
                                tint = if (isAnnotating) AccentAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 5. Appearance
                        IconButton(onClick = { showAppearance = true }) {
                            Icon(Icons.Default.Tune, "Appearance")
                        }

                        // 6. More (Overflow menu for secondary study tools)
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }

                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(if (ui.highlightMode) "✓ Highlight mode" else "Highlight mode") },
                                onClick = { showOverflow = false; state.toggleHighlightMode() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (ui.eraserMode) "✓ Eraser mode" else "Eraser mode") },
                                onClick = { showOverflow = false; state.toggleEraserMode() }
                            )
                            DropdownMenuItem(
                                text = { Text("Add study note on this page") },
                                onClick = { showOverflow = false; showNoteSheet = true }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Bookmarks & revision list") },
                                onClick = { showOverflow = false; showBookmarks = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Notes & highlights list") },
                                onClick = { showOverflow = false; showAnnotations = true }
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
                    val effectiveMode = ui.appearance.readingMode
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

            // Bottom docked bar — reading dock or study annotation studio
            AnimatedVisibility(
                visible = chromeVisible && ui.isReady,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AnimatedContent(
                    targetState = ui.highlightMode || ui.sketchMode || ui.eraserMode,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "BottomStudioBarTransition"
                ) { isAnnotationStudio ->
                    if (isAnnotationStudio) {
                        PdfAnnotationStudioBar(
                            highlightMode = ui.highlightMode,
                            sketchMode = ui.sketchMode,
                            eraserMode = ui.eraserMode,
                            currentColor = if (ui.highlightMode) ui.highlightColor else ui.sketchColor,
                            currentStrokeWidth = ui.strokeWidth,
                            canUndo = ui.canUndo,
                            canRedo = ui.canRedo,
                            currentPage = ui.currentPage,
                            totalPages = ui.totalPages,
                            onPrevPage = { state.previousPage() },
                            onNextPage = { state.nextPage() },
                            onUndo = { state.undo() },
                            onRedo = { state.redo() },
                            onClearPage = { state.clearAllAnnotationsOnPage(ui.currentPage) },
                            onSelectHighlighter = { c ->
                                state.setHighlightColor(c)
                                if (!ui.highlightMode) state.toggleHighlightMode()
                            },
                            onSelectPen = { c ->
                                state.setSketchColor(c)
                                if (!ui.sketchMode) state.toggleSketchMode()
                            },
                            onSelectEraser = {
                                if (!ui.eraserMode) state.toggleEraserMode()
                            },
                            onSelectStrokeWidth = { w -> state.setStrokeWidth(w) },
                            onClose = {
                                state.exitAnnotationStudio()
                            }
                        )
                    } else {
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
                }
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
        PdfExpressiveJumpSheet(
            currentPage = ui.currentPage,
            totalPages = ui.totalPages,
            bookmarks = ui.bookmarks,
            onJump = { state.jumpToPage(it) },
            onDismiss = { showJump = false }
        )
    }
    if (showNoteSheet) {
        PdfExpressiveNoteSheet(
            currentPage = ui.currentPage,
            onSaveNote = { page, text, color ->
                state.addNoteAnnotation(page, text, color)
            },
            onDismiss = { showNoteSheet = false }
        )
    }
    if (showErrorLog) {
        PdfErrorLogSheet(
            onClear = { PdfErrorLog.clear() },
            onDismiss = { showErrorLog = false }
        )
    }
}
