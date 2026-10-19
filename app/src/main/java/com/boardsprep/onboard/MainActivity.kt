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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.boardsprep.onboard.core.glass.GlassSettings
import com.boardsprep.onboard.core.glass.LocalAppBackdrop
import com.boardsprep.onboard.core.glass.LocalGlassEnabled
import com.boardsprep.onboard.core.glass.LocalGlassTheme
import com.boardsprep.onboard.core.glass.isGlassSupported
import com.boardsprep.onboard.core.glass.backdrop.backdrops.layerBackdrop
import com.boardsprep.onboard.core.glass.backdrop.backdrops.rememberLayerBackdrop
import com.boardsprep.onboard.core.playback.DownloadState
import com.boardsprep.onboard.core.playback.LectureDownloader
import com.boardsprep.onboard.core.pdf.PdfPairedRole
import com.boardsprep.onboard.core.theme.OnboardTheme
import com.boardsprep.onboard.core.update.AppUpdateChecker
import com.boardsprep.onboard.core.update.UpdateState
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.UpdateAvailableDialog
import com.boardsprep.onboard.ui.navigation.Screen
import com.boardsprep.onboard.ui.screens.*
import com.boardsprep.onboard.ui.screens.pdf.PdfViewerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Liquid Glass preferences (Default: Disabled / Normal M3 UI)
        GlassSettings.init(applicationContext)

        val repository = BoardsRepository(applicationContext)
        val lectureDownloader = LectureDownloader(applicationContext)

        setContent {
            OnboardTheme {
                val windowBackground = MaterialTheme.colorScheme.background
                val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
                    {
                        drawRect(windowBackground)
                        drawContent()
                    }
                }
                val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)

                CompositionLocalProvider(
                    LocalGlassTheme provides GlassSettings.style,
                    LocalGlassEnabled provides GlassSettings.isGlassEnabled,
                    LocalAppBackdrop provides appBackdrop
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (GlassSettings.isGlassEnabled && isGlassSupported()) {
                                    Modifier.layerBackdrop(appBackdrop)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val navController = rememberNavController()
                        val coroutineScope = rememberCoroutineScope()
                        var subjects by remember { mutableStateOf(repository.getSubjects()) }
                        val updateChecker = remember { AppUpdateChecker(applicationContext) }
                        val updateState by updateChecker.updateState.collectAsState()

                        // Silent check for GitHub release updates on startup (12h rate-limited)
                        LaunchedEffect(Unit) {
                            updateChecker.checkForUpdates(isManual = false)
                        }

                        // User feedback for manual update checks
                        LaunchedEffect(updateState) {
                            when (val state = updateState) {
                                is UpdateState.Checking -> {
                                    if (state.isManual) {
                                        Toast.makeText(this@MainActivity, "Checking GitHub for updates...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is UpdateState.UpToDate -> {
                                    if (state.isManual) {
                                        Toast.makeText(this@MainActivity, "OnBOARD is up to date (v${state.currentVersion})", Toast.LENGTH_SHORT).show()
                                        updateChecker.dismiss()
                                    }
                                }
                                is UpdateState.Error -> {
                                    if (state.isManual) {
                                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                                else -> Unit
                            }
                        }


                // Request Notification Permission on Android 13+ (API 33+) for persistent download notifications
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { _ -> }
                    LaunchedEffect(Unit) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Silent background sync with GitHub manifest on app launch
                LaunchedEffect(Unit) {
                    val synced = repository.manifestManager.syncManifest()
                    if (synced) {
                        subjects = repository.getSubjects()
                    }
                }

                val prefs = remember { applicationContext.getSharedPreferences("onboard_prefs", android.content.Context.MODE_PRIVATE) }
                val isOnboardingCompleted = remember { prefs.getBoolean("onboarding_completed", false) }
                val startDestination = if (isOnboardingCompleted) Screen.MainApp.route else Screen.OnboardingTour.route

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    // Top-Level Main App Scaffold (Floating Dock with Home, Q-Bank, Papers, Library)
                    composable(
                        route = Screen.MainApp.route,
                        arguments = listOf(
                            navArgument("tab") {
                                type = NavType.IntType
                                defaultValue = 0
                            }
                        )
                    ) { backStackEntry ->
                        val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
                        MainAppScaffold(
                            initialTab = initialTab,
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
                            onAccountClick = {
                                navController.navigate(Screen.OnboardingSync.route)
                            },
                            onErrorVaultClick = {
                                navController.navigate(Screen.ErrorVault.route)
                            },
                            onDailyBlitzClick = {
                                navController.navigate(Screen.DailyBlitz.route)
                            },
                            onToolsClick = {
                                navController.navigate(Screen.Tools.route)
                            },
                            onReplayTourClick = {
                                navController.navigate(Screen.OnboardingTour.route)
                            },
                            onOpenPdfWithPair = { url, title, pairedUrl, pairedTitle, pairedRole ->
                                navController.navigate(
                                    Screen.PdfViewer.createRoute(
                                        url = url,
                                        title = title,
                                        pairedUrl = pairedUrl,
                                        pairedTitle = pairedTitle,
                                        pairedRole = pairedRole.name
                                    )
                                )
                            },
                            onOpenPdfSimple = { url, title ->
                                navController.navigate(Screen.PdfViewer.createRoute(url = url, title = title))
                            },
                            onStartExamSimulation = { paperTitle, subject, paperUrl, msUrl ->
                                navController.navigate(
                                    Screen.ExamSimulation.createRoute(
                                        paperTitle = paperTitle,
                                        subject = subject,
                                        paperUrl = paperUrl,
                                        msUrl = msUrl
                                    )
                                )
                            },
                            onCheckForUpdates = {
                                updateChecker.checkForUpdates(isManual = true)
                            }
                        )
                    }

                    // Dashboard Screen (Legacy alias route)
                    composable(Screen.Dashboard.route) {
                        navController.navigate(Screen.MainApp.createRoute(0)) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
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
                            },
                            onQuestionBankClick = { subId ->
                                navController.navigate(Screen.QuestionBank.createRoute(subId))
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
                                            formatSelector = "720",
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

                    // In-App PDF Viewer Screen (OnBOARD Reader)
                    composable(
                        route = Screen.PdfViewer.route,
                        arguments = listOf(
                            navArgument("url") { type = NavType.StringType; defaultValue = "" },
                            navArgument("title") { type = NavType.StringType; defaultValue = "" },
                            navArgument("pairedUrl") { type = NavType.StringType; defaultValue = "" },
                            navArgument("pairedTitle") { type = NavType.StringType; defaultValue = "" },
                            navArgument("pairedRole") { type = NavType.StringType; defaultValue = "GENERAL" }
                        )
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments
                        fun decode(v: String?): String =
                            if (v.isNullOrEmpty()) "" else try { URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { v }
                        val decodedUrl = decode(args?.getString("url"))
                        val decodedTitle = decode(args?.getString("title"))
                        val decodedPairedUrl = decode(args?.getString("pairedUrl"))
                        val decodedPairedTitle = decode(args?.getString("pairedTitle"))
                        val decodedPairedRoleStr = decode(args?.getString("pairedRole"))
                        val pairedRole = runCatching { PdfPairedRole.valueOf(decodedPairedRoleStr) }
                            .getOrDefault(PdfPairedRole.GENERAL)

                        PdfViewerScreen(
                            url = decodedUrl,
                            title = decodedTitle,
                            onBackClick = { navController.popBackStack() },
                            pairedSource = decodedPairedUrl.ifBlank { null },
                            pairedTitle = decodedPairedTitle.ifBlank { null },
                            pairedRole = pairedRole,
                            onNavigateToPairedOverride = { src, ttl ->
                                // When the user taps the QP<->MS switcher, navigate to the paired
                                // document. We pass the *original* document back as the new pair so
                                // the switcher remains bidirectional.
                                val newPairedRole = if (pairedRole == PdfPairedRole.QUESTION_PAPER)
                                    PdfPairedRole.MARKING_SCHEME else PdfPairedRole.QUESTION_PAPER
                                navController.navigate(
                                    Screen.PdfViewer.createRoute(
                                        url = src,
                                        title = ttl,
                                        pairedUrl = decodedUrl,
                                        pairedTitle = decodedTitle,
                                        pairedRole = newPairedRole.name
                                    )
                                ) {
                                    // Replace so the back stack doesn't grow unboundedly.
                                    popUpTo(Screen.PdfViewer.route) { inclusive = true }
                                }
                            }
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
                            onOpenPdf = { url, title, pairedUrl, pairedTitle, pairedRole ->
                                navController.navigate(
                                    Screen.PdfViewer.createRoute(
                                        url = url,
                                        title = title,
                                        pairedUrl = pairedUrl,
                                        pairedTitle = pairedTitle,
                                        pairedRole = pairedRole.name
                                    )
                                )
                            },
                            onStartExamSimulation = { paperTitle, subject, paperUrl, msUrl ->
                                navController.navigate(
                                    Screen.ExamSimulation.createRoute(
                                        paperTitle = paperTitle,
                                        subject = subject,
                                        paperUrl = paperUrl,
                                        msUrl = msUrl
                                    )
                                )
                            },
                            onQuestionBankClick = { sId ->
                                navController.navigate(Screen.QuestionBank.createRoute(sId))
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // 3-Hour Exam Simulation Screen
                    composable(
                        route = Screen.ExamSimulation.route,
                        arguments = listOf(
                            navArgument("paperTitle") { type = NavType.StringType; defaultValue = "CBSE Sample Paper" },
                            navArgument("subject") { type = NavType.StringType; defaultValue = "Class 12" },
                            navArgument("paperUrl") { type = NavType.StringType; defaultValue = "" },
                            navArgument("msUrl") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        fun decode(v: String?): String =
                            if (v.isNullOrEmpty()) "" else try { URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { v }
                        val paperTitle = decode(backStackEntry.arguments?.getString("paperTitle"))
                        val subject = decode(backStackEntry.arguments?.getString("subject"))
                        val paperUrl = decode(backStackEntry.arguments?.getString("paperUrl"))
                        val msUrl = decode(backStackEntry.arguments?.getString("msUrl"))

                        ExamSimulationScreen(
                            paperTitle = paperTitle,
                            subject = subject,
                            paperUrl = paperUrl,
                            markingSchemeUrl = msUrl,
                            onOpenPdf = { url, title ->
                                navController.navigate(Screen.PdfViewer.createRoute(url = url, title = title))
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // CBSE Class 12 Master Tools Screen
                    composable(Screen.Tools.route) {
                        ToolsScreen(
                            onBackClick = { navController.popBackStack() },
                            onStartExamSimulation = { paperTitle, subject, paperUrl, msUrl ->
                                navController.navigate(
                                    Screen.ExamSimulation.createRoute(
                                        paperTitle = paperTitle,
                                        subject = subject,
                                        paperUrl = paperUrl,
                                        msUrl = msUrl
                                    )
                                )
                            }
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

                    // ClearPDF-style Interactive Onboarding Tour Screen
                    composable(Screen.OnboardingTour.route) {
                        OnboardingTourScreen(
                            onFinishOnboarding = {
                                navController.navigate(Screen.MainApp.route) {
                                    popUpTo(Screen.OnboardingTour.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    // Library Hub Screen alias
                    composable(Screen.LibraryHub.route) {
                        navController.navigate(Screen.MainApp.createRoute(3)) {
                            popUpTo(Screen.MainApp.route) { inclusive = false }
                        }
                    }

                    // CBSE Official Question Bank Studio Screen (Subjective 2m, 3m, 4m, 5m)
                    composable(
                        route = Screen.QuestionBank.route,
                        arguments = listOf(
                            navArgument("subjectId") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
                        QuestionBankScreen(
                            initialSubjectId = subjectId,
                            onBackClick = { navController.popBackStack() },
                            onOpenPdf = { url, title ->
                                navController.navigate(Screen.PdfViewer.createRoute(url = url, title = title))
                            }
                        )
                    }
                }

                // GitHub Release In-App Update Dialog
                UpdateAvailableDialog(
                    updateState = updateState,
                    onStartDownload = { release -> updateChecker.startDownload(release) },
                    onCancelDownload = { updateChecker.cancelDownload() },
                    onInstallUpdate = { apkFile -> updateChecker.installUpdate(apkFile) },
                    onDismiss = { updateChecker.dismiss() }
                )
            }
        }
    }
}
}
}



