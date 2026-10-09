package com.boardsprep.onboard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.boardsprep.onboard.data.local.entities.MasteredItemEntity
import com.boardsprep.onboard.data.local.entities.PdfAnnotationEntity
import com.boardsprep.onboard.data.local.entities.PdfBookmarkEntity
import com.boardsprep.onboard.data.local.entities.PdfReadingStateEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MasteryDao {
    @Query("SELECT * FROM chapter_mastery WHERE chapterId = :chapterId")
    fun getMastery(chapterId: String): Flow<ChapterMasteryEntity?>

    @Query("SELECT * FROM chapter_mastery WHERE chapterId = :chapterId")
    suspend fun getMasteryOnce(chapterId: String): ChapterMasteryEntity?

    @Query("SELECT * FROM chapter_mastery")
    fun getAllMastery(): Flow<List<ChapterMasteryEntity>>

    @Query("SELECT * FROM chapter_mastery")
    suspend fun getAllMasteryList(): List<ChapterMasteryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMastery(entity: ChapterMasteryEntity)
}

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_progress WHERE videoId = :videoId")
    fun getProgress(videoId: String): Flow<VideoProgressEntity?>

    @Query("SELECT * FROM video_progress WHERE videoId = :videoId")
    suspend fun getProgressOnce(videoId: String): VideoProgressEntity?

    @Query("SELECT * FROM video_progress ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentVideos(limit: Int = 10): Flow<List<VideoProgressEntity>>

    @Query("SELECT * FROM video_progress")
    suspend fun getAllProgressList(): List<VideoProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(entity: VideoProgressEntity)
}

@Dao
interface DownloadsDao {
    @Query("SELECT * FROM downloaded_files ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedFileEntity>>

    @Query("SELECT * FROM downloaded_files WHERE id = :id")
    suspend fun getDownload(id: String): DownloadedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(entity: DownloadedFileEntity)

    @Query("DELETE FROM downloaded_files WHERE id = :id")
    suspend fun deleteDownload(id: String)
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quiz_attempts WHERE quizId = :quizId ORDER BY attemptedAt DESC")
    fun getAttemptsForQuiz(quizId: String): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts ORDER BY attemptedAt DESC")
    fun getAllAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts ORDER BY attemptedAt DESC")
    suspend fun getAllAttemptsList(): List<QuizAttemptEntity>

    @Insert
    suspend fun saveAttempt(entity: QuizAttemptEntity)
}

@Dao
interface HandbookDao {
    @Query("SELECT * FROM mastered_items")
    fun getAllMastered(): Flow<List<MasteredItemEntity>>

    @Query("SELECT * FROM mastered_items")
    suspend fun getAllMasteredList(): List<MasteredItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMastered(entity: MasteredItemEntity)

    @Query("DELETE FROM mastered_items WHERE itemId = :itemId")
    suspend fun removeMastered(itemId: String)
}

@Dao
interface ErrorVaultDao {
    @Query("SELECT * FROM error_vault WHERE isResolved = 0 ORDER BY lastAttemptedAt DESC")
    fun getUnresolvedErrors(): Flow<List<com.boardsprep.onboard.data.local.entities.ErrorVaultEntity>>

    @Query("SELECT * FROM error_vault ORDER BY lastAttemptedAt DESC")
    fun getAllErrors(): Flow<List<com.boardsprep.onboard.data.local.entities.ErrorVaultEntity>>

    @Query("SELECT COUNT(*) FROM error_vault WHERE isResolved = 0")
    fun getUnresolvedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM error_vault WHERE isResolved = 1")
    fun getResolvedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertError(entity: com.boardsprep.onboard.data.local.entities.ErrorVaultEntity): Long

    @Query("UPDATE error_vault SET isResolved = 1, lastAttemptedAt = :now WHERE id = :id")
    suspend fun markResolved(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE error_vault SET failureCount = failureCount + 1, lastAttemptedAt = :now WHERE id = :id")
    suspend fun recordReattemptFailure(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM error_vault WHERE questionId = :questionId LIMIT 1")
    suspend fun getErrorByQuestionId(questionId: String): com.boardsprep.onboard.data.local.entities.ErrorVaultEntity?

    @Query("SELECT * FROM error_vault ORDER BY lastAttemptedAt DESC")
    suspend fun getAllErrorsList(): List<com.boardsprep.onboard.data.local.entities.ErrorVaultEntity>

    @Query("DELETE FROM error_vault WHERE id = :id")
    suspend fun deleteError(id: Long)
}

@Dao
interface SpacedReviewDao {
    @Query("SELECT * FROM spaced_reviews WHERE nextReviewDate <= :cutoffTime ORDER BY nextReviewDate ASC LIMIT :limit")
    fun getDueReviews(cutoffTime: Long, limit: Int = 5): Flow<List<com.boardsprep.onboard.data.local.entities.SpacedReviewEntity>>

    @Query("SELECT * FROM spaced_reviews ORDER BY nextReviewDate ASC")
    fun getAllReviews(): Flow<List<com.boardsprep.onboard.data.local.entities.SpacedReviewEntity>>

    @Query("SELECT * FROM spaced_reviews ORDER BY nextReviewDate ASC")
    suspend fun getAllReviewsList(): List<com.boardsprep.onboard.data.local.entities.SpacedReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(entities: List<com.boardsprep.onboard.data.local.entities.SpacedReviewEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateReview(entity: com.boardsprep.onboard.data.local.entities.SpacedReviewEntity)

    @Query("SELECT COUNT(*) FROM spaced_reviews")
    suspend fun getCount(): Int
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSessions(): Flow<List<com.boardsprep.onboard.data.local.entities.FocusSessionEntity>>

    @Insert
    suspend fun recordSession(entity: com.boardsprep.onboard.data.local.entities.FocusSessionEntity)
}

// ===========================================================================
// OnBOARD Reader DAOs
// ===========================================================================

@Dao
interface PdfReadingStateDao {
    @Query("SELECT * FROM pdf_reading_state WHERE documentId = :documentId")
    suspend fun get(documentId: String): PdfReadingStateEntity?

    @Query("SELECT * FROM pdf_reading_state ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<PdfReadingStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfReadingStateEntity)

    @Query("DELETE FROM pdf_reading_state WHERE documentId = :documentId")
    suspend fun delete(documentId: String)
}

@Dao
interface PdfBookmarkDao {
    @Query("SELECT * FROM pdf_bookmarks WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun bookmarksForDocument(documentId: String): Flow<List<PdfBookmarkEntity>>

    @Query("SELECT * FROM pdf_bookmarks WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun bookmarksForDocumentOnce(documentId: String): List<PdfBookmarkEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM pdf_bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex LIMIT 1)")
    suspend fun isBookmarked(documentId: String, pageIndex: Int): Boolean

    @Query("SELECT * FROM pdf_bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun bookmarksForPage(documentId: String, pageIndex: Int): List<PdfBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfBookmarkEntity)

    @Query("DELETE FROM pdf_bookmarks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pdf_bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun deleteForPage(documentId: String, pageIndex: Int)
}

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations WHERE documentId = :documentId ORDER BY pageIndex ASC, updatedAt ASC")
    fun annotationsForDocument(documentId: String): Flow<List<PdfAnnotationEntity>>

    @Query("SELECT * FROM pdf_annotations WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun annotationsForPage(documentId: String, pageIndex: Int): List<PdfAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfAnnotationEntity)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pdf_annotations WHERE documentId = :documentId")
    suspend fun deleteAllForDocument(documentId: String)
}
