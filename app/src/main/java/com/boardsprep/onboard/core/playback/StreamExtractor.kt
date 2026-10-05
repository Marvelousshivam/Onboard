package com.boardsprep.onboard.core.playback

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ExtractedStream(
    val title: String,
    val streamUrl: String,
    val quality: String,
    val isHls: Boolean = false
)

object StreamExtractor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Public privacy-preserving stream extraction instances
    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacydev.net",
        "https://piped-api.lunar.icu"
    )

    fun extractVideoId(url: String): String? {
        val pattern = Pattern.compile("(?:youtu\\.be/|v=|/embed/|watch\\?v=|&v=)([^#&?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Resolves an ad-free direct video stream URL for ExoPlayer
     */
    suspend fun resolveStream(youtubeUrlOrId: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val videoId = if (youtubeUrlOrId.length == 11 && !youtubeUrlOrId.contains("/")) {
            youtubeUrlOrId
        } else {
            extractVideoId(youtubeUrlOrId) ?: return@withContext null
        }

        // Try instances in round-robin / fallback
        for (instance in PIPED_INSTANCES) {
            try {
                val apiUrl = "$instance/streams/$videoId"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: continue
                    val json = JsonParser.parseString(bodyString).asJsonObject

                    val title = json.get("title")?.asString ?: "Video Lecture"

                    // Check for HLS stream first (best adaptive streaming)
                    val hlsUrl = json.get("hls")?.asString
                    if (!hlsUrl.isNullOrEmpty()) {
                        return@withContext ExtractedStream(title, hlsUrl, "Adaptive (HLS)", isHls = true)
                    }

                    // Otherwise check progressive videoStreams
                    if (json.has("videoStreams")) {
                        val videoStreams = json.getAsJsonArray("videoStreams")
                        var bestUrl: String? = null
                        var bestQuality = "720p"

                        for (elem in videoStreams) {
                            val streamObj = elem.asJsonObject
                            val quality = streamObj.get("quality")?.asString ?: ""
                            val url = streamObj.get("url")?.asString ?: ""
                            val videoOnly = streamObj.get("videoOnly")?.asBoolean ?: false

                            // Prefer muxed audio+video or 720p
                            if (!videoOnly && url.isNotEmpty()) {
                                bestUrl = url
                                bestQuality = quality
                                if (quality.contains("720") || quality.contains("1080")) break
                            }
                        }

                        if (!bestUrl.isNullOrEmpty()) {
                            return@withContext ExtractedStream(title, bestUrl, bestQuality, isHls = false)
                        }
                    }
                }
            } catch (ignored: Exception) {
                // Try next instance
            }
        }

        null
    }
}
