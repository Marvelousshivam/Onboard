package com.boardsprep.onboard.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.pdf.PdfRendererHelper
import com.boardsprep.onboard.core.theme.AccentAmber
import com.boardsprep.onboard.core.theme.ExpressiveDockShape
import com.boardsprep.onboard.core.theme.ExpressivePillSmall
import com.boardsprep.onboard.core.theme.PrimaryBlue
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    url: String,
    title: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val helper = remember { PdfRendererHelper(context) }

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Builder Essentials State
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isNightMode by remember { mutableStateOf(false) }
    var isFullscreenReader by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    val bookmarkedPages = remember { mutableStateListOf<Int>() }

    // Color Inversion Matrix for OLED Dark Reading Mode
    val invertColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f,  0f,  0f,  0f, 255f,
                     0f, -1f,  0f,  0f, 255f,
                     0f,  0f, -1f,  0f, 255f,
                     0f,  0f,  0f,  1f,   0f
                )
            )
        )
    }

    // Reset zoom and pan when switching pages
    fun resetZoomAndPan() {
        zoomScale = 1.0f
        panOffset = Offset.Zero
    }

    // Download/Load PDF
    LaunchedEffect(url) {
        isLoading = true
        val file = helper.getOrDownloadPdfFile(url)
        pdfFile = file
        if (file != null) {
            totalPages = helper.getPageCount(file)
            currentPageIndex = 0
            currentBitmap = helper.renderPageToBitmap(file, 0, targetWidth = 1440)
        }
        isLoading = false
    }

    // Render page when index changes
    LaunchedEffect(currentPageIndex, pdfFile) {
        val file = pdfFile
        if (file != null && totalPages > 0) {
            currentBitmap = helper.renderPageToBitmap(file, currentPageIndex, targetWidth = 1440)
            resetZoomAndPan()
        }
    }

    // Handle back button in fullscreen mode
    BackHandler(enabled = isFullscreenReader) {
        isFullscreenReader = false
    }

    Scaffold(
        topBar = {
            if (!isFullscreenReader) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                maxLines = 1,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (totalPages > 0) {
                                Text(
                                    text = "CBSE 2027 In-App Reader • Page ${currentPageIndex + 1} of $totalPages",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        // Quick Bookmark action in top bar
                        IconButton(onClick = {
                            if (bookmarkedPages.contains(currentPageIndex)) {
                                bookmarkedPages.remove(currentPageIndex)
                            } else {
                                bookmarkedPages.add(currentPageIndex)
                            }
                        }) {
                            Icon(
                                imageVector = if (bookmarkedPages.contains(currentPageIndex)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (bookmarkedPages.contains(currentPageIndex)) AccentAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Open in external app
                        IconButton(onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open external viewer: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External App"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreenReader) PaddingValues(0.dp) else innerPadding)
                .background(if (isNightMode) Color(0xFF100E14) else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                com.boardsprep.onboard.ui.components.PdfAsymmetricLoadingView(title = title)
            } else if (currentBitmap != null) {

                // Interactive Zoom & Pan Surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.3f) {
                                        resetZoomAndPan()
                                    } else {
                                        zoomScale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1.0f, 5.0f)
                                zoomScale = newScale
                                if (newScale > 1.0f) {
                                    panOffset += pan
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        colorFilter = if (isNightMode) invertColorFilter else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isFullscreenReader) 0.dp else 6.dp)
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    )
                }

                // Bookmark Badge Indicator on Page
                if (bookmarkedPages.contains(currentPageIndex)) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        shape = ExpressivePillSmall,
                        color = AccentAmber.copy(alpha = 0.95f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "High-Yield Flag",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                        }
                    }
                }

                // Floating Exit Fullscreen Button
                if (isFullscreenReader) {
                    IconButton(
                        onClick = { isFullscreenReader = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                // Builder Essentials Floating Capsule Dock (M3 Expressive Dock)
                if (totalPages > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (isFullscreenReader) 16.dp else 20.dp)
                            .padding(horizontal = 16.dp)
                            .shadow(12.dp, ExpressiveDockShape),
                        shape = ExpressiveDockShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Zoom Out
                            IconButton(
                                onClick = {
                                    val next = (zoomScale - 0.5f).coerceAtLeast(1.0f)
                                    zoomScale = next
                                    if (next <= 1.0f) panOffset = Offset.Zero
                                },
                                enabled = zoomScale > 1.05f,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Zoom Percentage Pill (Tap to reset zoom)
                            Surface(
                                shape = ExpressivePillSmall,
                                color = if (zoomScale > 1.05f) PrimaryBlue.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .clickable { resetZoomAndPan() }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).roundToInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (zoomScale > 1.05f) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Zoom In
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale + 0.5f).coerceAtMost(5.0f)
                                },
                                enabled = zoomScale < 4.95f,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Divider
                            VerticalDivider(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            // Previous Page
                            IconButton(
                                onClick = {
                                    if (currentPageIndex > 0) currentPageIndex--
                                },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Page",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Page Jump Capsule Pill (Tap to open quick slider)
                            Surface(
                                shape = ExpressivePillSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clickable { showJumpDialog = true }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "${currentPageIndex + 1} / $totalPages",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            // Next Page
                            IconButton(
                                onClick = {
                                    if (currentPageIndex < totalPages - 1) currentPageIndex++
                                },
                                enabled = currentPageIndex < totalPages - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Page",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Divider
                            VerticalDivider(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            // Night Mode Reader Toggle
                            IconButton(
                                onClick = { isNightMode = !isNightMode },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Night Mode",
                                    tint = if (isNightMode) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Fullscreen Reader Mode Toggle
                            IconButton(
                                onClick = { isFullscreenReader = !isFullscreenReader },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreenReader) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Page Jump Bottom Sheet / Dialog
                if (showJumpDialog) {
                    var sliderValue by remember { mutableFloatStateOf((currentPageIndex + 1).toFloat()) }
                    AlertDialog(
                        onDismissRequest = { showJumpDialog = false },
                        title = {
                            Text(
                                text = "Jump to Page",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Page ${sliderValue.roundToInt()} of $totalPages",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = PrimaryBlue
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    valueRange = 1f..totalPages.toFloat(),
                                    steps = if (totalPages > 2) totalPages - 2 else 0,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryBlue,
                                        activeTrackColor = PrimaryBlue
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    currentPageIndex = (sliderValue.roundToInt() - 1).coerceIn(0, totalPages - 1)
                                    showJumpDialog = false
                                },
                                shape = ExpressivePillSmall
                            ) {
                                Text("Jump")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showJumpDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

            } else {
                // Fallback state if PDF fails to render
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Could not render PDF in-app",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "NCERT servers occasionally throttle direct downloads. You can open it directly in your browser or external PDF viewer.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open external viewer: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = ExpressivePillSmall,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Browser / External App")
                    }
                }
            }
        }
    }
}
