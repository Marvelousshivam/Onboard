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

@androidx.room.Entity(
    tableName = "error_vault",
    indices = [
        androidx.room.Index(value = ["chapterId"]),
        androidx.room.Index(value = ["isResolved"])
    ]
)
data class ErrorVaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val chapterId: String = "",
    val subjectId: String = "",
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOptionIndex: Int,
    val userSelectedOptionIndex: Int,
    val explanation: String = "",
    val mistakeCategory: String = "conceptual", // "conceptual", "calculation", "formula_miss", "rushed"
    val failureCount: Int = 1,
    val isResolved: Boolean = false,
    val lastAttemptedAt: Long = System.currentTimeMillis()
)

@androidx.room.Entity(tableName = "spaced_reviews")
data class SpacedReviewEntity(
    @PrimaryKey val itemId: String,
    val chapterId: String = "",
    val subjectId: String = "",
    val title: String,
    val prompt: String,
    val answer: String,
    val category: String = "formula", // "formula", "derivation_step", "name_reaction", "definition"
    val intervalDays: Int = 1,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
    val nextReviewDate: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long = 0L
)

@androidx.room.Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionType: String, // "pomodoro_25", "pomodoro_50", "cbse_mock_180"
    val targetSubjectId: String = "",
    val durationMinutes: Int,
    val completedMinutes: Int,
    val wasInterrupted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

