package com.boardsprep.onboard.core.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.collection.LruCache

/**
 * Bounded LRU cache of rendered PDF page bitmaps.
 *
 * The cache budget is derived from the device's per-process memory class so
 * that the reader never causes a low-end device to thrash or OOM. We target a
 * conservative fraction (~20%) of the available heap, capped to a sensible
 * range, and we eagerly release bitmaps on trim.
 *
 * Pages are keyed by (documentId, pageIndex, targetWidthPx). The target width
 * is part of the key so that re-renders at a higher zoom (sharper) don't
 * collide with cached lower-zoom copies — the older entry simply evicts.
 */
class PdfPageCache(maxSizeKib: Int) {

    private val cache: LruCache<PageKey, PdfPageBitmap> = object : LruCache<PageKey, PdfPageBitmap>(maxSizeKib * 1024) {
        override fun sizeOf(key: PageKey, value: PdfPageBitmap): Int {
            return value.bitmap.byteCount.coerceAtLeast(1)
        }

        // IMPORTANT: Do NOT recycle bitmaps here.
        //
        // The previous implementation called oldValue.bitmap.recycle() on
        // eviction, which caused "Canvas: trying to use a recycled bitmap"
        // crashes. This happens because Compose's Image composable holds a
        // reference to the bitmap via asImageBitmap(), and the LRU can evict
        // + recycle a bitmap while a composable still references it (e.g.
        // during fast scroll in the continuous canvas). The GC is perfectly
        // capable of reclaiming bitmap memory without explicit recycling;
        // recycling is an optimization that is not safe when UI layers hold
        // direct references.
    }

    private data class PageKey(val documentId: String, val pageIndex: Int, val targetWidthPx: Int)

    fun get(documentId: String, pageIndex: Int, targetWidthPx: Int): PdfPageBitmap? =
        cache.get(PageKey(documentId, pageIndex, targetWidthPx))

    fun put(documentId: String, page: PdfPageBitmap, targetWidthPx: Int) {
        cache.put(PageKey(documentId, page.pageIndex, targetWidthPx), page)
    }

    fun remove(documentId: String, pageIndex: Int, targetWidthPx: Int) {
        cache.remove(PageKey(documentId, pageIndex, targetWidthPx))
    }

    /** Drop all cached bitmaps for a document (e.g. when the viewer closes). */
    fun evictDocument(documentId: String) {
        // Snapshot then remove to avoid CME.
        val keys = cache.snapshot().keys.filter { it.documentId == documentId }
        for (k in keys) cache.remove(k)
    }

    /** Trim the cache in response to memory pressure. */
    fun trimToSize(fraction: Float) {
        val target = (cache.size() * fraction.coerceIn(0f, 1f)).toInt()
        cache.trimToSize(target)
    }

    fun evictAll() {
        cache.evictAll()
    }

    /** Current cache size in bytes. */
    fun sizeBytes(): Int = cache.size()

    /** Maximum cache size in bytes. */
    fun maxSizeBytes(): Int = cache.maxSize()

    companion object {
        private const val TAG = "PdfPageCache"

        /**
         * Compute a cache budget suited to the device's per-process heap.
         *
         * Low-end devices (96–128MB class) get ~12MB, mid-range gets ~24MB,
         * flagship gets ~48MB, all hard-capped to avoid pathological cases.
         */
        fun budgetKib(context: Context): Int {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memClass = am?.memoryClass ?: 96
            // ~18% of heap, in KiB, clamped to [8192, 49152].
            val budget = (memClass * 1024 * 0.18).toInt()
            return budget.coerceIn(8192, 49152)
        }

        /**
         * React to a system trim callback. The reader registers a component
         * callbacks listener that forwards here so cached bitmaps are released
         * before the OS kills the process.
         */
        fun onTrimMemory(cache: PdfPageCache, level: Int) {
            when (level) {
                in 1..9 -> {
                    // Light pressure: drop prefetch / older half.
                    cache.trimToSize(0.5f)
                }
                in 10..13 -> {
                    // Moderate: keep only the currently visible page-ish.
                    cache.trimToSize(0.25f)
                }
                else -> {
                    // Heavy: drop everything.
                    Log.d(TAG, "Heavy memory pressure ($level); evicting all cached pages")
                    cache.evictAll()
                }
            }
        }
    }
}
