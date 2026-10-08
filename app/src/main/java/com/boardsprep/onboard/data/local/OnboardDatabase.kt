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
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.boardsprep.onboard.data.local.entities.MasteredItemEntity
import com.boardsprep.onboard.data.local.entities.PdfAnnotationEntity
import com.boardsprep.onboard.data.local.entities.PdfBookmarkEntity
import com.boardsprep.onboard.data.local.entities.PdfReadingStateEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity

@Database(
    entities = [
        ChapterMasteryEntity::class,
        VideoProgressEntity::class,
        DownloadedFileEntity::class,
        QuizAttemptEntity::class,
        MasteredItemEntity::class,
        // OnBOARD Reader tables (added in v2)
        PdfReadingStateEntity::class,
        PdfBookmarkEntity::class,
        PdfAnnotationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OnboardDatabase : RoomDatabase() {

    abstract fun masteryDao(): MasteryDao
    abstract fun videoProgressDao(): VideoProgressDao
    abstract fun downloadsDao(): DownloadsDao
    abstract fun quizDao(): QuizDao
    abstract fun handbookDao(): HandbookDao

    // OnBOARD Reader DAOs
    abstract fun pdfReadingStateDao(): PdfReadingStateDao
    abstract fun pdfBookmarkDao(): PdfBookmarkDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao

    companion object {
        @Volatile
        private var INSTANCE: OnboardDatabase? = null

        /**
         * Version 1 -> 2 migration: adds the three OnBOARD Reader tables.
         *
         * Existing user data (chapter mastery, video progress, downloads, quiz
         * attempts, mastered items) is preserved untouched. We deliberately do
         * NOT use fallbackToDestructiveMigration anymore so reader bookmarks,
         * annotations and reading state can never be silently wiped by a future
         * schema change.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        fun getInstance(context: Context): OnboardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OnboardDatabase::class.java,
                    "onboard_app.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Only fall back to destructive migration as a last resort for
                    // unknown future versions; v1->v2 is handled explicitly above.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
