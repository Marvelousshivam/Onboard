package com.boardsprep.onboard.data.repository

import android.content.Context
import com.boardsprep.onboard.data.models.Lecture
import com.boardsprep.onboard.data.models.Quiz
import com.boardsprep.onboard.data.models.QuizItem
import com.boardsprep.onboard.data.models.ResourceItem
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class CurriculumManifest(
    val version: Int = 2,
    val lastUpdated: String = "",
    val baseRawUrl: String = "",
    val subjects: List<RemoteSubject> = emptyList(),
    val samplePapers: List<RemoteSamplePaper> = emptyList()
)

data class RemoteSubject(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val stream: String = "",
    val totalTheoryMarks: Int = 0,
    val totalPracticalMarks: Int = 0,
    val iconName: String = "",
    val chapters: List<RemoteChapter> = emptyList(),
    val writingSectionNotes: List<RemoteResource> = emptyList()
)

data class RemoteChapter(
    val id: String = "",
    val subjectId: String = "",
    val number: Int = 0,
    val name: String = "",
    val folder: String = "",
    val weightageMarks: Int = 0,
    val keyTopics: List<String> = emptyList(),
    val ncertPdfUrl: String = "",
    val notes: List<RemoteResource> = emptyList(),
    val dpps: List<RemoteResource> = emptyList(),
    val quizzes: List<RemoteQuiz> = emptyList(),
    val videos: List<RemoteVideo> = emptyList()
)

data class RemoteResource(
    val title: String = "",
    val filename: String = "",
    val url: String = ""
)

data class RemoteQuiz(
    val id: String = "",
    val title: String = "",
    val filename: String = "",
    val url: String = "",
    val totalQuestions: Int = 10
)

data class RemoteVideo(
    val id: String = "",
    val title: String = "",
    val youtubeUrl: String = "",
    val duration: String = "",
    val author: String = "Curated"
)

data class RemoteSamplePaper(
    val title: String = "",
    val filename: String = "",
    val subject: String = "",
    val type: String = "",
    val url: String = ""
)

class CurriculumManifestManager(private val context: Context) {

    companion object {
        const val MANIFEST_URL = "https://raw.githubusercontent.com/Marvelousshivam/OnBoard-files/main/manifest.json"
        @Volatile
        private var memoryCache: CurriculumManifest? = null
    }

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getManifestCacheFile(): File {
        return File(context.filesDir, "manifest_cache.json")
    }

    private fun getQuizzesCacheDir(): File {
        val dir = File(context.filesDir, "cached_quizzes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun syncManifest(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OnBoard/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body?.string() ?: return@withContext false
            if (body.isBlank() || !body.contains("subjects")) return@withContext false

            val parsed = gson.fromJson(body, CurriculumManifest::class.java)
            if (parsed != null && parsed.subjects.isNotEmpty()) {
                memoryCache = parsed
                getManifestCacheFile().writeText(body, Charsets.UTF_8)
                return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun getManifest(): CurriculumManifest? {
        val cachedMem = memoryCache
        if (cachedMem != null) return cachedMem

        val cacheFile = getManifestCacheFile()
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val json = cacheFile.readText(Charsets.UTF_8)
                val parsed = gson.fromJson(json, CurriculumManifest::class.java)
                if (parsed != null && parsed.subjects.isNotEmpty()) {
                    memoryCache = parsed
                    return parsed
                }
            } catch (_: Exception) {}
        }

        // Bundled baseline manifest in assets
        try {
            context.assets.open("manifest.json").use { stream ->
                InputStreamReader(stream, "UTF-8").use { reader ->
                    val parsed = gson.fromJson(reader, CurriculumManifest::class.java)
                    if (parsed != null && parsed.subjects.isNotEmpty()) {
                        memoryCache = parsed
                        return parsed
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun getSubject(subjectId: String): RemoteSubject? {
        return getManifest()?.subjects?.firstOrNull { it.id.equals(subjectId, ignoreCase = true) }
    }

    fun getChapter(subjectId: String, chapterNameOrId: String): RemoteChapter? {
        val subject = getSubject(subjectId) ?: return null
        return subject.chapters.firstOrNull {
            it.id.equals(chapterNameOrId, ignoreCase = true) ||
            it.name.equals(chapterNameOrId, ignoreCase = true)
        }
    }

    suspend fun fetchRemoteQuiz(urlOrName: String): Quiz? = withContext(Dispatchers.IO) {
        if (urlOrName.isBlank()) return@withContext null

        // 1. If it's a remote URL from GitHub
        if (urlOrName.startsWith("http://") || urlOrName.startsWith("https://")) {
            val hash = Math.abs(urlOrName.hashCode()).toString()
            val fileName = "${hash}_${urlOrName.substringAfterLast("/")}"
            val cachedFile = File(getQuizzesCacheDir(), fileName)

            if (cachedFile.exists() && cachedFile.length() > 0) {
                try {
                    val parsed = gson.fromJson(cachedFile.readText(Charsets.UTF_8), Quiz::class.java)
                    if (parsed != null) return@withContext parsed
                } catch (_: Exception) {}
            }

            try {
                val request = Request.Builder()
                    .url(urlOrName)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OnBoard/1.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return@withContext null
                    cachedFile.writeText(json, Charsets.UTF_8)
                    val parsed = gson.fromJson(json, Quiz::class.java)
                    if (parsed != null) return@withContext parsed
                }
            } catch (_: Exception) {}
        }

        // 2. Local asset check
        val cleanName = urlOrName.substringAfterLast("/").substringAfterLast("\\")
        try {
            val assetPath = "quizzes/english/$cleanName"
            context.assets.open(assetPath).use { stream ->
                InputStreamReader(stream, "UTF-8").use { reader ->
                    val parsed = gson.fromJson(reader, Quiz::class.java)
                    if (parsed != null) return@withContext parsed
                }
            }
        } catch (_: Exception) {}

        null
    }
}
