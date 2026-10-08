package com.boardsprep.onboard.core.pdf

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PdfPageCache] LRU behaviour.
 *
 * The cache wraps `androidx.collection.LruCache`, which is pure-Java, but the
 * cached values are [PdfPageBitmap]s containing real [Bitmap]s, so we need
 * Robolectric to get a working Bitmap implementation.
 *
 * All bitmaps used here are 10x10 ARGB_8888 = 400 bytes each, which makes the
 * eviction arithmetic predictable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfPageCacheTest {

    private val pageSize = PdfPageSize(612, 792)

    private fun makePage(documentId: String, pageIndex: Int): PdfPageBitmap {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        return PdfPageBitmap(
            pageIndex = pageIndex,
            bitmap = bitmap,
            renderedWidthPx = 700,
            renderedHeightPx = 700,
            pageSize = pageSize,
            renderToken = 0L
        )
    }

    // -----------------------------------------------------------------------
    // put / get
    // -----------------------------------------------------------------------

    @Test
    fun putThenGetReturnsSameBitmap() {
        val cache = PdfPageCache(maxSizeKib = 8)
        val page = makePage("doc1", 0)
        cache.put("doc1", page, targetWidthPx = 700)

        val retrieved = cache.get("doc1", 0, 700)
        assertNotNull(retrieved)
        // Same instance — the cache stores by reference.
        assertTrue("get must return the exact instance that was put", retrieved === page)
    }

    @Test
    fun getReturnsNullForAbsentKey() {
        val cache = PdfPageCache(maxSizeKib = 8)
        assertNull(cache.get("doc1", 0, 700))
    }

    @Test
    fun distinctKeysAreDistinctEntries() {
        // The cache key is (documentId, pageIndex, targetWidthPx) — so the same
        // page rendered at two different widths produces two entries.
        val cache = PdfPageCache(maxSizeKib = 8)
        val w700 = makePage("doc1", 0)
        val w1400 = makePage("doc1", 0)
        cache.put("doc1", w700, targetWidthPx = 700)
        cache.put("doc1", w1400, targetWidthPx = 1400)

        assertTrue(cache.get("doc1", 0, 700) === w700)
        assertTrue(cache.get("doc1", 0, 1400) === w1400)
    }

    // -----------------------------------------------------------------------
    // LRU eviction
    // -----------------------------------------------------------------------

    @Test
    fun evictionRemovesLeastRecentlyUsedEntry() {
        // maxSizeKib = 1 -> 1024 bytes. Each bitmap = 400 bytes.
        // 2 entries fit (800 bytes); a 3rd pushes total to 1200 > 1024, evicting
        // the LRU entry.
        val cache = PdfPageCache(maxSizeKib = 1)
        val p0 = makePage("doc1", 0)
        val p1 = makePage("doc1", 1)
        val p2 = makePage("doc1", 2)

        cache.put("doc1", p0, 700) // size = 400
        cache.put("doc1", p1, 700) // size = 800
        cache.put("doc1", p2, 700) // size = 1200 -> evict LRU (p0) -> size = 800

        assertNull("LRU entry p0 must have been evicted", cache.get("doc1", 0, 700))
        assertNotNull("p1 must still be cached", cache.get("doc1", 1, 700))
        assertNotNull("p2 must still be cached", cache.get("doc1", 2, 700))
    }

    @Test
    fun getUpdatesRecencyAndProtectsEntryFromEviction() {
        // p0 is accessed (get) after p1 is inserted, so when p2 is inserted,
        // p1 — not p0 — is the LRU and gets evicted.
        val cache = PdfPageCache(maxSizeKib = 1)
        val p0 = makePage("doc1", 0)
        val p1 = makePage("doc1", 1)
        val p2 = makePage("doc1", 2)

        cache.put("doc1", p0, 700) // size = 400
        cache.put("doc1", p1, 700) // size = 800
        // Touch p0 so it becomes the most-recently-used.
        assertNotNull(cache.get("doc1", 0, 700))
        cache.put("doc1", p2, 700) // size = 1200 -> evict LRU (p1)

        assertNotNull("p0 was touched; must survive", cache.get("doc1", 0, 700))
        assertNull("p1 must be the LRU and get evicted", cache.get("doc1", 1, 700))
        assertNotNull("p2 must be cached", cache.get("doc1", 2, 700))
    }

    // -----------------------------------------------------------------------
    // evictDocument
    // -----------------------------------------------------------------------

    @Test
    fun evictDocumentRemovesAllEntriesForThatDocument() {
        val cache = PdfPageCache(maxSizeKib = 8)
        cache.put("doc1", makePage("doc1", 0), 700)
        cache.put("doc1", makePage("doc1", 1), 700)
        cache.put("doc1", makePage("doc1", 2), 700)

        cache.evictDocument("doc1")

        assertNull(cache.get("doc1", 0, 700))
        assertNull(cache.get("doc1", 1, 700))
        assertNull(cache.get("doc1", 2, 700))
        assertEquals(0, cache.sizeBytes())
    }

    @Test
    fun evictDocumentLeavesOtherDocumentsIntact() {
        val cache = PdfPageCache(maxSizeKib = 8)
        val d1p0 = makePage("doc1", 0)
        val d2p0 = makePage("doc2", 0)
        val d2p1 = makePage("doc2", 1)
        cache.put("doc1", d1p0, 700)
        cache.put("doc2", d2p0, 700)
        cache.put("doc2", d2p1, 700)

        cache.evictDocument("doc1")

        assertNull(cache.get("doc1", 0, 700))
        assertTrue("doc2 entries must be untouched", cache.get("doc2", 0, 700) === d2p0)
        assertTrue(cache.get("doc2", 1, 700) === d2p1)
    }

    @Test
    fun evictDocumentForUnknownDocumentIsNoOp() {
        val cache = PdfPageCache(maxSizeKib = 8)
        val page = makePage("doc1", 0)
        cache.put("doc1", page, 700)

        cache.evictDocument("no-such-doc")

        assertTrue(cache.get("doc1", 0, 700) === page)
    }

    // -----------------------------------------------------------------------
    // trimToSize
    // -----------------------------------------------------------------------

    @Test
    fun trimToSizeReducesCacheToApproximateFraction() {
        // 8 KiB cap lets us fit 5 bitmaps comfortably (5*400 = 2000 bytes).
        val cache = PdfPageCache(maxSizeKib = 8)
        for (i in 0 until 5) {
            cache.put("doc1", makePage("doc1", i), 700)
        }
        val sizeBefore = cache.sizeBytes()
        assertEquals(2000, sizeBefore)

        cache.trimToSize(0.5f) // target = 1000 bytes

        val sizeAfter = cache.sizeBytes()
        assertTrue(
            "size after trim ($sizeAfter) must be <= target 1000",
            sizeAfter <= 1000
        )
        assertTrue(
            "size after trim ($sizeAfter) must still be > 0",
            sizeAfter > 0
        )
    }

    @Test
    fun trimToSizeWithFractionZeroEmptiesCache() {
        val cache = PdfPageCache(maxSizeKib = 8)
        cache.put("doc1", makePage("doc1", 0), 700)
        cache.put("doc1", makePage("doc1", 1), 700)

        cache.trimToSize(0f)

        assertEquals(0, cache.sizeBytes())
    }

    // -----------------------------------------------------------------------
    // evictAll
    // -----------------------------------------------------------------------

    @Test
    fun evictAllEmptiesTheCache() {
        val cache = PdfPageCache(maxSizeKib = 8)
        cache.put("doc1", makePage("doc1", 0), 700)
        cache.put("doc1", makePage("doc1", 1), 700)
        cache.put("doc2", makePage("doc2", 0), 700)
        assertTrue("cache should have entries before evictAll", cache.sizeBytes() > 0)

        cache.evictAll()

        assertEquals(0, cache.sizeBytes())
        assertNull(cache.get("doc1", 0, 700))
        assertNull(cache.get("doc1", 1, 700))
        assertNull(cache.get("doc2", 0, 700))
    }

    // -----------------------------------------------------------------------
    // budgetKib
    // -----------------------------------------------------------------------

    @Test
    fun budgetKibReturnsValueInDocumentedRange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val budget = PdfPageCache.budgetKib(context)
        assertTrue(
            "budget ($budget KiB) must be >= 8192 KiB (8 MiB floor)",
            budget >= 8192
        )
        assertTrue(
            "budget ($budget KiB) must be <= 49152 KiB (48 MiB ceiling)",
            budget <= 49152
        )
    }
}
