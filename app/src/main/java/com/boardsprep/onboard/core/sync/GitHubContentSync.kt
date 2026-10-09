package com.boardsprep.onboard.core.sync

import android.content.Context
import com.boardsprep.onboard.data.models.Lecture
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class GitHubManifest(
    @SerializedName(value = "version", alternate = ["v"]) val version: Int = 1,
    @SerializedName(value = "last_updated", alternate = ["lastUpdated"]) val lastUpdated: String = "2026-10-04",
    @SerializedName(value = "github_repo", alternate = ["githubRepo"]) val githubRepo: String = "gtxPrime/BoardsPrep-Content",
    @SerializedName(value = "cdn_base_url", alternate = ["cdnBaseUrl"]) val cdnBaseUrl: String = "https://cdn.jsdelivr.net/gh/gtxPrime/BoardsPrep-Content@main/",
    @SerializedName(value = "raw_base_url", alternate = ["rawBaseUrl"]) val rawBaseUrl: String = "https://raw.githubusercontent.com/gtxPrime/BoardsPrep-Content/main/",
    @SerializedName(value = "chapters_notes_map", alternate = ["chaptersNotesMap"]) val chaptersNotesMap: Map<String, String> = emptyMap(),
    @SerializedName(value = "extra_lectures", alternate = ["extraLectures"]) val extraLectures: List<RemoteLectureItem> = emptyList()
)

@Keep
data class RemoteLectureItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName(value = "youtube_id", alternate = ["youtubeId"]) val youtubeId: String,
    @SerializedName(value = "duration_text", alternate = ["durationText"]) val durationText: String,
    @SerializedName(value = "chapter_name", alternate = ["chapterName"]) val chapterName: String
)

object GitHubContentSync {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Checks if a custom GitHub repo or CDN manifest is available and fetches updated resources
     */
    suspend fun fetchRemoteManifest(context: Context, repoOwnerAndName: String = "gtxPrime/BoardsPrep-Content"): GitHubManifest? = withContext(Dispatchers.IO) {
        val url = "https://raw.githubusercontent.com/$repoOwnerAndName/main/manifest.json"
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return@withContext null
                val manifest = gson.fromJson(json, GitHubManifest::class.java)

                // Save cached copy
                val cacheFile = File(context.filesDir, "remote_manifest.json")
                cacheFile.writeText(json)
                return@withContext manifest
            }
        } catch (e: Exception) {
            // Load cached version if available
            val cacheFile = File(context.filesDir, "remote_manifest.json")
            if (cacheFile.exists()) {
                try {
                    return@withContext gson.fromJson(cacheFile.readText(), GitHubManifest::class.java)
                } catch (_: Exception) {}
            }
        }
        null
    }

    /**
     * Constructs a fast CDN URL for a PDF stored on GitHub via jsDelivr
     */
    fun getCdnPdfUrl(repoOwnerAndName: String, relativePath: String): String {
        return "https://cdn.jsdelivr.net/gh/$repoOwnerAndName@main/$relativePath"
    }
}
