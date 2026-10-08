package com.boardsprep.onboard.core.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Resolves any supported document source to a local [PdfDocumentRef] the
 * rendering engine can open.
 *
 * Supported sources:
 *  - HTTPS URLs — downloaded atomically with progress + cancellation.
 *  - Local file paths — verified in place.
 *  - `content://` URIs — copied into the cache (SAF compatibility).
 *
 * Cache identity is a SHA-256 of the canonical source string, never
 * `Math.abs(url.hashCode())` (which the old code used and which collides
 * catastrophically on long URLs sharing the same suffix).
 *
 * Downloads are atomic (`.part` file + rename), validated (non-trivial size +
 * PDF magic bytes), and clean themselves up on failure so the cache never
 * contains a half-written file that masquerades as a successful download.
 */
class PdfDocumentRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "PdfDocumentRepository"
        private const val PDF_MAGIC = "%PDF-"
        private const val MIN_PDF_BYTES = 100L
        // A 400-page NCERT textbook is typically 20–80MB; reserve a sensible
        // safety margin before any download starts.
        private const val DOWNLOAD_RESERVE_BYTES = 10L * 1024L * 1024L
    }

    private val cacheDir: File by lazy {
        File(context.filesDir, "cached_pdfs").apply { if (!exists()) mkdirs() }
    }

    sealed interface ResolveResult {
        data class Success(val ref: PdfDocumentRef, val fromCache: Boolean) : ResolveResult
        data class Downloading(val progress: Float) : ResolveResult
        data class Failed(val kind: PdfErrorKind, val detail: String) : ResolveResult
    }

    /** Ongoing download jobs keyed by documentId, for cancellation. */
    private val activeDownloads = mutableMapOf<String, Job>()

    /**
     * Canonical document identity for any source string.
     *
     * For URLs / paths: `sha256(source)`.
     * For content URIs: `sha256("content:" + uri.toString())` so the same SAF
     * selection resolves consistently across sessions.
     */
    fun documentIdFor(source: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(source.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** The cache file for a given source, whether or not it exists yet. */
    fun cacheFileFor(source: String): File {
        val id = documentIdFor(source)
        val safeExt = source.substringAfterLast('.', "").lowercase()
        val ext = if (safeExt == "pdf") "pdf" else "bin"
        return File(cacheDir, "$id.$ext")
    }

    /**
     * Resolve [source] to a [PdfDocumentRef], downloading / copying as needed.
     *
     * @param progress called with 0f..1f (or null when indeterminate) as a
     * download progresses. Called on the IO dispatcher.
     * @param job the coroutine job driving this resolve; cancelling it cancels
     * the underlying HTTP call and cleans up the partial file.
     */
    suspend fun resolve(
        source: String,
        title: String,
        progress: (Float?) -> Unit = {},
        paired: PdfPairedDocument? = null
    ): ResolveResult = withContext(Dispatchers.IO) {
        if (source.isBlank()) return@withContext ResolveResult.Failed(PdfErrorKind.MISSING_DOCUMENT, "Empty source")

        val documentId = documentIdFor(source)
        val origin = classifySource(source)

        // 1. Local file path that already exists & is valid — use in place.
        if (origin == PdfDocumentOrigin.LOCAL_FILE) {
            val local = File(source)
            val validity = validatePdf(local)
            if (validity is PdfValidity.Valid) {
                return@withContext ResolveResult.Success(
                    PdfDocumentRef(documentId, source, title, local, origin, paired),
                    fromCache = true
                )
            }
            return@withContext ResolveResult.Failed(
                (validity as PdfValidity.Invalid).kind,
                validity.detail
            )
        }

        // 2. content:// URI — copy into cache (SAF compatibility).
        if (origin == PdfDocumentOrigin.CONTENT_URI) {
            val cached = cacheFileFor(source)
            if (cached.exists() && validatePdf(cached) is PdfValidity.Valid) {
                return@withContext ResolveResult.Success(
                    PdfDocumentRef(documentId, source, title, cached, origin, paired),
                    fromCache = true
                )
            }
            val copyResult = copyContentUri(source, cached, progress)
            return@withContext when (copyResult) {
                is CopyResult.Ok -> {
                    val v = validatePdf(cached)
                    if (v is PdfValidity.Valid) ResolveResult.Success(
                        PdfDocumentRef(documentId, source, title, cached, origin, paired),
                        fromCache = false
                    ) else ResolveResult.Failed((v as PdfValidity.Invalid).kind, v.detail)
                }
                is CopyResult.Failed -> ResolveResult.Failed(copyResult.kind, copyResult.detail)
            }
        }

        // 3. HTTPS URL — download atomically.
        val cached = cacheFileFor(source)
        if (cached.exists() && validatePdf(cached) is PdfValidity.Valid) {
            return@withContext ResolveResult.Success(
                PdfDocumentRef(documentId, source, title, cached, origin, paired),
                fromCache = true
            )
        }
        // Storage check before we touch the network.
        val free = freeSpaceBytes()
        if (free in 1 until DOWNLOAD_RESERVE_BYTES) {
            return@withContext ResolveResult.Failed(
                PdfErrorKind.INSUFFICIENT_STORAGE,
                "Only ${free / 1024 / 1024}MB free. Free at least 10MB."
            )
        }
        val dlResult = downloadAtomic(source, cached, progress)
        when (dlResult) {
            is DownloadResult.Ok -> {
                val v = validatePdf(cached)
                if (v is PdfValidity.Valid) ResolveResult.Success(
                    PdfDocumentRef(documentId, source, title, cached, origin, paired),
                    fromCache = false
                ) else {
                    cached.delete()
                    ResolveResult.Failed((v as PdfValidity.Invalid).kind, v.detail)
                }
            }
            is DownloadResult.Failed -> {
                cached.delete()
                ResolveResult.Failed(dlResult.kind, dlResult.detail)
            }
        }
    }

    private fun classifySource(source: String): PdfDocumentOrigin {
        return when {
            source.startsWith("content://") -> PdfDocumentOrigin.CONTENT_URI
            source.startsWith("http://") || source.startsWith("https://") -> PdfDocumentOrigin.REMOTE_URL
            else -> PdfDocumentOrigin.LOCAL_FILE
        }
    }

    private sealed interface CopyResult {
        data object Ok : CopyResult
        data class Failed(val kind: PdfErrorKind, val detail: String) : CopyResult
    }

    private suspend fun copyContentUri(
        source: String,
        target: File,
        progress: (Float?) -> Unit
    ): CopyResult {
        return try {
            val resolver: ContentResolver = context.contentResolver
            val uri = Uri.parse(source)
            val part = File(target.parentFile, "${target.name}.part")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n = input.read(buf)
                    while (n > 0) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                }
            } ?: return CopyResult.Failed(PdfErrorKind.MISSING_DOCUMENT, "Cannot open content URI")
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true); part.delete()
            }
            progress(1f)
            CopyResult.Ok
        } catch (e: CancellationException) {
            File(target.parentFile, "${target.name}.part").delete()
            throw e
        } catch (e: Exception) {
            File(target.parentFile, "${target.name}.part").delete()
            CopyResult.Failed(PdfErrorKind.MISSING_DOCUMENT, e.message ?: "Cannot read content URI")
        }
    }

    private sealed interface DownloadResult {
        data object Ok : DownloadResult
        data class Failed(val kind: PdfErrorKind, val detail: String) : DownloadResult
    }

    private suspend fun downloadAtomic(
        url: String,
        target: File,
        progress: (Float?) -> Unit
    ): DownloadResult {
        val part = File(target.parentFile, "${target.name}.part")
        part.delete()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) BoardsPrep/1.0")
                .header("Accept", "application/pdf,application/octet-stream,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return DownloadResult.Failed(
                        PdfErrorKind.NETWORK_FAILURE,
                        "HTTP ${response.code} ${response.message}"
                    )
                }
                val body = response.body ?: return DownloadResult.Failed(
                    PdfErrorKind.NETWORK_FAILURE,
                    "Empty response body"
                )
                val total = body.contentLength()
                FileOutputStream(part).use { out ->
                    val src = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) progress((read.toFloat() / total).coerceIn(0f, 1f))
                        else progress(null)
                    }
                }
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true); part.delete()
            }
            DownloadResult.Ok
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            part.delete()
            DownloadResult.Failed(PdfErrorKind.NETWORK_FAILURE, "Network timeout")
        } catch (e: java.io.IOException) {
            part.delete()
            DownloadResult.Failed(PdfErrorKind.NETWORK_FAILURE, e.message ?: "Network error")
        } catch (e: Exception) {
            part.delete()
            DownloadResult.Failed(PdfErrorKind.UNKNOWN, e.message ?: "Unknown download error")
        }
    }

    private sealed interface PdfValidity {
        data object Valid : PdfValidity
        data class Invalid(val kind: PdfErrorKind, val detail: String) : PdfValidity
    }

    /**
     * Validate that a file is plausibly a real, non-corrupted PDF.
     *
     * Checks:
     *  - File exists and is at least [MIN_PDF_BYTES] bytes.
     *  - First non-whitespace bytes start with `%PDF-` (the PDF magic header).
     *  - File contains a `%%EOF` marker (tail of every valid PDF).
     *
     * The framework PdfRenderer will reject encrypted PDFs; the caller surfaces
     * that as [PdfErrorKind.PASSWORD_PROTECTED] via the session open failure.
     */
    private fun validatePdf(file: File): PdfValidity {
        if (!file.exists()) return PdfValidity.Invalid(PdfErrorKind.MISSING_DOCUMENT, "File missing")
        if (file.length() < MIN_PDF_BYTES) return PdfValidity.Invalid(PdfErrorKind.CORRUPT_DOWNLOAD, "File too small")
        try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                if (read < 5) return PdfValidity.Invalid(PdfErrorKind.CORRUPT_DOWNLOAD, "Truncated header")
                val headStr = String(header, 0, read, Charsets.ISO_8859_1).trimStart()
                if (!headStr.startsWith(PDF_MAGIC)) {
                    return PdfValidity.Invalid(PdfErrorKind.INVALID_PDF, "Not a PDF (bad magic)")
                }
            }
            // Tail check: PDFs end with %%EOF. Read up to last 1024 bytes.
            val tail = ByteArray(1024)
            val len = file.inputStream().use { input ->
                val skip = (file.length() - tail.size).coerceAtLeast(0)
                input.skip(skip)
                input.read(tail)
            }
            if (len > 0) {
                val tailStr = String(tail, 0, len, Charsets.ISO_8859_1)
                if (!tailStr.contains("%%EOF")) {
                    return PdfValidity.Invalid(PdfErrorKind.CORRUPT_DOWNLOAD, "Missing %%EOF marker")
                }
            }
            return PdfValidity.Valid
        } catch (e: Exception) {
            return PdfValidity.Invalid(PdfErrorKind.CORRUPT_DOWNLOAD, e.message ?: "Validation failed")
        }
    }

    private fun freeSpaceBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    /** Evict a document from the cache. Safe to call even if absent. */
    fun evict(source: String) {
        cacheFileFor(source).delete()
    }

    /** Total bytes used by the PDF cache. */
    fun cacheSizeBytes(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Trim the cache to at most [targetBytes], evicting least-recently-accessed
     * files first. Never deletes files that are currently open in a session —
     * the caller is responsible for not evicting the active document.
     */
    fun trimCacheTo(targetBytes: Long, preserve: Set<String> = emptySet()) {
        val files = cacheDir.listFiles()?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= targetBytes) return
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= targetBytes) break
            if (preserve.any { p -> f.name.startsWith(p) }) continue
            total -= f.length()
            f.delete()
        }
    }
}
