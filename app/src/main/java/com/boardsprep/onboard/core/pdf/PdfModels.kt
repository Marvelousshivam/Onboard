package com.boardsprep.onboard.core.pdf

import android.graphics.RectF
import android.net.Uri
import java.io.File

/**
 * Domain models for the OnBOARD Reader.
 *
 * These types are engine-agnostic: they describe *what* the reader works with
 * (documents, pages, bookmarks, annotations, search results, appearance,
 * reading state) without depending on a particular PDF backend. The rendering
 * engine ([PdfDocumentSession]) and the text engine ([PdfTextExtractor]) adapt
 * their backend-specific types into these models.
 */

// ---------------------------------------------------------------------------
// Document identity
// ---------------------------------------------------------------------------

/**
 * A reference to a PDF document that the reader can open.
 *
 * [documentId] is the canonical, collision-resistant identity derived from the
 * source (SHA-256 of the canonical source string — see [PdfDocumentRepository]).
 * It is used as the Room foreign key for bookmarks, annotations and reading
 * state so that everything survives cache eviction, file renames and process
 * death as long as the source identity is stable.
 *
 * [source] is what the user/caller originally provided: an HTTPS URL, a local
 * file path, or a `content://` URI. [localFile] is the on-disk file the engine
 * will actually render from (already downloaded / copied locally).
 */
data class PdfDocumentRef(
    val documentId: String,
    val source: String,
    val title: String,
    val localFile: File,
    val origin: PdfDocumentOrigin,
    /** Optional paired document (e.g. marking scheme for a question paper). */
    val paired: PdfPairedDocument? = null
)

enum class PdfDocumentOrigin { REMOTE_URL, LOCAL_FILE, CONTENT_URI }

/**
 * A sibling document linked to the current one (question paper <-> marking
 * scheme). The reader exposes a one-tap switcher for this pair.
 */
data class PdfPairedDocument(
    val documentId: String,
    val source: String,
    val title: String,
    val role: PdfPairedRole
)

enum class PdfPairedRole(val displayName: String) {
    QUESTION_PAPER("Question Paper"),
    MARKING_SCHEME("Marking Scheme"),
    GENERAL("Paired Document")
}

// ---------------------------------------------------------------------------
// Page geometry
// ---------------------------------------------------------------------------

/** Native PDF page dimensions in PDF points (1/72 inch). */
data class PdfPageSize(val widthPts: Int, val heightPts: Int) {
    val aspectRatio: Float get() = if (widthPts > 0) heightPts.toFloat() / widthPts.toFloat() else 1f
}

/**
 * A rendered page bitmap plus the metadata needed to composite it correctly.
 *
 * [renderedWidthPx] is the actual bitmap width in pixels. The bitmap is
 * rendered at a target width chosen by the engine based on the current zoom
 * and viewport so that text stays sharp without burning memory.
 */
data class PdfPageBitmap(
    val pageIndex: Int,
    val bitmap: android.graphics.Bitmap,
    val renderedWidthPx: Int,
    val renderedHeightPx: Int,
    val pageSize: PdfPageSize,
    /** Monotonic token used to discard stale render results. */
    val renderToken: Long
)

// ---------------------------------------------------------------------------
// Reading state & navigation
// ---------------------------------------------------------------------------

enum class PdfReadingMode {
    /** Continuous vertical scrolling — the default for textbooks. */
    CONTINUOUS_VERTICAL,

    /** One page at a time, swipe horizontally. */
    HORIZONTAL_PAGED,

    /** One page at a time, swipe vertically. */
    SINGLE_PAGE
}

/** Fit preference for the page within the viewport. */
enum class PdfFitMode { FIT_WIDTH, FIT_PAGE, FREE }

/** Persisted reading position for a document. */
data class PdfReadingState(
    val documentId: String,
    val pageIndex: Int,
    /** 0f..1f — vertical offset of the viewport within the page. */
    val pageOffsetY: Float,
    val zoom: Float,
    val readingMode: PdfReadingMode,
    val fitMode: PdfFitMode,
    val lastOpenedAt: Long
)

// ---------------------------------------------------------------------------
// Bookmarks & annotations
// ---------------------------------------------------------------------------

data class PdfBookmark(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val label: String,
    val note: String,
    val forRevision: Boolean,
    val createdAt: Long
)

enum class PdfAnnotationType {
    HIGHLIGHT, UNDERLINE, NOTE, FREEHAND, RECTANGLE
}

/**
 * An annotation anchored to a page.
 *
 * Geometry is stored in **normalised PDF page coordinates** (0f..1f on both
 * axes, origin top-left of the page). This is the only coordinate system that
 * survives every zoom, pan, page-size, orientation and rendering-resolution
 * change without re-derivation. See [PdfCoordinateTransforms] for the
 * transforms to/from screen coordinates.
 */
data class PdfAnnotation(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val type: PdfAnnotationType,
    /** Normalised rects in page space (0..1). Multiple rects model multi-line highlights. */
    val rects: List<RectF>,
    /** Hex ARGB color, e.g. 0xFFFFC043. */
    val color: Int,
    val strokeWidth: Float,
    /** Freehand polyline in normalised page coords (one polyline per stroke). */
    val strokes: List<List<android.graphics.PointF>>,
    val noteText: String,
    val createdAt: Long,
    val updatedAt: Long
)

// ---------------------------------------------------------------------------
// Outline / Table of contents
// ---------------------------------------------------------------------------

/**
 * One entry in the document's outline (PDF bookmarks/TOC).
 *
 * The Android framework [android.graphics.pdf.PdfRenderer] does not expose the
 * document outline, so when no text engine is available this list is empty
 * and the UI honestly reports "no table of contents available for this PDF".
 */
data class PdfOutlineEntry(
    val title: String,
    val pageIndex: Int,
    val level: Int,
    val children: List<PdfOutlineEntry> = emptyList()
)

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

data class PdfSearchMatch(
    val pageIndex: Int,
    /** Normalised rect (0..1) of the matched text on the page. */
    val rect: RectF,
    val matchedText: String
)

data class PdfSearchResult(
    val query: String,
    val matches: List<PdfSearchMatch>,
    val scannedPages: Int,
    val hasTextLayer: Boolean
)

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

/**
 * Visual appearance of the reader canvas. Stored as a small sealed hierarchy
 * so the UI can render distinct controls and the repository can persist them.
 */
enum class PdfCanvasTheme {
    /** Light grey canvas, original PDF colors. */
    LIGHT,
    /** Near-black canvas, original PDF colors (best for OLED). */
    DARK_CANVAS,
    /** Near-black canvas with PDF color-inversion applied. */
    INVERT_PDF,
    /** Warm sepia canvas, original PDF colors. */
    SEPIA
}

/** Persisted reader appearance. */
data class PdfAppearanceSettings(
    val canvasTheme: PdfCanvasTheme,
    val fitMode: PdfFitMode,
    val readingMode: PdfReadingMode,
    val fullscreen: Boolean,
    /** 0..1 screen brightness override, or null to use system. */
    val brightnessOverride: Float?,
    /** Page spacing in dp between pages in continuous mode. */
    val pageSpacingDp: Int
)

/** Defaults applied on first open and when the user resets appearance. */
val DefaultPdfAppearance = PdfAppearanceSettings(
    canvasTheme = PdfCanvasTheme.LIGHT,
    fitMode = PdfFitMode.FIT_WIDTH,
    readingMode = PdfReadingMode.CONTINUOUS_VERTICAL,
    fullscreen = false,
    brightnessOverride = null,
    pageSpacingDp = 12
)

// ---------------------------------------------------------------------------
// Loading & error states
// ---------------------------------------------------------------------------

sealed interface PdfLoadState {
    /** No document requested yet. */
    data object Idle : PdfLoadState
    /** Downloading or resolving the document. [progress] is 0f..1f or null when indeterminate. */
    data class Loading(val progress: Float?) : PdfLoadState
    /** Document is open and the first page is being rendered. */
    data object Opening : PdfLoadState
    /** Document is ready. [pageCount] is the total page count. */
    data class Ready(val pageCount: Int) : PdfLoadState
    /** A recoverable or non-recoverable error. */
    data class Error(val kind: PdfErrorKind, val detail: String) : PdfLoadState
}

enum class PdfErrorKind {
    MISSING_DOCUMENT,
    INVALID_PDF,
    CORRUPT_DOWNLOAD,
    PASSWORD_PROTECTED,
    UNSUPPORTED_PDF,
    NETWORK_FAILURE,
    INSUFFICIENT_STORAGE,
    RENDER_ERROR,
    UNKNOWN
}
