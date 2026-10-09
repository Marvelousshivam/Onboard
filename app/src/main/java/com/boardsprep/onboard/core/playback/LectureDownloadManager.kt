package com.boardsprep.onboard.core.playback

import android.content.Context
import android.util.Log
import com.boardsprep.onboard.data.local.OnboardDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Global persistent download manager.
 * Runs downloads on an application-level coroutine scope so downloads
 * continue uninterrupted across screen transitions, backgrounding, and navigation.
 * Retains reactive state so returning to any lecture immediately restores
 * the live progress, speed, ETA, and stage indicators.
 */
object LectureDownloadManager {

    private const val TAG = "LectureDownloadManager"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloadStates = ConcurrentHashMap<String, MutableStateFlow<DownloadState>>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeTitles = ConcurrentHashMap<String, String>()

    // Global flow of active downloads for DownloadsScreen and Global HUD
    private val _activeDownloadsList = MutableStateFlow<Map<String, Pair<String, DownloadState.Progress>>>(emptyMap())
    val activeDownloadsList: StateFlow<Map<String, Pair<String, DownloadState.Progress>>> = _activeDownloadsList.asStateFlow()

    fun getDownloadState(context: Context, lectureId: String, title: String): StateFlow<DownloadState> {
        val existing = downloadStates[lectureId]
        if (existing != null) {
            return existing.asStateFlow()
        }

        // Initialize state: Check if already completed on disk or Room DB
        val flow = MutableStateFlow<DownloadState>(DownloadState.Idle)
        downloadStates[lectureId] = flow

        appScope.launch {
            try {
                val db = OnboardDatabase.getInstance(context)
                val entity = db.downloadsDao().getDownload(lectureId)
                if (entity != null && File(entity.localPath).exists()) {
                    flow.value = DownloadState.Success(File(entity.localPath))
                    return@launch
                }

                // Check disk directly in case it was saved with sanitized title
                val lecturesDir = LocalLectureScanner.getLecturesDirectory(context)
                val sanitized = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val mp4 = File(lecturesDir, "$sanitized.mp4")
                val m4a = File(lecturesDir, "$sanitized.m4a")

                if (mp4.exists() && mp4.length() > 0) {
                    flow.value = DownloadState.Success(mp4)
                } else if (m4a.exists() && m4a.length() > 0) {
                    flow.value = DownloadState.Success(m4a)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking existing download status: ${e.message}")
            }
        }

        return flow.asStateFlow()
    }

    fun startDownload(
        context: Context,
        id: String,
        title: String,
        chapterId: String,
        url: String,
        formatSelector: String = "",
        isAudioOnly: Boolean = false
    ) {
        if (activeJobs.containsKey(id)) {
            Log.d(TAG, "Download already active for $id ($title)")
            return
        }

        val flow = downloadStates.getOrPut(id) { MutableStateFlow(DownloadState.Idle) }
        activeTitles[id] = title

        DownloadNotificationHelper.createNotificationChannel(context)

        val job = appScope.launch {
            // Monitor flow for Notification updates and Global Active Downloads Map
            var lastNotifiedTime = 0L
            val observerJob = launch {
                flow.collect { state ->
                    when (state) {
                        is DownloadState.Progress -> {
                            val now = System.currentTimeMillis()
                            if (now - lastNotifiedTime >= 400 || state.percentage >= 99) {
                                lastNotifiedTime = now
                                DownloadNotificationHelper.notifyProgress(context, id, title, state)
                            }

                            // Update global active downloads map
                            val currentMap = _activeDownloadsList.value.toMutableMap()
                            currentMap[id] = Pair(title, state)
                            _activeDownloadsList.value = currentMap
                        }
                        is DownloadState.Success -> {
                            DownloadNotificationHelper.notifySuccess(context, id, title)
                            val currentMap = _activeDownloadsList.value.toMutableMap()
                            currentMap.remove(id)
                            _activeDownloadsList.value = currentMap
                        }
                        is DownloadState.Error -> {
                            DownloadNotificationHelper.notifyError(context, id, title, state.message)
                            val currentMap = _activeDownloadsList.value.toMutableMap()
                            currentMap.remove(id)
                            _activeDownloadsList.value = currentMap
                        }
                        DownloadState.Idle -> {
                            val currentMap = _activeDownloadsList.value.toMutableMap()
                            currentMap.remove(id)
                            _activeDownloadsList.value = currentMap
                        }
                    }
                }
            }

            try {
                val downloader = LectureDownloader(context)
                downloader.downloadLecture(
                    id = id,
                    title = title,
                    chapterId = chapterId,
                    url = url,
                    formatSelector = formatSelector,
                    isAudioOnly = isAudioOnly,
                    progressFlow = flow
                )
            } catch (e: CancellationException) {
                Log.d(TAG, "Download job cancelled for $id")
                flow.value = DownloadState.Idle
                DownloadNotificationHelper.cancelNotification(context, id)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in download job: ${e.message}", e)
                flow.value = DownloadState.Error(e.localizedMessage ?: "Download failed")
            } finally {
                observerJob.cancel()
                activeJobs.remove(id)
                activeTitles.remove(id)
            }
        }

        activeJobs[id] = job
    }

    fun cancelDownload(context: Context, id: String, title: String) {
        val job = activeJobs.remove(id)
        job?.cancel()

        activeTitles.remove(id)
        val flow = downloadStates[id]
        if (flow != null) {
            flow.value = DownloadState.Idle
        }

        val currentMap = _activeDownloadsList.value.toMutableMap()
        currentMap.remove(id)
        _activeDownloadsList.value = currentMap

        DownloadNotificationHelper.cancelNotification(context, id)

        // Clean up partial artifacts
        appScope.launch {
            try {
                val lecturesDir = LocalLectureScanner.getLecturesDirectory(context)
                val sanitized = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                listOf(
                    File(lecturesDir, "$sanitized.mp4.part"),
                    File(lecturesDir, "$sanitized.m4a.part"),
                    File(lecturesDir, "${sanitized}_vid.part"),
                    File(lecturesDir, "${sanitized}_aud.part")
                ).forEach { part ->
                    if (part.exists()) part.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up partial download files: ${e.message}")
            }
        }
    }

    fun isDownloading(id: String): Boolean = activeJobs.containsKey(id)
}
