package com.boardsprep.onboard.core.playback

import android.content.Context
import android.util.Log
import com.boardsprep.onboard.data.local.OnboardDatabase
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class DownloadStage(val label: String) {
    INITIALIZING("Preparing download..."),
    DOWNLOADING_VIDEO("Downloading video track..."),
    DOWNLOADING_AUDIO("Downloading audio track..."),
    MUXING("Merging video & audio..."),
    COMPLETED("Download complete")
}

sealed class DownloadState {
    object Idle : DownloadState()

    data class Progress(
        val percentage: Int, // 0 to 100
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L,
        val etaSeconds: Long = -1L,
        val stage: DownloadStage = DownloadStage.INITIALIZING,
        val qualityLabel: String = ""
    ) : DownloadState() {
        val speedFormatted: String
            get() = when {
                speedBytesPerSec <= 0L -> "-- KB/s"
                speedBytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
                else -> "${speedBytesPerSec / 1024} KB/s"
            }

        val etaFormatted: String
            get() = when {
                etaSeconds < 0 -> "-- remaining"
                etaSeconds < 60 -> "${etaSeconds}s left"
                etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s left"
                else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m left"
            }

        val sizeFormatted: String
            get() {
                val dlMb = bytesDownloaded / (1024.0 * 1024.0)
                val totMb = totalBytes / (1024.0 * 1024.0)
                return if (totalBytes > 0) {
                    String.format(Locale.US, "%.1f MB / %.1f MB", dlMb, totMb)
                } else {
                    String.format(Locale.US, "%.1f MB", dlMb)
                }
            }
    }

    data class Success(val localFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Resilient, zero-native-bloat lecture downloader.
 * Connects directly to YouTube servers via VISIONOS InnerTube extraction,
 * downloads streams over HTTP with resumable Range requests and retries,
 * and muxes high-res video and audio natively with Android's built-in MediaMuxer.
 * 0 KB native binary footprint!
 */
class LectureDownloader(private val context: Context) {

    private val db = OnboardDatabase.getInstance(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "LectureDownloader"
    }

    suspend fun downloadLecture(
        id: String,
        title: String,
        chapterId: String,
        url: String,
        formatSelector: String = "",
        isAudioOnly: Boolean = false,
        progressFlow: MutableStateFlow<DownloadState>
    ) = withContext(Dispatchers.IO) {
        val lecturesDir = LocalLectureScanner.getLecturesDirectory(context)
        val sanitizedTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val extension = if (isAudioOnly) "m4a" else "mp4"
        val targetFile = File(lecturesDir, "$sanitizedTitle.$extension")

        // If it already exists, report success immediately
        if (targetFile.exists() && targetFile.length() > 0) {
            progressFlow.value = DownloadState.Success(targetFile)
            return@withContext
        }

        try {
            progressFlow.value = DownloadState.Progress(
                percentage = 1,
                bytesDownloaded = 0,
                totalBytes = 0,
                stage = DownloadStage.INITIALIZING,
                qualityLabel = if (isAudioOnly) "Audio" else "Video"
            )

            val isYouTube = StreamExtractor.extractVideoId(url) != null

            if (isYouTube) {
                val mediaDetails = StreamExtractor.resolveMediaDetails(url)
                if (mediaDetails == null || mediaDetails.availableQualities.isEmpty()) {
                    progressFlow.value = DownloadState.Error("Unable to resolve direct stream from YouTube. Please check network.")
                    return@withContext
                }

                // Choose the appropriate quality stream option
                val chosenOption: QualityStreamOption = if (isAudioOnly) {
                    mediaDetails.availableQualities.firstOrNull { it.isAudioOnly }
                        ?: mediaDetails.availableQualities.first()
                } else {
                    // Try to match preferred format string (1080, 720, 480, 360)
                    val preferred = mediaDetails.availableQualities.firstOrNull { opt ->
                        !opt.isAudioOnly && formatSelector.isNotBlank() && opt.qualityLabel.contains(formatSelector.take(4), ignoreCase = true)
                    }
                    preferred ?: mediaDetails.availableQualities.firstOrNull { !it.isAudioOnly }
                    ?: mediaDetails.availableQualities.first()
                }

                Log.d(TAG, "Selected download quality: ${chosenOption.qualityLabel}, isProgressive: ${chosenOption.isProgressive}")

                if (chosenOption.isAudioOnly || (chosenOption.isProgressive && chosenOption.audioUrl == null)) {
                    // Single direct progressive stream (Audio M4A or 360p progressive)
                    val streamUrl = chosenOption.audioUrl ?: chosenOption.videoUrl ?: run {
                        progressFlow.value = DownloadState.Error("Stream URL missing in selected quality")
                        return@withContext
                    }

                    val tempFile = File(lecturesDir, "$sanitizedTitle.$extension.part")
                    val totalExpected = chosenOption.approxTotalBytes

                    downloadHttpStream(
                        streamUrl = streamUrl,
                        destinationFile = tempFile,
                        expectedTotalBytes = totalExpected,
                        progressBase = 0,
                        progressRange = 100,
                        stage = if (chosenOption.isAudioOnly) DownloadStage.DOWNLOADING_AUDIO else DownloadStage.DOWNLOADING_VIDEO,
                        qualityLabel = chosenOption.qualityLabel,
                        overallTotalBytes = totalExpected,
                        overallBytesOffset = 0L,
                        progressFlow = progressFlow
                    )

                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                    } else {
                        progressFlow.value = DownloadState.Error("Downloaded file is empty")
                        return@withContext
                    }

                } else {
                    // Adaptive separate video + audio streams: download each and mux with MediaMuxer
                    val videoStreamUrl = chosenOption.videoUrl ?: run {
                        progressFlow.value = DownloadState.Error("Video stream URL missing")
                        return@withContext
                    }
                    val audioStreamUrl = chosenOption.audioUrl ?: run {
                        progressFlow.value = DownloadState.Error("Audio stream URL missing")
                        return@withContext
                    }

                    val qTag = chosenOption.qualityLabel.filter { it.isLetterOrDigit() }
                    val tempVideoFile = File(lecturesDir, "${sanitizedTitle}_${qTag}_vid.part")
                    val tempAudioFile = File(lecturesDir, "${sanitizedTitle}_${qTag}_aud.part")

                    // Estimate parts based on total approx
                    val totalCombined = chosenOption.approxTotalBytes
                    // Typical ratio: video ~82%, audio ~18%
                    val estVideoBytes = (totalCombined * 0.82).toLong()
                    val estAudioBytes = totalCombined - estVideoBytes

                    try {
                        // 1. Download Video part (0% -> 75%)
                        Log.d(TAG, "Downloading video stream part...")
                        downloadHttpStream(
                            streamUrl = videoStreamUrl,
                            destinationFile = tempVideoFile,
                            expectedTotalBytes = estVideoBytes,
                            progressBase = 0,
                            progressRange = 75,
                            stage = DownloadStage.DOWNLOADING_VIDEO,
                            qualityLabel = chosenOption.qualityLabel,
                            overallTotalBytes = totalCombined,
                            overallBytesOffset = 0L,
                            progressFlow = progressFlow
                        )

                        val actualVideoBytes = tempVideoFile.length()

                        // 2. Download Audio part (75% -> 92%)
                        Log.d(TAG, "Downloading audio stream part...")
                        downloadHttpStream(
                            streamUrl = audioStreamUrl,
                            destinationFile = tempAudioFile,
                            expectedTotalBytes = estAudioBytes,
                            progressBase = 75,
                            progressRange = 17,
                            stage = DownloadStage.DOWNLOADING_AUDIO,
                            qualityLabel = chosenOption.qualityLabel,
                            overallTotalBytes = actualVideoBytes + estAudioBytes,
                            overallBytesOffset = actualVideoBytes,
                            progressFlow = progressFlow
                        )

                        // 3. Mux Video + Audio natively via MediaMuxer (92% -> 100%)
                        val finalTotalBytes = tempVideoFile.length() + tempAudioFile.length()
                        progressFlow.value = DownloadState.Progress(
                            percentage = 93,
                            bytesDownloaded = finalTotalBytes,
                            totalBytes = finalTotalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = 2L,
                            stage = DownloadStage.MUXING,
                            qualityLabel = chosenOption.qualityLabel
                        )

                        Log.d(TAG, "Muxing video and audio tracks with native MediaMuxer...")
                        val muxSuccess = MediaMuxerHelper.muxVideoAndAudio(tempVideoFile, tempAudioFile, targetFile)

                        if (!muxSuccess || !targetFile.exists() || targetFile.length() == 0L) {
                            progressFlow.value = DownloadState.Error("Native media muxing failed")
                            return@withContext
                        }

                        progressFlow.value = DownloadState.Progress(
                            percentage = 100,
                            bytesDownloaded = targetFile.length(),
                            totalBytes = targetFile.length(),
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L,
                            stage = DownloadStage.COMPLETED,
                            qualityLabel = chosenOption.qualityLabel
                        )

                    } finally {
                        // Cleanup intermediate chunk parts
                        if (tempVideoFile.exists()) tempVideoFile.delete()
                        if (tempAudioFile.exists()) tempAudioFile.delete()
                    }
                }

            } else {
                // Non-YouTube direct URL download
                val tempFile = File(lecturesDir, "$sanitizedTitle.$extension.part")
                downloadHttpStream(
                    streamUrl = url,
                    destinationFile = tempFile,
                    expectedTotalBytes = 0L,
                    progressBase = 0,
                    progressRange = 100,
                    stage = DownloadStage.DOWNLOADING_VIDEO,
                    qualityLabel = "Direct Video",
                    overallTotalBytes = 0L,
                    overallBytesOffset = 0L,
                    progressFlow = progressFlow
                )

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                } else {
                    progressFlow.value = DownloadState.Error("Downloaded file is empty")
                    return@withContext
                }
            }

            // Register in Room DB for offline playback in DownloadsScreen
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
            Log.e(TAG, "Download failed: ${e.message}", e)
            progressFlow.value = DownloadState.Error(e.localizedMessage ?: "Download interrupted")
        }
    }

    /**
     * Downloads an HTTP/HTTPS stream to a local destination file using 5MB bounded Range chunks.
     * Bounded chunks bypass YouTube's playback bitrate throttle (which caps unbounded streams to ~68 KB/s),
     * unlocking full 4-10 MB/s line speed with per-chunk resilience and auto-resuming.
     */
    private fun downloadHttpStream(
        streamUrl: String,
        destinationFile: File,
        expectedTotalBytes: Long,
        progressBase: Int,
        progressRange: Int,
        stage: DownloadStage,
        qualityLabel: String,
        overallTotalBytes: Long,
        overallBytesOffset: Long,
        progressFlow: MutableStateFlow<DownloadState>
    ) {
        val chunkSize = 5 * 1024 * 1024L // 5 MB discrete chunks for maximum CDN throughput
        var totalBytes = expectedTotalBytes
        var downloaded = if (destinationFile.exists()) destinationFile.length() else 0L

        // If file is already fully downloaded
        if (totalBytes > 0 && downloaded >= totalBytes) {
            return
        }

        var lastReportTime = 0L
        var windowStartTime = System.currentTimeMillis()
        var bytesAtWindowStart = downloaded

        while (totalBytes <= 0 || downloaded < totalBytes) {
            val chunkStart = downloaded
            val chunkEnd = if (totalBytes > 0) {
                minOf(chunkStart + chunkSize - 1, totalBytes - 1)
            } else {
                chunkStart + chunkSize - 1
            }

            if (totalBytes > 0 && chunkStart >= totalBytes) {
                break
            }

            val maxRetries = 5
            var attempt = 0
            var chunkSuccess = false

            while (attempt < maxRetries && !chunkSuccess) {
                val requestBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Range", "bytes=$chunkStart-$chunkEnd")

                try {
                    httpClient.newCall(requestBuilder.build()).execute().use { response ->
                        val code = response.code

                        // Learn exact total stream size from Content-Range header whenever available
                        // Example: "Content-Range: bytes 0-5242879/63876996" or "Content-Range: bytes */63876996"
                        val contentRange = response.header("Content-Range")
                        if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                            val totalStr = contentRange.substringAfterLast("/").trim()
                            val exactTotal = totalStr.toLongOrNull()
                            if (exactTotal != null && exactTotal > 0) {
                                totalBytes = exactTotal
                            }
                        }

                        if (code == 416) {
                            // HTTP 416: Requested Range Not Satisfiable (stream reached EOF)
                            if (downloaded > 0) {
                                totalBytes = downloaded
                            }
                            chunkSuccess = true
                            return
                        }

                        if (code != 206 && code != 200) {
                            throw Exception("HTTP stream chunk returned code $code")
                        }

                        if (totalBytes <= 0) {
                            val cl = response.body?.contentLength() ?: -1L
                            if (cl > 0) totalBytes = cl
                        }

                        val body = response.body ?: throw Exception("Empty HTTP response body")
                        val appendMode = chunkStart > 0 && code == 206
                        var bytesReadInThisChunk = 0L

                        body.byteStream().use { input ->
                            FileOutputStream(destinationFile, appendMode).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int

                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    bytesReadInThisChunk += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime >= 200 || (totalBytes > 0 && downloaded >= totalBytes)) {
                                        val windowDurationMs = (now - windowStartTime).coerceAtLeast(1L)
                                        val speed = if (windowDurationMs >= 350) {
                                            val deltaBytes = downloaded - bytesAtWindowStart
                                            val calcSpeed = (deltaBytes * 1000L) / windowDurationMs
                                            windowStartTime = now
                                            bytesAtWindowStart = downloaded
                                            calcSpeed.coerceAtLeast(0L)
                                        } else {
                                            (progressFlow.value as? DownloadState.Progress)?.speedBytesPerSec ?: 0L
                                        }

                                        val streamFraction = if (totalBytes > 0) {
                                            (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                        } else 0.5f

                                        val mappedPct = (progressBase + (streamFraction * progressRange).toInt()).coerceIn(0, 99)
                                        val effectiveOverallTotal = if (overallTotalBytes > 0) overallTotalBytes else (overallBytesOffset + totalBytes)
                                        val overallDownloaded = overallBytesOffset + downloaded
                                        val remainingBytes = (effectiveOverallTotal - overallDownloaded).coerceAtLeast(0L)
                                        val eta = if (speed > 0) (remainingBytes / speed) else -1L

                                        progressFlow.value = DownloadState.Progress(
                                            percentage = mappedPct,
                                            bytesDownloaded = overallDownloaded,
                                            totalBytes = effectiveOverallTotal,
                                            speedBytesPerSec = speed,
                                            etaSeconds = eta,
                                            stage = stage,
                                            qualityLabel = qualityLabel
                                        )
                                        lastReportTime = now
                                    }
                                }
                                output.flush()
                            }
                        }

                        chunkSuccess = true
                        if (code == 200) {
                            // Server ignored range and sent full stream
                            totalBytes = downloaded
                        } else if (bytesReadInThisChunk < (chunkEnd - chunkStart + 1)) {
                            // Server sent fewer bytes than chunk requested (reached EOF)
                            totalBytes = downloaded
                        }
                    }
                } catch (e: Exception) {
                    attempt++
                    Log.w(TAG, "Chunk $chunkStart-$chunkEnd failed (attempt $attempt/$maxRetries): ${e.message}")
                    if (attempt >= maxRetries) throw e
                    try {
                        Thread.sleep(600L * attempt)
                    } catch (_: InterruptedException) {
                        return
                    }
                }
            }

            if (totalBytes > 0 && downloaded >= totalBytes) {
                break
            }
        }
    }
}
