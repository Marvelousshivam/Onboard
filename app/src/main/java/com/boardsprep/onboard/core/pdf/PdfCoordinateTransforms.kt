package com.boardsprep.onboard.core.pdf

import android.graphics.PointF
import android.graphics.RectF

/**
 * Coordinate transforms for the OnBOARD Reader.
 *
 * The reader uses three coordinate systems:
 *
 * 1. **PDF page space** — native PDF points (1/72"), origin top-left, +Y down.
 *    Used only by the text extractor (PdfBox) and the engine.
 *
 * 2. **Normalised page space** — [0f, 1f] on both axes, origin top-left.
 *    This is what annotations store. It is the only coordinate system that
 *    survives every zoom, pan, page-size, orientation and rendering-resolution
 *    change without re-derivation, because it is independent of all of them.
 *
 * 3. **Screen space** — pixels relative to the page's top-left within the
 *    scrollable canvas, *after* applying the current zoom and pan. Used by
 *    gesture handlers and the annotation overlay.
 *
 * All transforms are pure and trivially unit-testable.
 */
object PdfCoordinateTransforms {

    // ---- Page-space (PDF points) <-> Normalised ----

    /** Convert a rect in PDF points to normalised page coords. */
    fun pageRectToNormalised(rect: RectF, pageWidthPts: Float, pageHeightPts: Float): RectF {
        if (pageWidthPts <= 0f || pageHeightPts <= 0f) return RectF()
        return RectF(
            rect.left / pageWidthPts,
            rect.top / pageHeightPts,
            rect.right / pageWidthPts,
            rect.bottom / pageHeightPts
        )
    }

    /** Convert a normalised rect to PDF points. */
    fun normalisedRectToPage(rect: RectF, pageWidthPts: Float, pageHeightPts: Float): RectF {
        return RectF(
            rect.left * pageWidthPts,
            rect.top * pageHeightPts,
            rect.right * pageWidthPts,
            rect.bottom * pageHeightPts
        )
    }

    // ---- Normalised <-> Screen (within a single page view) ----

    /**
     * Convert a normalised point to screen pixels within a page view.
     *
     * @param renderedWidthPx the width of the page view on screen (after zoom).
     * @param renderedHeightPx the height of the page view on screen (after zoom).
     */
    fun normalisedToScreen(
        point: PointF,
        renderedWidthPx: Float,
        renderedHeightPx: Float
    ): PointF = PointF(
        point.x * renderedWidthPx,
        point.y * renderedHeightPx
    )

    /** Convert a screen point (within a page view) to normalised page coords. */
    fun screenToNormalised(
        point: PointF,
        renderedWidthPx: Float,
        renderedHeightPx: Float
    ): PointF {
        if (renderedWidthPx <= 0f || renderedHeightPx <= 0f) return PointF(0f, 0f)
        return PointF(
            (point.x / renderedWidthPx).coerceIn(0f, 1f),
            (point.y / renderedHeightPx).coerceIn(0f, 1f)
        )
    }

    /** Convert a normalised rect to screen pixels within a page view. */
    fun normalisedRectToScreen(
        rect: RectF,
        renderedWidthPx: Float,
        renderedHeightPx: Float
    ): RectF = RectF(
        rect.left * renderedWidthPx,
        rect.top * renderedHeightPx,
        rect.right * renderedWidthPx,
        rect.bottom * renderedHeightPx
    )

    /** Convert a screen rect (within a page view) to normalised page coords. */
    fun screenRectToNormalised(
        rect: RectF,
        renderedWidthPx: Float,
        renderedHeightPx: Float
    ): RectF {
        if (renderedWidthPx <= 0f || renderedHeightPx <= 0f) return RectF()
        return RectF(
            (rect.left / renderedWidthPx).coerceIn(0f, 1f),
            (rect.top / renderedHeightPx).coerceIn(0f, 1f),
            (rect.right / renderedWidthPx).coerceIn(0f, 1f),
            (rect.bottom / renderedHeightPx).coerceIn(0f, 1f)
        )
    }

    // ---- Page index bounds ----

    /** Clamp a page index to [0, pageCount - 1]. Returns 0 for empty documents. */
    fun clampPageIndex(index: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return index.coerceIn(0, pageCount - 1)
    }

    /** True if the page index is in range [0, pageCount - 1]. */
    fun isValidPageIndex(index: Int, pageCount: Int): Boolean {
        return pageCount > 0 && index in 0 until pageCount
    }

    // ---- Render target width strategy ----

    /**
     * Compute the bitmap render width (in pixels) for a page given the viewport
     * width and the current zoom, subject to an absolute memory-safety cap.
     *
     * The page is rendered at a resolution that matches what the user actually
     * sees on screen at the current zoom — never a fixed 1080/1440px bitmap
     * stretched by graphicsLayer. This keeps small typography sharp at high
     * zoom while bounding memory on long textbooks.
     *
     * @param viewportWidthPx the on-screen page width at zoom = 1, in pixels.
     * @param zoom the current zoom factor (1f = fit).
     * @param maxRenderWidthPx hard cap to avoid OOM on huge zooms / huge pages.
     */
    fun computeRenderTargetWidth(
        viewportWidthPx: Int,
        zoom: Float,
        maxRenderWidthPx: Int = 2200
    ): Int {
        if (viewportWidthPx <= 0) return 1080
        val desired = (viewportWidthPx * zoom.coerceAtLeast(1f)).toInt()
        return desired.coerceIn(700, maxRenderWidthPx)
    }

    /** Compute the rendered bitmap height from width and the page aspect ratio. */
    fun computeRenderedHeight(renderedWidthPx: Int, aspectRatio: Float): Int {
        if (aspectRatio <= 0f) return renderedWidthPx
        return (renderedWidthPx * aspectRatio).toInt().coerceAtLeast(1)
    }

    // ---- JSON (de)serialisation for annotation geometry ----

    /** Serialise a list of normalised rects to a compact JSON array string. */
    fun rectsToJson(rects: List<RectF>): String {
        if (rects.isEmpty()) return "[]"
        return buildString {
            append('[')
            for ((i, r) in rects.withIndex()) {
                if (i > 0) append(',')
                append('[').append(r.left).append(',').append(r.top)
                    .append(',').append(r.right).append(',').append(r.bottom).append(']')
            }
            append(']')
        }
    }

    /** Parse a JSON array of normalised rects produced by [rectsToJson]. */
    fun rectsFromJson(json: String): List<RectF> {
        if (json.isBlank()) return emptyList()
        // Lightweight parser tolerant of the exact format emitted by rectsToJson.
        val result = mutableListOf<RectF>()
        val s = json.trim()
        if (!s.startsWith('[') || !s.endsWith(']')) return emptyList()
        val inner = s.substring(1, s.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        var i = 0
        while (i < inner.length) {
            val open = inner.indexOf('[', i)
            if (open == -1) break
            val close = inner.indexOf(']', open)
            if (close == -1) break
            val parts = inner.substring(open + 1, close).split(',').mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size == 4) {
                result.add(RectF(parts[0], parts[1], parts[2], parts[3]))
            }
            i = close + 1
        }
        return result
    }

    /** Serialise freehand strokes (list of polylines of normalised points) to JSON. */
    fun strokesToJson(strokes: List<List<PointF>>): String {
        if (strokes.isEmpty()) return "[]"
        return buildString {
            append('[')
            for ((si, stroke) in strokes.withIndex()) {
                if (si > 0) append(',')
                append('[')
                for ((pi, p) in stroke.withIndex()) {
                    if (pi > 0) append(',')
                    append('[').append(p.x).append(',').append(p.y).append(']')
                }
                append(']')
            }
            append(']')
        }
    }

    /** Parse freehand strokes from JSON produced by [strokesToJson]. */
    fun strokesFromJson(json: String): List<List<PointF>> {
        if (json.isBlank()) return emptyList()
        val result = mutableListOf<MutableList<PointF>>()
        val s = json.trim()
        if (!s.startsWith('[') || !s.endsWith(']')) return emptyList()
        // Tokenise into top-level stroke groups.
        var i = 1
        var depth = 0
        var groupStart = -1
        while (i < s.length - 1) {
            val c = s[i]
            when (c) {
                '[' -> {
                    if (depth == 0) groupStart = i
                    depth++
                }
                ']' -> {
                    depth--
                    if (depth == 0 && groupStart != -1) {
                        val group = s.substring(groupStart, i + 1)
                        val points = mutableListOf<PointF>()
                        var j = 1
                        while (j < group.length - 1) {
                            val open = group.indexOf('[', j)
                            if (open == -1) break
                            val close = group.indexOf(']', open)
                            if (close == -1) break
                            val parts = group.substring(open + 1, close).split(',').mapNotNull { it.trim().toFloatOrNull() }
                            if (parts.size == 2) points.add(PointF(parts[0], parts[1]))
                            j = close + 1
                        }
                        result.add(points)
                        groupStart = -1
                    }
                }
            }
            i++
        }
        return result
    }
}
