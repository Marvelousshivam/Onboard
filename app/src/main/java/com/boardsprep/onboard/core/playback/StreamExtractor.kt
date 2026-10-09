package com.boardsprep.onboard.core.playback

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ExtractedStream(
    val title: String,
    val streamUrl: String,
    val quality: String,
    val isHls: Boolean = false
)

data class QualityStreamOption(
    val qualityLabel: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val isProgressive: Boolean,
    val approxTotalBytes: Long,
    val isAudioOnly: Boolean = false
)

data class ResolvedMediaDetails(
    val videoId: String,
    val title: String,
    val durationSeconds: Long,
    val availableQualities: List<QualityStreamOption>
)

object StreamExtractor {

    private const val TAG = "StreamExtractor"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun extractVideoId(url: String): String? {
        val pattern = Pattern.compile("(?:youtu\\.be/|v=|/embed/|watch\\?v=|&v=)([^#&?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Resolves all available progressive and adaptive streams directly from YouTube
     * using the zero-botguard VISIONOS InnerTube client specification.
     * Avoids third-party proxy domains that fail DNS resolution or get blocked.
     */
    suspend fun resolveMediaDetails(youtubeUrlOrId: String): ResolvedMediaDetails? = withContext(Dispatchers.IO) {
        val videoId = if (youtubeUrlOrId.length == 11 && !youtubeUrlOrId.contains("/")) {
            youtubeUrlOrId
        } else {
            extractVideoId(youtubeUrlOrId) ?: return@withContext null
        }

        try {
            // Step 1: Query YouTube Watch page to extract Title, Visitor Data, and Signature Timestamp
            val watchRequest = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            var visitorId = ""
            var signatureTimestamp = 20732
            var videoTitle = "Board Video Lecture"

            httpClient.newCall(watchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""

                    // Extract title
                    val titleMatcher = Pattern.compile("<title>(.+?)</title>").matcher(html)
                    if (titleMatcher.find()) {
                        videoTitle = titleMatcher.group(1)?.replace(" - YouTube", "")?.trim() ?: videoTitle
                    }

                    // Extract visitorData
                    val visitorMatcher = Pattern.compile("\"VISITOR_DATA\":\"([^\"]+)\"").matcher(html)
                    if (visitorMatcher.find()) {
                        visitorId = visitorMatcher.group(1) ?: ""
                    } else {
                        val altVisitorMatcher = Pattern.compile("visitorData[\"':\\s]+([a-zA-Z0-9%_-]+)").matcher(html)
                        if (altVisitorMatcher.find()) {
                            visitorId = altVisitorMatcher.group(1) ?: ""
                        }
                    }

                    // Extract signatureTimestamp
                    val stsMatcher = Pattern.compile("\"signatureTimestamp\":(\\d+)").matcher(html)
                    if (stsMatcher.find()) {
                        signatureTimestamp = stsMatcher.group(1)?.toIntOrNull() ?: 20732
                    }
                }
            }

            // Step 2: Query InnerTube player with VISIONOS client (bypasses bot challenges & cipher)
            val clientContext = JsonObject().apply {
                val clientObj = JsonObject().apply {
                    addProperty("clientName", "VISIONOS")
                    addProperty("clientVersion", "1.02")
                    addProperty("deviceMake", "Apple")
                    addProperty("deviceModel", "RealityDevice17,1")
                    addProperty("userAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                    addProperty("osName", "visionOS")
                    addProperty("osVersion", "26.5.23O471")
                    addProperty("hl", "en")
                    addProperty("timeZone", "UTC")
                    addProperty("utcOffsetMinutes", 0)
                }
                add("client", clientObj)
            }

            val playbackContext = JsonObject().apply {
                val contentCtx = JsonObject().apply {
                    addProperty("html5Preference", "HTML5_PREF_WANTS")
                    addProperty("signatureTimestamp", signatureTimestamp)
                }
                add("contentPlaybackContext", contentCtx)
            }

            val requestBodyJson = JsonObject().apply {
                add("context", clientContext)
                addProperty("videoId", videoId)
                add("playbackContext", playbackContext)
                addProperty("contentCheckOk", true)
                addProperty("racyCheckOk", true)
            }

            val postBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
            val playerRequestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("X-Youtube-Client-Name", "101")
                .header("X-Youtube-Client-Version", "1.02")
                .header("Origin", "https://www.youtube.com")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")

            if (visitorId.isNotBlank()) {
                playerRequestBuilder.header("X-Goog-Visitor-Id", visitorId)
            }

            val playerResponse = httpClient.newCall(playerRequestBuilder.post(postBody).build()).execute()
            if (!playerResponse.isSuccessful) {
                Log.e(TAG, "InnerTube request failed: ${playerResponse.code}")
                return@withContext null
            }

            val responseBody = playerResponse.body?.string() ?: return@withContext null
            val rootJson = JsonParser.parseString(responseBody).asJsonObject

            val playability = rootJson.getAsJsonObject("playabilityStatus")
            val status = playability?.get("status")?.asString ?: ""
            if (!status.equals("OK", ignoreCase = true)) {
                Log.w(TAG, "Playability status is not OK: $status")
            }

            // Extract basic info title if available
            val videoDetails = rootJson.getAsJsonObject("videoDetails")
            if (videoDetails != null && videoDetails.has("title")) {
                videoTitle = videoDetails.get("title").asString
            }
            val durationSec = videoDetails?.get("lengthSeconds")?.asLong ?: 0L

            val streamingData = rootJson.getAsJsonObject("streamingData") ?: return@withContext null

            // 1. Gather MP4 audio and video streams (AAC audio + H.264/AVC video for native MediaMuxer)
            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            val audioStreams = mutableListOf<JsonObject>()
            val videoStreams = mutableListOf<JsonObject>()

            if (adaptiveFormats != null) {
                for (elem in adaptiveFormats) {
                    val fmt = elem.asJsonObject
                    val mime = fmt.get("mimeType")?.asString ?: ""
                    val url = fmt.get("url")?.asString ?: ""
                    if (url.isBlank()) continue

                    // Android native MediaMuxer strictly requires AAC (audio/mp4) audio
                    // and AVC/H.264 (video/mp4) video for MP4 containers.
                    if (mime.contains("audio/mp4") || mime.contains("mp4a")) {
                        audioStreams.add(fmt)
                    } else if (mime.contains("video/mp4") && (mime.contains("avc") || !mime.contains("vp9"))) {
                        videoStreams.add(fmt)
                    }
                }

                // Fallbacks if strict AAC or AVC was absent
                if (audioStreams.isEmpty()) {
                    for (elem in adaptiveFormats) {
                        val fmt = elem.asJsonObject
                        val mime = fmt.get("mimeType")?.asString ?: ""
                        val url = fmt.get("url")?.asString ?: ""
                        if (url.isNotBlank() && mime.startsWith("audio/")) {
                            audioStreams.add(fmt)
                        }
                    }
                }

                if (videoStreams.isEmpty()) {
                    for (elem in adaptiveFormats) {
                        val fmt = elem.asJsonObject
                        val mime = fmt.get("mimeType")?.asString ?: ""
                        val url = fmt.get("url")?.asString ?: ""
                        if (url.isNotBlank() && mime.startsWith("video/")) {
                            videoStreams.add(fmt)
                        }
                    }
                }
            }

            // Pick the best available AAC audio stream (highest bitrate)
            val bestAudio = audioStreams.maxByOrNull { it.get("bitrate")?.asInt ?: 0 }
            val bestAudioUrl = bestAudio?.get("url")?.asString
            val bestAudioBytes = bestAudio?.get("contentLength")?.asLong ?: (durationSec * 16000L)

            val qualityOptions = mutableListOf<QualityStreamOption>()

            // 2. Add Audio-Only Option
            if (bestAudioUrl != null) {
                qualityOptions.add(
                    QualityStreamOption(
                        qualityLabel = "Audio Lecture (M4A)",
                        videoUrl = null,
                        audioUrl = bestAudioUrl,
                        isProgressive = true,
                        approxTotalBytes = bestAudioBytes,
                        isAudioOnly = true
                    )
                )
            }

            // 3. Match video streams for 1080p, 720p, 480p, 360p
            val desiredQualities = listOf("1080p", "720p", "480p", "360p")
            for (q in desiredQualities) {
                val matchingVideo = videoStreams.firstOrNull {
                    val label = it.get("qualityLabel")?.asString ?: ""
                    label.startsWith(q, ignoreCase = true)
                }

                if (matchingVideo != null) {
                    val vUrl = matchingVideo.get("url")?.asString ?: ""
                    val vBytes = matchingVideo.get("contentLength")?.asLong ?: (durationSec * 120000L)
                    qualityOptions.add(
                        QualityStreamOption(
                            qualityLabel = if (q == "720p") "720p (HD) [Recommended]" else "$q (MP4)",
                            videoUrl = vUrl,
                            audioUrl = bestAudioUrl,
                            isProgressive = false,
                            approxTotalBytes = vBytes + bestAudioBytes,
                            isAudioOnly = false
                        )
                    )
                }
            }

            // 4. Check for direct progressive formats (itag 18 / 22 with both audio & video)
            val progressiveFormats = streamingData.getAsJsonArray("formats")
            if (progressiveFormats != null) {
                for (elem in progressiveFormats) {
                    val fmt = elem.asJsonObject
                    val pUrl = fmt.get("url")?.asString ?: ""
                    if (pUrl.isNotBlank()) {
                        val qLabel = fmt.get("qualityLabel")?.asString ?: "360p"
                        val cLen = fmt.get("contentLength")?.asLong ?: (durationSec * 45000L)
                        qualityOptions.add(
                            QualityStreamOption(
                                qualityLabel = "$qLabel (Data Saver)",
                                videoUrl = pUrl,
                                audioUrl = null,
                                isProgressive = true,
                                approxTotalBytes = cLen,
                                isAudioOnly = false
                            )
                        )
                        break
                    }
                }
            }

            return@withContext ResolvedMediaDetails(
                videoId = videoId,
                title = videoTitle,
                durationSeconds = durationSec,
                availableQualities = qualityOptions
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error resolving YouTube stream: ${e.message}", e)
            null
        }
    }

    /**
     * Backward-compatible stream resolver returning direct video stream for ExoPlayer / simple callers
     */
    suspend fun resolveStream(youtubeUrlOrId: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val details = resolveMediaDetails(youtubeUrlOrId) ?: return@withContext null
        val topQuality = details.availableQualities.firstOrNull { !it.isAudioOnly }
            ?: details.availableQualities.firstOrNull()

        if (topQuality != null) {
            val url = topQuality.videoUrl ?: topQuality.audioUrl ?: return@withContext null
            ExtractedStream(
                title = details.title,
                streamUrl = url,
                quality = topQuality.qualityLabel,
                isHls = false
            )
        } else null
    }
}
