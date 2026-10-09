package com.boardsprep.onboard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.boardsprep.onboard.data.local.dao.DownloadsDao
import com.boardsprep.onboard.data.local.dao.HandbookDao
import com.boardsprep.onboard.data.local.dao.MasteryDao
import com.boardsprep.onboard.data.local.dao.QuizDao
import com.boardsprep.onboard.data.local.dao.VideoProgressDao
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.boardsprep.onboard.data.local.entities.MasteredItemEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import com.boardsprep.onboard.data.local.entities.ErrorVaultEntity
import com.boardsprep.onboard.data.local.entities.SpacedReviewEntity
import com.boardsprep.onboard.data.local.entities.FocusSessionEntity
import com.boardsprep.onboard.data.local.dao.ErrorVaultDao
import com.boardsprep.onboard.data.local.dao.SpacedReviewDao
import com.boardsprep.onboard.data.local.dao.FocusSessionDao

@Database(
    entities = [
        ChapterMasteryEntity::class,
        VideoProgressEntity::class,
        DownloadedFileEntity::class,
        QuizAttemptEntity::class,
        MasteredItemEntity::class,
        ErrorVaultEntity::class,
        SpacedReviewEntity::class,
        FocusSessionEntity::class
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
    abstract fun errorVaultDao(): ErrorVaultDao
    abstract fun spacedReviewDao(): SpacedReviewDao
    abstract fun focusSessionDao(): FocusSessionDao


    companion object {
        @Volatile
        private var INSTANCE: OnboardDatabase? = null

        fun getInstance(context: Context): OnboardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OnboardDatabase::class.java,
                    "onboard_app.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
