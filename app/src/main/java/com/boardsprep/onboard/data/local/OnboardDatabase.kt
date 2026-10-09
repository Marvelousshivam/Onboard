package com.boardsprep.onboard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boardsprep.onboard.data.local.dao.DownloadsDao
import com.boardsprep.onboard.data.local.dao.HandbookDao
import com.boardsprep.onboard.data.local.dao.MasteryDao
import com.boardsprep.onboard.data.local.dao.PdfAnnotationDao
import com.boardsprep.onboard.data.local.dao.PdfBookmarkDao
import com.boardsprep.onboard.data.local.dao.PdfReadingStateDao
import com.boardsprep.onboard.data.local.dao.QuizDao
import com.boardsprep.onboard.data.local.dao.VideoProgressDao
import com.boardsprep.onboard.data.local.dao.ErrorVaultDao
import com.boardsprep.onboard.data.local.dao.SpacedReviewDao
import com.boardsprep.onboard.data.local.dao.FocusSessionDao
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.boardsprep.onboard.data.local.entities.MasteredItemEntity
import com.boardsprep.onboard.data.local.entities.PdfAnnotationEntity
import com.boardsprep.onboard.data.local.entities.PdfBookmarkEntity
import com.boardsprep.onboard.data.local.entities.PdfReadingStateEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import com.boardsprep.onboard.data.local.entities.ErrorVaultEntity
import com.boardsprep.onboard.data.local.entities.SpacedReviewEntity
import com.boardsprep.onboard.data.local.entities.FocusSessionEntity

@Database(
    entities = [
        ChapterMasteryEntity::class,
        VideoProgressEntity::class,
        DownloadedFileEntity::class,
        QuizAttemptEntity::class,
        MasteredItemEntity::class,
        ErrorVaultEntity::class,
        SpacedReviewEntity::class,
        FocusSessionEntity::class,
        // OnBOARD Reader tables
        PdfReadingStateEntity::class,
        PdfBookmarkEntity::class,
        PdfAnnotationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OnboardDatabase : RoomDatabase() {

    abstract fun masteryDao(): MasteryDao
    abstract fun videoProgressDao(): VideoProgressDao
    abstract fun downloadsDao(): DownloadsDao
    abstract fun quizDao(): QuizDao
    abstract fun handbookDao(): HandbookDao
    abstract fun errorVaultDao(): ErrorVaultDao
    abstract fun spacedReviewDao(): SpacedReviewDao
    abstract fun focusSessionDao(): FocusSessionDao

    // OnBOARD Reader DAOs
    abstract fun pdfReadingStateDao(): PdfReadingStateDao
    abstract fun pdfBookmarkDao(): PdfBookmarkDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao

    companion object {
        @Volatile
        private var INSTANCE: OnboardDatabase? = null

        /**
         * Version 1 -> 2 migration: adds the three OnBOARD Reader tables.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // pdf_reading_state
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_reading_state (
                        documentId TEXT NOT NULL PRIMARY KEY,
                        pageIndex INTEGER NOT NULL,
                        pageOffsetY REAL NOT NULL,
                        zoom REAL NOT NULL,
                        readingMode TEXT NOT NULL,
                        fitMode TEXT NOT NULL,
                        canvasTheme TEXT NOT NULL,
                        fullscreen INTEGER NOT NULL,
                        brightnessOverride REAL,
                        pageSpacingDp INTEGER NOT NULL,
                        lastOpenedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // pdf_bookmarks
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_bookmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        note TEXT NOT NULL,
                        forRevision INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_bookmarks_documentId ON pdf_bookmarks(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_bookmarks_documentId_pageIndex ON pdf_bookmarks(documentId, pageIndex)")
                // pdf_annotations
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_annotations (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        rectsJson TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        strokeWidth REAL NOT NULL,
                        strokesJson TEXT NOT NULL,
                        noteText TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_documentId ON pdf_annotations(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_documentId_pageIndex ON pdf_annotations(documentId, pageIndex)")
            }
        }

        /**
         * Version 2 -> 3 migration: adds Error Vault, Spaced Reviews, and Focus Sessions.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS error_vault (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questionId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        subjectId TEXT NOT NULL,
                        questionText TEXT NOT NULL,
                        optionA TEXT NOT NULL,
                        optionB TEXT NOT NULL,
                        optionC TEXT NOT NULL,
                        optionD TEXT NOT NULL,
                        correctOptionIndex INTEGER NOT NULL,
                        userSelectedOptionIndex INTEGER NOT NULL,
                        explanation TEXT NOT NULL,
                        mistakeCategory TEXT NOT NULL,
                        failureCount INTEGER NOT NULL,
                        isResolved INTEGER NOT NULL,
                        lastAttemptedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_error_vault_chapterId ON error_vault(chapterId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_error_vault_isResolved ON error_vault(isResolved)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS spaced_reviews (
                        itemId TEXT PRIMARY KEY NOT NULL,
                        chapterId TEXT NOT NULL,
                        subjectId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        category TEXT NOT NULL,
                        intervalDays INTEGER NOT NULL,
                        easeFactor REAL NOT NULL,
                        repetitions INTEGER NOT NULL,
                        nextReviewDate INTEGER NOT NULL,
                        lastReviewedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionType TEXT NOT NULL,
                        targetSubjectId TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        completedMinutes INTEGER NOT NULL,
                        wasInterrupted INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): OnboardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OnboardDatabase::class.java,
                    "onboard_app.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
