package com.boardsprep.onboard.core.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns a single [PdfRenderer] + [ParcelFileDescriptor] for the lifetime of a
 * document.
 *
 * **All catch blocks catch [Throwable]** (not just [Exception]) because
 * [OutOfMemoryError], [StackOverflowError] and native crashes surface as
 * [Error] subtypes that `catch (Exception)` silently lets through — this was
 * the root cause of the crash-on-open bug.
 *
 * Page sizes are computed lazily (cached on first access), NOT pre-computed
 * in a loop. The open() method only reads [PdfRenderer.pageCount] (O(1)).
 *
 * Bitmap memory is capped: the product of width × height is clamped to
 * [MAX_BITMAP_PIXELS] to prevent OOM on large pages at high zoom.
 */
class PdfDocumentSession private constructor(
    private val file: File,
    private val scope: CoroutineScope
) : AutoCloseable {

    companion object {
        private const val TAG = "PdfDocumentSession"

        /** Maximum bitmap width in pixels. */
        private const val MAX_BITMAP_WIDTH = 2000

        /** Maximum bitmap height in pixels. */
        private const val MAX_BITMAP_HEIGHT = 2800

        /** Maximum total pixels (width × height) to prevent OOM. */
        private const val MAX_BITMAP_PIXELS = 4_500_000 // ~18MB at ARGB_8888

        suspend fun open(file: File): PdfDocumentSession? = withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() < 100) {
                PdfErrorLog.warn(TAG, "open: file missing or too small: ${file.absolutePath} (${file.length()} bytes)")
                return@withContext null
            }
            PdfErrorLog.info(TAG, "open: opening PFD for ${file.name} (${file.length()} bytes)")
            val pfd = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (t: Throwable) {
                PdfErrorLog.error(TAG, "Failed to open ParcelFileDescriptor for ${file.name}", t)
                return@withContext null
            }
            PdfErrorLog.info(TAG, "open: creating PdfRenderer")
            val renderer = try {
                PdfRenderer(pfd)
            } catch (t: Throwable) {
                PdfErrorLog.error(TAG, "PdfRenderer failed to open ${file.name}: ${t.message}", t)
                try { pfd.close() } catch (_: Throwable) {}
                return@withContext null
            }
            val session = PdfDocumentSession(file, CoroutineScope(SupervisorJob() + Dispatchers.IO))
            session.pfd = pfd
            session.renderer = renderer
            session.pageCountValue = renderer.pageCount
            PdfErrorLog.info(TAG, "Session opened: ${file.name}, ${renderer.pageCount} pages")
            session
        }
    }

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageCountValue: Int = 0
    private val pageSizeCache = ConcurrentHashMap<Int, PdfPageSize>()
    private val renderMutex = Mutex()
    private val tokenCounter = AtomicLong(0L)

    private val _state = MutableStateFlow<PdfLoadState>(PdfLoadState.Opening)
    val state: StateFlow<PdfLoadState> = _state.asStateFlow()

    val pageCount: Int get() = pageCountValue

    fun getCachedPageSize(index: Int): PdfPageSize? = pageSizeCache[index]

    suspend fun pageSize(index: Int): PdfPageSize? {
        if (index !in 0 until pageCountValue) return null
        pageSizeCache[index]?.let { return it }
        return renderMutex.withLock {
            pageSizeCache[index]?.let { return@withLock it }
            val r = renderer ?: return@withLock null
            try {
                r.openPage(index).use { page ->
                    val size = PdfPageSize(page.width, page.height)
                    pageSizeCache[index] = size
                    size
                }
            } catch (t: Throwable) {
                PdfErrorLog.warn(TAG, "pageSize($index) failed: ${t.message}", t)
                null
            }
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        renderMode: Int = PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
    ): PdfPageBitmap? {
        if (pageIndex !in 0 until pageCountValue) return null
        val token = tokenCounter.incrementAndGet()
        return renderMutex.withLock {
            val liveRenderer = renderer ?: return@withLock null
            if (pageIndex !in 0 until pageCountValue) return@withLock null
            try {
                liveRenderer.openPage(pageIndex).use { page ->
                    val size = PdfPageSize(page.width, page.height)
                    pageSizeCache[pageIndex] = size
                    val aspect = size.aspectRatio

                    // Clamp width, then clamp height, then re-clamp to the
                    // pixel budget so we never allocate a bitmap that causes OOM.
                    var width = targetWidthPx.coerceIn(360, MAX_BITMAP_WIDTH)
                    var height = (width * aspect).toInt().coerceAtLeast(1)
                    if (height > MAX_BITMAP_HEIGHT) {
                        height = MAX_BITMAP_HEIGHT
                        width = (height / aspect).toInt().coerceAtLeast(1)
                    }
                    val pixels = width.toLong() * height.toLong()
                    if (pixels > MAX_BITMAP_PIXELS) {
                        val scale = Math.sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels.toDouble()).toFloat()
                        width = (width * scale).toInt().coerceAtLeast(1)
                        height = (height * scale).toInt().coerceAtLeast(1)
                        PdfErrorLog.info(TAG, "renderPage(${pageIndex + 1}): scaled bitmap to ${width}x${height} to fit memory budget")
                    }

                    PdfErrorLog.info(TAG, "renderPage(${pageIndex + 1}): rendering at ${width}x${height}")
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, renderMode)
                    PdfPageBitmap(
                        pageIndex = pageIndex,
                        bitmap = bitmap,
                        renderedWidthPx = width,
                        renderedHeightPx = height,
                        pageSize = size,
                        renderToken = token
                    )
                }
            } catch (t: OutOfMemoryError) {
                PdfErrorLog.error(TAG, "OOM rendering page ${pageIndex + 1} at width $targetWidthPx (bitmap would be too large)", t)
                _state.value = PdfLoadState.Error(PdfErrorKind.RENDER_ERROR, "Out of memory rendering page ${pageIndex + 1}")
                null
            } catch (ce: CancellationException) {
                // Normal coroutine cancellation (e.g. LazyColumn item scrolled
                // off-screen). Must re-throw so the coroutine is properly
                // cancelled — do NOT log or swallow.
                throw ce
            } catch (t: Throwable) {
                PdfErrorLog.error(TAG, "Failed to render page ${pageIndex + 1}: ${t.message}", t)
                null
            }
        }
    }

    fun currentToken(): Long = tokenCounter.get()

    fun markReady() {
        _state.value = PdfLoadState.Ready(pageCountValue)
    }

    fun publishError(kind: PdfErrorKind, detail: String) {
        _state.value = PdfLoadState.Error(kind, detail)
    }

    override fun close() {
        scope.cancel()
        try { renderer?.close() } catch (_: Throwable) {}
        try { pfd?.close() } catch (_: Throwable) {}
        renderer = null
        pfd = null
        pageSizeCache.clear()
        pageCountValue = 0
        _state.value = PdfLoadState.Idle
    }
}
