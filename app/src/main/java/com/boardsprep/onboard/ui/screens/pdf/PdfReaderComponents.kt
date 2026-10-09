package com.boardsprep.onboard.ui.screens.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.pdf.PdfAnnotationType
import com.boardsprep.onboard.core.pdf.PdfCanvasTheme
import com.boardsprep.onboard.core.pdf.PdfErrorKind
import com.boardsprep.onboard.core.pdf.PdfFitMode
import com.boardsprep.onboard.core.pdf.PdfPageBitmap
import com.boardsprep.onboard.core.pdf.PdfReadingMode
import com.boardsprep.onboard.core.theme.AccentAmber
import com.boardsprep.onboard.core.theme.ExpressiveDockShape
import com.boardsprep.onboard.core.theme.ExpressivePillSmall
import com.boardsprep.onboard.core.theme.PrimaryBlue
import kotlin.math.roundToInt

// ---- Color matrices (shared across all canvas variants) -------------------

private val InvertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

private val SepiaColorMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Compute the color filter for a canvas theme, or null for original colors. */
fun canvasColorFilter(theme: PdfCanvasTheme): ColorFilter? = when (theme) {
    PdfCanvasTheme.INVERT_PDF -> ColorFilter.colorMatrix(InvertColorMatrix)
    PdfCanvasTheme.SEPIA -> ColorFilter.colorMatrix(SepiaColorMatrix)
    else -> null
}

/** Background color for the reading canvas, visible around pages. */
fun canvasBackgroundColor(theme: PdfCanvasTheme): Color = when (theme) {
    PdfCanvasTheme.LIGHT -> Color(0xFFF1F5F9)
    PdfCanvasTheme.DARK_CANVAS -> Color(0xFF0B0B0F)
    PdfCanvasTheme.INVERT_PDF -> Color(0xFF000000)
    PdfCanvasTheme.SEPIA -> Color(0xFFF5E9D4)
}

// ===========================================================================
// Single-page canvas — one page at a time with pinch zoom / pan
// ===========================================================================

/**
 * Single-page reading canvas. Shows one page at a time with pinch-to-zoom,
 * double-tap zoom, and drag-to-pan. Uses [ContentScale.Fit] so the page
 * maintains its aspect ratio and the surrounding canvas (dark / sepia / etc.)
 * is visible.
 *
 * When highlight mode is on, zoom/pan gestures are replaced by a drag gesture
 * that creates highlight annotations.
 */
@Composable
fun PdfSinglePageCanvas(
    stateHolder: PdfReaderState,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = stateHolder.uiState.collectAsState().value
    val bitmap = ui.currentBitmap
    val colorFilter = canvasColorFilter(ui.appearance.canvasTheme)

    val currentZoom by rememberUpdatedState(ui.zoom)
    var pan by remember(ui.currentPage) { mutableStateOf(Offset.Zero) }

    // Highlight state
    val highlights = remember(ui.annotations, ui.currentPage) {
        ui.annotations.filter { it.pageIndex == ui.currentPage && it.type == PdfAnnotationType.HIGHLIGHT }
    }
    val sketches = remember(ui.annotations, ui.currentPage) {
        ui.annotations.filter { it.pageIndex == ui.currentPage && it.type == PdfAnnotationType.FREEHAND }
    }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var currentStroke by remember { mutableStateOf<List<android.graphics.PointF>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                stateHolder.onViewportSizeChanged(coords.size.width, coords.size.height)
                containerSize = coords.size
            }
            .pointerInput(ui.currentPage, ui.highlightMode, ui.sketchMode) {
                when {
                    ui.sketchMode -> {
                        // Sketch mode: freehand drawing
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(android.graphics.PointF(offset.x, offset.y))
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty() && containerSize.width > 0 && containerSize.height > 0) {
                                    val normalised = currentStroke.map {
                                        android.graphics.PointF(it.x / containerSize.width, it.y / containerSize.height)
                                    }
                                    stateHolder.addSketch(ui.currentPage, listOf(normalised), ui.sketchColor, 4f)
                                }
                                currentStroke = emptyList()
                            },
                            onDrag = { change, _ ->
                                currentStroke = currentStroke + android.graphics.PointF(change.position.x, change.position.y)
                            }
                        )
                    }
                    ui.highlightMode -> {
                        // Highlight mode: drag to create highlights
                        detectDragGestures(
                            onDragStart = { offset -> dragStart = offset; dragEnd = offset },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragEnd
                                if (start != null && end != null && containerSize.width > 0 && containerSize.height > 0 && bitmap != null) {
                                    val left = minOf(start.x, end.x) / containerSize.width
                                    val top = minOf(start.y, end.y) / containerSize.height
                                    val right = maxOf(start.x, end.x) / containerSize.width
                                    val bottom = maxOf(start.y, end.y) / containerSize.height
                                    if (right - left > 0.01f && bottom - top > 0.01f) {
                                        stateHolder.addHighlight(
                                            ui.currentPage,
                                            android.graphics.RectF(left, top, right, bottom),
                                            0x66FFC043
                                        )
                                    }
                                }
                                dragStart = null
                                dragEnd = null
                            },
                            onDrag = { change, _ -> dragEnd = change.position }
                        )
                    }
                    else -> {
                        // Normal mode: tap + pinch zoom
                        detectTapGestures(
                            onDoubleTap = {
                                if (currentZoom > 1.3f) {
                                    stateHolder.resetZoom()
                                    pan = Offset.Zero
                                } else {
                                    stateHolder.setZoom(2.5f)
                                }
                            },
                            onTap = { onToggleChrome() }
                        )
                    }
                }
            }
            .pointerInput(ui.currentPage, ui.highlightMode, ui.sketchMode) {
                if (!ui.highlightMode && !ui.sketchMode) {
                    detectTransformGestures { _, panDelta, zoomDelta, _ ->
                        val newZoom = (currentZoom * zoomDelta).coerceIn(1f, 5f)
                        stateHolder.setZoom(newZoom)
                        pan = if (newZoom > 1f) pan + panDelta else Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        } else {
            Image(
                bitmap = bitmap.bitmap.asImageBitmap(),
                contentDescription = "Page ${ui.currentPage + 1} of ${ui.totalPages}",
                colorFilter = colorFilter,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (ui.appearance.fullscreen) 0.dp else 6.dp)
                    .graphicsLayer {
                        scaleX = currentZoom
                        scaleY = currentZoom
                        translationX = pan.x
                        translationY = pan.y
                    }
            )
            // Annotation overlay — highlights + sketches + current drawing
            Canvas(modifier = Modifier.matchParentSize()) {
                // Draw existing highlights
                for (ann in highlights) {
                    for (rect in ann.rects) {
                        drawRect(
                            color = Color(ann.color),
                            topLeft = Offset(rect.left * size.width, rect.top * size.height),
                            size = Size(
                                (rect.right - rect.left) * size.width,
                                (rect.bottom - rect.top) * size.height
                            )
                        )
                    }
                }
                // Draw existing sketches
                for (ann in sketches) {
                    for (stroke in ann.strokes) {
                        if (stroke.size < 2) continue
                        val path = androidx.compose.ui.graphics.Path()
                        val first = stroke[0]
                        path.moveTo(first.x * size.width, first.y * size.height)
                        for (i in 1 until stroke.size) {
                            path.lineTo(stroke[i].x * size.width, stroke[i].y * size.height)
                        }
                        drawPath(
                            path = path,
                            color = Color(ann.color),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = ann.strokeWidth * 2f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
                // Draw current sketch stroke
                if (currentStroke.size >= 2) {
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(currentStroke[0].x, currentStroke[0].y)
                    for (i in 1 until currentStroke.size) {
                        path.lineTo(currentStroke[i].x, currentStroke[i].y)
                    }
                    drawPath(
                        path = path,
                        color = Color(ui.sketchColor),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 8f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
                // Draw current highlight drag selection
                val start = dragStart
                val end = dragEnd
                if (start != null && end != null) {
                    drawRect(
                        color = Color(0x88FFC043),
                        topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        size = Size(
                            kotlin.math.abs(end.x - start.x),
                            kotlin.math.abs(end.y - start.y)
                        )
                    )
                }
            }
        }
    }
}

// ===========================================================================
// Continuous vertical canvas — LazyColumn of pages with spacing
// ===========================================================================

/**
 * Continuous vertical scrolling canvas. Renders all pages in a LazyColumn;
 * only visible items are composed and rendered. Page spacing is applied
 * between items. The current page is tracked from the first visible item.
 */
@Composable
fun PdfContinuousCanvas(
    stateHolder: PdfReaderState,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = stateHolder.uiState.collectAsState().value
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.currentPage)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Observe scroll requests (from bookmark/note jumps) and scroll to the page.
    LaunchedEffect(ui.scrollRequest) {
        if (ui.scrollRequest > 0L) {
            try {
                listState.scrollToItem(ui.currentPage)
            } catch (_: Exception) {}
        }
    }

    // Track scroll position -> current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { page ->
                if (page != stateHolder.uiState.value.currentPage) {
                    // Update without triggering a scrollRequest (avoids loop)
                    stateHolder.updateCurrentPageFromScroll(page)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                stateHolder.onViewportSizeChanged(coords.size.width, coords.size.height)
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleChrome() })
            },
        verticalArrangement = Arrangement.spacedBy(ui.appearance.pageSpacingDp.dp),
        contentPadding = PaddingValues(vertical = ui.appearance.pageSpacingDp.dp)
    ) {
        items(ui.totalPages) { pageIndex ->
            PdfPageRenderBox(
                stateHolder = stateHolder,
                pageIndex = pageIndex,
                fitMode = ui.appearance.fitMode,
                canvasTheme = ui.appearance.canvasTheme,
                fullscreen = ui.appearance.fullscreen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===========================================================================
// Horizontal paged canvas — HorizontalPager of pages
// ===========================================================================

/**
 * Horizontal paged reading canvas. Swipe left/right between pages.
 */
@Composable
fun PdfHorizontalPagedCanvas(
    stateHolder: PdfReaderState,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = stateHolder.uiState.collectAsState().value
    val pagerState = rememberPagerState(initialPage = ui.currentPage) { ui.totalPages }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Observe scroll requests (from bookmark/note jumps) and scroll to the page.
    LaunchedEffect(ui.scrollRequest) {
        if (ui.scrollRequest > 0L) {
            try {
                pagerState.scrollToPage(ui.currentPage)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (page != stateHolder.uiState.value.currentPage) {
                    stateHolder.updateCurrentPageFromScroll(page)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                stateHolder.onViewportSizeChanged(coords.size.width, coords.size.height)
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleChrome() })
            }
    ) { pageIndex ->
        PdfPageRenderBox(
            stateHolder = stateHolder,
            pageIndex = pageIndex,
            fitMode = ui.appearance.fitMode,
            canvasTheme = ui.appearance.canvasTheme,
            fullscreen = ui.appearance.fullscreen,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ===========================================================================
// Page render box — shared single-page renderer used by continuous + paged
// ===========================================================================

@Composable
fun PdfPageRenderBox(
    stateHolder: PdfReaderState,
    pageIndex: Int,
    fitMode: PdfFitMode,
    canvasTheme: PdfCanvasTheme,
    fullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    val ui = stateHolder.uiState.collectAsState().value
    val targetWidth = stateHolder.computeTargetWidth(pageIndex)
    var pageBitmap by remember(pageIndex, targetWidth) {
        mutableStateOf<PdfPageBitmap?>(null)
    }
    var renderFailed by remember(pageIndex, targetWidth) { mutableStateOf(false) }

    LaunchedEffect(pageIndex, targetWidth) {
        renderFailed = false
        try {
            pageBitmap = stateHolder.renderPage(pageIndex, targetWidth)
            if (pageBitmap == null) renderFailed = true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            renderFailed = true
        }
    }

    val colorFilter = canvasColorFilter(canvasTheme)
    val pb = pageBitmap
    val highlights = remember(ui.annotations, pageIndex) {
        ui.annotations.filter { it.pageIndex == pageIndex && it.type == PdfAnnotationType.HIGHLIGHT }
    }
    val sketches = remember(ui.annotations, pageIndex) {
        ui.annotations.filter { it.pageIndex == pageIndex && it.type == PdfAnnotationType.FREEHAND }
    }

    // Highlight drag state
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Sketch state
    var currentStroke by remember { mutableStateOf<List<android.graphics.PointF>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(ui.highlightMode, ui.sketchMode, pageIndex) {
                when {
                    ui.sketchMode -> {
                        // Sketch mode: freehand drawing
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(android.graphics.PointF(offset.x, offset.y))
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty() && containerSize.width > 0 && containerSize.height > 0) {
                                    val normalised = currentStroke.map {
                                        android.graphics.PointF(it.x / containerSize.width, it.y / containerSize.height)
                                    }
                                    stateHolder.addSketch(pageIndex, listOf(normalised), ui.sketchColor, 4f)
                                }
                                currentStroke = emptyList()
                            },
                            onDrag = { change, _ ->
                                currentStroke = currentStroke + android.graphics.PointF(change.position.x, change.position.y)
                            }
                        )
                    }
                    ui.highlightMode -> {
                        // Highlight mode: rect drag
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragEnd = offset
                            },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragEnd
                                if (start != null && end != null && containerSize.width > 0 && containerSize.height > 0) {
                                    val left = minOf(start.x, end.x) / containerSize.width
                                    val top = minOf(start.y, end.y) / containerSize.height
                                    val right = maxOf(start.x, end.x) / containerSize.width
                                    val bottom = maxOf(start.y, end.y) / containerSize.height
                                    if (right - left > 0.01f && bottom - top > 0.01f) {
                                        stateHolder.addHighlight(
                                            pageIndex,
                                            android.graphics.RectF(left, top, right, bottom),
                                            0x66FFC043
                                        )
                                    }
                                }
                                dragStart = null
                                dragEnd = null
                            },
                            onDrag = { change, _ ->
                                dragEnd = change.position
                            }
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (pb != null) {
            Image(
                bitmap = pb.bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
            // Annotation overlay — highlights + sketches + current drawing.
            Canvas(modifier = Modifier.matchParentSize()) {
                // Draw existing highlights
                for (ann in highlights) {
                    for (rect in ann.rects) {
                        drawRect(
                            color = Color(ann.color),
                            topLeft = Offset(rect.left * size.width, rect.top * size.height),
                            size = Size(
                                (rect.right - rect.left) * size.width,
                                (rect.bottom - rect.top) * size.height
                            )
                        )
                    }
                }
                // Draw existing sketches
                for (ann in sketches) {
                    for (stroke in ann.strokes) {
                        if (stroke.size < 2) continue
                        val path = androidx.compose.ui.graphics.Path()
                        val first = stroke[0]
                        path.moveTo(first.x * size.width, first.y * size.height)
                        for (i in 1 until stroke.size) {
                            path.lineTo(stroke[i].x * size.width, stroke[i].y * size.height)
                        }
                        drawPath(
                            path = path,
                            color = Color(ann.color),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = ann.strokeWidth * 2f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
                // Draw current sketch stroke
                if (currentStroke.size >= 2) {
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(currentStroke[0].x, currentStroke[0].y)
                    for (i in 1 until currentStroke.size) {
                        path.lineTo(currentStroke[i].x, currentStroke[i].y)
                    }
                    drawPath(
                        path = path,
                        color = Color(ui.sketchColor),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 8f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
                // Draw current highlight drag selection
                val start = dragStart
                val end = dragEnd
                if (start != null && end != null) {
                    drawRect(
                        color = Color(0x88FFC043),
                        topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        size = Size(
                            kotlin.math.abs(end.x - start.x),
                            kotlin.math.abs(end.y - start.y)
                        )
                    )
                }
            }
        } else if (renderFailed) {
            Text(
                text = "Page ${pageIndex + 1} could not be rendered",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            CircularProgressIndicator(
                color = PrimaryBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp).padding(16.dp)
            )
        }
    }
}

// ===========================================================================
// Floating bottom navigation dock
// ===========================================================================

@Composable
fun PdfBottomDock(
    currentPage: Int,
    totalPages: Int,
    zoom: Float,
    readingMode: PdfReadingMode,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onToggleAppearance: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomEnabled = readingMode == PdfReadingMode.SINGLE_PAGE
    // Show undo/redo only when there's something to undo/redo.
    val showUndoRedo = canUndo || canRedo

    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .shadow(12.dp, ExpressiveDockShape),
        shape = ExpressiveDockShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Undo/Redo — only visible when there's something to undo/redo
            if (showUndoRedo) {
                DockIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = canUndo, onClick = onUndo)
                DockIcon(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = canRedo, onClick = onRedo)
                VerticalDivider(modifier = Modifier.padding(horizontal = 3.dp).height(22.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            // Page navigation
            DockIcon(Icons.Default.ChevronLeft, "Previous Page", enabled = currentPage > 0, onClick = onPrev)
            Surface(
                shape = ExpressivePillSmall,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clickable { onJump() }
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "${currentPage + 1} / $totalPages",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            DockIcon(Icons.Default.ChevronRight, "Next Page", enabled = currentPage < totalPages - 1, onClick = onNext)
            VerticalDivider(modifier = Modifier.padding(horizontal = 3.dp).height(22.dp), color = MaterialTheme.colorScheme.outlineVariant)
            // Zoom (only in single-page mode)
            DockIcon(Icons.Default.ZoomOut, "Zoom Out", enabled = zoomEnabled && zoom > 1.05f, onClick = onZoomOut)
            Surface(
                shape = ExpressivePillSmall,
                color = if (zoom > 1.05f) PrimaryBlue.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .clickable { if (zoomEnabled) onZoomReset() }
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "${(zoom * 100).roundToInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (zoom > 1.05f) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            DockIcon(Icons.Default.ZoomIn, "Zoom In", enabled = zoomEnabled && zoom < 4.95f, onClick = onZoomIn)
            VerticalDivider(modifier = Modifier.padding(horizontal = 3.dp).height(22.dp), color = MaterialTheme.colorScheme.outlineVariant)
            // Appearance + Fullscreen
            DockIcon(Icons.Default.Tune, "Appearance", onClick = onToggleAppearance)
            DockIcon(
                imageVector = if (readingMode == PdfReadingMode.CONTINUOUS_VERTICAL || true) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                description = "Fullscreen",
                onClick = onToggleFullscreen
            )
        }
    }
}

@Composable
private fun DockIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(imageVector, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

// ===========================================================================
// Page jump dialog with slider
// ===========================================================================

@Composable
fun PdfPageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf((currentPage + 1).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Page", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Page ${sliderValue.roundToInt()} of $totalPages",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryBlue
                )
                Spacer(Modifier.size(12.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                    steps = if (totalPages > 2) totalPages - 2 else 0,
                    colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJump((sliderValue.roundToInt() - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))); onDismiss() },
                shape = ExpressivePillSmall
            ) { Text("Jump") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ===========================================================================
// Loading + error states
// ===========================================================================

@Composable
fun PdfLoadingState(progress: Float?, modifier: Modifier = Modifier) {
    com.boardsprep.onboard.ui.components.PdfAsymmetricLoadingView(
        progress = progress,
        modifier = modifier
    )
}

@Composable
fun PdfErrorState(
    kind: PdfErrorKind,
    detail: String,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    onViewErrorLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, body) = when (kind) {
        PdfErrorKind.MISSING_DOCUMENT -> "Document unavailable" to "The document could not be found. It may have been moved or deleted."
        PdfErrorKind.INVALID_PDF -> "Not a valid PDF" to "This file doesn't appear to be a real PDF document."
        PdfErrorKind.CORRUPT_DOWNLOAD -> "Download was incomplete" to "The cached file is corrupted. Please retry the download."
        PdfErrorKind.PASSWORD_PROTECTED -> "Password-protected PDF" to "OnBOARD Reader cannot open encrypted PDFs. Try opening it in an external viewer."
        PdfErrorKind.UNSUPPORTED_PDF -> "Unsupported PDF" to "This PDF uses features the built-in reader can't handle."
        PdfErrorKind.NETWORK_FAILURE -> "Network problem" to (detail.ifBlank { "Could not download the document. Check your connection and retry." })
        PdfErrorKind.INSUFFICIENT_STORAGE -> "Not enough storage" to (detail.ifBlank { "Free up space on your device and retry." })
        PdfErrorKind.RENDER_ERROR -> "Rendering problem" to (detail.ifBlank { "The page could not be rendered." })
        PdfErrorKind.UNKNOWN -> "Something went wrong" to (detail.ifBlank { "An unexpected error occurred." })
    }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.size(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry, shape = ExpressivePillSmall, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                Text("Retry")
            }
            Button(onClick = onOpenExternal, shape = ExpressivePillSmall, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Open externally")
            }
        }
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = onViewErrorLog) {
            Text("View error log", fontSize = 12.sp)
        }
    }
}

// ===========================================================================
// QP <-> MS switcher chip
// ===========================================================================

@Composable
fun PdfPairedSwitcher(
    pairedTitle: String,
    roleLabel: String,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onSwitch() }
            .padding(end = 8.dp),
        shape = ExpressivePillSmall,
        color = AccentAmber.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SwapHoriz, null, tint = AccentAmber, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(
                text = roleLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
