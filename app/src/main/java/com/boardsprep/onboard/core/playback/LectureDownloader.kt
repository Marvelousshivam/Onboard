package com.boardsprep.onboard.core.playback

import android.content.Context
import com.boardsprep.onboard.data.local.OnboardDatabase
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val percentage: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Success(val localFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class LectureDownloader(private val context: Context) {

    private val db = OnboardDatabase.getInstance(context)

    suspend fun downloadLecture(
        id: String,
        title: String,
        chapterId: String,
        url: String,
        formatSelector: String, // e.g. "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best"
        isAudioOnly: Boolean,
        progressFlow: MutableStateFlow<DownloadState>
    ) = withContext(Dispatchers.IO) {
        try {
            progressFlow.value = DownloadState.Progress(0, 0, 0)
            val lecturesDir = LocalLectureScanner.getLecturesDirectory(context)
            val sanitizedTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val extension = if (isAudioOnly) "m4a" else "mp4"
            val targetFile = File(lecturesDir, "$sanitizedTitle.$extension")

            // If it already exists, just return success
            if (targetFile.exists() && targetFile.length() > 0) {
                progressFlow.value = DownloadState.Success(targetFile)
                return@withContext
            }

            val request = YoutubeDLRequest(url)
            request.addOption("-f", formatSelector)
            request.addOption("-o", targetFile.absolutePath)
            request.addOption("--no-mtime")
            request.addOption("--no-update")
            request.addOption("--no-check-certificates")
            request.addOption("--extractor-args", "youtube:player_client=android")
            
            // Allow merging with ffmpeg if needed
            if (!isAudioOnly) {
                request.addOption("--merge-output-format", "mp4")
            }

            try {
                YoutubeDL.getInstance().execute(request, "LectureDownload") { progress, etaInSeconds, line ->
                    val p = progress.toInt()
                    progressFlow.value = DownloadState.Progress(p, p.toLong(), 100L)
                }
            } catch (dlEx: Exception) {
                val msg = dlEx.message.orEmpty()
                if (msg.contains("403") || msg.contains("older than 90 days") || msg.contains("SABR")) {
                    // Attempt on-the-fly yt-dlp engine update
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
                        // Retry with updated binary
                        YoutubeDL.getInstance().execute(request, "LectureDownload") { progress, etaInSeconds, line ->
                            val p = progress.toInt()
                            progressFlow.value = DownloadState.Progress(p, p.toLong(), 100L)
                        }
                    } catch (_: Exception) {
                        throw dlEx
                    }
                } else {
                    throw dlEx
                }
            }

            // Verify if downloaded
            if (!targetFile.exists()) {
                progressFlow.value = DownloadState.Error("Download completed but file not found.")
                return@withContext
            }

            // Save to database
            db.downloadsDao().saveDownload(
                DownloadedFileEntity(
                    id = id,
                    title = title,
                    chapterId = chapterId,
                    fileType = if (isAudioOnly) "audio" else "video",
                    localPath = targetFile.absolutePath,
                    fileSizeBytes = targetFile.length(),
                    downloadedAt = System.currentTimeMillis()
                )
            )

            progressFlow.value = DownloadState.Success(targetFile)

        } catch (e: Exception) {
            progressFlow.value = DownloadState.Error(e.localizedMessage ?: "Download failed")
        }
    }
}
