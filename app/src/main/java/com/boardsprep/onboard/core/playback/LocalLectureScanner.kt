package com.boardsprep.onboard.core.playback

import android.content.Context
import com.boardsprep.onboard.data.models.Lecture
import java.io.File

object LocalLectureScanner {

    private val SUPPORTED_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "3gp")

    /**
     * Returns the dedicated lectures directory on external app storage
     */
    fun getLecturesDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "lectures")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Scans the lectures directory for video files.
     * Supports nested folders matching chapter IDs or names.
     */
    fun scanLocalLectures(context: Context, chapterIdFilter: String? = null): List<Lecture> {
        val lecturesDir = getLecturesDirectory(context)
        val result = mutableListOf<Lecture>()

        if (!lecturesDir.exists() || !lecturesDir.isDirectory) {
            return emptyList()
        }

        fun scanRecursive(dir: File, currentChapterId: String) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    scanRecursive(file, file.name)
                } else if (file.isFile && SUPPORTED_VIDEO_EXTENSIONS.contains(file.extension.lowercase())) {
                    if (file.name.startsWith(".")) continue

                    val cleanTitle = file.nameWithoutExtension
                        .replace("_", " ")
                        .replace("-", " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    if (cleanTitle.isBlank()) continue

                    val targetChapter = if (currentChapterId.isNotEmpty()) currentChapterId else "custom"
                    val isCustom = targetChapter.equals("custom", ignoreCase = true)

                    val matchesFilter = chapterIdFilter == null ||
                        targetChapter.equals(chapterIdFilter, ignoreCase = true) ||
                        (!isCustom && chapterIdFilter.contains(targetChapter, ignoreCase = true)) ||
                        cleanTitle.contains(chapterIdFilter, ignoreCase = true) ||
                        chapterIdFilter.contains(cleanTitle, ignoreCase = true)

                    if (matchesFilter) {
                        result.add(
                            Lecture(
                                id = "local_${Math.abs(file.absolutePath.hashCode())}",
                                chapterId = if (!isCustom) targetChapter else (chapterIdFilter ?: "local"),
                                title = cleanTitle,
                                localFilePath = file.absolutePath,
                                streamUrl = file.toURI().toString(),
                                isDownloaded = true,
                                isUserAdded = true,
                                author = "Local / User Added"
                            )
                        )
                    }
                }
            }
        }

        scanRecursive(lecturesDir, "")
        return result
    }
}
