package com.boardsprep.onboard.core.pdf

import android.graphics.PointF

/**
 * High-performance hit testing for PDF annotations (freehand ink, freehand highlighters, and text boxes).
 * Used by the object/stroke eraser in OnBOARD Reader.
 */
object PdfAnnotationHitTest {

    /**
     * Compute squared distance from point (px, py) to line segment (ax, ay)-(bx, by).
     * Fully normalised in page space [0f, 1f].
     */
    fun distanceSqToSegment(
        px: Float, py: Float,
        ax: Float, ay: Float,
        bx: Float, by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0f && dy == 0f) {
            val dpx = px - ax
            val dpy = py - ay
            return dpx * dpx + dpy * dpy
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val ex = px - cx
        val ey = py - cy
        return ex * ex + ey * ey
    }

    /**
     * Test whether [annotation] intersects with a normalised [point] within [tolerance].
     *
     * @param annotation The annotation to test.
     * @param point The touch point in normalised [0..1] page coordinates.
     * @param tolerance Normalised distance threshold (default ~0.045f, approx 20-25dp on phone).
     */
    fun isHit(
        annotation: PdfAnnotation,
        point: PointF,
        tolerance: Float = 0.045f
    ): Boolean {
        val tolSq = tolerance * tolerance

        // 1. Check freehand strokes (pen or freehand highlighter)
        for (stroke in annotation.strokes) {
            if (stroke.isEmpty()) continue
            if (stroke.size == 1) {
                val p = stroke[0]
                val dSq = (point.x - p.x) * (point.x - p.x) + (point.y - p.y) * (point.y - p.y)
                if (dSq <= tolSq) return true
            } else {
                for (i in 0 until stroke.size - 1) {
                    val a = stroke[i]
                    val b = stroke[i + 1]
                    val dSq = distanceSqToSegment(point.x, point.y, a.x, a.y, b.x, b.y)
                    if (dSq <= tolSq) return true
                }
            }
        }

        // 2. Check rectangular highlight areas
        for (rect in annotation.rects) {
            if (point.x >= rect.left - tolerance &&
                point.x <= rect.right + tolerance &&
                point.y >= rect.top - tolerance &&
                point.y <= rect.bottom + tolerance
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Find all annotations on [pageIndex] that intersect with normalised [point].
     */
    fun findHits(
        annotations: List<PdfAnnotation>,
        pageIndex: Int,
        point: PointF,
        tolerance: Float = 0.045f
    ): List<PdfAnnotation> {
        return annotations.filter { it.pageIndex == pageIndex && isHit(it, point, tolerance) }
    }
}
