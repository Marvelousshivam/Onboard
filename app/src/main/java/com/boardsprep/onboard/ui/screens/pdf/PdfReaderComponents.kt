package com.boardsprep.onboard.ui.screens.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.boardsprep.onboard.core.pdf.PdfAnnotationHitTest
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
import com.boardsprep.onboard.core.theme.SuccessGreen
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

/**
 * Draw a smooth polyline using midpoint quadratic bezier interpolation.
 * Normalised points (0..1) are scaled to current canvas dimensions.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothPolyline(
    points: List<android.graphics.PointF>,
    color: Color,
    strokeWidthPx: Float,
    alpha: Float = 1f,
    blendMode: androidx.compose.ui.graphics.BlendMode = androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
) {
    if (points.isEmpty()) return
    val w = size.width
    val h = size.height

    if (points.size == 1) {
        val p = points[0]
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = strokeWidthPx / 2f,
            center = Offset(p.x * w, p.y * h),
            blendMode = blendMode
        )
        return
    }

    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(points[0].x * w, points[0].y * h)

    if (points.size == 2) {
        path.lineTo(points[1].x * w, points[1].y * h)
    } else {
        for (i in 1 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val midX = (p0.x + p1.x) / 2f * w
            val midY = (p0.y + p1.y) / 2f * h
            path.quadraticTo(p0.x * w, p0.y * h, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * w, last.y * h)
    }

    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = blendMode
    )
}

// ===========================================================================
// Single-page canvas — one page at a time with pinch zoom / pan
// ===========================================================================

/**
 * Single-page reading canvas. Shows one page at a time with pinch-to-zoom,
 * double-tap zoom, and drag-to-pan.
 *
 * Annotations and active drawings are anchored in an aspect-ratio-locked box
 * so they match the rendered PDF bitmap pixel-for-pixel with zero letterbox drift.
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

    val highlights = remember(ui.annotations, ui.currentPage) {
        ui.annotations.filter { it.pageIndex == ui.currentPage && it.type == PdfAnnotationType.HIGHLIGHT }
    }
    val sketches = remember(ui.annotations, ui.currentPage) {
        ui.annotations.filter { it.pageIndex == ui.currentPage && it.type == PdfAnnotationType.FREEHAND }
    }
    var currentStroke by remember { mutableStateOf<List<android.graphics.PointF>>(emptyList()) }
    var eraserPoint by remember { mutableStateOf<Offset?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val isAnnotating = ui.sketchMode || ui.highlightMode || ui.eraserMode

    val touchModifier = if (isAnnotating) {
        Modifier.pointerInput(ui.currentPage, ui.highlightMode, ui.sketchMode, ui.eraserMode) {
            when {
                ui.eraserMode -> {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        eraserPoint = down.position
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            val norm = android.graphics.PointF(down.position.x / containerSize.width, down.position.y / containerSize.height)
                            stateHolder.eraseAtPoint(ui.currentPage, norm)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                eraserPoint = change.position
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val norm = android.graphics.PointF(change.position.x / containerSize.width, change.position.y / containerSize.height)
                                    stateHolder.eraseAtPoint(ui.currentPage, norm)
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        eraserPoint = null
                    }
                }
                ui.highlightMode || ui.sketchMode -> {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        currentStroke = listOf(android.graphics.PointF(down.position.x, down.position.y))
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                currentStroke = currentStroke + android.graphics.PointF(change.position.x, change.position.y)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (currentStroke.isNotEmpty() && containerSize.width > 0 && containerSize.height > 0) {
                            val normalised = currentStroke.map {
                                android.graphics.PointF(it.x / containerSize.width, it.y / containerSize.height)
                            }
                            if (ui.highlightMode) {
                                stateHolder.addHighlightStroke(
                                    ui.currentPage,
                                    listOf(normalised),
                                    ui.highlightColor,
                                    ui.strokeWidth
                                )
                            } else {
                                stateHolder.addSketch(
                                    ui.currentPage,
                                    listOf(normalised),
                                    ui.sketchColor,
                                    ui.strokeWidth
                                )
                            }
                        }
                        currentStroke = emptyList()
                    }
                }
            }
        }
    } else {
        Modifier
            .pointerInput(ui.currentPage) {
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
            .pointerInput(ui.currentPage) {
                detectTransformGestures { _, panDelta, zoomDelta, _ ->
                    val newZoom = (currentZoom * zoomDelta).coerceIn(1f, 5f)
                    stateHolder.setZoom(newZoom)
                    pan = if (newZoom > 1f) pan + panDelta else Offset.Zero
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                stateHolder.onViewportSizeChanged(coords.size.width, coords.size.height)
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            com.boardsprep.onboard.ui.components.PdfPageShimmerSkeleton()
        } else {
            val pageAspect = (bitmap.renderedWidthPx.toFloat() / bitmap.renderedHeightPx.toFloat().coerceAtLeast(1f)).coerceAtLeast(0.1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (ui.appearance.fullscreen) 0.dp else 6.dp)
                    .graphicsLayer {
                        scaleX = currentZoom
                        scaleY = currentZoom
                        translationX = pan.x
                        translationY = pan.y
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(pageAspect, matchHeightConstraintsFirst = false)
                        .onGloballyPositioned { coords ->
                            containerSize = coords.size
                        }
                        .then(touchModifier)
                ) {
                    Image(
                        bitmap = bitmap.bitmap.asImageBitmap(),
                        contentDescription = "Page ${ui.currentPage + 1} of ${ui.totalPages}",
                        colorFilter = colorFilter,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Annotation overlay
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // 1. Highlights
                        for (ann in highlights) {
                            val highlightColor = Color(ann.color)
                            for (rect in ann.rects) {
                                drawRect(
                                    color = highlightColor.copy(alpha = 0.38f),
                                    topLeft = Offset(rect.left * size.width, rect.top * size.height),
                                    size = Size(
                                        (rect.right - rect.left) * size.width,
                                        (rect.bottom - rect.top) * size.height
                                    )
                                )
                            }
                            val strokeW = (ann.strokeWidth * 4f * density.density).coerceAtLeast(16.dp.toPx())
                            for (stroke in ann.strokes) {
                                drawSmoothPolyline(
                                    points = stroke,
                                    color = highlightColor,
                                    strokeWidthPx = strokeW,
                                    alpha = 0.38f
                                )
                            }
                        }

                        // 2. Sketches (pen)
                        for (ann in sketches) {
                            val penColor = Color(ann.color)
                            val strokeW = (ann.strokeWidth * density.density).coerceAtLeast(2.dp.toPx())
                            for (stroke in ann.strokes) {
                                drawSmoothPolyline(
                                    points = stroke,
                                    color = penColor,
                                    strokeWidthPx = strokeW,
                                    alpha = 1f
                                )
                            }
                        }

                        // 3. Active wet stroke
                        if (currentStroke.isNotEmpty() && size.width > 0 && size.height > 0) {
                            val activeNorm = currentStroke.map { android.graphics.PointF(it.x / size.width, it.y / size.height) }
                            if (ui.highlightMode) {
                                val strokeW = (ui.strokeWidth * 4f * density.density).coerceAtLeast(16.dp.toPx())
                                drawSmoothPolyline(
                                    points = activeNorm,
                                    color = Color(ui.highlightColor),
                                    strokeWidthPx = strokeW,
                                    alpha = 0.38f
                                )
                            } else if (ui.sketchMode) {
                                val strokeW = (ui.strokeWidth * density.density).coerceAtLeast(2.dp.toPx())
                                drawSmoothPolyline(
                                    points = activeNorm,
                                    color = Color(ui.sketchColor),
                                    strokeWidthPx = strokeW,
                                    alpha = 1f
                                )
                            }
                        }

                        // 4. Eraser cursor
                        val ep = eraserPoint
                        if (ep != null) {
                            val eraserRadius = 22.dp.toPx()
                            drawCircle(
                                color = Color.White.copy(alpha = 0.55f),
                                radius = eraserRadius,
                                center = ep
                            )
                            drawCircle(
                                color = Color(0xFFFF5252),
                                radius = eraserRadius,
                                center = ep,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color(0xFFFF5252),
                                radius = 3.dp.toPx(),
                                center = ep
                            )
                        }
                    }
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

    val isAnnotating = ui.sketchMode || ui.highlightMode || ui.eraserMode

    LazyColumn(
        state = listState,
        userScrollEnabled = !isAnnotating,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                stateHolder.onViewportSizeChanged(coords.size.width, coords.size.height)
            }
            .pointerInput(isAnnotating) {
                if (!isAnnotating) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
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

    val isAnnotating = ui.sketchMode || ui.highlightMode || ui.eraserMode

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !isAnnotating,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                stateHolder.onViewportSizeChanged(coords.size.width, coords.size.height)
            }
            .pointerInput(isAnnotating) {
                if (!isAnnotating) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
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

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var currentStroke by remember { mutableStateOf<List<android.graphics.PointF>>(emptyList()) }
    var eraserPoint by remember { mutableStateOf<Offset?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val isAnnotating = ui.sketchMode || ui.highlightMode || ui.eraserMode

    val touchModifier = if (isAnnotating) {
        Modifier.pointerInput(ui.sketchMode, ui.highlightMode, ui.eraserMode, pageIndex) {
            when {
                ui.eraserMode -> {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        eraserPoint = down.position
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            val norm = android.graphics.PointF(down.position.x / containerSize.width, down.position.y / containerSize.height)
                            stateHolder.eraseAtPoint(pageIndex, norm)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                eraserPoint = change.position
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val norm = android.graphics.PointF(change.position.x / containerSize.width, change.position.y / containerSize.height)
                                    stateHolder.eraseAtPoint(pageIndex, norm)
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        eraserPoint = null
                    }
                }
                ui.highlightMode || ui.sketchMode -> {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        currentStroke = listOf(android.graphics.PointF(down.position.x, down.position.y))
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                currentStroke = currentStroke + android.graphics.PointF(change.position.x, change.position.y)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (currentStroke.isNotEmpty() && containerSize.width > 0 && containerSize.height > 0) {
                            val normalised = currentStroke.map {
                                android.graphics.PointF(it.x / containerSize.width, it.y / containerSize.height)
                            }
                            if (ui.highlightMode) {
                                stateHolder.addHighlightStroke(
                                    pageIndex,
                                    listOf(normalised),
                                    ui.highlightColor,
                                    ui.strokeWidth
                                )
                            } else {
                                stateHolder.addSketch(
                                    pageIndex,
                                    listOf(normalised),
                                    ui.sketchColor,
                                    ui.strokeWidth
                                )
                            }
                        }
                        currentStroke = emptyList()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerSize = it.size }
            .then(touchModifier),
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
            // Annotation overlay — highlights + sketches + current drawing + eraser cursor
            Canvas(modifier = Modifier.matchParentSize()) {
                // 1. Draw existing highlights (both rects and smooth freehand strokes)
                for (ann in highlights) {
                    val highlightColor = Color(ann.color)
                    for (rect in ann.rects) {
                        drawRect(
                            color = highlightColor.copy(alpha = 0.38f),
                            topLeft = Offset(rect.left * size.width, rect.top * size.height),
                            size = Size(
                                (rect.right - rect.left) * size.width,
                                (rect.bottom - rect.top) * size.height
                            )
                        )
                    }
                    val strokeW = (ann.strokeWidth * 4f * density.density).coerceAtLeast(16.dp.toPx())
                    for (stroke in ann.strokes) {
                        drawSmoothPolyline(
                            points = stroke,
                            color = highlightColor,
                            strokeWidthPx = strokeW,
                            alpha = 0.38f
                        )
                    }
                }

                // 2. Draw existing sketches (pen)
                for (ann in sketches) {
                    val penColor = Color(ann.color)
                    val strokeW = (ann.strokeWidth * density.density).coerceAtLeast(2.dp.toPx())
                    for (stroke in ann.strokes) {
                        drawSmoothPolyline(
                            points = stroke,
                            color = penColor,
                            strokeWidthPx = strokeW,
                            alpha = 1f
                        )
                    }
                }

                // 3. Draw active wet stroke (while dragging)
                if (currentStroke.isNotEmpty() && size.width > 0 && size.height > 0) {
                    val activeNorm = currentStroke.map { android.graphics.PointF(it.x / size.width, it.y / size.height) }
                    if (ui.highlightMode) {
                        val strokeW = (ui.strokeWidth * 4f * density.density).coerceAtLeast(16.dp.toPx())
                        drawSmoothPolyline(
                            points = activeNorm,
                            color = Color(ui.highlightColor),
                            strokeWidthPx = strokeW,
                            alpha = 0.38f
                        )
                    } else if (ui.sketchMode) {
                        val strokeW = (ui.strokeWidth * density.density).coerceAtLeast(2.dp.toPx())
                        drawSmoothPolyline(
                            points = activeNorm,
                            color = Color(ui.sketchColor),
                            strokeWidthPx = strokeW,
                            alpha = 1f
                        )
                    }
                }

                // 4. Draw eraser cursor circle
                val ep = eraserPoint
                if (ep != null) {
                    val eraserRadius = 22.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = eraserRadius,
                        center = ep
                    )
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = eraserRadius,
                        center = ep,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = 3.dp.toPx(),
                        center = ep
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
            com.boardsprep.onboard.ui.components.PdfPageShimmerSkeleton()
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

/**
 * Expressive tactile bottom palette for studying & annotations.
 * Appears when highlighter, pen, or eraser mode is engaged.
 */
@Composable
fun PdfAnnotationStudioBar(
    highlightMode: Boolean,
    sketchMode: Boolean,
    eraserMode: Boolean,
    currentColor: Int,
    currentStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onSelectHighlighter: (Int) -> Unit,
    onSelectPen: (Int) -> Unit,
    onSelectEraser: () -> Unit,
    onSelectStrokeWidth: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlighterPalette = listOf(
        0x66FFEB3B.toInt() to "Yellow",
        0x6681C784.toInt() to "Mint",
        0x664DD0E1.toInt() to "Cyan",
        0x66FF8A65.toInt() to "Coral",
        0x66CE93D8.toInt() to "Lavender"
    )
    val penPalette = listOf(
        0xFFFF1744.toInt() to "Red",
        0xFF2979FF.toInt() to "Blue",
        0xFF00E676.toInt() to "Green",
        0xFF212121.toInt() to "Black",
        0xFF9C27B0.toInt() to "Purple",
        0xFFFF9100.toInt() to "Amber"
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .padding(bottom = 12.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Mode selectors + Quick Page Navigation + Undo/Redo/Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tool selection pills (Pen, Highlighter, Eraser)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Pen pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (sketchMode) PrimaryBlue else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clickable { onSelectPen(penPalette.first().first) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Pen",
                                modifier = Modifier.size(15.dp),
                                tint = if (sketchMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Pen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sketchMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Highlighter pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (highlightMode) PrimaryBlue else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clickable { onSelectHighlighter(highlighterPalette.first().first) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Highlight,
                                contentDescription = "Highlighter",
                                modifier = Modifier.size(15.dp),
                                tint = if (highlightMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Highlight",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (highlightMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Eraser pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (eraserMode) Color(0xFFFF5252) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clickable { onSelectEraser() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoFixNormal,
                                contentDescription = "Eraser",
                                modifier = Modifier.size(15.dp),
                                tint = if (eraserMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Eraser",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (eraserMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Actions: Undo, Redo, Page Nav, Done
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Redo", modifier = Modifier.size(16.dp))
                    }

                    VerticalDivider(modifier = Modifier.padding(horizontal = 2.dp).height(18.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Page switcher
                    IconButton(onClick = onPrevPage, enabled = currentPage > 0, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.ChevronLeft, "Prev Page", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${currentPage + 1}/$totalPages",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onNextPage, enabled = currentPage < totalPages - 1, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.ChevronRight, "Next Page", modifier = Modifier.size(16.dp))
                    }

                    VerticalDivider(modifier = Modifier.padding(horizontal = 2.dp).height(18.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Check, "Done", modifier = Modifier.size(18.dp), tint = SuccessGreen)
                    }
                }
            }

            // Row 2: Contextual Tool Controls
            if (eraserMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoFixNormal,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFF5252)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Tap or scrub across strokes to erase",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        shape = ExpressivePillSmall,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { onClearPage() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear Page",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Clear Page ${currentPage + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (highlightMode) "Color" else "Ink",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val activePalette = if (highlightMode) highlighterPalette else penPalette
                    for ((c, name) in activePalette) {
                        val isSelected = c == currentColor
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (highlightMode) onSelectHighlighter(c) else onSelectPen(c)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = name,
                                    tint = if (highlightMode) Color.DarkGray else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    val widthOptions = if (highlightMode) {
                        listOf(16f to "Fine", 24f to "Thick", 36f to "Chisel")
                    } else {
                        listOf(2f to "Fine", 4f to "Med", 8f to "Bold")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        widthOptions.forEach { (w, label) ->
                            val isSelected = kotlin.math.abs(currentStrokeWidth - w) < 0.5f ||
                                (!highlightMode && currentStrokeWidth == w) ||
                                (highlightMode && currentStrokeWidth == w)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { onSelectStrokeWidth(w) }
                            ) {
                                Text(
                                    label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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
