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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfAnnotationHitTestTest {

    @Test
    fun testDistanceSqToSegment_exactPointOnSegment() {
        val dSq = PdfAnnotationHitTest.distanceSqToSegment(
            px = 0.5f, py = 0.5f,
            ax = 0.0f, ay = 0.5f,
            bx = 1.0f, by = 0.5f
        )
        assertEquals(0.0f, dSq, 0.0001f)
    }

    @Test
    fun testDistanceSqToSegment_perpendicularOffset() {
        val dSq = PdfAnnotationHitTest.distanceSqToSegment(
            px = 0.5f, py = 0.6f,
            ax = 0.0f, ay = 0.5f,
            bx = 1.0f, by = 0.5f
        )
        // Perpendicular offset is 0.1, squared is 0.01
        assertEquals(0.01f, dSq, 0.0001f)
    }

    @Test
    fun testDistanceSqToSegment_pastEndpoint() {
        val dSq = PdfAnnotationHitTest.distanceSqToSegment(
            px = 1.2f, py = 0.5f,
            ax = 0.0f, ay = 0.5f,
            bx = 1.0f, by = 0.5f
        )
        // Distance to endpoint B(1.0, 0.5) is 0.2, squared is 0.04
        assertEquals(0.04f, dSq, 0.0001f)
    }

    @Test
    fun testIsHit_strokeHitsWithinTolerance() {
        val stroke = listOf(PointF(0.1f, 0.1f), PointF(0.9f, 0.1f))
        val ann = PdfAnnotation(
            id = "test_1",
            documentId = "doc_1",
            pageIndex = 0,
            type = PdfAnnotationType.FREEHAND,
            rects = emptyList(),
            color = 0xFF000000.toInt(),
            strokeWidth = 4f,
            strokes = listOf(stroke),
            noteText = "",
            createdAt = 0L,
            updatedAt = 0L
        )

        // Point near (0.5, 0.12) is within tolerance 0.045
        assertTrue(PdfAnnotationHitTest.isHit(ann, PointF(0.5f, 0.12f), tolerance = 0.045f))
        // Point far at (0.5, 0.3) is outside tolerance
        assertFalse(PdfAnnotationHitTest.isHit(ann, PointF(0.5f, 0.30f), tolerance = 0.045f))
    }

    @Test
    fun testIsHit_rectHitsWithinTolerance() {
        val rect = RectF(0.2f, 0.3f, 0.8f, 0.5f)
        val ann = PdfAnnotation(
            id = "test_rect",
            documentId = "doc_1",
            pageIndex = 0,
            type = PdfAnnotationType.HIGHLIGHT,
            rects = listOf(rect),
            color = 0x66FFFF00.toInt(),
            strokeWidth = 0f,
            strokes = emptyList(),
            noteText = "",
            createdAt = 0L,
            updatedAt = 0L
        )

        // Inside rect
        assertTrue(PdfAnnotationHitTest.isHit(ann, PointF(0.5f, 0.4f), tolerance = 0.02f))
        // Just outside border within tolerance
        assertTrue(PdfAnnotationHitTest.isHit(ann, PointF(0.81f, 0.4f), tolerance = 0.02f))
        // Completely outside
        assertFalse(PdfAnnotationHitTest.isHit(ann, PointF(0.9f, 0.9f), tolerance = 0.02f))
    }

    @Test
    fun testFindHits_filtersByPageIndex() {
        val annPage0 = PdfAnnotation(
            id = "p0",
            documentId = "doc_1",
            pageIndex = 0,
            type = PdfAnnotationType.FREEHAND,
            rects = emptyList(),
            color = 0xFF000000.toInt(),
            strokeWidth = 4f,
            strokes = listOf(listOf(PointF(0.5f, 0.5f), PointF(0.6f, 0.6f))),
            noteText = "",
            createdAt = 0L,
            updatedAt = 0L
        )
        val annPage1 = annPage0.copy(id = "p1", pageIndex = 1)

        val list = listOf(annPage0, annPage1)
        val hitsPage0 = PdfAnnotationHitTest.findHits(list, pageIndex = 0, PointF(0.55f, 0.55f))
        assertEquals(1, hitsPage0.size)
        assertEquals("p0", hitsPage0[0].id)

        val hitsPage1 = PdfAnnotationHitTest.findHits(list, pageIndex = 1, PointF(0.55f, 0.55f))
        assertEquals(1, hitsPage1.size)
        assertEquals("p1", hitsPage1[0].id)
    }
}
