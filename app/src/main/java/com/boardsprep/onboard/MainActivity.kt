package com.boardsprep.onboard

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boardsprep.onboard.core.playback.DownloadState
import com.boardsprep.onboard.core.playback.LectureDownloader
import com.boardsprep.onboard.core.theme.OnboardTheme
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.navigation.Screen
import com.boardsprep.onboard.ui.screens.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = BoardsRepository(applicationContext)
        val lectureDownloader = LectureDownloader(applicationContext)

        setContent {
            OnboardTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                var subjects by remember { mutableStateOf(repository.getSubjects()) }

                // Silent background sync with GitHub manifest on app launch
                LaunchedEffect(Unit) {
                    val synced = repository.manifestManager.syncManifest()
                    if (synced) {
                        subjects = repository.getSubjects()
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route
                ) {
                    // Dashboard Screen
                    composable(Screen.Dashboard.route) {
                        DashboardScreen(
                            subjects = subjects,
                            onSubjectClick = { subjectId ->
                                navController.navigate(Screen.SubjectHub.createRoute(subjectId))
                            },
                            onHandbookClick = { subjectId ->
                                navController.navigate(Screen.Handbook.createRoute(subjectId))
                            },
                            onDownloadsClick = {
                                navController.navigate(Screen.Downloads.route)
                            },
                            onSamplePapersClick = { subjectId ->
                                navController.navigate(Screen.SamplePapers.createRoute(subjectId))
                            },
                            onAccountClick = {
                                navController.navigate(Screen.OnboardingSync.route)
                            },
                            onErrorVaultClick = {
                                navController.navigate(Screen.ErrorVault.route)
                            },
                            onDailyBlitzClick = {
                                navController.navigate(Screen.DailyBlitz.route)
                            }
                        )
                    }


                    // Subject Hub Screen
                    composable(
                        route = Screen.SubjectHub.route,
                        arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val subjectId = backStackEntry.arguments?.getString("subjectId") ?: "english"
                        val subject = subjects.find { it.id == subjectId } ?: subjects.first()

                        SubjectHubScreen(
                            subject = subject,
                            onBackClick = { navController.popBackStack() },
                            onChapterClick = { chapterId ->
                                navController.navigate(Screen.ChapterDetail.createRoute(subject.id, chapterId))
                            },
                            onHandbookClick = {
                                navController.navigate(Screen.Handbook.createRoute(subject.id))
                            },
                            onPlayLecture = { lectureId, url, title ->
                                navController.navigate(Screen.Player.createRoute(lectureId, url, title))
                            },
                            onOpenPdf = { url, title ->
                                navController.navigate(Screen.PdfViewer.createRoute(url, title))
                            },
                            onSamplePapersClick = { subId ->
                                navController.navigate(Screen.SamplePapers.createRoute(subId))
                            }
                        )
                    }

                    // Chapter Detail Screen
                    composable(
                        route = Screen.ChapterDetail.route,
                        arguments = listOf(
                            navArgument("subjectId") { type = NavType.StringType },
                            navArgument("chapterId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val subjectId = backStackEntry.arguments?.getString("subjectId") ?: "english"
                        val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                        val subject = subjects.find { it.id == subjectId } ?: subjects.first()
                        val chapter = subject.chapters.find { it.id == chapterId } ?: subject.chapters.firstOrNull()

                        if (chapter != null) {
                            val lectures = remember(chapter.id) { repository.getLecturesForChapter(chapter) }
                            val mastery by repository.getMastery(chapter.id).collectAsState(initial = null)

                            ChapterDetailScreen(
                                chapter = chapter,
                                lectures = lectures,
                                mastery = mastery,
                                onBackClick = { navController.popBackStack() },
                                onLectureClick = { lecture ->
                                    val streamOrYt = lecture.streamUrl.ifEmpty {
                                        lecture.youtubeUrl.ifEmpty { lecture.localFilePath ?: "" }
                                    }
                                    navController.navigate(Screen.Player.createRoute(lecture.id, streamOrYt, lecture.title))
                                },
                                onDownloadLectureClick = { lecture ->
                                    coroutineScope.launch {
                                        Toast.makeText(applicationContext, "Starting download: ${lecture.title}", Toast.LENGTH_SHORT).show()
                                        val progressFlow = MutableStateFlow<DownloadState>(DownloadState.Idle)
                                        val downloadUrl = lecture.streamUrl.ifEmpty { lecture.youtubeUrl }
                                        lectureDownloader.downloadLecture(
                                            id = lecture.id,
                                            title = lecture.title,
                                            chapterId = chapter.id,
                                            url = downloadUrl,
                                            formatSelector = "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
                                            isAudioOnly = false,
                                            progressFlow = progressFlow
                                        )
                                        Toast.makeText(applicationContext, "Download finished for ${lecture.title}!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenPdf = { url, title ->
                                    navController.navigate(Screen.PdfViewer.createRoute(url, title))
                                },
                                onOpenQuiz = { quizFile, title ->
                                    navController.navigate(Screen.Quiz.createRoute(quizFile, title))
                                },
                                onMasteryChange = { updatedMastery ->
                                    coroutineScope.launch {
                                        repository.saveMastery(updatedMastery)
                                    }
                                }
                            )
                        }
                    }

                    // Video Player Screen
                    composable(
                        route = Screen.Player.route,
                        arguments = listOf(
                            navArgument("lectureId") { type = NavType.StringType },
                            navArgument("url") { type = NavType.StringType; defaultValue = "" },
                            navArgument("title") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val lectureId = backStackEntry.arguments?.getString("lectureId") ?: ""
                        val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                        val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                        val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                        val decodedTitle = URLDecoder.decode(encodedTitle, "UTF-8")

                        VideoPlayerScreen(
                            lectureId = lectureId,
                            url = decodedUrl,
                            title = decodedTitle,
                            repository = repository,
                            onBackClick = { navController.popBackStack() },
                            onOpenPdf = { pdfUrl, pdfTitle ->
                                navController.navigate(Screen.PdfViewer.createRoute(pdfUrl, pdfTitle))
                            },
                            onOpenQuiz = { quizFile, quizTitle ->
                                navController.navigate(Screen.Quiz.createRoute(quizFile, quizTitle))
                            },
                            onPlayLecture = { nextId, nextUrl, nextTitle ->
                                navController.navigate(Screen.Player.createRoute(nextId, nextUrl, nextTitle))
                            },
                            onOpenChapter = { subId, chapId ->
                                navController.navigate(Screen.ChapterDetail.createRoute(subId, chapId))
                            }
                        )
                    }

                    // In-App PDF Viewer Screen
                    composable(
                        route = Screen.PdfViewer.route,
                        arguments = listOf(
                            navArgument("url") { type = NavType.StringType; defaultValue = "" },
                            navArgument("title") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                        val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                        val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                        val decodedTitle = URLDecoder.decode(encodedTitle, "UTF-8")

                        PdfViewerScreen(
                            url = decodedUrl,
                            title = decodedTitle,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // DPP Quiz Screen
                    composable(
                        route = Screen.Quiz.route,
                        arguments = listOf(
                            navArgument("quizFile") { type = NavType.StringType },
                            navArgument("title") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val rawQuizFile = backStackEntry.arguments?.getString("quizFile") ?: ""
                        val quizFile = try { URLDecoder.decode(rawQuizFile, "UTF-8") } catch (_: Exception) { rawQuizFile }
                        val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                        val decodedTitle = try { URLDecoder.decode(encodedTitle, "UTF-8") } catch (_: Exception) { encodedTitle }

                        QuizScreen(
                            quizFile = quizFile,
                            title = decodedTitle,
                            repository = repository,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // High-Yield Handbook Screen
                    composable(
                        route = Screen.Handbook.route,
                        arguments = listOf(
                            navArgument("subjectId") { type = NavType.StringType; defaultValue = "physics" }
                        )
                    ) { backStackEntry ->
                        val subjectId = backStackEntry.arguments?.getString("subjectId") ?: "physics"
                        HandbookScreen(
                            initialSubjectId = subjectId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    // Downloads & Local Library Screen
                    composable(Screen.Downloads.route) {
                        DownloadsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onPlayLecture = { lectureId, url, title ->
                                navController.navigate(Screen.Player.createRoute(lectureId, url, title))
                            },
                            onOpenPdf = { url, title ->
                                navController.navigate(Screen.PdfViewer.createRoute(url, title))
                            }
                        )
                    }

                    // Official CBSE 2027 Sample Papers Screen
                    composable(
                        route = Screen.SamplePapers.route,
                        arguments = listOf(
                            navArgument("subjectId") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
                        SamplePapersScreen(
                            initialSubjectId = subjectId,
                            onOpenPdf = { url, title ->
                                navController.navigate(Screen.PdfViewer.createRoute(url, title))
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Dedicated Onboarding & Cloud Sync Screen
                    composable(Screen.OnboardingSync.route) {
                        OnboardingSyncScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Mistake Notebook (Error Vault) Screen
                    composable(Screen.ErrorVault.route) {
                        ErrorVaultScreen(
                            repository = repository,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // 5-Min Spaced Repetition Daily Blitz Screen
                    composable(Screen.DailyBlitz.route) {
                        DailyBlitzScreen(
                            repository = repository,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }

        }
    }
}
