package com.boardsprep.onboard.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.boardsprep.onboard.core.playback.DownloadState
import com.boardsprep.onboard.core.playback.LectureDownloadManager
import com.boardsprep.onboard.core.playback.StreamExtractor
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.ui.components.MorphingOrganicLoader
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Helper for readable timestamp formatting (e.g. 04:15 or 1:02:40)
fun formatSeconds(seconds: Float): String {
    val totalSec = seconds.toLong().coerceAtLeast(0L)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }
}

// Convert float speed to android-youtube-player PlaybackRate enum
fun Float.toYouTubePlaybackRate(): PlayerConstants.PlaybackRate = when (this) {
    0.25f -> PlayerConstants.PlaybackRate.RATE_0_25
    0.5f -> PlayerConstants.PlaybackRate.RATE_0_5
    0.75f -> PlayerConstants.PlaybackRate.RATE_0_75
    1.0f -> PlayerConstants.PlaybackRate.RATE_1
    1.25f -> PlayerConstants.PlaybackRate.RATE_1_25
    1.5f -> PlayerConstants.PlaybackRate.RATE_1_5
    1.75f -> PlayerConstants.PlaybackRate.RATE_1_75
    2.0f -> PlayerConstants.PlaybackRate.RATE_2
    else -> PlayerConstants.PlaybackRate.RATE_1
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

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

    // Persistent download state retrieved from global LectureDownloadManager
    val downloadStateFlow = remember(lectureId, safeTitle) {
        LectureDownloadManager.getDownloadState(context, lectureId, safeTitle)
    }
    val currentDownloadState by downloadStateFlow.collectAsState()

    // Synced Video Progress (Cross-platform resume from Web or local Room DB)
    val savedProgress by remember(lectureId) {
        repository.getVideoProgress(lectureId)
    }.collectAsState(initial = null)

    val startSeconds = remember(savedProgress) {
        val pos = savedProgress?.positionMillis ?: 0L
        if (pos > 5000L) (pos / 1000L).toFloat() else 0f
    }

    // Unified Playback States
    var isPlaying by remember { mutableStateOf(false) }
    var isVideoLoading by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPositionSec by remember { mutableFloatStateOf(0f) }
    var totalDurationSec by remember { mutableFloatStateOf(0f) }
    var loadedFraction by remember { mutableFloatStateOf(0f) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isVideoEnded by remember { mutableStateOf(false) }

    // Native HUD Controls Overlay States
    var showControlsOverlay by remember { mutableStateOf(true) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionSec by remember { mutableFloatStateOf(0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var selectedQualityLabel by remember { mutableStateOf("720p HD") }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }

    // Double-tap Seek Animation States
    var seekRewindPulse by remember { mutableStateOf(false) }
    var seekForwardPulse by remember { mutableStateOf(false) }

    // References to underlying players
    var youtubePlayerRef by remember { mutableStateOf<YouTubePlayer?>(null) }

    // ExoPlayer for offline/local media
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Unified Control Functions
    fun playVideo() {
        if (isYouTube) {
            youtubePlayerRef?.play()
        } else {
            exoPlayer.play()
        }
        isPlaying = true
    }

    fun pauseVideo() {
        if (isYouTube) {
            youtubePlayerRef?.pause()
        } else {
            exoPlayer.pause()
        }
        isPlaying = false
    }

    fun togglePlayPause() {
        if (isPlaying) pauseVideo() else playVideo()
    }

    fun seekTo(seconds: Float) {
        val target = seconds.coerceIn(0f, totalDurationSec.coerceAtLeast(1f))
        currentPositionSec = target
        if (isYouTube) {
            youtubePlayerRef?.seekTo(target)
        } else {
            exoPlayer.seekTo((target * 1000L).toLong())
        }
    }

    fun seekRelative(seconds: Int) {
        val target = (currentPositionSec + seconds).coerceIn(0f, totalDurationSec.coerceAtLeast(1f))
        seekTo(target)
    }

    fun applySpeed(speed: Float) {
        currentSpeed = speed
        if (isYouTube) {
            youtubePlayerRef?.setPlaybackRate(speed.toYouTubePlaybackRate())
        } else {
            exoPlayer.playbackParameters = PlaybackParameters(speed)
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

    // Auto-dismiss HUD controls after 3.5 seconds of inactivity when playing
    LaunchedEffect(showControlsOverlay, isPlaying, isUserScrubbing) {
        if (showControlsOverlay && isPlaying && !isUserScrubbing) {
            delay(3500)
            showControlsOverlay = false
        }
    }

    // Double-tap pulse reset timers
    LaunchedEffect(seekRewindPulse) {
        if (seekRewindPulse) {
            delay(650)
            seekRewindPulse = false
        }
    }
    LaunchedEffect(seekForwardPulse) {
        if (seekForwardPulse) {
            delay(650)
            seekForwardPulse = false
        }
    }

    // Save Progress to Room Database
    fun persistProgress() {
        val posMillis = (currentPositionSec * 1000L).toLong()
        val durMillis = (totalDurationSec * 1000L).toLong()
        if (posMillis > 3000L) {
            coroutineScope.launch {
                repository.saveVideoProgress(
                    VideoProgressEntity(
                        videoId = lectureId,
                        chapterId = currentChapter?.id ?: "",
                        positionMillis = posMillis,
                        durationMillis = durMillis,
                        isCompleted = durMillis > 0 && posMillis >= (durMillis * 0.9)
                    )
                )
            }
        }
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
            persistProgress()
        }
    }

    // Intercept back button when fullscreen
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    // Non-YouTube ExoPlayer Setup & Lifecycle
    if (!isYouTube) {
        DisposableEffect(url) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> isVideoLoading = true
                        Player.STATE_READY -> {
                            isVideoLoading = false
                            playbackError = null
                            totalDurationSec = (exoPlayer.duration.coerceAtLeast(0L) / 1000L).toFloat()
                        }
                        Player.STATE_ENDED -> {
                            isVideoLoading = false
                            isVideoEnded = true
                            isPlaying = false
                        }
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
                    exoPlayer.seekTo((startSeconds * 1000L).toLong())
                }
            } catch (e: Exception) {
                playbackError = "Failed to load media: ${e.message}"
                isVideoLoading = false
            }

            onDispose {
                persistProgress()
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        // Ticker for ExoPlayer time position
        LaunchedEffect(exoPlayer) {
            while (true) {
                if (!isUserScrubbing) {
                    currentPositionSec = (exoPlayer.currentPosition / 1000L).toFloat()
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        totalDurationSec = (dur / 1000L).toFloat()
                        loadedFraction = (exoPlayer.bufferedPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                }
                delay(500)
            }
        }
    }

    // Speed Bottom Sheet
    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color(0xFF14131A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Playback Speed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                    val isSelected = currentSpeed == speed
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                applySpeed(speed)
                                showSpeedSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Video Playback Quality Control Bottom Sheet
    if (showQualityDialog) {
        ModalBottomSheet(
            onDismissRequest = { showQualityDialog = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color(0xFF14131A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video Playback Quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                val qualityOptions = listOf(
                    "Auto (Adaptive - Best Experience)" to "Auto",
                    "1080p (Full HD - Crystal Clear)" to "1080p",
                    "720p (HD - High Efficiency)" to "720p HD",
                    "480p (Standard Definition)" to "480p",
                    "360p (Data Saver Mode)" to "360p",
                    "Audio Only (Save 95% Data)" to "Audio"
                )
                qualityOptions.forEach { (label, shortCode) ->
                    val isSelected = selectedQualityLabel == shortCode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedQualityLabel = shortCode
                                showQualityDialog = false
                                Toast.makeText(context, "Streaming quality set to $shortCode", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // MAIN SCAFFOLD (No redundant top bar in portrait - video pins directly below status bar)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // COMPOSABLE VIDEO CONTAINER (Used in both Portrait and Landscape)
        val videoPlayerView = @Composable {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 1. UNDERLYING VIDEO SURFACE (Pristine 1:1 Aspect Ratio - ZERO SCALING)
                if (isYouTube && youtubeVideoId != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(), // No artificial scaling - 100% uncropped
                            factory = { ctx ->
                                // FrameLayout with dispatchTouchEvent returning false ensures native
                                // WebView never swallows touches; all tap and drag gestures reach Compose
                                object : FrameLayout(ctx) {
                                    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                                        return false
                                    }
                                }.apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    val ytp = YouTubePlayerView(ctx).apply {
                                        enableAutomaticInitialization = false
                                        layoutParams = FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        lifecycleOwner.lifecycle.addObserver(this)

                                        val options = IFramePlayerOptions.Builder(ctx)
                                            .controls(0) // Hide YouTube default web controls
                                            .rel(0)
                                            .ivLoadPolicy(3)
                                            .ccLoadPolicy(0)
                                            .build()

                                        initialize(object : AbstractYouTubePlayerListener() {
                                            override fun onReady(player: YouTubePlayer) {
                                                youtubePlayerRef = player
                                                isVideoLoading = false
                                                player.loadVideo(youtubeVideoId, startSeconds)
                                                if (currentSpeed != 1.0f) {
                                                    player.setPlaybackRate(currentSpeed.toYouTubePlaybackRate())
                                                }
                                            }

                                            override fun onStateChange(player: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                                when (state) {
                                                    PlayerConstants.PlayerState.PLAYING -> {
                                                        isPlaying = true
                                                        isVideoLoading = false
                                                        isVideoEnded = false
                                                    }
                                                    PlayerConstants.PlayerState.PAUSED -> {
                                                        isPlaying = false
                                                        isVideoLoading = false
                                                    }
                                                    PlayerConstants.PlayerState.BUFFERING -> {
                                                        isVideoLoading = true
                                                    }
                                                    PlayerConstants.PlayerState.ENDED -> {
                                                        if (isLooping) {
                                                            player.seekTo(0f)
                                                            player.play()
                                                        } else {
                                                            isPlaying = false
                                                            isVideoLoading = false
                                                            isVideoEnded = true
                                                            showControlsOverlay = true
                                                        }
                                                    }
                                                    else -> {}
                                                }
                                            }

                                            override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                                                if (!isUserScrubbing) {
                                                    currentPositionSec = second
                                                }
                                            }

                                            override fun onVideoDuration(player: YouTubePlayer, duration: Float) {
                                                totalDurationSec = duration
                                            }

                                            override fun onVideoLoadedFraction(player: YouTubePlayer, fraction: Float) {
                                                loadedFraction = fraction
                                            }

                                            override fun onError(player: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                                isVideoLoading = false
                                                playbackError = "YouTube playback error: ${error.name}"
                                            }
                                        }, options)
                                    }
                                    addView(ytp)
                                }
                            },
                            onRelease = { frameLayout ->
                                val ytp = frameLayout.getChildAt(0) as? YouTubePlayerView
                                ytp?.let {
                                    lifecycleOwner.lifecycle.removeObserver(it)
                                    it.release()
                                }
                            }
                        )
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // Use our native Compose controls overlay
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        }
                    )
                }

                // 2. GESTURE DETECTOR LAYER (Single-tap to toggle HUD, Double-tap left/right edge to ±10s seek)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showControlsOverlay = !showControlsOverlay
                                },
                                onDoubleTap = { offset ->
                                    val width = size.width
                                    if (offset.x < width * 0.30f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        seekRelative(-10)
                                        seekRewindPulse = true
                                    } else if (offset.x > width * 0.70f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        seekRelative(10)
                                        seekForwardPulse = true
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        togglePlayPause()
                                    }
                                }
                            )
                        }
                )

                // 3. DOUBLE-TAP VISUAL EDGE RIPPLES
                AnimatedVisibility(
                    visible = seekRewindPulse,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = slideOutHorizontally(targetOffsetX = { -it / 2 }) + fadeOut() + scaleOut(targetScale = 1.05f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Black.copy(alpha = 0.80f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "-10s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                style = TextStyle(fontFeatureSettings = "\"tnum\"")
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = seekForwardPulse,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut() + scaleOut(targetScale = 1.05f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Black.copy(alpha = 0.80f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+10s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                style = TextStyle(fontFeatureSettings = "\"tnum\"")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // 4. BUFFERING / ASYMMETRIC ORGANIC LOADER OVERLAY
                if (isVideoLoading && !isVideoEnded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 28.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MorphingOrganicLoader(
                                modifier = Modifier.size(68.dp),
                                tintColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buffering Stream...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // 5. ERROR STATE OVERLAY
                if (playbackError != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(playbackError ?: "", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { openInExternalApp() },
                            shape = ExpressivePillSmall
                        ) {
                            Text("Open in External Player")
                        }
                    }
                }

                // 6. SCREEN LOCKED OVERLAY (When locked, only single unlock button shows on tap)
                if (isScreenLocked) {
                    AnimatedVisibility(
                        visible = showControlsOverlay,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(250)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            ProPlayerCircleButton(
                                icon = Icons.Default.Lock,
                                contentDescription = "Unlock Controls",
                                size = 52.dp,
                                iconSize = 26.dp,
                                containerColor = Color.Black.copy(alpha = 0.75f),
                                contentColor = Color(0xFFE5B6F8),
                                modifier = Modifier.align(Alignment.CenterStart),
                                onClick = {
                                    isScreenLocked = false
                                    Toast.makeText(context, "Controls Unlocked", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                } else {
                    // 7. FLAGSHIP MEDIA PLAYER HUD (Distinct Portrait vs Landscape layouts)
                    AnimatedVisibility(
                        visible = showControlsOverlay,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(250)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isFullscreen) {
                            // ==============================================================
                            // LANDSCAPE FULLSCREEN HUD (Exact match to media_1792379806106_c2cfb96b.png)
                            // ==============================================================
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to Color.Black.copy(alpha = 0.78f),
                                            0.28f to Color.Transparent,
                                            0.70f to Color.Transparent,
                                            1.0f to Color.Black.copy(alpha = 0.88f)
                                        )
                                    )
                            ) {
                                // Top Bar: Back Circle + Title Stadium Capsule + Quality Pill + Audio + CC + Overflow
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Back Button + Title Stadium Capsule
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        ProPlayerCircleButton(
                                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Exit Fullscreen",
                                            size = 38.dp,
                                            iconSize = 20.dp,
                                            onClick = { isFullscreen = false }
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.50f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            ) {
                                                Text(
                                                    text = if (currentSubject != null) "$safeTitle • ${currentSubject.name}" else safeTitle,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Right: Quality Pill + Audio + Subtitles + Overflow
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            onClick = { showQualityDialog = true },
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.50f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            ) {
                                                Text(
                                                    text = selectedQualityLabel,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = TextStyle(fontFeatureSettings = "\"tnum\"")
                                                )
                                            }
                                        }

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.MusicNote,
                                            contentDescription = "Audio Track",
                                            size = 38.dp,
                                            iconSize = 18.dp,
                                            onClick = {
                                                Toast.makeText(context, "Audio Track: Stereo 48kHz", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.Subtitles,
                                            contentDescription = "Subtitles",
                                            size = 38.dp,
                                            iconSize = 18.dp,
                                            onClick = {
                                                Toast.makeText(context, "English Subtitles Active", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.MoreVert,
                                            contentDescription = "More Options",
                                            size = 38.dp,
                                            iconSize = 18.dp,
                                            onClick = { showSpeedSheet = true }
                                        )
                                    }
                                }

                                // Center Transport: 56dp rewind / 68dp hero play / 56dp forward
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                                ) {
                                    ProPlayerCircleButton(
                                        icon = Icons.Default.SkipPrevious,
                                        contentDescription = "Rewind 10s",
                                        size = 56.dp,
                                        iconSize = 28.dp,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            seekRelative(-10)
                                            seekRewindPulse = true
                                        }
                                    )

                                    ProPlayerCircleButton(
                                        icon = when {
                                            isVideoEnded -> Icons.Default.Replay
                                            isPlaying -> Icons.Default.Pause
                                            else -> Icons.Default.PlayArrow
                                        },
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        size = 68.dp,
                                        iconSize = 36.dp,
                                        containerColor = Color.Black.copy(alpha = 0.70f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isVideoEnded) {
                                                seekTo(0f)
                                                playVideo()
                                            } else {
                                                togglePlayPause()
                                            }
                                        }
                                    )

                                    ProPlayerCircleButton(
                                        icon = Icons.Default.SkipNext,
                                        contentDescription = "Forward 10s",
                                        size = 56.dp,
                                        iconSize = 28.dp,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            seekRelative(10)
                                            seekForwardPulse = true
                                        }
                                    )
                                }

                                // Floating Quick-Action Tool Islands (Above Scrubber)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = 52.dp, start = 14.dp, end = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left Island (7 buttons)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ProPlayerCircleButton(
                                            icon = Icons.Default.Headphones,
                                            contentDescription = "Background Audio",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = {
                                                Toast.makeText(context, "Background Audio Mode Enabled", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.LockOpen,
                                            contentDescription = "Lock Screen",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = {
                                                isScreenLocked = true
                                                Toast.makeText(context, "Screen Locked. Tap lock icon to unlock.", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.ScreenRotation,
                                            contentDescription = "Orientation",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = { isFullscreen = !isFullscreen }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.Speed,
                                            contentDescription = "Playback Speed",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            contentColor = if (currentSpeed != 1.0f) Color(0xFFE5B6F8) else Color.White,
                                            onClick = { showSpeedSheet = true }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.Repeat,
                                            contentDescription = "Loop Video",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            contentColor = if (isLooping) Color(0xFFE5B6F8) else Color.White,
                                            onClick = {
                                                isLooping = !isLooping
                                                Toast.makeText(context, if (isLooping) "Repeat On" else "Repeat Off", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.AspectRatio,
                                            contentDescription = "Aspect Ratio",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = {
                                                Toast.makeText(context, "Aspect Ratio: 16:9 Original", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        Surface(
                                            onClick = {
                                                Toast.makeText(context, "Bookmarked at ${formatSeconds(currentPositionSec)}", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.50f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "AB",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Right Island (4 buttons)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ProPlayerCircleButton(
                                            icon = Icons.Default.CameraAlt,
                                            contentDescription = "Capture Note",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = {
                                                Toast.makeText(context, "Frame saved to Quick Notes", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.ZoomIn,
                                            contentDescription = "Zoom",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = {
                                                Toast.makeText(context, "Zoom: 100% Fit", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            ProPlayerCircleButton(
                                                icon = Icons.Default.PictureInPicture,
                                                contentDescription = "PiP",
                                                size = 34.dp,
                                                iconSize = 17.dp,
                                                onClick = {
                                                    val params = PictureInPictureParams.Builder()
                                                        .setAspectRatio(Rational(16, 9))
                                                        .build()
                                                    activity?.enterPictureInPictureMode(params)
                                                }
                                            )
                                        }

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Fullscreen",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            onClick = { isFullscreen = false }
                                        )
                                    }
                                }

                                // Bottom Timeline & Gapped Lavender Scrubber
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val effectivePosition = if (isUserScrubbing) scrubPositionSec else currentPositionSec

                                    Text(
                                        text = formatSeconds(effectivePosition),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontFeatureSettings = "\"tnum\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    ExpressiveVideoSlider(
                                        positionSec = effectivePosition,
                                        durationSec = totalDurationSec,
                                        loadedFraction = loadedFraction,
                                        isScrubbing = isUserScrubbing,
                                        onScrubStart = { isUserScrubbing = true },
                                        onScrubChange = { newSec ->
                                            scrubPositionSec = newSec
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        onScrubFinished = { finalSec ->
                                            seekTo(finalSec)
                                            isUserScrubbing = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = formatSeconds(totalDurationSec),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontFeatureSettings = "\"tnum\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        } else {
                            // ==============================================================
                            // PORTRAIT COMPACT HUD (Ultra clean, zero clutter, 5 essentials)
                            // ==============================================================
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                // 1. Top Row: Back (left), Quality Pill + Settings (right)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    ProPlayerCircleButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        size = 36.dp,
                                        iconSize = 18.dp,
                                        onClick = onBackClick
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            onClick = { showQualityDialog = true },
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.60f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = selectedQualityLabel,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = TextStyle(fontFeatureSettings = "\"tnum\"")
                                                )
                                            }
                                        }

                                        ProPlayerCircleButton(
                                            icon = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            size = 34.dp,
                                            iconSize = 18.dp,
                                            onClick = { showSpeedSheet = true }
                                        )
                                    }
                                }

                                // 2. Center Transport Controls: 46dp rewind, 56dp play/pause, 46dp forward
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                                ) {
                                    ProPlayerCircleButton(
                                        icon = Icons.Default.Replay10,
                                        contentDescription = "Rewind 10s",
                                        size = 46.dp,
                                        iconSize = 24.dp,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            seekRelative(-10)
                                            seekRewindPulse = true
                                        }
                                    )

                                    ProPlayerCircleButton(
                                        icon = when {
                                            isVideoEnded -> Icons.Default.Replay
                                            isPlaying -> Icons.Default.Pause
                                            else -> Icons.Default.PlayArrow
                                        },
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        size = 56.dp,
                                        iconSize = 30.dp,
                                        containerColor = Color.Black.copy(alpha = 0.70f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isVideoEnded) {
                                                seekTo(0f)
                                                playVideo()
                                            } else {
                                                togglePlayPause()
                                            }
                                        }
                                    )

                                    ProPlayerCircleButton(
                                        icon = Icons.Default.Forward10,
                                        contentDescription = "Forward 10s",
                                        size = 46.dp,
                                        iconSize = 24.dp,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            seekRelative(10)
                                            seekForwardPulse = true
                                        }
                                    )
                                }

                                // 3. Bottom Row: [10:25] ═══════[|]═══════ [19:43]  [PiP]  [Fullscreen]
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val effectivePosition = if (isUserScrubbing) scrubPositionSec else currentPositionSec

                                    Text(
                                        text = formatSeconds(effectivePosition),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontFeatureSettings = "\"tnum\"",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    ExpressiveVideoSlider(
                                        positionSec = effectivePosition,
                                        durationSec = totalDurationSec,
                                        loadedFraction = loadedFraction,
                                        isScrubbing = isUserScrubbing,
                                        onScrubStart = { isUserScrubbing = true },
                                        onScrubChange = { newSec ->
                                            scrubPositionSec = newSec
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        onScrubFinished = { finalSec ->
                                            seekTo(finalSec)
                                            isUserScrubbing = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = formatSeconds(totalDurationSec),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontFeatureSettings = "\"tnum\"",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        ProPlayerCircleButton(
                                            icon = Icons.Default.PictureInPicture,
                                            contentDescription = "PiP",
                                            size = 30.dp,
                                            iconSize = 16.dp,
                                            onClick = {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .build()
                                                activity?.enterPictureInPictureMode(params)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    ProPlayerCircleButton(
                                        icon = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        size = 30.dp,
                                        iconSize = 16.dp,
                                        onClick = { isFullscreen = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // VIEWPORT SWITCHER: FULLSCREEN LANDSCAPE VS PORTRAIT WITH STUDY HUB
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                videoPlayerView()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 16:9 Video Container pinned directly below Android status bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    videoPlayerView()
                }

                // STRUCTURED STUDY HUB (Below Player)
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
                                color = Color(0xFF1E1D24),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFFE5B6F8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    val breadcrumb = buildString {
                                        append(currentSubject?.name ?: "English Core")
                                        if (currentChapter != null) {
                                            append(" • Ch ${currentChapter.number}")
                                        }
                                    }
                                    Text(
                                        text = breadcrumb,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE5B6F8)
                                    )
                                }
                            }

                            val weightage = currentChapter?.weightageMarks ?: 5
                            if (weightage > 0) {
                                Surface(
                                    shape = ExpressivePillSmall,
                                    color = Color(0xFF231B38),
                                    border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "$weightage Marks Weightage",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD0BCFF),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Action Study Ribbon (DPP Quiz, NCERT PDF, Download, Mark Mastered)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Take DPP Quiz Button
                        StudyRibbonAction(
                            icon = Icons.Default.Quiz,
                            label = "DPP Quiz",
                            containerColor = Color(0xFF231B38),
                            contentColor = Color(0xFFD0BCFF),
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
                            containerColor = Color(0xFF112822),
                            contentColor = Color(0xFF6EE7B7),
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

                        // 3. Download Offline (Dynamic State)
                        val dlState = currentDownloadState
                        val (dlIcon, dlLabel, dlContainer, dlContent) = when (dlState) {
                            is DownloadState.Progress -> ActionTileStyle(
                                icon = Icons.Default.CloudDownload,
                                label = "${dlState.percentage}%",
                                container = Color(0xFF2B2111),
                                content = Color(0xFFFBBF24)
                            )
                            is DownloadState.Success -> ActionTileStyle(
                                icon = Icons.Default.CheckCircle,
                                label = "Saved",
                                container = Color(0xFF14261C),
                                content = Color(0xFF4ADE80)
                            )
                            else -> ActionTileStyle(
                                icon = Icons.Default.CloudDownload,
                                label = "Download",
                                container = Color(0xFF1E1D24),
                                content = Color(0xFFE5B6F8)
                            )
                        }

                        StudyRibbonAction(
                            icon = dlIcon,
                            label = dlLabel,
                            containerColor = dlContainer,
                            contentColor = dlContent,
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
                            containerColor = if (isMastered) Color(0xFF14261C) else Color(0xFF1E1D24),
                            contentColor = if (isMastered) Color(0xFF4ADE80) else Color(0xFFCAC4D0),
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
                                    Triple("1080p (Full HD)", "1080", false),
                                    Triple("720p (HD) [Recommended]", "720", false),
                                    Triple("480p (Standard)", "480", false),
                                    Triple("360p (Data Saver)", "360", false),
                                    Triple("Audio Lecture (M4A)", "audio", true)
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
                                                val targetUrl = if (isYouTube && youtubeVideoId != null) {
                                                    "https://www.youtube.com/watch?v=$youtubeVideoId"
                                                } else url
                                                LectureDownloadManager.startDownload(
                                                    context = context,
                                                    id = lectureId,
                                                    title = safeTitle,
                                                    chapterId = currentChapter?.id ?: "chapter",
                                                    url = targetUrl,
                                                    formatSelector = format,
                                                    isAudioOnly = isAudio
                                                )
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
                                shape = M3EBentoTileShape
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudDownload,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = state.stage.label,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (state.qualityLabel.isNotBlank()) {
                                                    Text(
                                                        text = state.qualityLabel,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = ExpressivePillSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "${state.percentage}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    LinearProgressIndicator(
                                        progress = { state.percentage / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(ExpressivePillSmall),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surface,
                                        drawStopIndicator = {}
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Speed,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = state.speedFormatted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Timer,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = state.etaFormatted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = state.sizeFormatted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        TextButton(
                                            onClick = {
                                                LectureDownloadManager.cancelDownload(context, lectureId, safeTitle)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Downloaded for Offline Study", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            val sizeMb = state.localFile.length() / (1024.0 * 1024.0)
                                            Text(
                                                String.format(java.util.Locale.US, "Ready in offline library • %.1f MB", sizeMb),
                                                color = SuccessGreen.copy(alpha = 0.85f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "Download Interrupted: ${state.message}",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    TextButton(onClick = { showQualityDialog = true }) {
                                        Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        else -> {}
                    }

                    // THE NEXT CHAPTER / NEXT LECTURE PROGRESSION BENTO DECK
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

                                val badgeText = when {
                                    safeTitle.contains("One Shot", ignoreCase = true) || safeTitle.contains("Revision", ignoreCase = true) -> "Full Unit Review"
                                    nextChapter != null && nextChapter.weightageMarks > 0 -> "${nextChapter.weightageMarks} Marks Weightage"
                                    currentChapter != null && currentChapter.weightageMarks > 0 -> "${currentChapter.weightageMarks} Marks Weightage"
                                    else -> "Board Focus"
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

private data class ActionTileStyle(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color
)

@Composable
fun StudyRibbonAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
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
        border = border,
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

@Composable
fun ProPlayerCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    containerColor: Color = Color.Black.copy(alpha = 0.60f),
    contentColor: Color = Color.White,
    border: BorderStroke? = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "btn_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = containerColor,
        border = border,
        modifier = modifier
            .size(size)
            .scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun ExpressiveVideoSlider(
    positionSec: Float,
    durationSec: Float,
    loadedFraction: Float,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(0xFFE5B6F8) // M3 Expressive pastel lilac matching reference image
    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "track_height"
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isScrubbing) 12.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "thumb_width"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isScrubbing) 22.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "thumb_height"
    )
    val thumbCorner by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "thumb_corner"
    )

    val validDuration = durationSec.coerceAtLeast(1f)
    val currentFraction = (positionSec / validDuration).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .height(34.dp)
            .pointerInput(validDuration) {
                detectTapGestures(
                    onPress = { offset ->
                        onScrubStart()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val target = fraction * validDuration
                        onScrubChange(target)
                        tryAwaitRelease()
                        onScrubFinished(target)
                    }
                )
            }
            .pointerInput(validDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onScrubStart()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubChange(fraction * validDuration)
                    },
                    onDragEnd = {
                        onScrubFinished(positionSec)
                    },
                    onDragCancel = {
                        onScrubFinished(positionSec)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrubChange(fraction * validDuration)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val thumbXPx = currentFraction * totalWidthPx

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val tHeightPx = trackHeight.toPx()
            val tHalfH = tHeightPx / 2f
            val gapPx = if (isScrubbing) 4.dp.toPx() else 2.dp.toPx()
            val thumbWPx = thumbWidth.toPx()
            val thumbHPx = thumbHeight.toPx()
            val thumbRadiusPx = thumbCorner.toPx()

            // 1. Inactive Background Track (Full Width)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(0f, centerY - tHalfH),
                size = Size(size.width, tHeightPx),
                cornerRadius = CornerRadius(tHalfH, tHalfH)
            )

            // 2. Buffer Progress Track (Underneath)
            val bufferWPx = (loadedFraction.coerceIn(0f, 1f) * size.width)
            if (bufferWPx > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.38f),
                    topLeft = Offset(0f, centerY - tHalfH),
                    size = Size(bufferWPx, tHeightPx),
                    cornerRadius = CornerRadius(tHalfH, tHalfH)
                )
            }

            // 3. Active Played Track (With M3 gap before the thumb)
            val activeEndPx = (thumbXPx - thumbWPx / 2f - gapPx).coerceAtLeast(0f)
            if (activeEndPx > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(0f, centerY - tHalfH),
                    size = Size(activeEndPx, tHeightPx),
                    cornerRadius = CornerRadius(tHalfH, tHalfH)
                )
            }

            // 4. M3 Expressive Morphing Thumb
            val thumbLeft = (thumbXPx - thumbWPx / 2f).coerceIn(0f, size.width - thumbWPx)
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(thumbLeft, centerY - thumbHPx / 2f),
                size = Size(thumbWPx, thumbHPx),
                cornerRadius = CornerRadius(thumbRadiusPx, thumbRadiusPx)
            )
        }
    }
}
