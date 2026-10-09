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

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class CurriculumManifest(
    @SerializedName(value = "version", alternate = ["v"]) val version: Int = 2,
    @SerializedName(value = "last_updated", alternate = ["lastUpdated"]) val lastUpdated: String = "",
    @SerializedName(value = "base_raw_url", alternate = ["baseRawUrl"]) val baseRawUrl: String = "",
    @SerializedName(value = "subjects") val subjects: List<RemoteSubject> = emptyList(),
    @SerializedName(value = "sample_papers", alternate = ["samplePapers"]) val samplePapers: List<RemoteSamplePaper> = emptyList(),
    @SerializedName(value = "derivations") val derivations: List<RemoteDerivation> = emptyList(),
    @SerializedName(value = "named_reactions", alternate = ["namedReactions"]) val namedReactions: List<RemoteReaction> = emptyList(),
    @SerializedName(value = "formulas") val formulas: List<RemoteFormula> = emptyList()
)

@Keep
data class RemoteDerivation(
    @SerializedName("id") val id: String = "",
    @SerializedName(value = "chapter_id", alternate = ["chapterId"]) val chapterId: String = "",
    @SerializedName(value = "chapter_name", alternate = ["chapterName"]) val chapterName: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("formula") val formula: String = "",
    @SerializedName("steps") val steps: List<String> = emptyList(),
    @SerializedName("importance") val importance: String = "High"
)

@Keep
data class RemoteReaction(
    @SerializedName("id") val id: String = "",
    @SerializedName("chapter") val chapter: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("equation") val equation: String = "",
    @SerializedName("reagents") val reagents: String = "",
    @SerializedName("significance") val significance: String = ""
)

@Keep
data class RemoteFormula(
    @SerializedName("id") val id: String = "",
    @SerializedName(value = "subject_id", alternate = ["subjectId"]) val subjectId: String = "",
    @SerializedName(value = "chapter_id", alternate = ["chapterId"]) val chapterId: String = "",
    @SerializedName(value = "chapter_name", alternate = ["chapterName"]) val chapterName: String = "",
    @SerializedName("topic") val topic: String = "",
    @SerializedName(value = "formula_text", alternate = ["formulaText", "formula"]) val formulaText: String = "",
    @SerializedName("latex") val latex: String = "",
    @SerializedName("notes") val notes: String = ""
)

@Keep
data class FormulasManifest(
    @SerializedName("version") val version: Int = 1,
    @SerializedName(value = "last_updated", alternate = ["lastUpdated"]) val lastUpdated: String = "",
    @SerializedName("derivations") val derivations: List<RemoteDerivation> = emptyList(),
    @SerializedName(value = "named_reactions", alternate = ["namedReactions"]) val namedReactions: List<RemoteReaction> = emptyList(),
    @SerializedName("formulas") val formulas: List<RemoteFormula> = emptyList()
)

@Keep
data class RemoteSubject(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("code") val code: String = "",
    @SerializedName("stream") val stream: String = "",
    @SerializedName(value = "total_theory_marks", alternate = ["totalTheoryMarks"]) val totalTheoryMarks: Int = 0,
    @SerializedName(value = "total_practical_marks", alternate = ["totalPracticalMarks"]) val totalPracticalMarks: Int = 0,
    @SerializedName(value = "icon_name", alternate = ["iconName"]) val iconName: String = "",
    @SerializedName("chapters") val chapters: List<RemoteChapter> = emptyList(),
    @SerializedName(value = "writing_section_notes", alternate = ["writingSectionNotes"]) val writingSectionNotes: List<RemoteResource> = emptyList()
)

@Keep
data class RemoteChapter(
    @SerializedName("id") val id: String = "",
    @SerializedName(value = "subject_id", alternate = ["subjectId"]) val subjectId: String = "",
    @SerializedName("number") val number: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("folder") val folder: String = "",
    @SerializedName(value = "weightage_marks", alternate = ["weightageMarks"]) val weightageMarks: Int = 0,
    @SerializedName(value = "key_topics", alternate = ["keyTopics"]) val keyTopics: List<String> = emptyList(),
    @SerializedName(value = "ncert_pdf_url", alternate = ["ncertPdfUrl"]) val ncertPdfUrl: String = "",
    @SerializedName("notes") val notes: List<RemoteResource> = emptyList(),
    @SerializedName("dpps") val dpps: List<RemoteResource> = emptyList(),
    @SerializedName("quizzes") val quizzes: List<RemoteQuiz> = emptyList(),
    @SerializedName("videos") val videos: List<RemoteVideo> = emptyList()
)

@Keep
data class RemoteResource(
    @SerializedName("title") val title: String = "",
    @SerializedName("filename") val filename: String = "",
    @SerializedName("url") val url: String = ""
)

@Keep
data class RemoteQuiz(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("filename") val filename: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName(value = "total_questions", alternate = ["totalQuestions"]) val totalQuestions: Int = 10
)

@Keep
data class RemoteVideo(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName(value = "youtube_url", alternate = ["youtubeUrl"]) val youtubeUrl: String = "",
    @SerializedName("duration") val duration: String = "",
    @SerializedName("author") val author: String = "Curated"
)

@Keep
data class RemoteSamplePaper(
    @SerializedName("title") val title: String = "",
    @SerializedName("filename") val filename: String = "",
    @SerializedName("subject") val subject: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("url") val url: String = ""
)

class CurriculumManifestManager(private val context: Context) {

    companion object {
        const val MANIFEST_URL = "https://raw.githubusercontent.com/Marvelousshivam/OnBoard-files/main/manifest.json"
        const val FORMULAS_URL = "https://raw.githubusercontent.com/Marvelousshivam/OnBoard-files/main/formulas.json"
        @Volatile
        private var memoryCache: CurriculumManifest? = null
        @Volatile
        private var formulasMemoryCache: FormulasManifest? = null
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

    private fun getFormulasCacheFile(): File {
        return File(context.filesDir, "formulas_cache.json")
    }

    private fun getQuizzesCacheDir(): File {
        val dir = File(context.filesDir, "cached_quizzes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun syncManifest(): Boolean = withContext(Dispatchers.IO) {
        var success = false
        try {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OnBoard/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank() && body.contains("subjects")) {
                    val parsed = gson.fromJson(body, CurriculumManifest::class.java)
                    if (parsed != null && parsed.subjects.isNotEmpty()) {
                        memoryCache = parsed
                        getManifestCacheFile().writeText(body, Charsets.UTF_8)
                        success = true
                    }
                }
            }
        } catch (_: Exception) {}

        // Also sync formulas.json from GitHub
        try {
            val fRequest = Request.Builder()
                .url(FORMULAS_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OnBoard/1.0")
                .build()

            val fResponse = httpClient.newCall(fRequest).execute()
            if (fResponse.isSuccessful) {
                val fBody = fResponse.body?.string()
                if (!fBody.isNullOrBlank()) {
                    val parsedF = gson.fromJson(fBody, FormulasManifest::class.java)
                    if (parsedF != null) {
                        formulasMemoryCache = parsedF
                        getFormulasCacheFile().writeText(fBody, Charsets.UTF_8)
                    }
                }
            }
        } catch (_: Exception) {}

        success
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

    fun getFormulasManifest(): FormulasManifest? {
        val cachedMem = formulasMemoryCache
        if (cachedMem != null) return cachedMem

        // 1. Cached formulas file from GitHub
        val cacheFile = getFormulasCacheFile()
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val json = cacheFile.readText(Charsets.UTF_8)
                val parsed = gson.fromJson(json, FormulasManifest::class.java)
                if (parsed != null && (parsed.derivations.isNotEmpty() || parsed.namedReactions.isNotEmpty() || parsed.formulas.isNotEmpty())) {
                    formulasMemoryCache = parsed
                    return parsed
                }
            } catch (_: Exception) {}
        }

        // 2. Check main manifest if derivations / namedReactions are embedded
        val mainM = getManifest()
        if (mainM != null && (mainM.derivations.isNotEmpty() || mainM.namedReactions.isNotEmpty() || mainM.formulas.isNotEmpty())) {
            val fromMain = FormulasManifest(
                version = mainM.version,
                lastUpdated = mainM.lastUpdated,
                derivations = mainM.derivations,
                namedReactions = mainM.namedReactions,
                formulas = mainM.formulas
            )
            formulasMemoryCache = fromMain
            return fromMain
        }

        // 3. Fallback to bundled assets/formulas.json
        try {
            context.assets.open("formulas.json").use { stream ->
                InputStreamReader(stream, "UTF-8").use { reader ->
                    val parsed = gson.fromJson(reader, FormulasManifest::class.java)
                    if (parsed != null) {
                        formulasMemoryCache = parsed
                        return parsed
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun getDerivations(): List<RemoteDerivation> {
        return getFormulasManifest()?.derivations ?: emptyList()
    }

    fun getNamedReactions(): List<RemoteReaction> {
        return getFormulasManifest()?.namedReactions ?: emptyList()
    }

    fun getAllFormulas(): List<RemoteFormula> {
        return getFormulasManifest()?.formulas ?: emptyList()
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

        // 2. Local asset check across all subjects
        val cleanName = urlOrName.substringAfterLast("/").substringAfterLast("\\")
        val possibleFolders = listOf("english", "biology", "physics", "chemistry", "maths", "physical_education")
        for (folder in possibleFolders) {
            try {
                val assetPath = "quizzes/$folder/$cleanName"
                context.assets.open(assetPath).use { stream ->
                    InputStreamReader(stream, "UTF-8").use { reader ->
                        val parsed = gson.fromJson(reader, Quiz::class.java)
                        if (parsed != null) return@withContext parsed
                    }
                }
            } catch (_: Exception) {}
        }

        null
    }
}
