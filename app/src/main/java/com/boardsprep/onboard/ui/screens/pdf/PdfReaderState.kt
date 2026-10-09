package com.boardsprep.onboard.ui.screens.pdf

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.boardsprep.onboard.core.pdf.PdfAnnotation
import com.boardsprep.onboard.core.pdf.PdfAnnotationType
import com.boardsprep.onboard.core.pdf.PdfAppearanceSettings
import com.boardsprep.onboard.core.pdf.PdfAppearanceStore
import com.boardsprep.onboard.core.pdf.PdfBookmark
import com.boardsprep.onboard.core.pdf.PdfCoordinateTransforms
import com.boardsprep.onboard.core.pdf.PdfDocumentRef
import com.boardsprep.onboard.core.pdf.PdfDocumentRepository
import com.boardsprep.onboard.core.pdf.PdfDocumentSession
import com.boardsprep.onboard.core.pdf.PdfErrorKind
import com.boardsprep.onboard.core.pdf.PdfErrorLog
import com.boardsprep.onboard.core.pdf.PdfExternalViewer
import com.boardsprep.onboard.core.pdf.PdfFitMode
import com.boardsprep.onboard.core.pdf.PdfLoadState
import com.boardsprep.onboard.core.pdf.PdfPageBitmap
import com.boardsprep.onboard.core.pdf.PdfPageCache
import com.boardsprep.onboard.core.pdf.PdfPairedDocument
import com.boardsprep.onboard.core.pdf.PdfReadingMode
import com.boardsprep.onboard.core.pdf.PdfReadingState
import com.boardsprep.onboard.core.pdf.PdfReaderRepository
import com.boardsprep.onboard.core.pdf.PdfSearchResult
import com.boardsprep.onboard.core.pdf.PdfTextExtractor
import com.boardsprep.onboard.data.local.OnboardDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The single source of truth for the OnBOARD Reader screen.
 *
 * Created via [rememberPdfReaderState] so it is scoped to the composable's
 * lifetime. Owns the document session, LRU page cache, lazy text extractor and
 * persisted reader repository, plus all mutable UI state.
 *
 * All heavy work runs on [Dispatchers.IO]; the UI reads [uiState] which is a
 * [StateFlow] updated atomically via [MutableStateFlow.update].
 */
class PdfReaderState(
    private val appContext: Context,
    val source: String,
    val title: String,
    val paired: PdfPairedDocument?,
    private val scope: CoroutineScope
) {
    companion object { private const val TAG = "PdfReaderState" }

    private val repository = PdfDocumentRepository(appContext)
    private val readerRepository = PdfReaderRepository(
        OnboardDatabase.getInstance(appContext).pdfReadingStateDao(),
        OnboardDatabase.getInstance(appContext).pdfBookmarkDao(),
        OnboardDatabase.getInstance(appContext).pdfAnnotationDao()
    )
    private val appearanceStore = PdfAppearanceStore(appContext)
    private val pageCache = PdfPageCache(PdfPageCache.budgetKib(appContext))

    private var session: PdfDocumentSession? = null
    private var textExtractor: PdfTextExtractor? = null
    private val renderJobs = mutableMapOf<Int, Job>()
    private var prefetchJob: Job? = null
    private var searchJob: Job? = null

    /** Undo/redo stacks for annotations (highlights, notes, sketches). */
    private val undoStack = mutableListOf<PdfAnnotation>()
    private val redoStack = mutableListOf<PdfAnnotation>()

    /** Canonical document id, recomputed once the document is resolved. */
    private var documentId: String = repository.documentIdFor(source)

    /** Viewport dimensions, reported by the canvas via [onViewportSizeChanged]. */
    var viewportWidthPx by mutableIntStateOf(0)
        private set
    var viewportHeightPx by mutableIntStateOf(0)
        private set

    private val _uiState = MutableStateFlow(
        PdfReaderUiState(
            loadState = PdfLoadState.Loading(null),
            appearance = appearanceStore.loadGlobalDefault(),
            currentPage = 0,
            totalPages = 0,
            zoom = 1f,
            bookmarks = emptyList(),
            annotations = emptyList(),
            search = null,
            searchQuery = "",
            searchIndex = -1,
            extractorHasTextLayer = null,
            extractorState = PdfTextExtractor.ExtractionState.Idle,
            pairedDocument = paired,
            offlineModeEnabled = false,
            offlineModeLoading = false
        )
    )
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private var resolvedRef: PdfDocumentRef? = null

    // ---- Lifecycle ---------------------------------------------------------

    fun start() {
        PdfErrorLog.info(TAG, "start: source=$source, title=$title")
        scope.launch(Dispatchers.IO) {
            try {
                val result = repository.resolve(source, title, progress = { progress ->
                    _uiState.update { it.copy(loadState = PdfLoadState.Loading(progress)) }
                }, paired = paired)
                when (result) {
                    is PdfDocumentRepository.ResolveResult.Success -> {
                        PdfErrorLog.info(TAG, "Document resolved: ${result.ref.documentId}, fromCache=${result.fromCache}")
                        onDocumentResolved(result.ref)
                    }
                    is PdfDocumentRepository.ResolveResult.Failed -> {
                        PdfErrorLog.error(TAG, "Document resolve failed: ${result.kind} — ${result.detail}")
                        _uiState.update {
                            it.copy(loadState = PdfLoadState.Error(result.kind, result.detail))
                        }
                    }
                    is PdfDocumentRepository.ResolveResult.Downloading -> Unit
                }
            } catch (t: Throwable) {
                PdfErrorLog.error(TAG, "start failed: ${t.message}", t)
                _uiState.update {
                    it.copy(
                        loadState = PdfLoadState.Error(
                            PdfErrorKind.UNKNOWN,
                            "Failed to load document: ${t.message}"
                        )
                    )
                }
            }
        }
    }

    private suspend fun onDocumentResolved(ref: PdfDocumentRef) {
        try {
            resolvedRef = ref
            documentId = ref.documentId
            val session = PdfDocumentSession.open(ref.localFile)
            if (session == null) {
            PdfErrorLog.error(TAG, "PdfDocumentSession.open returned null for ${ref.localFile.name}")
            _uiState.update {
                it.copy(
                    loadState = PdfLoadState.Error(
                        PdfErrorKind.INVALID_PDF,
                        "Could not open the document. It may be encrypted or corrupted."
                    )
                )
            }
            return
        }
        this.session = session
        PdfErrorLog.info(TAG, "Session opened: ${session.pageCount} pages")
        val restored = readerRepository.loadReadingState(documentId)
        val appearance = _uiState.value.appearance.let { cur ->
            cur.copy(
                readingMode = restored?.readingMode ?: cur.readingMode,
                fitMode = restored?.fitMode ?: cur.fitMode
            )
        }
        val firstPage = restored?.pageIndex?.coerceIn(0, session.pageCount.coerceAtLeast(1) - 1) ?: 0
        _uiState.update {
            it.copy(
                loadState = PdfLoadState.Opening,
                appearance = appearance,
                currentPage = firstPage,
                totalPages = session.pageCount,
                zoom = restored?.zoom ?: 1f
            )
        }
        session.markReady()
        _uiState.update { it.copy(loadState = PdfLoadState.Ready(session.pageCount)) }

        scope.launch {
            readerRepository.bookmarksForDocument(documentId).collectLatest { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
        scope.launch {
            readerRepository.annotationsForDocument(documentId).collectLatest { list ->
                _uiState.update { it.copy(annotations = list) }
            }
        }

        renderCurrentPage()
        prefetchAdjacent()
        persistReadingState()

        // If the user requested offline mode before the document resolved,
        // execute it now.
        if (pendingOfflineMode) {
            pendingOfflineMode = false
            enableOfflineMode()
        }
        } catch (t: Throwable) {
            PdfErrorLog.error(TAG, "onDocumentResolved failed: ${t.message}", t)
            _uiState.update {
                it.copy(
                    loadState = PdfLoadState.Error(
                        PdfErrorKind.UNKNOWN,
                        "Failed to open document: ${t.message}"
                    )
                )
            }
        }
    }

    fun stop() {
        PdfErrorLog.info(TAG, "stop: releasing session + cache for documentId=$documentId")
        searchJob?.cancel(); searchJob = null
        prefetchJob?.cancel(); prefetchJob = null
        renderJobs.values.forEach { it.cancel() }; renderJobs.clear()
        try { textExtractor?.close() } catch (e: Exception) {
            PdfErrorLog.warn(TAG, "textExtractor.close failed", e)
        }
        try { session?.close() } catch (e: Exception) {
            PdfErrorLog.warn(TAG, "session.close failed", e)
        }
        textExtractor = null
        session = null
        pageCache.evictDocument(documentId)
    }

    fun persistReadingState() {
        val s = session ?: return
        val st = _uiState.value
        scope.launch(Dispatchers.IO) {
            try {
                readerRepository.saveReadingState(
                    PdfReadingState(
                        documentId = documentId,
                        pageIndex = st.currentPage,
                        pageOffsetY = 0f,
                        zoom = st.zoom,
                        readingMode = st.appearance.readingMode,
                        fitMode = st.appearance.fitMode,
                        lastOpenedAt = System.currentTimeMillis()
                    ),
                    st.appearance
                )
            } catch (e: Exception) {
                PdfErrorLog.error(TAG, "persistReadingState failed", e)
            }
        }
    }

    // ---- Viewport ----------------------------------------------------------

    fun onViewportSizeChanged(widthPx: Int, heightPx: Int) {
        if (widthPx == viewportWidthPx && heightPx == viewportHeightPx) return
        viewportWidthPx = widthPx
        viewportHeightPx = heightPx
        // Re-render the current page at the correct width if in single-page mode.
        if (_uiState.value.appearance.readingMode == PdfReadingMode.SINGLE_PAGE) {
            renderCurrentPage(respectCache = false)
        }
    }

    // ---- Navigation --------------------------------------------------------

    /** Monotonic counter that increments whenever the canvas should scroll to a page. */
    private var scrollRequestCounter = 0L

    fun setCurrentPage(index: Int) {
        val s = session ?: return
        val clamped = PdfCoordinateTransforms.clampPageIndex(index, s.pageCount)
        val mode = _uiState.value.appearance.readingMode
        _uiState.update {
            it.copy(
                currentPage = clamped,
                // Only reset zoom in single-page mode (continuous/paged keep zoom).
                zoom = if (mode == PdfReadingMode.SINGLE_PAGE) 1f else it.zoom,
                scrollRequest = ++scrollRequestCounter
            )
        }
        if (mode == PdfReadingMode.SINGLE_PAGE) {
            renderCurrentPage()
            prefetchAdjacent()
        }
        persistReadingState()
    }

    fun nextPage() = setCurrentPage(_uiState.value.currentPage + 1)
    fun previousPage() = setCurrentPage(_uiState.value.currentPage - 1)
    fun jumpToPage(index: Int) = setCurrentPage(index)

    /**
     * Called by the continuous canvas when the user scrolls. Updates the
     * current page WITHOUT triggering a scrollRequest (which would cause a
     * feedback loop: scroll -> setCurrentPage -> scrollRequest -> scrollToItem).
     */
    fun updateCurrentPageFromScroll(index: Int) {
        val s = session ?: return
        val clamped = PdfCoordinateTransforms.clampPageIndex(index, s.pageCount)
        if (clamped == _uiState.value.currentPage) return
        _uiState.update { it.copy(currentPage = clamped) }
        persistReadingState()
    }

    // ---- Zoom --------------------------------------------------------------

    fun setZoom(zoom: Float) {
        val z = zoom.coerceIn(1f, 5f)
        _uiState.update { it.copy(zoom = z) }
        if (_uiState.value.appearance.readingMode == PdfReadingMode.SINGLE_PAGE) {
            renderCurrentPage(respectCache = false)
        }
    }

    fun resetZoom() {
        _uiState.update { it.copy(zoom = 1f) }
        if (_uiState.value.appearance.readingMode == PdfReadingMode.SINGLE_PAGE) {
            renderCurrentPage(respectCache = false)
        }
    }

    // ---- Appearance --------------------------------------------------------

    /**
     * Update appearance settings. Only re-renders when [PdfFitMode] changes
     * (since that affects the render target width). Canvas theme, brightness,
     * spacing and fullscreen changes are purely visual and don't need a re-render.
     */
    fun updateAppearance(transform: (PdfAppearanceSettings) -> PdfAppearanceSettings) {
        val current = _uiState.value.appearance
        val next = transform(current)
        val fitChanged = current.fitMode != next.fitMode
        _uiState.update { it.copy(appearance = next) }
        appearanceStore.saveGlobalDefault(next)
        if (fitChanged) {
            renderCurrentPage(respectCache = false)
        }
        persistReadingState()
    }

    fun toggleFullscreen() = updateAppearance { it.copy(fullscreen = !it.fullscreen) }

    /**
     * Apply the brightness override to the Activity's window. Called from the
     * screen via a LaunchedEffect whenever the appearance changes.
     */
    fun applyBrightness(activity: Activity) {
        val params = activity.window.attributes
        params.screenBrightness = _uiState.value.appearance.brightnessOverride
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = params
    }

    /** Restore system brightness when leaving the reader. */
    fun restoreBrightness(activity: Activity) {
        val params = activity.window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = params
    }

    // ---- Bookmarks ---------------------------------------------------------

    fun toggleBookmarkOnCurrentPage(label: String? = null) {
        val page = _uiState.value.currentPage
        scope.launch(Dispatchers.IO) {
            try {
                val existing = readerRepository.bookmarksForPage(documentId, page)
                if (existing.isEmpty()) {
                    readerRepository.addBookmark(
                        documentId = documentId,
                        pageIndex = page,
                        label = label ?: "Page ${page + 1}"
                    )
                } else {
                    readerRepository.removeBookmarksForPage(documentId, page)
                }
            } catch (e: Exception) {
                PdfErrorLog.error(TAG, "toggleBookmark failed", e)
            }
        }
    }

    fun removeBookmark(id: String) {
        scope.launch(Dispatchers.IO) { readerRepository.removeBookmark(id) }
    }

    // ---- Annotations -------------------------------------------------------

    fun addNoteAnnotation(pageIndex: Int, text: String, color: Int) {
        scope.launch(Dispatchers.IO) {
            val annotation = PdfAnnotation(
                id = java.util.UUID.randomUUID().toString(),
                documentId = documentId,
                pageIndex = pageIndex,
                type = PdfAnnotationType.NOTE,
                rects = emptyList(),
                color = color,
                strokeWidth = 0f,
                strokes = emptyList(),
                noteText = text,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            readerRepository.upsertAnnotation(annotation)
            undoStack.add(annotation)
            redoStack.clear()
            _uiState.update { it.copy(canUndo = true, canRedo = false) }
        }
    }

    fun deleteAnnotation(id: String) {
        scope.launch(Dispatchers.IO) {
            // Find the annotation to push to undo stack
            val ann = _uiState.value.annotations.find { it.id == id }
            if (ann != null) {
                undoStack.add(ann)
                redoStack.clear()
            }
            readerRepository.deleteAnnotation(id)
        }
    }

    /** Toggle highlight drawing mode. When on, dragging on a page creates a highlight. */
    fun toggleHighlightMode() {
        _uiState.update { it.copy(highlightMode = !it.highlightMode, sketchMode = false) }
    }

    /** Add a highlight annotation at [rect] (normalised 0..1 page coords) on [pageIndex]. */
    fun addHighlight(pageIndex: Int, rect: android.graphics.RectF, color: Int) {
        scope.launch(Dispatchers.IO) {
            val annotation = PdfAnnotation(
                id = java.util.UUID.randomUUID().toString(),
                documentId = documentId,
                pageIndex = pageIndex,
                type = PdfAnnotationType.HIGHLIGHT,
                rects = listOf(rect),
                color = color,
                strokeWidth = 0f,
                strokes = emptyList(),
                noteText = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            readerRepository.upsertAnnotation(annotation)
            undoStack.add(annotation)
            redoStack.clear()
            _uiState.update { it.copy(canUndo = true, canRedo = false) }
        }
    }

    /** Toggle sketch (freehand) drawing mode. When on, dragging draws a freehand stroke. */
    fun toggleSketchMode() {
        _uiState.update { it.copy(sketchMode = !it.sketchMode, highlightMode = false, sketchColor = it.sketchColor) }
    }

    fun setSketchColor(color: Int) {
        _uiState.update { it.copy(sketchColor = color) }
    }

    /** Add a freehand sketch annotation on [pageIndex]. */
    fun addSketch(pageIndex: Int, strokes: List<List<android.graphics.PointF>>, color: Int, strokeWidth: Float) {
        scope.launch(Dispatchers.IO) {
            val annotation = PdfAnnotation(
                id = java.util.UUID.randomUUID().toString(),
                documentId = documentId,
                pageIndex = pageIndex,
                type = PdfAnnotationType.FREEHAND,
                rects = emptyList(),
                color = color,
                strokeWidth = strokeWidth,
                strokes = strokes,
                noteText = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            readerRepository.upsertAnnotation(annotation)
            undoStack.add(annotation)
            redoStack.clear()
            _uiState.update { it.copy(canUndo = true, canRedo = false) }
        }
    }

    /** Undo the last annotation action (add or delete). */
    fun undo() {
        if (undoStack.isEmpty()) return
        val last = undoStack.removeAt(undoStack.lastIndex)
        scope.launch(Dispatchers.IO) {
            // If it exists in DB, delete it (undo add). If it doesn't, re-add it (undo delete).
            val exists = _uiState.value.annotations.any { it.id == last.id }
            if (exists) {
                readerRepository.deleteAnnotation(last.id)
            } else {
                readerRepository.upsertAnnotation(last)
            }
            redoStack.add(last)
            _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = true) }
        }
    }

    /** Redo the last undone annotation action. */
    fun redo() {
        if (redoStack.isEmpty()) return
        val last = redoStack.removeAt(redoStack.lastIndex)
        scope.launch(Dispatchers.IO) {
            // If it exists in DB, delete it (redo delete). If it doesn't, re-add it (redo add).
            val exists = _uiState.value.annotations.any { it.id == last.id }
            if (exists) {
                readerRepository.deleteAnnotation(last.id)
            } else {
                readerRepository.upsertAnnotation(last)
            }
            undoStack.add(last)
            _uiState.update { it.copy(canUndo = true, canRedo = redoStack.isNotEmpty()) }
        }
    }

    // ---- Offline mode ----------------------------------------------------

    /** When true, offline mode will be enabled as soon as the document resolves. */
    private var pendingOfflineMode = false

    /**
     * Enable offline mode: extracts the full text layer via PdfBox so that
     * text search (and future offline-only features) become available.
     *
     * If the document hasn't resolved yet, the request is queued and will
     * execute automatically once [onDocumentResolved] completes.
     */
    fun enableOfflineMode() {
        if (_uiState.value.offlineModeEnabled || _uiState.value.offlineModeLoading) return
        // If the document isn't resolved yet, queue the request.
        if (resolvedRef == null) {
            PdfErrorLog.info(TAG, "enableOfflineMode: document not resolved yet, queuing")
            pendingOfflineMode = true
            _uiState.update { it.copy(offlineModeLoading = true) }
            return
        }
        PdfErrorLog.info(TAG, "enableOfflineMode: starting text extraction")
        _uiState.update { it.copy(offlineModeLoading = true) }
        scope.launch(Dispatchers.IO) {
            val extractor = ensureTextExtractor()
            if (extractor == null) {
                PdfErrorLog.error(TAG, "enableOfflineMode: could not open text extractor")
                _uiState.update { it.copy(offlineModeLoading = false) }
                return@launch
            }
            // Force extraction now.
            extractor.ensureExtracted()
            _uiState.update {
                it.copy(
                    offlineModeEnabled = true,
                    offlineModeLoading = false,
                    extractorState = extractor.state.value,
                    extractorHasTextLayer = (extractor.state.value as? PdfTextExtractor.ExtractionState.Ready)?.hasTextLayer
                )
            }
            PdfErrorLog.info(TAG, "enableOfflineMode: complete")
        }
    }

    fun disableOfflineMode() {
        textExtractor?.let {
            try { it.close() } catch (_: Exception) {}
        }
        textExtractor = null
        _uiState.update {
            it.copy(
                offlineModeEnabled = false,
                offlineModeLoading = false,
                search = null,
                searchQuery = "",
                searchIndex = -1,
                extractorHasTextLayer = null,
                extractorState = PdfTextExtractor.ExtractionState.Idle
            )
        }
    }

    // ---- Search ------------------------------------------------------------

    /**
     * Run a search. If offline mode is not enabled, this is a no-op — the UI
     * is responsible for prompting the user to enable offline mode first.
     */
    fun runSearch(query: String) {
        if (!_uiState.value.offlineModeEnabled) {
            PdfErrorLog.info(TAG, "runSearch ignored: offline mode not enabled")
            return
        }
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchQuery = query, searchIndex = if (query.isBlank()) -1 else 0)
        }
        if (query.isBlank()) {
            _uiState.update { it.copy(search = null) }
            return
        }
        PdfErrorLog.info(TAG, "runSearch: query='$query'")
        searchJob = scope.launch(Dispatchers.IO) {
            val extractor = textExtractor ?: run {
                _uiState.update {
                    it.copy(search = PdfSearchResult(query, emptyList(), 0, false))
                }
                return@launch
            }
            val result = extractor.search(query)
            _uiState.update {
                it.copy(
                    search = result,
                    extractorHasTextLayer = result.hasTextLayer,
                    extractorState = extractor.state.value,
                    searchIndex = if (result.matches.isEmpty()) -1 else 0
                )
            }
        }
    }

    fun nextSearchMatch() {
        val s = _uiState.value
        val matches = s.search?.matches ?: return
        if (matches.isEmpty()) return
        val next = (s.searchIndex + 1) % matches.size
        _uiState.update { it.copy(searchIndex = next) }
        setCurrentPage(matches[next].pageIndex)
    }

    fun previousSearchMatch() {
        val s = _uiState.value
        val matches = s.search?.matches ?: return
        if (matches.isEmpty()) return
        val prev = (s.searchIndex - 1 + matches.size) % matches.size
        _uiState.update { it.copy(searchIndex = prev) }
        setCurrentPage(matches[prev].pageIndex)
    }

    fun clearSearch() {
        searchJob?.cancel(); searchJob = null
        _uiState.update { it.copy(search = null, searchQuery = "", searchIndex = -1) }
    }

    private suspend fun ensureTextExtractor(): PdfTextExtractor? {
        textExtractor?.let { return it }
        val ref = resolvedRef ?: return null
        val ext = PdfTextExtractor.open(appContext, ref.localFile) ?: return null
        textExtractor = ext
        scope.launch {
            ext.state.collectLatest { st ->
                _uiState.update { it.copy(extractorState = st) }
            }
        }
        return ext
    }

    // ---- Rendering --------------------------------------------------------

    /**
     * Compute the render target width for a page based on the current fit mode
     * and viewport dimensions. For FIT_PAGE, uses the cached page size if
     * available (populated after first render); falls back to FIT_WIDTH
     * behavior if the size isn't cached yet. This is NOT a suspend function
     * so it can be called from composable composition.
     */
    fun computeTargetWidth(pageIndex: Int): Int {
        val s = session ?: return 1080
        val zoom = _uiState.value.zoom
        val vw = viewportWidthPx.coerceAtLeast(1)
        val vh = viewportHeightPx.coerceAtLeast(1)
        return when (_uiState.value.appearance.fitMode) {
            PdfFitMode.FIT_WIDTH -> PdfCoordinateTransforms.computeRenderTargetWidth(vw, zoom)
            PdfFitMode.FIT_PAGE -> {
                // Use cached page size if available; otherwise fall back to
                // FIT_WIDTH. The size gets cached after the first render.
                val size = s.getCachedPageSize(pageIndex)
                if (size == null) {
                    PdfCoordinateTransforms.computeRenderTargetWidth(vw, zoom)
                } else {
                    val aspect = size.aspectRatio
                    val widthByHeight = (vh.toFloat() / aspect).toInt()
                    PdfCoordinateTransforms.computeRenderTargetWidth(
                        minOf(vw, widthByHeight), zoom
                    )
                }
            }
            PdfFitMode.FREE -> PdfCoordinateTransforms.computeRenderTargetWidth(vw, zoom)
        }
    }

    /**
     * Render a specific page for the multi-page canvases (continuous / paged).
     * Returns a cached or freshly-rendered [PdfPageBitmap], or null on failure.
     * This is safe to call from any composable's LaunchedEffect.
     */
    suspend fun renderPage(pageIndex: Int, targetWidth: Int): PdfPageBitmap? {
        val s = session ?: return null
        if (pageIndex !in 0 until s.pageCount) return null
        // Cache check first.
        pageCache.get(documentId, pageIndex, targetWidth)?.let { return it }
        // Render on the IO dispatcher via the session.
        return try {
            val result = s.renderPage(pageIndex, targetWidth) ?: run {
                PdfErrorLog.warn(TAG, "renderPage($pageIndex, $targetWidth) returned null")
                return null
            }
            pageCache.put(documentId, result, targetWidth)
            result
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            PdfErrorLog.error(TAG, "renderPage($pageIndex, $targetWidth) threw: ${t.message}", t)
            null
        }
    }

    private fun renderCurrentPage(respectCache: Boolean = true) {
        val s = session ?: return
        val page = _uiState.value.currentPage
        if (page !in 0 until s.pageCount) return
        val targetWidth = computeTargetWidth(page)
        if (respectCache) {
            val cached = pageCache.get(documentId, page, targetWidth)
            if (cached != null) {
                publishBitmap(cached)
                return
            }
        }
        renderJobs[page]?.cancel()
        renderJobs[page] = scope.launch(Dispatchers.IO) {
            val result = s.renderPage(page, targetWidth)
            if (result == null) return@launch
            if (result.pageIndex != _uiState.value.currentPage) {
                pageCache.put(documentId, result, targetWidth)
                return@launch
            }
            if (result.renderToken < s.currentToken()) {
                pageCache.put(documentId, result, targetWidth)
                return@launch
            }
            pageCache.put(documentId, result, targetWidth)
            publishBitmap(result)
        }
    }

    private fun publishBitmap(bitmap: PdfPageBitmap) {
        _uiState.update { it.copy(currentBitmap = bitmap) }
    }

    private fun prefetchAdjacent() {
        val s = session ?: return
        val page = _uiState.value.currentPage
        val window = 1
        val targetWidth = computeTargetWidth(page)
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            for (delta in 1..window) {
                for (p in listOf(page + delta, page - delta)) {
                    if (p !in 0 until s.pageCount) continue
                    if (pageCache.get(documentId, p, targetWidth) != null) continue
                    val result = s.renderPage(p, targetWidth) ?: continue
                    pageCache.put(documentId, result, targetWidth)
                }
            }
        }
    }

    fun switchToPaired(onNavigate: (String, String) -> Unit) {
        val p = paired ?: return
        onNavigate(p.source, p.title)
    }

    val cacheForTrim: PdfPageCache get() = pageCache

    fun openInExternalViewer(context: Context) {
        val ref = resolvedRef
        PdfExternalViewer.open(context, ref?.localFile, source)
    }
}

/** Snapshot of everything the UI needs to render the reader. */
data class PdfReaderUiState(
    val loadState: PdfLoadState,
    val appearance: PdfAppearanceSettings,
    val currentPage: Int,
    val totalPages: Int,
    val zoom: Float,
    val bookmarks: List<PdfBookmark>,
    val annotations: List<PdfAnnotation>,
    val currentBitmap: PdfPageBitmap? = null,
    val search: PdfSearchResult? = null,
    val searchQuery: String,
    val searchIndex: Int,
    val extractorHasTextLayer: Boolean? = null,
    val extractorState: PdfTextExtractor.ExtractionState,
    val pairedDocument: PdfPairedDocument? = null,
    val offlineModeEnabled: Boolean = false,
    val offlineModeLoading: Boolean = false,
    val highlightMode: Boolean = false,
    val sketchMode: Boolean = false,
    val sketchColor: Int = 0xFFFF1744.toInt(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val scrollRequest: Long = 0L
) {
    val isReady: Boolean get() = loadState is PdfLoadState.Ready
    val isLoading: Boolean get() = loadState is PdfLoadState.Loading || loadState is PdfLoadState.Opening
    val isError: Boolean get() = loadState is PdfLoadState.Error
}

@Composable
fun rememberPdfReaderState(
    source: String,
    title: String,
    paired: PdfPairedDocument?
): PdfReaderState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(source, title, paired?.source) {
        PdfReaderState(context.applicationContext, source, title, paired, scope)
    }
    LaunchedEffect(state) { state.start() }
    DisposableEffect(state) {
        onDispose { state.persistReadingState(); state.stop() }
    }
    DisposableEffect(state) {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() {
                PdfPageCache.onTrimMemory(state.cacheForTrim, 15)
            }
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
    return state
}
