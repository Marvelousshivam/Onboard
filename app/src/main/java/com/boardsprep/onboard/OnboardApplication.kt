package com.boardsprep.onboard

import android.app.Application
import com.boardsprep.onboard.core.pdf.PdfErrorLog
import com.boardsprep.onboard.core.playback.LocalLectureScanner
import com.boardsprep.onboard.data.local.OnboardDatabase
import com.boardsprep.onboard.data.repository.BoardsRepository
import kotlinx.coroutines.launch

class OnboardApplication : Application() {

    lateinit var database: OnboardDatabase
        private set

    lateinit var repository: BoardsRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize the reader error logger first so every subsequent
        // initialization error is captured.
        PdfErrorLog.init(this)

        // Install a global uncaught-exception handler so hard crashes leave a
        // trail in the error log that the user can read on next launch.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                PdfErrorLog.error(
                    "UncaughtException",
                    "Uncaught exception on thread '${thread.name}': ${throwable.javaClass.name}: ${throwable.message}",
                    throwable
                )
            } catch (_: Throwable) {
                // Logging must never throw.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        database = OnboardDatabase.getInstance(this)
        repository = BoardsRepository(this)

        // Initialize Cloud Firebase & Firestore Synchronization
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(this)
        } catch (e: Exception) {
            android.util.Log.e("OnboardApp", "Failed to initialize FirebaseSyncManager: ${e.message}")
        }

        // Ensure the external lectures directory exists for user-added lectures
        LocalLectureScanner.getLecturesDirectory(this)
        
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)

            // Asynchronously update yt-dlp engine in background if internet is active
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().updateYoutubeDL(this@OnboardApplication, com.yausername.youtubedl_android.YoutubeDL.UpdateChannel._STABLE)
                } catch (e: Exception) {
                    android.util.Log.d("OnboardApp", "Background yt-dlp update check: ${e.message}")
                }
            }
        } catch (e: Exception) {
            PdfErrorLog.error("OnboardApp", "Failed to initialize youtubedl-android", e)
        }
    }
}
