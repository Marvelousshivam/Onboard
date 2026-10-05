package com.boardsprep.onboard.data.local.entities

import androidx.room.Entity
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
