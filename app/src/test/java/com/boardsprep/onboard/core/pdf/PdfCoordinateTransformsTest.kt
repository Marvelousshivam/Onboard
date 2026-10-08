package com.boardsprep.onboard.core.pdf

import android.graphics.PointF
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the pure coordinate transforms in [PdfCoordinateTransforms].
 *
 * These functions are pure (no Android framework state beyond the lightweight
 * [RectF] / [PointF] data holders), so they exercise the real implementation
 * directly. Robolectric is used only because [RectF] / [PointF] are Android
 * framework classes whose default stubs return 0f for every field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfCoordinateTransformsTest {

    // -----------------------------------------------------------------------
    // clampPageIndex
    // -----------------------------------------------------------------------

    @Test
    fun clampPageIndex_returnsZeroForEmptyDocument() {
        assertEquals(0, PdfCoordinateTransforms.clampPageIndex(5, 0))
    }

    @Test
    fun clampPageIndex_returnsZeroForNegativePageCount() {
        assertEquals(0, PdfCoordinateTransforms.clampPageIndex(5, -3))
    }

    @Test
    fun clampPageIndex_clampsNegativeIndexToZero() {
        assertEquals(0, PdfCoordinateTransforms.clampPageIndex(-1, 10))
    }

    @Test
    fun clampPageIndex_clampsOverflowToLastPage() {
        assertEquals(9, PdfCoordinateTransforms.clampPageIndex(20, 10))
    }

    @Test
    fun clampPageIndex_returnsIndexWhenInRange() {
        assertEquals(5, PdfCoordinateTransforms.clampPageIndex(5, 10))
    }

    @Test
    fun clampPageIndex_returnsZeroForZeroIndexInSinglePageDoc() {
        assertEquals(0, PdfCoordinateTransforms.clampPageIndex(0, 1))
    }

    // -----------------------------------------------------------------------
    // isValidPageIndex
    // -----------------------------------------------------------------------

    @Test
    fun isValidPageIndex_returnsTrueForValidIndex() {
        assertTrue(PdfCoordinateTransforms.isValidPageIndex(5, 10))
    }

    @Test
    fun isValidPageIndex_returnsTrueForZeroIndex() {
        assertTrue(PdfCoordinateTransforms.isValidPageIndex(0, 10))
    }

    @Test
    fun isValidPageIndex_returnsTrueForLastIndex() {
        assertTrue(PdfCoordinateTransforms.isValidPageIndex(9, 10))
    }

    @Test
    fun isValidPageIndex_returnsFalseForNegativeIndex() {
        assertFalse(PdfCoordinateTransforms.isValidPageIndex(-1, 10))
    }

    @Test
    fun isValidPageIndex_returnsFalseForOverflowIndex() {
        assertFalse(PdfCoordinateTransforms.isValidPageIndex(10, 10))
    }

    @Test
    fun isValidPageIndex_returnsFalseForEmptyDocument() {
        assertFalse(PdfCoordinateTransforms.isValidPageIndex(0, 0))
    }

    @Test
    fun isValidPageIndex_returnsFalseForNegativePageCount() {
        assertFalse(PdfCoordinateTransforms.isValidPageIndex(0, -1))
    }

    // -----------------------------------------------------------------------
    // computeRenderTargetWidth
    // -----------------------------------------------------------------------

    @Test
    fun computeRenderTargetWidth_returns1080ForZeroViewport() {
        assertEquals(1080, PdfCoordinateTransforms.computeRenderTargetWidth(0, 1f))
    }

    @Test
    fun computeRenderTargetWidth_returns1080ForNegativeViewport() {
        assertEquals(1080, PdfCoordinateTransforms.computeRenderTargetWidth(-100, 1f))
    }

    @Test
    fun computeRenderTargetWidth_clampsToMin700() {
        // 500 * 1f = 500 -> clamped up to 700.
        assertEquals(700, PdfCoordinateTransforms.computeRenderTargetWidth(500, 1f))
    }

    @Test
    fun computeRenderTargetWidth_clampsToDefaultMax() {
        // 10000 * 1f = 10000 -> clamped down to default max of 2200.
        assertEquals(2200, PdfCoordinateTransforms.computeRenderTargetWidth(10000, 1f))
    }

    @Test
    fun computeRenderTargetWidth_clampsToCustomMax() {
        assertEquals(1500, PdfCoordinateTransforms.computeRenderTargetWidth(10000, 1f, 1500))
    }

    @Test
    fun computeRenderTargetWidth_scalesWithZoom() {
        // 800 * 2f = 1600, which is within [700, 2200].
        assertEquals(1600, PdfCoordinateTransforms.computeRenderTargetWidth(800, 2f))
    }

    @Test
    fun computeRenderTargetWidth_treatsZoomBelow1As1() {
        // zoom coerced to at least 1f, so 800 * 1f = 800.
        assertEquals(800, PdfCoordinateTransforms.computeRenderTargetWidth(800, 0.25f))
    }

    @Test
    fun computeRenderTargetWidth_zoomClampsAtMax() {
        // 800 * 10f = 8000 -> clamped to 2200.
        assertEquals(2200, PdfCoordinateTransforms.computeRenderTargetWidth(800, 10f))
    }

    // -----------------------------------------------------------------------
    // computeRenderedHeight
    // -----------------------------------------------------------------------

    @Test
    fun computeRenderedHeight_correctProduct() {
        assertEquals(1200, PdfCoordinateTransforms.computeRenderedHeight(800, 1.5f))
    }

    @Test
    fun computeRenderedHeight_returnsWidthWhenAspectRatioZero() {
        assertEquals(800, PdfCoordinateTransforms.computeRenderedHeight(800, 0f))
    }

    @Test
    fun computeRenderedHeight_returnsWidthWhenAspectRatioNegative() {
        assertEquals(800, PdfCoordinateTransforms.computeRenderedHeight(800, -2f))
    }

    @Test
    fun computeRenderedHeight_returnsAtLeast1() {
        // 1 * 0.001f = 0.001 -> toInt() = 0 -> coerced up to 1.
        assertEquals(1, PdfCoordinateTransforms.computeRenderedHeight(1, 0.001f))
    }

    // -----------------------------------------------------------------------
    // pageRectToNormalised / normalisedRectToPage round-trip
    // -----------------------------------------------------------------------

    @Test
    fun pageRectToNormalised_normalisesCorrectly() {
        // US-letter page (612x792 pts); a 100x100 rect at (50, 50).
        val r = RectF(50f, 50f, 150f, 150f)
        val n = PdfCoordinateTransforms.pageRectToNormalised(r, 612f, 792f)
        assertEquals(50f / 612f, n.left, 1e-5f)
        assertEquals(50f / 792f, n.top, 1e-5f)
        assertEquals(150f / 612f, n.right, 1e-5f)
        assertEquals(150f / 792f, n.bottom, 1e-5f)
    }

    @Test
    fun normalisedRectToPage_scalesCorrectly() {
        val n = RectF(0.25f, 0.5f, 0.75f, 0.9f)
        val p = PdfCoordinateTransforms.normalisedRectToPage(n, 612f, 792f)
        assertEquals(0.25f * 612f, p.left, 1e-3f)
        assertEquals(0.5f * 792f, p.top, 1e-3f)
        assertEquals(0.75f * 612f, p.right, 1e-3f)
        assertEquals(0.9f * 792f, p.bottom, 1e-3f)
    }

    @Test
    fun pageRectToNormalised_roundTripsThroughNormalisedRectToPage() {
        val original = RectF(50f, 80f, 300f, 600f)
        val normalised = PdfCoordinateTransforms.pageRectToNormalised(original, 612f, 792f)
        val restored = PdfCoordinateTransforms.normalisedRectToPage(normalised, 612f, 792f)
        assertEquals(original.left, restored.left, 1e-2f)
        assertEquals(original.top, restored.top, 1e-2f)
        assertEquals(original.right, restored.right, 1e-2f)
        assertEquals(original.bottom, restored.bottom, 1e-2f)
    }

    @Test
    fun pageRectToNormalised_returnsEmptyForZeroSizePage() {
        val r = RectF(10f, 10f, 50f, 50f)
        val n = PdfCoordinateTransforms.pageRectToNormalised(r, 0f, 0f)
        assertEquals(0f, n.left, 0f)
        assertEquals(0f, n.top, 0f)
        assertEquals(0f, n.right, 0f)
        assertEquals(0f, n.bottom, 0f)
    }

    @Test
    fun pageRectToNormalised_returnsEmptyForNegativeSizePage() {
        val r = RectF(10f, 10f, 50f, 50f)
        val n = PdfCoordinateTransforms.pageRectToNormalised(r, -10f, 100f)
        assertEquals(0f, n.width(), 0f)
        assertEquals(0f, n.height(), 0f)
    }

    // -----------------------------------------------------------------------
    // Page-space Y flip correctness (verified NO flip — top stays at top)
    // -----------------------------------------------------------------------

    @Test
    fun pageRectToNormalised_doesNotFlipYAxis() {
        // Page-space origin is top-left, +Y down — same as normalised space.
        // A rect near the TOP of the page must remain near the TOP after
        // normalisation (i.e. top < bottom, both small in normalised coords).
        val topRect = RectF(50f, 30f, 200f, 60f) // top=30, bottom=60 (near top of 792pt page)
        val n = PdfCoordinateTransforms.pageRectToNormalised(topRect, 612f, 792f)
        assertTrue("normalised top must be < normalised bottom (no Y flip)", n.top < n.bottom)
        assertTrue(
            "rect near top of page must remain near top in normalised space",
            n.top < 0.1f && n.bottom < 0.1f
        )
    }

    // -----------------------------------------------------------------------
    // normalisedToScreen / screenToNormalised round-trip
    // -----------------------------------------------------------------------

    @Test
    fun normalisedToScreen_scalesPointCorrectly() {
        val p = PointF(0.5f, 0.25f)
        val s = PdfCoordinateTransforms.normalisedToScreen(p, 1000f, 800f)
        assertEquals(500f, s.x, 1e-3f)
        assertEquals(200f, s.y, 1e-3f)
    }

    @Test
    fun screenToNormalised_roundTripsThroughNormalisedToScreen() {
        val original = PointF(0.3f, 0.7f)
        val screen = PdfCoordinateTransforms.normalisedToScreen(original, 1000f, 800f)
        val restored = PdfCoordinateTransforms.screenToNormalised(screen, 1000f, 800f)
        assertEquals(original.x, restored.x, 1e-4f)
        assertEquals(original.y, restored.y, 1e-4f)
    }

    @Test
    fun screenToNormalised_clampsToZeroToOne() {
        // Point well outside the page (negative and >1).
        val s = PointF(-200f, 5000f)
        val n = PdfCoordinateTransforms.screenToNormalised(s, 1000f, 800f)
        assertEquals(0f, n.x, 1e-6f)
        assertEquals(1f, n.y, 1e-6f)
    }

    @Test
    fun screenToNormalised_returnsOriginForZeroRenderedSize() {
        val s = PointF(100f, 100f)
        val n = PdfCoordinateTransforms.screenToNormalised(s, 0f, 0f)
        assertEquals(0f, n.x, 0f)
        assertEquals(0f, n.y, 0f)
    }

    // -----------------------------------------------------------------------
    // normalisedRectToScreen / screenRectToNormalised round-trip
    // -----------------------------------------------------------------------

    @Test
    fun normalisedRectToScreen_roundTripsThroughScreenRectToNormalised() {
        val original = RectF(0.1f, 0.2f, 0.6f, 0.8f)
        val screen = PdfCoordinateTransforms.normalisedRectToScreen(original, 1000f, 800f)
        val restored = PdfCoordinateTransforms.screenRectToNormalised(screen, 1000f, 800f)
        assertEquals(original.left, restored.left, 1e-3f)
        assertEquals(original.top, restored.top, 1e-3f)
        assertEquals(original.right, restored.right, 1e-3f)
        assertEquals(original.bottom, restored.bottom, 1e-3f)
    }

    @Test
    fun screenRectToNormalised_clampsToZeroToOne() {
        // Rect partially outside the page on all sides.
        val s = RectF(-100f, -100f, 2000f, 2000f)
        val n = PdfCoordinateTransforms.screenRectToNormalised(s, 1000f, 800f)
        assertEquals(0f, n.left, 1e-6f)
        assertEquals(0f, n.top, 1e-6f)
        assertEquals(1f, n.right, 1e-6f)
        assertEquals(1f, n.bottom, 1e-6f)
    }

    @Test
    fun screenRectToNormalised_returnsEmptyForZeroRenderedSize() {
        val s = RectF(10f, 10f, 100f, 100f)
        val n = PdfCoordinateTransforms.screenRectToNormalised(s, 0f, 0f)
        assertEquals(0f, n.width(), 0f)
        assertEquals(0f, n.height(), 0f)
    }

    // -----------------------------------------------------------------------
    // rectsToJson / rectsFromJson
    // -----------------------------------------------------------------------

    @Test
    fun rectsToJson_emptyListReturnsEmptyJsonArray() {
        assertEquals("[]", PdfCoordinateTransforms.rectsToJson(emptyList()))
    }

    @Test
    fun rectsFromJson_emptyJsonArrayReturnsEmptyList() {
        val parsed = PdfCoordinateTransforms.rectsFromJson("[]")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun rectsToJson_roundTripsMultipleRects() {
        val original = listOf(
            RectF(0.1f, 0.2f, 0.3f, 0.4f),
            RectF(0.5f, 0.6f, 0.7f, 0.8f),
            RectF(0.9f, 0.95f, 1.0f, 1.0f)
        )
        val json = PdfCoordinateTransforms.rectsToJson(original)
        val parsed = PdfCoordinateTransforms.rectsFromJson(json)
        assertEquals(original.size, parsed.size)
        for (i in original.indices) {
            assertEquals(original[i].left, parsed[i].left, 1e-6f)
            assertEquals(original[i].top, parsed[i].top, 1e-6f)
            assertEquals(original[i].right, parsed[i].right, 1e-6f)
            assertEquals(original[i].bottom, parsed[i].bottom, 1e-6f)
        }
    }

    @Test
    fun rectsFromJson_returnsEmptyForMalformedInput() {
        // Not bracketed, not parseable, etc.
        assertTrue(PdfCoordinateTransforms.rectsFromJson("not json").isEmpty())
        assertTrue(PdfCoordinateTransforms.rectsFromJson("").isEmpty())
        assertTrue(PdfCoordinateTransforms.rectsFromJson("   ").isEmpty())
        assertTrue(PdfCoordinateTransforms.rectsFromJson("[[1,2,3]]").isEmpty()) // only 3 numbers
        assertTrue(PdfCoordinateTransforms.rectsFromJson("[[a,b,c,d]]").isEmpty()) // non-numeric
    }

    @Test
    fun rectsFromJson_toleratesPartialMalformedEntries() {
        // One good rect, one malformed (only 3 numbers) — only the good one survives.
        val parsed = PdfCoordinateTransforms.rectsFromJson("[[0.1,0.2,0.3,0.4],[1,2,3]]")
        assertEquals(1, parsed.size)
        assertEquals(0.1f, parsed[0].left, 1e-6f)
    }

    // -----------------------------------------------------------------------
    // strokesToJson / strokesFromJson
    // -----------------------------------------------------------------------

    @Test
    fun strokesToJson_emptyListReturnsEmptyJsonArray() {
        assertEquals("[]", PdfCoordinateTransforms.strokesToJson(emptyList()))
    }

    @Test
    fun strokesFromJson_emptyJsonArrayReturnsEmptyList() {
        val parsed = PdfCoordinateTransforms.strokesFromJson("[]")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun strokesToJson_roundTripsMultipleStrokes() {
        val original = listOf(
            listOf(PointF(0.1f, 0.2f), PointF(0.3f, 0.4f), PointF(0.5f, 0.6f)),
            listOf(PointF(0.7f, 0.8f)),
            listOf(PointF(0.0f, 0.0f), PointF(1.0f, 1.0f))
        )
        val json = PdfCoordinateTransforms.strokesToJson(original)
        val parsed = PdfCoordinateTransforms.strokesFromJson(json)
        assertEquals(original.size, parsed.size)
        for (si in original.indices) {
            assertEquals(original[si].size, parsed[si].size)
            for (pi in original[si].indices) {
                assertEquals(original[si][pi].x, parsed[si][pi].x, 1e-6f)
                assertEquals(original[si][pi].y, parsed[si][pi].y, 1e-6f)
            }
        }
    }

    @Test
    fun strokesFromJson_returnsEmptyForMalformedInput() {
        assertTrue(PdfCoordinateTransforms.strokesFromJson("not json").isEmpty())
        assertTrue(PdfCoordinateTransforms.strokesFromJson("").isEmpty())
        assertTrue(PdfCoordinateTransforms.strokesFromJson("   ").isEmpty())
    }

    @Test
    fun strokesFromJson_handlesEmptyStrokeGroup() {
        // A stroke group with no points should round-trip as an empty polyline.
        val parsed = PdfCoordinateTransforms.strokesFromJson("[[]]")
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].isEmpty())
    }
}
