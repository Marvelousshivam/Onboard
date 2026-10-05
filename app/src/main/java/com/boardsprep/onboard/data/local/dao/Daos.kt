package com.boardsprep.onboard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.boardsprep.onboard.data.local.entities.MasteredItemEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MasteryDao {
    @Query("SELECT * FROM chapter_mastery WHERE chapterId = :chapterId")
    fun getMastery(chapterId: String): Flow<ChapterMasteryEntity?>

    @Query("SELECT * FROM chapter_mastery")
    fun getAllMastery(): Flow<List<ChapterMasteryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMastery(entity: ChapterMasteryEntity)
}

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_progress WHERE videoId = :videoId")
    fun getProgress(videoId: String): Flow<VideoProgressEntity?>

    @Query("SELECT * FROM video_progress ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentVideos(limit: Int = 10): Flow<List<VideoProgressEntity>>

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

    @Insert
    suspend fun saveAttempt(entity: QuizAttemptEntity)
}

@Dao
interface HandbookDao {
    @Query("SELECT * FROM mastered_items")
    fun getAllMastered(): Flow<List<MasteredItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMastered(entity: MasteredItemEntity)

    @Query("DELETE FROM mastered_items WHERE itemId = :itemId")
    suspend fun removeMastered(itemId: String)
}
