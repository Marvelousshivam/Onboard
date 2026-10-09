package com.boardsprep.onboard.data.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Subject(
    val id: String,
    val name: String,
    val code: String,
    val stream: String, // PCM, PCB, General
    val totalTheoryMarks: Int,
    val totalPracticalMarks: Int,
    val iconName: String,
    val chapters: List<Chapter> = emptyList()
)

@Keep
data class ResourceItem(
    val title: String,
    val url: String,
    val filename: String = "",
    val type: String = "pdf"
)

@Keep
data class QuizItem(
    val id: String,
    val title: String,
    val url: String,
    val filename: String = "",
    val totalQuestions: Int = 10
)

@Keep
data class Chapter(
    val id: String,
    val subjectId: String,
    val number: Int,
    val name: String,
    val weightageMarks: Int,
    val ncertPdfUrl: String,
    val exemplarPdfUrl: String = "",
    val notesPdfUrl: String = "",
    val dppPdfUrl: String = "",
    val keyTopics: List<String> = emptyList(),
    val totalLecturesCount: Int = 0,
    val totalDppsCount: Int = 0,
    val notesList: List<ResourceItem> = emptyList(),
    val dppsList: List<ResourceItem> = emptyList(),
    val quizzesList: List<QuizItem> = emptyList(),
    val videosList: List<Lecture> = emptyList()
)

@Keep
data class Lecture(
    val id: String,
    val chapterId: String,
    val title: String,
    val youtubeUrl: String = "",
    val streamUrl: String = "", // Direct ad-free MP4 / HLS stream
    val durationText: String = "",
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isUserAdded: Boolean = false,
    val author: String = "Curated"
)

@Keep
data class Quiz(
    val id: String = "",
    val subject: String = "",
    val book: String = "",
    val chapter: String = "",
    val title: String = "",
    @SerializedName("source_pdf") val sourcePdf: String = "",
    @SerializedName("total_questions") val totalQuestions: Int = 0,
    val questions: List<Question> = emptyList()
)

@Keep
data class Question(
    val id: String = "",
    @SerializedName("q_no") val qNo: Int = 1,
    val question: String = "",
    val options: List<String> = emptyList(),
    @SerializedName("correct_option_index") val correctOptionIndex: Int = 0,
    @SerializedName("correct_answer_letter") val correctAnswerLetter: String = "",
    val explanation: String = ""
)

@Keep
data class DerivationItem(
    val id: String,
    val chapterId: String,
    val chapterName: String,
    val title: String,
    val formulaStatement: String,
    val keySteps: List<String>,
    val probabilityRating: String = "Very High", // Board rating
    val isMastered: Boolean = false
)

@Keep
data class NamedReactionItem(
    val id: String,
    val chapterName: String,
    val reactionName: String,
    val equation: String,
    val reagents: String,
    val keyApplication: String,
    val isMastered: Boolean = false
)

@Keep
data class BlueprintSection(
    val sectionName: String, // Section A, B, C, D, E
    val questionType: String, // MCQ, SA-I, SA-II, Case-Based, LA
    val numberOfQuestions: Int,
    val marksPerQuestion: Int,
    val totalMarks: Int
)

@Keep
data class BoardBlueprint(
    val subject: String,
    val subjectCode: String,
    val totalMarks: Int,
    val totalTimeMinutes: Int = 180,
    val totalQuestions: Int = 33,
    val competencyBreakdown: String = "",
    val notes: String = "",
    val sections: List<BlueprintSection>
)

@Keep
data class SamplePaper(
    val title: String,
    val filename: String,
    val subject: String,
    val subjectId: String,
    val type: String, // "sqp" or "ms"
    val url: String
)

