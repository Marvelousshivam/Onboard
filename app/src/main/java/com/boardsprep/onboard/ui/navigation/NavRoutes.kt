package com.boardsprep.onboard.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object SubjectHub : Screen("subject/{subjectId}") {
        fun createRoute(subjectId: String) = "subject/$subjectId"
    }
    object ChapterDetail : Screen("chapter/{subjectId}/{chapterId}") {
        fun createRoute(subjectId: String, chapterId: String) = "chapter/$subjectId/$chapterId"
    }
    object Player : Screen("player/{lectureId}?url={url}&title={title}") {
        fun createRoute(lectureId: String, url: String, title: String): String {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val safeTitle = title.trim().ifBlank { "Board Lecture" }
            val encodedTitle = java.net.URLEncoder.encode(safeTitle, "UTF-8")
            return "player/$lectureId?url=$encodedUrl&title=$encodedTitle"
        }
    }
    object PdfViewer : Screen("pdf_viewer?url={url}&title={title}") {
        fun createRoute(url: String, title: String): String {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "pdf_viewer?url=$encodedUrl&title=$encodedTitle"
        }
    }
    object Quiz : Screen("quiz/{quizFile}?title={title}") {
        fun createRoute(quizFile: String, title: String): String {
            val encodedFile = java.net.URLEncoder.encode(quizFile, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "quiz/$encodedFile?title=$encodedTitle"
        }
    }
    object Handbook : Screen("handbook/{subjectId}") {
        fun createRoute(subjectId: String) = "handbook/$subjectId"
    }
    object Downloads : Screen("downloads")
    object SamplePapers : Screen("sample_papers?subjectId={subjectId}") {
        fun createRoute(subjectId: String = "") = if (subjectId.isBlank()) "sample_papers" else "sample_papers?subjectId=$subjectId"
    }
    object OnboardingSync : Screen("onboarding_sync")
    object ErrorVault : Screen("error_vault")
    object DailyBlitz : Screen("daily_blitz")
}

