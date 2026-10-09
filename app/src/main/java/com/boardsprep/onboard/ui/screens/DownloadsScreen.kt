package com.boardsprep.onboard.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.playback.LocalLectureScanner
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.local.entities.DownloadedFileEntity
import com.boardsprep.onboard.data.models.Lecture
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveEmptyState
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import com.boardsprep.onboard.ui.components.expressiveBounce
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onPlayLecture: (lectureId: String, url: String, title: String) -> Unit,
    onOpenPdf: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Downloaded files from Room DB
    val downloads by repository.getAllDownloads().collectAsState(initial = emptyList())

    // Local user lectures scanned directly from storage
    val localLectures = remember(refreshKey) {
        LocalLectureScanner.scanLocalLectures(context)
    }

    val lecturesFolder = remember {
        LocalLectureScanner.getLecturesDirectory(context).absolutePath
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Offline Library",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        val totalBytes = downloads.sumOf { it.fileSizeBytes }
                        Text(
                            text = "Storage used: ${Formatter.formatFileSize(context, totalBytes)} • CBSE 2027",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Local Files")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val downloadsTabs = listOf(
                ExpressiveTabItem(
                    title = "In-App Downloads",
                    icon = Icons.Default.CloudDownload,
                    badge = "${downloads.size}"
                ),
                ExpressiveTabItem(
                    title = "Local Storage",
                    icon = Icons.Default.Folder,
                    badge = "${localLectures.size}"
                )
            )

            ExpressiveSegmentedTabs(
                tabs = downloadsTabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                accentColor = MaterialTheme.colorScheme.primary
            )

            if (selectedTabIndex == 0) {
                // In-App Downloads
                if (downloads.isEmpty()) {
                    ExpressiveEmptyState(
                        icon = Icons.Default.CloudDownload,
                        title = "No Offline Downloads Yet",
                        description = "Tap the download icon on any lecture video or PDF across your subjects to study 100% offline without distractions.",
                        tintColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(downloads, key = { it.id }) { item ->
                            DownloadedItemCard(
                                item = item,
                                onOpen = {
                                    if (item.fileType == "video") {
                                        onPlayLecture(item.id, item.localPath, item.title)
                                    } else {
                                        onOpenPdf(item.localPath, item.title)
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        repository.deleteDownload(item.id, item.localPath)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // User Added / Local Folder Scanned
                val validLocalLectures = remember(localLectures) {
                    localLectures.filter { it.title.isNotBlank() }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            shape = ExpressiveCardMedium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryBlue.copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = PrimaryBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Add Your Own Lectures",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "You can copy your own lecture files (.mp4, .mkv, .webm) into OnBOARD's lectures directory to watch them ad-free:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = ExpressiveCardMedium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lecturesFolder,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(10.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (validLocalLectures.isEmpty()) {
                        item {
                            ExpressiveEmptyState(
                                icon = Icons.Default.FolderOpen,
                                title = "No Local Media Files Found",
                                description = "Copy your own lecture files (.mp4, .mkv) into OnBOARD's lectures directory to watch them anytime offline.",
                                tintColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        items(validLocalLectures, key = { it.id }) { lecture ->
                            LocalLectureCard(
                                lecture = lecture,
                                onPlay = {
                                    val path = lecture.localFilePath ?: lecture.streamUrl
                                    onPlayLecture(lecture.id, path, lecture.title)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedItemCard(
    item: DownloadedFileEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(item.downloadedAt) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(item.downloadedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveCardLarge)
            .clickable { onOpen() },
        shape = ExpressiveCardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (item.fileType == "video") PrimaryBlue.copy(alpha = 0.2f)
                    else Color(0xFFFFEBEE)
                ) {
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.fileType == "video") Icons.Default.PlayArrow else Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = if (item.fileType == "video") PrimaryBlue else Color(0xFFC62828),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${Formatter.formatFileSize(context, item.fileSizeBytes)} • $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpen) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Open",
                        tint = PrimaryBlue
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalLectureCard(
    lecture: Lecture,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveCardLarge)
            .clickable { onPlay() },
        shape = ExpressiveCardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = AccentAmber.copy(alpha = 0.2f)
                ) {
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = lecture.title.trim().ifBlank { "Local Lecture" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val fileSize = remember(lecture.localFilePath) {
                        lecture.localFilePath?.let { path ->
                            val f = File(path)
                            if (f.exists()) "${(f.length() / (1024 * 1024))} MB" else "Local File"
                        } ?: "Local File"
                    }
                    Text(
                        text = "User Added • $fileSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onPlay,
                shape = ExpressivePillSmall
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
