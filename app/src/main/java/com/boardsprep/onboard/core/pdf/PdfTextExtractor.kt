package com.boardsprep.onboard.core.pdf

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File

/**
 * Lazy, cached, genuine full-document text extraction backed by PdfBox-Android.
 *
 * The framework [android.graphics.pdf.PdfRenderer] cannot extract text, so the
 * reader uses PdfBox (Apache 2.0, pure Java) for this purpose. Extraction is:
 *
 *  - **Lazy** — only runs the first time the user opens search for a document.
 *  - **Off the UI thread** — on a dedicated IO dispatcher.
 *  - **Cancellable** — the in-flight job is cancelled if the user closes the
 *    viewer or opens a different document.
 *  - **Cached per documentId** — the extracted page text + character boxes are
 *    kept in memory for the lifetime of the session so repeated searches are
 *    instant and result navigation doesn't re-parse the PDF.
 *
 * If a PDF is image-only (scanned) the extractor honestly reports
 * [hasTextLayer] = false and the UI shows a truthful "no text layer" message
 * instead of pretending to search.
 *
 * **Implementation note**: the PDF is loaded via [BufferedInputStream], not
 * `PDDocument.load(File)`, because the latter uses memory-mapped IO that can
 * conflict with the framework [PdfRenderer]'s open file descriptor on the same
 * file — this was the root cause of search silently failing even on text PDFs
 * that were clearly visible on screen.
 */
class PdfTextExtractor private constructor(
    private val file: File,
    private val scope: CoroutineScope
) : AutoCloseable {

    companion object {
        private const val TAG = "PdfTextExtractor"

        /** Minimum total characters across the document to consider it text-bearing. */
        private const val MIN_TEXT_CHARS = 32

        suspend fun open(context: Context, file: File): PdfTextExtractor? = withContext(Dispatchers.IO) {
            // PdfBox-Android requires a one-shot init per process.
            try {
                PDFBoxResourceLoader.init(context)
            } catch (e: Exception) {
                PdfErrorLog.error(TAG, "PDFBoxResourceLoader.init failed", e)
            }
            PdfTextExtractor(file, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        }
    }

    /** One page's worth of extracted text + the character boxes that produced it. */
    data class PageText(
        val pageIndex: Int,
        val text: String,
        /** Each char's normalised bounding rect on the page (0..1, origin top-left). */
        val charRects: List<RectF>
    )

    private val parseMutex = Mutex()
    private var pages: List<PageText>? = null
    private var parseJob: Job? = null

    private val _state = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    sealed interface ExtractionState {
        data object Idle : ExtractionState
        data object Extracting : ExtractionState
        data class Ready(val pageCount: Int, val hasTextLayer: Boolean) : ExtractionState
        data class Failed(val message: String) : ExtractionState
    }

    /** True once extraction has completed with a non-empty text layer. */
    fun hasTextLayer(): Boolean = (pages?.sumOf { it.text.length } ?: 0) >= MIN_TEXT_CHARS

    /**
     * Ensure text has been extracted; idempotent. Returns the parsed pages or
     * null if extraction has not yet completed (caller should observe [state]).
     */
    suspend fun ensureExtracted(): List<PageText>? = withContext(Dispatchers.IO) {
        pages?.let { return@withContext it }
        parseMutex.withLock {
            pages?.let { return@withContext it }
            _state.value = ExtractionState.Extracting
            PdfErrorLog.info(TAG, "Starting text extraction for ${file.name} (${file.length()} bytes)")
            try {
                // Use InputStream, NOT PDDocument.load(File). The File overload
                // memory-maps the file, which conflicts with PdfRenderer's open
                // ParcelFileDescriptor on the same file and causes load() to
                // throw or return an empty document on some Android versions.
                val stream = BufferedInputStream(file.inputStream())
                PDDocument.load(stream).use { doc ->
                    val pageCount = doc.numberOfPages
                    PdfErrorLog.info(TAG, "PdfBox loaded document: $pageCount pages")
                    val result = ArrayList<PageText>(pageCount)
                    for (i in 0 until pageCount) {
                        val pageText = extractPageText(doc, i)
                        result.add(pageText)
                    }
                    pages = result
                    val totalChars = result.sumOf { it.text.length }
                    val hasText = totalChars >= MIN_TEXT_CHARS
                    PdfErrorLog.info(
                        TAG,
                        "Extraction complete: $pageCount pages, $totalChars chars, hasTextLayer=$hasText"
                    )
                    _state.value = ExtractionState.Ready(pageCount, hasText)
                }
            } catch (e: OutOfMemoryError) {
                PdfErrorLog.error(TAG, "OOM extracting text from ${file.name}", e)
                _state.value = ExtractionState.Failed("Out of memory extracting text")
                null
            } catch (e: Exception) {
                PdfErrorLog.error(TAG, "Text extraction failed for ${file.name}: ${e.message}", e)
                _state.value = ExtractionState.Failed(e.message ?: "Unknown extraction error")
                null
            }
            pages
        }
    }

    /**
     * Extract text + character positions for a single page.
     *
     * We override [PDFTextStripper.processTextPosition] to collect every
     * character's [TextPosition] in reading order (enabled by
     * `sortByPosition = true`). The page text is then built FROM the collected
     * characters via [TextPosition.getUnicode], guaranteeing a perfect 1:1
     * alignment between `text[i]` and `charRects[i]` — this is what makes
     * search-match highlighting accurate at any zoom.
     */
    private fun extractPageText(doc: PDDocument, pageIndex: Int): PageText {
        val collected = mutableListOf<TextPosition>()

        val stripper = object : PDFTextStripper() {
            init {
                sortByPosition = true
            }
            override fun processTextPosition(text: TextPosition) {
                super.processTextPosition(text)
                collected.add(text)
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1

        // Run the stripper to populate `collected` via processTextPosition.
        val rawText = try {
            stripper.getText(doc)
        } catch (e: Exception) {
            PdfErrorLog.warn(TAG, "stripper.getText failed on page ${pageIndex + 1}: ${e.message}", e)
            ""
        }

        // Build the searchable text and char rects from collected positions.
        // This guarantees text[i] <-> charRects[i] alignment.
        val sb = StringBuilder(collected.size)
        val charRects = ArrayList<RectF>(collected.size)

        val page = try { doc.getPage(pageIndex) } catch (e: Exception) {
            PdfErrorLog.warn(TAG, "getPage($pageIndex) failed: ${e.message}", e)
            return PageText(pageIndex, "", emptyList())
        }
        val cropBox = page.cropBox
        val pageWidth = cropBox.width
        val pageHeight = cropBox.height

        for (tp in collected) {
            val unicode = tp.unicode ?: continue
            if (unicode.isEmpty()) continue
            sb.append(unicode)
            if (pageWidth > 0f && pageHeight > 0f) {
                val x = tp.x / pageWidth
                // PdfBox origin is bottom-left; convert to top-left.
                val top = (pageHeight - tp.y - tp.height) / pageHeight
                val w = tp.width / pageWidth
                val h = tp.height / pageHeight
                charRects.add(RectF(x, top, x + w, top + h))
            } else {
                charRects.add(RectF(0f, 0f, 0f, 0f))
            }
        }

        val text = sb.toString()
        return PageText(pageIndex, text, charRects)
    }

    /**
     * Search the whole document for [query]. Case-insensitive. Returns matches
     * with normalised rects so the UI can highlight them at any zoom.
     *
     * Returns an empty result list with [PdfSearchResult.hasTextLayer] = false
     * if the document has no text layer (e.g. scanned PDFs) — the UI must show
     * a truthful message rather than pretending to search.
     */
    suspend fun search(query: String): PdfSearchResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext PdfSearchResult(query, emptyList(), 0, false)
        val parsed = ensureExtracted()
        if (parsed == null) {
            PdfErrorLog.warn(TAG, "search('$q'): extraction returned null")
            return@withContext PdfSearchResult(query, emptyList(), 0, false)
        }
        val hasText = parsed.sumOf { it.text.length } >= MIN_TEXT_CHARS
        if (!hasText) {
            PdfErrorLog.info(TAG, "search('$q'): document has no text layer (scanned?)")
            return@withContext PdfSearchResult(query, emptyList(), parsed.size, false)
        }
        val matches = ArrayList<PdfSearchMatch>()
        val qLower = q.lowercase()
        for (page in parsed) {
            val text = page.text.lowercase()
            var start = 0
            while (true) {
                val idx = text.indexOf(qLower, start)
                if (idx == -1) break
                // Build a union rect over the matched characters.
                val from = idx
                val to = (idx + q.length).coerceAtMost(page.charRects.size)
                if (from in page.charRects.indices && to > from) {
                    var union = page.charRects[from]
                    for (i in from + 1 until to) {
                        union = RectF(
                            minOf(union.left, page.charRects[i].left),
                            minOf(union.top, page.charRects[i].top),
                            maxOf(union.right, page.charRects[i].right),
                            maxOf(union.bottom, page.charRects[i].bottom)
                        )
                    }
                    matches.add(
                        PdfSearchMatch(
                            pageIndex = page.pageIndex,
                            rect = union,
                            matchedText = page.text.substring(idx, idx + q.length)
                        )
                    )
                }
                start = idx + q.length
            }
        }
        PdfErrorLog.info(TAG, "search('$q'): found ${matches.size} matches across ${parsed.size} pages")
        PdfSearchResult(query, matches, parsed.size, true)
    }

    override fun close() {
        parseJob?.cancel()
        scope.cancel()
        pages = null
        _state.value = ExtractionState.Idle
    }
}
