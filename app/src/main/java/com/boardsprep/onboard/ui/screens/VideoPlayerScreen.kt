package com.boardsprep.onboard.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.boardsprep.onboard.core.playback.DownloadState
import com.boardsprep.onboard.core.playback.LectureDownloader
import com.boardsprep.onboard.core.playback.StreamExtractor
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import com.boardsprep.onboard.data.repository.BoardsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    lectureId: String,
    url: String,
    title: String,
    repository: BoardsRepository,
    onBackClick: () -> Unit,
    onOpenPdf: (String, String) -> Unit = { _, _ -> },
    onOpenQuiz: (String, String) -> Unit = { _, _ -> },
    onPlayLecture: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenChapter: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // Resolve Chapter Context for Next Chapter, DPP Quiz, NCERT PDF
    val lectureContext = remember(lectureId, title) {
        repository.getLectureContext(lectureId, title)
    }
    val currentChapter = lectureContext?.currentChapter
    val currentSubject = lectureContext?.currentSubject
    val nextLecture = lectureContext?.nextLecture
    val nextChapter = lectureContext?.nextChapter
    val prevChapter = lectureContext?.prevChapter

    // Chapter Mastery State
    val chapterMastery by remember(currentChapter?.id) {
        if (currentChapter != null) {
            repository.getMastery(currentChapter.id)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsState(initial = null)

    var isMastered by remember(chapterMastery) {
        mutableStateOf(chapterMastery?.theoryCompleted == true)
    }

    // Identify stream source
    val youtubeVideoId = remember(url) { StreamExtractor.extractVideoId(url) }
    val isYouTube = youtubeVideoId != null
    val safeTitle = remember(title) { title.trim().ifBlank { "Board Video Lecture" } }

    var isVideoLoading by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showControlsOverlay by remember { mutableStateOf(true) }

    // HTML5 Fullscreen View State for YouTube WebChromeClient
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val downloadState = remember { MutableStateFlow<DownloadState>(DownloadState.Idle) }
    val currentDownloadState by downloadState.collectAsState()

    // ExoPlayer instance for local/direct stream
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // Synced Video Progress (Cross-platform resume from Web or local Room DB)
    val savedProgress by remember(lectureId) {
        repository.getVideoProgress(lectureId)
    }.collectAsState(initial = null)

    val startSeconds = remember(savedProgress) {
        val pos = savedProgress?.positionMillis ?: 0L
        if (pos > 5000L) pos / 1000L else 0L
    }

    // Remembered WebView for YouTube
    val webView = remember(youtubeVideoId) {
        if (isYouTube && youtubeVideoId != null) {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                    allowFileAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        customFullscreenView = view
                        customFullscreenCallback = callback
                        isFullscreen = true
                    }

                    override fun onHideCustomView() {
                        if (customFullscreenView != null) {
                            customFullscreenCallback?.onCustomViewHidden()
                            customFullscreenView = null
                            customFullscreenCallback = null
                        }
                        isFullscreen = false
                    }

                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress >= 85) isVideoLoading = false
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isVideoLoading = false
                    }
                }

                val embedHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000; }
                            body, html { width: 100%; height: 100%; overflow: hidden; background: #000; }
                            .container { position: relative; width: 100%; height: 100%; }
                            iframe { width: 100%; height: 100%; position: absolute; top: 0; left: 0; border: none; }
                            .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-player-overlay { display: none !important; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <iframe 
                                id="ytplayer"
                                src="https://www.youtube-nocookie.com/embed/$youtubeVideoId?autoplay=1&controls=1&rel=0&modestbranding=1&enablejsapi=1&playsinline=1&fs=1&start=$startSeconds" 
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen" 
                                allowfullscreen="true"
                                webkitallowfullscreen="true"
                                mozallowfullscreen="true">
                            </iframe>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
            }
        } else null
    }

    // Universal Speed & Skip Controller (Works on YouTube via JS injection and on ExoPlayer)
    fun seekRelative(seconds: Int) {
        if (isYouTube && webView != null) {
            webView.evaluateJavascript(
                """
                (function() {
                    var v = document.querySelector('video');
                    if (v) { v.currentTime = Math.max(0, v.currentTime + ($seconds)); }
                })();
                """.trimIndent(), null
            )
        } else {
            val target = (exoPlayer.currentPosition + seconds * 1000).coerceIn(0, exoPlayer.duration)
            exoPlayer.seekTo(target)
        }
    }

    fun applyPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (isYouTube && webView != null) {
            webView.evaluateJavascript(
                """
                (function() {
                    var v = document.querySelector('video');
                    if (v) { v.playbackRate = $speed; }
                })();
                """.trimIndent(), null
            )
        } else {
            exoPlayer.playbackParameters = PlaybackParameters(speed)
        }
    }

    fun exitFullscreen() {
        if (customFullscreenView != null) {
            customFullscreenCallback?.onCustomViewHidden()
            customFullscreenView = null
            customFullscreenCallback = null
        }
        isFullscreen = false
    }

    // Fullscreen Orientation & System Bars Management
    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.let { controller ->
                    controller.hide(
                        android.view.WindowInsets.Type.statusBars() or
                                android.view.WindowInsets.Type.navigationBars()
                    )
                    controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.decorView?.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.decorView?.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Intercept back button when fullscreen
    BackHandler(enabled = isFullscreen) {
        exitFullscreen()
    }

    // ExoPlayer position tracker ticker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isYouTube) {
                currentPositionMs = exoPlayer.currentPosition
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying
            }
            delay(500)
        }
    }

    val playerView = remember {
        if (!isYouTube) {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        } else null
    }

    // ExoPlayer Lifecycle
    DisposableEffect(url, isYouTube) {
        if (!isYouTube) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> isVideoLoading = true
                        Player.STATE_READY -> {
                            isVideoLoading = false
                            playbackError = null
                        }
                        Player.STATE_ENDED -> isVideoLoading = false
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    isVideoLoading = false
                    playbackError = "Error loading lecture: ${error.localizedMessage ?: "Unknown media error"}"
                }
            }

            exoPlayer.addListener(listener)
            isVideoLoading = true
            playbackError = null

            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (startSeconds > 0) {
                    exoPlayer.seekTo(startSeconds * 1000L)
                }
            } catch (e: Exception) {
                playbackError = "Failed to load media: ${e.message}"
                isVideoLoading = false
            }

            onDispose {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (currentPos > 0) {
                    coroutineScope.launch {
                        repository.saveVideoProgress(
                            VideoProgressEntity(
                                videoId = lectureId,
                                positionMillis = currentPos,
                                durationMillis = duration,
                                isCompleted = duration > 0 && currentPos >= (duration * 0.9)
                            )
                        )
                    }
                }
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        } else {
            onDispose {
                webView?.let { wv ->
                    wv.evaluateJavascript(
                        "(function() { var v = document.querySelector('video'); return v ? Math.floor(v.currentTime * 1000) : 0; })();"
                    ) { posStr ->
                        val pos = posStr?.toLongOrNull() ?: 0L
                        if (pos > 3000L) {
                            coroutineScope.launch {
                                repository.saveVideoProgress(
                                    VideoProgressEntity(
                                        videoId = lectureId,
                                        chapterId = currentChapter?.id ?: "",
                                        positionMillis = pos,
                                        durationMillis = totalDurationMs,
                                        isCompleted = false
                                    )
                                )
                            }
                        }
                    }
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.destroy()
                }
                exoPlayer.release()
            }
        }
    }

    fun openInExternalApp() {
        try {
            val targetUri = if (isYouTube && youtubeVideoId != null) {
                Uri.parse("https://www.youtube.com/watch?v=$youtubeVideoId")
            } else {
                Uri.parse(url)
            }
            val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open external player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentSubject != null) {
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = currentSubject.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (currentChapter != null) "Ch ${currentChapter.number}" else "Lecture",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = safeTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Fullscreen Toggle in Top Bar
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Enter Fullscreen")
                        }

                        // Open in external app
                        IconButton(onClick = { openInExternalApp() }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in External App")
                        }

                        // PiP Button
                        IconButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            }
                        }) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "PiP")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { innerPadding ->
        if (isFullscreen) {
            // Fullscreen Landscape View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showControlsOverlay = !showControlsOverlay },
                contentAlignment = Alignment.Center
            ) {
                if (customFullscreenView != null) {
                    AndroidView(
                        factory = { _ ->
                            (customFullscreenView?.parent as? ViewGroup)?.removeView(customFullscreenView)
                            customFullscreenView!!
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isYouTube && webView != null) {
                    AndroidView(
                        factory = { _ ->
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (playerView != null) {
                    AndroidView(
                        factory = { _ ->
                            (playerView.parent as? ViewGroup)?.removeView(playerView)
                            playerView.apply { useController = true }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Fullscreen Exit Floating Button
                IconButton(
                    onClick = { exitFullscreen() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White
                    )
                }
            }
        } else {
            // Standard Portrait View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Video Player Container (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isYouTube && webView != null) {
                        AndroidView(
                            factory = { _ ->
                                (webView.parent as? ViewGroup)?.removeView(webView)
                                webView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (playerView != null) {
                        AndroidView(
                            factory = { _ ->
                                (playerView.parent as? ViewGroup)?.removeView(playerView)
                                playerView.apply { useController = true }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating Fullscreen Overlay button on player corner
                    IconButton(
                        onClick = { isFullscreen = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Enter Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (isVideoLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            com.boardsprep.onboard.ui.components.MorphingOrganicLoader(modifier = Modifier.size(44.dp), tintColor = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading Board Lecture...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (playbackError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(playbackError ?: "", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { openInExternalApp() },
                                shape = ExpressivePillSmall
                            ) {
                                Text("Open in External Player")
                            }
                        }
                    }
                }

                // Controls Ribbon (Only for Local Media, as YouTube embed has native controls)
                if (!isYouTube) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind / Forward Chips
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { seekRelative(-10) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Replay10, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("-10s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { seekRelative(10) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Forward10, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+10s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Speed Selection Chips
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    val isSelected = currentSpeed == speed
                                    Surface(
                                        shape = ExpressivePillSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.clickable { applyPlaybackSpeed(speed) }
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Below Player Structured Study Hub
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Lecture Title & Metadata Header
                    Column {
                        Text(
                            text = safeTitle,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = ExpressivePillSmall,
                                color = SuccessGreen.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isYouTube) "Ad-Free Stream" else "Downloaded",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }

                            if (currentChapter != null) {
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${currentChapter.weightageMarks}M Board Weightage",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Action Study Ribbon (DPP Quiz, NCERT PDF, Download, Mark Mastered)
                    var showQualityDialog by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Take DPP Quiz Button
                        StudyRibbonAction(
                            icon = Icons.Default.Quiz,
                            label = "DPP Quiz",
                            containerColor = PhysicsLightBg,
                            contentColor = PhysicsLightOnBg,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val quiz = currentChapter?.quizzesList?.firstOrNull()
                                if (quiz != null) {
                                    onOpenQuiz(quiz.filename.ifBlank { quiz.url }, quiz.title)
                                } else {
                                    Toast.makeText(context, "No DPP quiz available for this chapter.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 2. View NCERT PDF
                        StudyRibbonAction(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "NCERT PDF",
                            containerColor = ChemistryLightBg,
                            contentColor = ChemistryLightOnBg,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val pdfUrl = currentChapter?.ncertPdfUrl ?: ""
                                if (pdfUrl.isNotBlank()) {
                                    onOpenPdf(pdfUrl, "${currentChapter?.name ?: "Chapter"} NCERT")
                                } else {
                                    Toast.makeText(context, "NCERT PDF url unavailable.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 3. Download Offline
                        StudyRibbonAction(
                            icon = Icons.Default.CloudDownload,
                            label = "Download",
                            containerColor = MathsLightBg,
                            contentColor = MathsLightOnBg,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isYouTube) {
                                    showQualityDialog = true
                                } else {
                                    Toast.makeText(context, "Already saved locally!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 4. Mark Mastered Toggle
                        StudyRibbonAction(
                            icon = if (isMastered) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            label = if (isMastered) "Mastered" else "Mark Done",
                            containerColor = if (isMastered) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isMastered) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (currentChapter != null) {
                                    val newStatus = !isMastered
                                    isMastered = newStatus
                                    coroutineScope.launch {
                                        repository.saveMastery(
                                            ChapterMasteryEntity(
                                                chapterId = currentChapter.id,
                                                theoryCompleted = newStatus,
                                                ncertCompleted = chapterMastery?.ncertCompleted ?: false,
                                                exemplarCompleted = chapterMastery?.exemplarCompleted ?: false,
                                                pyqCompleted = chapterMastery?.pyqCompleted ?: false
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            if (newStatus) "Marked as Mastered! Keep the momentum." else "Mastery unmarked.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }

                    // Download Quality Selection Modal
                    if (showQualityDialog) {
                        ModalBottomSheet(
                            onDismissRequest = { showQualityDialog = false },
                            shape = M3EBentoHeroShape
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .padding(bottom = 32.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadForOffline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Select Download Quality",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                val qualities = listOf(
                                    Triple("1080p (Full HD)", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", false),
                                    Triple("720p (HD) [Recommended]", "bestvideo[height<=720]+bestaudio/best[height<=720]/best", false),
                                    Triple("480p (Standard)", "bestvideo[height<=480]+bestaudio/best[height<=480]/best", false),
                                    Triple("360p (Data Saver)", "bestvideo[height<=360]+bestaudio/best[height<=360]/best", false),
                                    Triple("Audio Lecture (M4A)", "bestaudio[ext=m4a]/bestaudio/best", true)
                                )
                                qualities.forEach { (label, format, isAudio) ->
                                    ListItem(
                                        headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
                                        leadingContent = {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.HighQuality,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .clip(M3EBentoTileShape)
                                            .clickable {
                                                showQualityDialog = false
                                                coroutineScope.launch {
                                                    val downloader = LectureDownloader(context)
                                                    val targetUrl = if (isYouTube && youtubeVideoId != null) {
                                                        "https://www.youtube.com/watch?v=$youtubeVideoId"
                                                    } else url
                                                    downloader.downloadLecture(
                                                        id = lectureId,
                                                        title = title,
                                                        chapterId = currentChapter?.id ?: "chapter",
                                                        url = targetUrl,
                                                        formatSelector = format,
                                                        isAudioOnly = isAudio,
                                                        progressFlow = downloadState
                                                    )
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Download Progress Feedback Card
                    when (val state = currentDownloadState) {
                        is DownloadState.Progress -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = M3EBentoTileShape
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Saving lecture offline...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("${state.percentage}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { state.percentage / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(ExpressivePillSmall),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        is DownloadState.Success -> {
                            Surface(
                                shape = M3EBentoTileShape,
                                color = SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Downloaded! Available in Offline Library.", color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                        is DownloadState.Error -> {
                            Surface(
                                shape = M3EBentoTileShape,
                                color = ErrorRed.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Download Failed: ${state.message}", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                }
                            }
                        }
                        else -> {}
                    }

                    // THE NEXT CHAPTER / NEXT LECTURE PROGRESSION BENTO DECK (Requested Feature!)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = M3EBentoHeroShape
                            ),
                        shape = M3EBentoHeroShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (nextLecture != null) "Next Lecture in Chapter" else "Next Chapter in Curriculum",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (nextChapter != null) {
                                    Text(
                                        text = "${nextChapter.weightageMarks} Marks Weightage",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (nextLecture != null) {
                                Text(
                                    text = nextLecture.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Duration: ${nextLecture.durationText} • ${currentSubject?.name ?: "Curated"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        onPlayLecture(
                                            nextLecture.id,
                                            nextLecture.streamUrl.ifBlank { nextLecture.youtubeUrl },
                                            nextLecture.title
                                        )
                                    },
                                    shape = ExpressivePillSmall,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Play Next Lecture", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            } else if (nextChapter != null) {
                                Text(
                                    text = "Chapter ${nextChapter.number}: ${nextChapter.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${nextChapter.totalLecturesCount} Lectures • ${nextChapter.totalDppsCount} DPPs • NCERT Solutions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        if (currentSubject != null) {
                                            onOpenChapter(currentSubject.id, nextChapter.id)
                                        }
                                    },
                                    shape = ExpressivePillSmall,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start Next Chapter (${nextChapter.name})", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Text(
                                    text = "You've reached the final chapter in this curriculum!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Review earlier chapters or practice mock tests to consolidate your 95%+ aggregate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Key Topics Covered in this Chapter
                    if (currentChapter?.keyTopics?.isNotEmpty() == true) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3EBentoTileShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Chapter Syllabus & Key Topics",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                currentChapter.keyTopics.forEach { topic ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    ) {
                                        Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = topic,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Subject-Specific High-Yield Strategy Card
                    val strategyText = when (currentSubject?.id) {
                        "biology" -> "• Focus on NCERT diagrams: Practice neat labeling for 5-mark structured questions.\n" +
                                "• Highlight technical terminology (e.g. microsporogenesis, double fertilization) in written answers.\n" +
                                "• Immediately attempt the Chapter DPP Quiz in Practice Mode to consolidate synaptic recall."
                        "physics" -> "• Re-derive core formulas in your handbook without looking at references.\n" +
                                "• Always specify SI units and draw clear ray/circuit diagrams for full credit in Section D & E.\n" +
                                "• Complete the numerical DPP problems directly after this lecture."
                        "chemistry" -> "• Write down organic mechanisms and inorganic oxidation state trends 3 times.\n" +
                                "• Focus on NCERT back-of-chapter questions—frequent direct sources for 70/70 board marks.\n" +
                                "• Review the High-Yield Named Reactions deck in the Handbooks tab."
                        "maths" -> "• Write down key theorems and standard integrals on a flash card.\n" +
                                "• Avoid skipping intermediate algebraic steps—CBSE step-marking awards up to 80% marks for correct methodology.\n" +
                                "• Solve 5 standard PYQs from the question bank."
                        else -> "• Read the NCERT text carefully and underline recurring board keywords.\n" +
                                "• Attempt the chapter DPP quiz within 24 hours to maximize long-term synaptic retention.\n" +
                                "• Prepare concise summary points for rapid revision before pre-boards."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3EBentoTileShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = AccentAmber.copy(alpha = 0.2f),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = AccentAmberOnBg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "High-Yield Board Strategy (${currentSubject?.name ?: "Curriculum"})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = strategyText,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun StudyRibbonAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ribbon_scale"
    )

    Surface(
        shape = M3EBentoTileShape,
        color = containerColor,
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
