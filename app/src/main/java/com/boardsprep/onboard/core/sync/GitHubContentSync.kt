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

data class GitHubManifest(
    val version: Int = 1,
    val lastUpdated: String = "2026-10-04",
    val githubRepo: String = "gtxPrime/BoardsPrep-Content",
    val cdnBaseUrl: String = "https://cdn.jsdelivr.net/gh/gtxPrime/BoardsPrep-Content@main/",
    val rawBaseUrl: String = "https://raw.githubusercontent.com/gtxPrime/BoardsPrep-Content/main/",
    val chaptersNotesMap: Map<String, String> = emptyMap(),
    val extraLectures: List<RemoteLectureItem> = emptyList()
)

data class RemoteLectureItem(
    val id: String,
    val title: String,
    val youtubeId: String,
    val durationText: String,
    val chapterName: String
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
