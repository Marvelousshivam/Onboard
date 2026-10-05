package com.boardsprep.onboard.core.pdf

import android.content.Context
import java.io.File

object LocalDocumentScanner {

    fun getNotesDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "notes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getNcertDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "ncert")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDppDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "dpp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSamplePapersDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "sample_papers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun findSamplePaper(context: Context, filename: String): File? {
        val dir = getSamplePapersDirectory(context)
        val file = File(dir, filename)
        if (file.exists() && file.length() > 0) return file
        return null
    }

    /**
     * Map of chapters to their historical NCERT code filenames and standard clean filenames
     */
    private val chapterToNcertAliases = mapOf(
        // Flamingo
        "the last lesson" to listOf("ch01_the_last_lesson.pdf", "lefl101.pdf"),
        "lost spring" to listOf("ch02_lost_spring.pdf", "lefl102.pdf"),
        "deep water" to listOf("ch03_deep_water.pdf", "lefl103.pdf"),
        "the rattrap" to listOf("ch04_the_rattrap.pdf", "lefl104.pdf"),
        "indigo" to listOf("ch05_indigo.pdf", "lefl105.pdf"),
        "poets and pancakes" to listOf("ch06_poets_and_pancakes.pdf", "lefl106.pdf"),
        "the interview" to listOf("ch07_the_interview.pdf", "lefl107.pdf"),
        "going places" to listOf("ch08_going_places.pdf", "lefl108.pdf"),
        "my mother at sixty-six" to listOf("poem01_my_mother_at_sixty_six.pdf", "lefl111.pdf", "lefl109.pdf"),
        "keeping quiet" to listOf("poem02_keeping_quiet.pdf", "lefl112.pdf", "lefl110.pdf"),
        "a thing of beauty" to listOf("poem03_a_thing_of_beauty.pdf", "lefl113.pdf"),
        "a roadside stand" to listOf("poem04_a_roadside_stand.pdf", "lefl114.pdf"),
        "aunt jennifer's tigers" to listOf("poem05_aunt_jennifers_tigers.pdf", "lefl115.pdf"),
        // Vistas
        "the third level" to listOf("ch01_the_third_level.pdf", "levt101.pdf"),
        "the tiger king" to listOf("ch02_the_tiger_king.pdf", "levt102.pdf"),
        "journey to the end of the earth" to listOf("ch03_journey_to_the_end_of_the_earth.pdf", "levt103.pdf"),
        "the enemy" to listOf("ch04_the_enemy.pdf", "levt104.pdf"),
        "on the face of it" to listOf("ch05_on_the_face_of_it.pdf", "levt105.pdf"),
        "memories of childhood" to listOf("ch06_memories_of_childhood.pdf", "levt106.pdf")
    )

    /**
     * Finds local handwritten notes PDF for a chapter
     */
    fun findNotesForChapter(context: Context, chapterName: String): File? {
        val notesDir = getNotesDirectory(context)
        if (!notesDir.exists()) return null

        val mappedFileNames = when (chapterName.trim().lowercase()) {
            "the last lesson" -> listOf("ch01_the_last_lesson_notes.pdf", "Flamingo 01 Class Notes.pdf", "Flamingo 01 Class Notes-1.pdf")
            "lost spring" -> listOf("ch02_lost_spring_notes.pdf", "Flamingo 02 Class Notes.pdf")
            "deep water" -> listOf("ch03_deep_water_notes.pdf", "Flamingo 03 Class Notes.pdf", "Flamingo - Deep Water (Part - 02) 08 _Class Notes.pdf")
            "the rattrap" -> listOf("ch04_the_rattrap_notes.pdf", "Flamingo 04 Class Notes.pdf")
            "indigo" -> listOf("ch05_indigo_notes.pdf", "Flamingo 05 Class Notes.pdf")
            "poets and pancakes" -> listOf("ch06_poets_and_pancakes_notes.pdf", "Flamingo 06 Class Notes.pdf")
            "the interview" -> listOf("ch07_the_interview_notes.pdf", "Flamingo 07 Class Notes.pdf")
            "going places" -> listOf("ch08_going_places_notes.pdf", "Flamingo 09 Class Notes.pdf")
            "my mother at sixty-six" -> listOf("poem01_my_mother_at_sixty_six_notes.pdf", "Flamingo 10 Class Notes.pdf")
            "keeping quiet" -> listOf("poem02_keeping_quiet_notes.pdf", "Flamingo 11 Class Notes.pdf")
            "a thing of beauty" -> listOf("poem03_a_thing_of_beauty_notes.pdf")
            "a roadside stand" -> listOf("poem04_a_roadside_stand_notes.pdf", "Flamingo - A Roadside Stand Class Notes.pdf")
            else -> emptyList()
        }

        for (candidateName in mappedFileNames) {
            val directFile = File(notesDir, candidateName)
            if (directFile.exists() && directFile.length() > 0) return directFile
        }

        // Fuzzy match across notes directory recursively
        val cleanChapter = chapterName.lowercase().replace("[^a-z0-9]".toRegex(), "")
        val allFiles = notesDir.walkTopDown().filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
        for (f in allFiles) {
            val cleanName = f.nameWithoutExtension.lowercase().replace("[^a-z0-9]".toRegex(), "")
            if (cleanName.contains(cleanChapter) || cleanChapter.contains(cleanName)) {
                return f
            }
        }

        return null
    }

    /**
     * Finds local NCERT textbook PDF for a chapter based on its URL or chapter name aliases
     */
    fun findNcertForChapter(context: Context, chapterName: String?, ncertUrl: String?): File? {
        val ncertDir = getNcertDirectory(context)
        if (!ncertDir.exists()) return null

        // 1. Direct filename from URL
        if (!ncertUrl.isNullOrEmpty()) {
            val fileName = ncertUrl.substringAfterLast("/")
            val directFile = File(ncertDir, fileName)
            if (directFile.exists() && directFile.length() > 0) {
                return directFile
            }
        }

        // 2. Check chapter-specific aliases (e.g. lefl101.pdf for The Last Lesson)
        if (!chapterName.isNullOrEmpty()) {
            val aliases = chapterToNcertAliases[chapterName.trim().lowercase()] ?: emptyList()
            for (alias in aliases) {
                val candidate = File(ncertDir, alias)
                if (candidate.exists() && candidate.length() > 0) {
                    return candidate
                }
            }

            // 3. Recursive fuzzy match in ncert folder
            val cleanChapter = chapterName.lowercase().replace("[^a-z0-9]".toRegex(), "")
            val allFiles = ncertDir.walkTopDown().filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            for (f in allFiles) {
                val cleanName = f.nameWithoutExtension.lowercase().replace("[^a-z0-9]".toRegex(), "")
                if (cleanName.contains(cleanChapter) || cleanChapter.contains(cleanName)) {
                    return f
                }
            }
        }

        return null
    }

    /**
     * Finds local official DPP PDF for a chapter
     */
    fun findDppForChapter(context: Context, chapterName: String): File? {
        val dppDir = getDppDirectory(context)
        val notesDir = getNotesDirectory(context)

        val mappedNames = when (chapterName.trim().lowercase()) {
            "the last lesson" -> listOf("ch01_the_last_lesson_dpp.pdf", "DPP 01.pdf")
            "lost spring" -> listOf("ch02_lost_spring_dpp.pdf", "DPP 03.pdf")
            "deep water" -> listOf("ch03_deep_water_dpp.pdf", "Flamingo DPP 04.pdf")
            "the rattrap" -> listOf("ch04_the_rattrap_part1_dpp.pdf", "Flamingo - The Rattrap (Part - 01) DPP.pdf", "Flamingo - The Rattrap (Part - 02) DPP.pdf")
            "indigo" -> listOf("ch05_indigo_dpp.pdf", "Flamingo DPP 05.pdf")
            "poets and pancakes" -> listOf("ch06_poets_and_pancakes_dpp.pdf", "Flamingo DPP 07.pdf")
            "the interview" -> listOf("ch07_the_interview_dpp.pdf", "Flamingo DPP 08.pdf")
            "going places" -> listOf("ch08_going_places_dpp.pdf", "Flamingo DPP 09.pdf")
            "keeping quiet" -> listOf("poem02_keeping_quiet_dpp.pdf", "Flamingo - Keeping Quiet DPP 06.pdf")
            else -> emptyList()
        }

        for (candidateName in mappedNames) {
            val dppFile = File(dppDir, candidateName)
            if (dppFile.exists() && dppFile.length() > 0) return dppFile
            val notesFile = File(notesDir, candidateName)
            if (notesFile.exists() && notesFile.length() > 0) return notesFile
        }

        return null
    }

    /**
     * Lists all offline writing section PDFs
     */
    fun getWritingSectionNotes(context: Context): List<File> {
        val notesDir = getNotesDirectory(context)
        if (!notesDir.exists()) return emptyList()
        return notesDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) && it.name.contains("writing", ignoreCase = true) }
            .toList()
    }
}
