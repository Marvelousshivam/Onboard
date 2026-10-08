package com.boardsprep.onboard.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * A focused, framework-free test of the stale-render-result-discarding contract
 * documented in [PdfDocumentSession].
 *
 * [PdfDocumentSession] issues a monotonically increasing `renderToken` for
 * every `renderPage` call via an internal `AtomicLong tokenCounter`. When the
 * user navigates quickly, an in-flight request whose result arrives after a
 * newer request was issued must be discarded — otherwise the user would briefly
 * see a page they already swiped past.
 *
 * The consumer pattern (mirrored from `PdfReaderState`) is:
 *
 * ```
 * val result = session.renderPage(...)
 * if (result.renderToken < session.currentToken()) return  // stale, discard
 * // ... apply result
 * ```
 *
 * [PdfDocumentSession] needs a real PDF file to instantiate, so this test
 * mirrors the `tokenCounter` discipline with an [AtomicLong] and verifies the
 * ordering contract directly. This documents and pins the contract without
 * needing a real PDF.
 */
class PdfRenderTokenOrderingTest {

    /**
     * Minimal stand-in for [PdfDocumentSession]'s token bookkeeping.
     * Mirrors `private val tokenCounter = AtomicLong(0L)` and
     * `fun currentToken(): Long = tokenCounter.get()`.
     */
    private class TokenSession {
        private val counter = AtomicLong(0L)
        fun nextToken(): Long = counter.incrementAndGet()
        fun currentToken(): Long = counter.get()
    }

    /** Stand-in for a [PdfPageBitmap] carrying its token. */
    private data class RenderResult(val renderToken: Long, val pageIndex: Int)

    /** Mirrors the consumer's stale-check predicate. */
    private fun isStale(result: RenderResult, currentToken: Long): Boolean =
        result.renderToken < currentToken

    @Test
    fun staleResultIsDiscardedWhenNewerTokenIssued() {
        val session = TokenSession()

        // Two render requests are issued in order: T1 then T2 (T2 > T1).
        val t1 = session.nextToken()
        val t2 = session.nextToken()
        assertTrue("tokens must be strictly monotonic", t2 > t1)

        // By the time the results arrive, the session's current token has
        // advanced to T2 (the latest request issued).
        val currentToken = session.currentToken()
        assertEquals(t2, currentToken)

        val lateResultForT1 = RenderResult(renderToken = t1, pageIndex = 0)
        val freshResultForT2 = RenderResult(renderToken = t2, pageIndex = 0)

        assertTrue(
            "T1 result must be discarded because its token < currentToken",
            isStale(lateResultForT1, currentToken)
        )
        assertFalse(
            "T2 result must be accepted because its token == currentToken (not stale)",
            isStale(freshResultForT2, currentToken)
        )
    }

    @Test
    fun freshResultIsAcceptedWhenNoNewerRequestWasIssued() {
        val session = TokenSession()
        val t = session.nextToken()
        val currentToken = session.currentToken()

        assertEquals("token matches current right after issuance", t, currentToken)
        assertFalse(
            "result with token == currentToken must NOT be discarded",
            isStale(RenderResult(t, 0), currentToken)
        )
    }

    @Test
    fun tokensAreMonotonicallyIncreasingAcrossRequests() {
        val session = TokenSession()
        val ts = (1..10).map { session.nextToken() }
        // Each token must be strictly greater than the previous one.
        for (i in 1 until ts.size) {
            assertTrue(
                "token at step $i (${ts[i]}) must be > token at step ${i - 1} (${ts[i - 1]})",
                ts[i] > ts[i - 1]
            )
        }
        // currentToken reflects the latest issued token.
        assertEquals(ts.last(), session.currentToken())
    }

    @Test
    fun staleCheckIsFalseWhenResultArrivesInOrder() {
        // Simulates normal (non-racing) navigation: each result arrives before
        // the next request is issued, so no result is ever stale.
        val session = TokenSession()

        val t1 = session.nextToken()
        // T1 result arrives before T2 is issued.
        assertFalse(isStale(RenderResult(t1, 0), session.currentToken()))

        val t2 = session.nextToken()
        assertFalse(isStale(RenderResult(t2, 0), session.currentToken()))
    }
}
