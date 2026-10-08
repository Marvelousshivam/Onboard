package com.boardsprep.onboard.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_mastery")
data class ChapterMasteryEntity(
    @PrimaryKey val chapterId: String,
    val theoryCompleted: Boolean = false,
    val ncertCompleted: Boolean = false,
    val exemplarCompleted: Boolean = false,
    val pyqCompleted: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "video_progress")
data class VideoProgressEntity(
    @PrimaryKey val videoId: String,
    val chapterId: String = "",
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val isCompleted: Boolean = false,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_files")
data class DownloadedFileEntity(
    @PrimaryKey val id: String,
    val title: String,
    val chapterId: String,
    val fileType: String, // "video" or "pdf"
    val localPath: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: String,
    val score: Int,
    val totalQuestions: Int,
    val timeTakenSeconds: Long,
    val attemptedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mastered_items")
data class MasteredItemEntity(
    @PrimaryKey val itemId: String,
    val category: String, // "derivation", "reaction"
    val isMastered: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

// ===========================================================================
// OnBOARD Reader — persistence entities (added in DB version 2)
//
// documentId is the canonical SHA-256 identity produced by PdfDocumentRepository.
// It is shared across the three reader tables so that bookmarks, annotations
// and reading state all survive cache eviction, file renames and process death.
// ===========================================================================

/**
 * Persisted reading position + appearance per document.
 *
 * One row per documentId. Written whenever the user navigates, zooms, changes
 * mode or closes the viewer, and read back on next open so reopening a 400-page
 * NCERT textbook lands the student exactly where they left off.
 */
@Entity(tableName = "pdf_reading_state")
data class PdfReadingStateEntity(
    @PrimaryKey val documentId: String,
    val pageIndex: Int,
    val pageOffsetY: Float,
    val zoom: Float,
    val readingMode: String,        // PdfReadingMode.name
    val fitMode: String,            // PdfFitMode.name
    val canvasTheme: String,        // PdfCanvasTheme.name
    val fullscreen: Boolean,
    val brightnessOverride: Float?,  // null = use system brightness
    val pageSpacingDp: Int,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

/**
 * A user-created page bookmark. Distinct from PDF outline bookmarks.
 */
@Entity(
    tableName = "pdf_bookmarks",
    indices = [Index("documentId"), Index(value = ["documentId", "pageIndex"])]
)
data class PdfBookmarkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val label: String,
    val note: String,
    val forRevision: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A persistent annotation (highlight / underline / note / freehand / rectangle).
 *
 * Geometry is stored in normalised PDF page coordinates (0f..1f) so it can be
 * transformed correctly for any zoom, pan, page size or orientation. Strokes
 * are flattened into a JSON string to keep the schema Room-primitive-friendly.
 */
@Entity(
    tableName = "pdf_annotations",
    indices = [Index("documentId"), Index(value = ["documentId", "pageIndex"])]
)
data class PdfAnnotationEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val type: String,              // PdfAnnotationType.name
    /** JSON array of normalised rects: [[l,t,r,b], ...]. */
    val rectsJson: String,
    val color: Int,
    val strokeWidth: Float,
    /** JSON array of polylines: [[[x,y],...], ...] in normalised page coords. */
    val strokesJson: String,
    val noteText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

