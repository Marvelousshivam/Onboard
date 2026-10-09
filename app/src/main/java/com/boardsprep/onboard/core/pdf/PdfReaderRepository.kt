package com.boardsprep.onboard.core.pdf

import android.graphics.PointF
import android.graphics.RectF
import com.boardsprep.onboard.data.local.dao.PdfAnnotationDao
import com.boardsprep.onboard.data.local.dao.PdfBookmarkDao
import com.boardsprep.onboard.data.local.dao.PdfReadingStateDao
import com.boardsprep.onboard.data.local.entities.PdfAnnotationEntity
import com.boardsprep.onboard.data.local.entities.PdfBookmarkEntity
import com.boardsprep.onboard.data.local.entities.PdfReadingStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Adapts the Room DAOs to the reader's domain models. Keeps the UI layer free
 * of entity/JSON details and centralises the (de)serialisation of annotation
 * geometry via [PdfCoordinateTransforms].
 */
class PdfReaderRepository(
    private val readingStateDao: PdfReadingStateDao,
    private val bookmarkDao: PdfBookmarkDao,
    private val annotationDao: PdfAnnotationDao
) {

    // ---- Reading state -----------------------------------------------------

    suspend fun loadReadingState(documentId: String): PdfReadingState? {
        val e = readingStateDao.get(documentId) ?: return null
        return PdfReadingState(
            documentId = e.documentId,
            pageIndex = e.pageIndex,
            pageOffsetY = e.pageOffsetY,
            zoom = e.zoom,
            readingMode = runCatching { PdfReadingMode.valueOf(e.readingMode) }.getOrDefault(PdfReadingMode.CONTINUOUS_VERTICAL),
            fitMode = runCatching { PdfFitMode.valueOf(e.fitMode) }.getOrDefault(PdfFitMode.FIT_WIDTH),
            lastOpenedAt = e.lastOpenedAt
        )
    }

    suspend fun saveReadingState(state: PdfReadingState, appearance: PdfAppearanceSettings) {
        readingStateDao.upsert(
            PdfReadingStateEntity(
                documentId = state.documentId,
                pageIndex = state.pageIndex,
                pageOffsetY = state.pageOffsetY,
                zoom = state.zoom,
                readingMode = appearance.readingMode.name,
                fitMode = appearance.fitMode.name,
                canvasTheme = appearance.canvasTheme.name,
                fullscreen = appearance.fullscreen,
                brightnessOverride = appearance.brightnessOverride,
                pageSpacingDp = appearance.pageSpacingDp,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    fun recentDocuments(limit: Int = 20): Flow<List<PdfReadingStateEntity>> =
        readingStateDao.recent(limit)

    // ---- Bookmarks ---------------------------------------------------------

    fun bookmarksForDocument(documentId: String): Flow<List<PdfBookmark>> =
        bookmarkDao.bookmarksForDocument(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun isBookmarked(documentId: String, pageIndex: Int): Boolean =
        bookmarkDao.isBookmarked(documentId, pageIndex)

    suspend fun bookmarksForPage(documentId: String, pageIndex: Int): List<PdfBookmark> =
        bookmarkDao.bookmarksForPage(documentId, pageIndex).map { it.toDomain() }

    suspend fun addBookmark(
        documentId: String,
        pageIndex: Int,
        label: String,
        note: String = "",
        forRevision: Boolean = false
    ): PdfBookmark {
        val entity = PdfBookmarkEntity(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            pageIndex = pageIndex,
            label = label,
            note = note,
            forRevision = forRevision
        )
        bookmarkDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun removeBookmark(id: String) = bookmarkDao.delete(id)

    suspend fun removeBookmarksForPage(documentId: String, pageIndex: Int) =
        bookmarkDao.deleteForPage(documentId, pageIndex)

    // ---- Annotations -------------------------------------------------------

    fun annotationsForDocument(documentId: String): Flow<List<PdfAnnotation>> =
        annotationDao.annotationsForDocument(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun annotationsForPage(documentId: String, pageIndex: Int): List<PdfAnnotation> =
        annotationDao.annotationsForPage(documentId, pageIndex).map { it.toDomain() }

    suspend fun upsertAnnotation(annotation: PdfAnnotation) {
        annotationDao.upsert(annotation.toEntity())
    }

    suspend fun deleteAnnotation(id: String) = annotationDao.delete(id)

    // ---- Mappers -----------------------------------------------------------

    private fun PdfBookmarkEntity.toDomain() = PdfBookmark(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        label = label,
        note = note,
        forRevision = forRevision,
        createdAt = createdAt
    )

    private fun PdfAnnotationEntity.toDomain() = PdfAnnotation(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        type = runCatching { PdfAnnotationType.valueOf(type) }.getOrDefault(PdfAnnotationType.HIGHLIGHT),
        rects = PdfCoordinateTransforms.rectsFromJson(rectsJson),
        color = color,
        strokeWidth = strokeWidth,
        strokes = PdfCoordinateTransforms.strokesFromJson(strokesJson),
        noteText = noteText,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PdfAnnotation.toEntity() = PdfAnnotationEntity(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        type = type.name,
        rectsJson = PdfCoordinateTransforms.rectsToJson(rects),
        color = color,
        strokeWidth = strokeWidth,
        strokesJson = PdfCoordinateTransforms.strokesToJson(strokes),
        noteText = noteText,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
