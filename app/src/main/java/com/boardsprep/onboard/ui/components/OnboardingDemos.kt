package com.boardsprep.onboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.boardsprep.onboard.core.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Paced demonstration loop helper inspired by ClearPDF.
 * Uses a dwell hold ([holdMs]) so students can absorb the completed state
 * before it gently resets. Gated strictly on [isActive].
 */
@Composable
fun rememberDemoLoop(
    isActive: Boolean,
    riseMs: Int = 750,
    holdMs: Long = 1500L,
    gapMs: Long = 400L
): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isActive) {
        if (!isActive) {
            target = 0f
            return@LaunchedEffect
        }
        delay(250)
        while (true) {
            target = 1f
            delay(riseMs + holdMs)
            target = 0f
            delay(gapMs)
        }
    }
    val v by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(riseMs, easing = FastOutSlowInEasing),
        label = "demoLoop"
    )
    return v
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private const val OrbitPages = 6
private const val OrbitRadius = 82f
private const val TwoPi = (2 * PI).toFloat()
private const val Pi = PI.toFloat()
private val SheetW = 118.dp
private val SheetH = 154.dp

private data class SubjectKind(val name: String, val code: String, val tint: Color)

private val SubjectKinds = listOf(
    SubjectKind("Physics", "042", Color(0xFF9C27B0)),
    SubjectKind("Chemistry", "043", Color(0xFF00E676)),
    SubjectKind("Mathematics", "041", Color(0xFFFFB300)),
    SubjectKind("Biology", "044", Color(0xFF00E5FF)),
    SubjectKind("English Core", "301", Color(0xFFFF4081)),
    SubjectKind("Physical Ed", "048", Color(0xFF3D5AFE))
)

private fun bookIn(p: Float): Float = ((p - 0.82f) / 0.15f).coerceIn(0f, 1f)

/**
 * Interactive Demo 1: Cosmic In-Fall & 3D Assembled Book (ClearPDF Spiral Vortex)
 * 6 flying subject sheets spiral inward with angular velocity acceleration (`pull²`),
 * crease in 3D perspective (`rotationY`), and assemble into the unified CBSE 2027 Master Companion.
 */
@Composable
fun DemoDocumentAssembly(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val density = LocalDensity.current.density

    LaunchedEffect(isActive) {
        if (!isActive) {
            fade.snapTo(0f)
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        delay(200)
        fade.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        progress.animateTo(1f, tween(2400, easing = LinearEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1B4B).copy(alpha = 0.90f),
                        Color(0xFF0B0F19)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Starfield Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.40f

            val dashedStroke = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), 0f)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = baseRadius * 0.75f,
                center = center,
                style = dashedStroke
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = baseRadius,
                center = center,
                style = dashedStroke
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = baseRadius * 1.25f,
                center = center,
                style = dashedStroke
            )
        }

        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer { alpha = fade.value },
            contentAlignment = Alignment.Center
        ) {
            // 6 flying orbiting subject sheets
            repeat(OrbitPages) { i ->
                val depth = (OrbitPages - 1 - i).toFloat()
                val base = (i.toFloat() / OrbitPages) * TwoPi
                val dir = if (i % 2 == 0) 1f else -1f

                OrbitSubjectSheet(
                    kind = SubjectKinds[i],
                    modifier = Modifier.graphicsLayer {
                        val p = progress.value
                        val head = i * 0.045f
                        val local = ((p - head) / (1f - head)).coerceIn(0f, 1f)
                        val pull = smooth(((local - 0.18f) / 0.58f).coerceIn(0f, 1f))

                        val angle = base + (0.40f * local + 1.15f * pull * pull) * TwoPi
                        val radius = OrbitRadius * (1f - pull) * density

                        translationX = cos(angle) * radius + (depth * 2.2f * density) * pull
                        translationY = sin(angle) * radius + (-depth * 2.6f * density) * pull

                        val fold = sin(pull * Pi)
                        rotationY = 64f * fold * dir
                        rotationZ = lerp(sin(angle) * 16f, depth * 1.6f, pull) + 20f * fold * dir
                        cameraDistance = 14f

                        val s = lerp(0.35f, 1f, pull)
                        scaleX = s
                        scaleY = s

                        alpha = smooth(local * 4f) *
                                if (i == OrbitPages - 1) 1f - bookIn(p) else 1f
                    }
                )
            }

            // Assembled Master Book Surface
            Surface(
                modifier = Modifier
                    .size(width = SheetW, height = SheetH)
                    .graphicsLayer {
                        val b = bookIn(progress.value)
                        alpha = b
                        val s = lerp(0.96f, 1f, b)
                        scaleX = s
                        scaleY = s
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1F2430).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFFD54F),
                            Color(0xFFE040FB),
                            Color(0xFF00E5FF)
                        )
                    )
                ),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CBSE 2027",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD54F)
                            )
                            Text(
                                text = "95%+",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF69F0AE)
                            )
                        }

                        // Content lines revealing in colors of all 6 assembled subjects
                        listOf(0.85f, 1f, 0.70f, 0.90f, 0.60f, 0.80f).forEachIndexed { idx, w ->
                            val lineTint = SubjectKinds[idx % SubjectKinds.size].tint
                            Box(
                                Modifier
                                    .fillMaxWidth(w)
                                    .height(if (idx == 0) 7.dp else 4.dp)
                                    .graphicsLayer {
                                        val head = idx * 0.10f
                                        val t = ((bookIn(progress.value) - head) / (1f - head)).coerceIn(0f, 1f)
                                        alpha = t
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                        scaleX = t
                                    }
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(lineTint.copy(if (idx == 0) 0.92f else 0.80f))
                            )
                        }
                    }

                    Text(
                        text = "Mastered Together",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // Bottom subject pill indicator
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubjectKinds.forEach { kind ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(kind.tint)
                )
            }
            Text(
                text = "6 Core Curricula Unified",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun OrbitSubjectSheet(kind: SubjectKind, modifier: Modifier) {
    Box(
        modifier
            .size(width = SheetW, height = SheetH)
            .clip(RoundedCornerShape(14.dp))
            .background(kind.tint)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = kind.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black.copy(alpha = 0.85f),
                    maxLines = 1
                )
                listOf(0.85f, 1f, 0.65f, 0.90f).forEach { w ->
                    Box(
                        Modifier
                            .fillMaxWidth(w)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.70f))
                    )
                }
            }
            Text(
                text = "Code ${kind.code}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Interactive Demo 2: The 4-Pillar Curriculum Engine (Paced Circuit Power)
 * Shows the continuous flow of concepts into marks via 4 interconnected pillars.
 */
@Composable
fun DemoFourPillarsEngine(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val loopClock = rememberDemoLoop(isActive, riseMs = 800, holdMs = 1500L)
    var selectedPillar by remember { mutableIntStateOf(3) }

    val pillars = remember {
        listOf(
            Triple("Lectures", "One-Shot Video Hub", Icons.Default.PlayCircle),
            Triple("NCERT", "Line-by-Line Reader", Icons.AutoMirrored.Filled.MenuBook),
            Triple("DPPs", "Daily Practice Vault", Icons.AutoMirrored.Filled.Assignment),
            Triple("Q-Bank", "357 Official Questions", Icons.Default.Quiz)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "THE 4-PILLAR REVISION SYSTEM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Zero Fluff • 100% Board Calibrated",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Class 12 Sync",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Interactive 4 Pillars Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pillars.forEachIndexed { index, (shortName, _, icon) ->
                    val isSelected = (selectedPillar == index)
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                        label = "pillar_bg_$index"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "pillar_fg_$index"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedPillar = index }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = shortName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }

            // Pillar Active Blueprint Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                when (selectedPillar) {
                    0 -> PillarDetail(
                        title = "One-Shot Lectures Hub",
                        desc = "Top-tier curated YouTube lectures organized chapter-by-chapter. No distraction, full screen mode, with instant speed control.",
                        stat = "100+ Hours Curated"
                    )
                    1 -> PillarDetail(
                        title = "Line-by-Line NCERT Reader",
                        desc = "Smart embedded PDF viewer with instant dark-mode inverse, bookmarking, and margin annotation tools.",
                        stat = "14 Subject Units Bundled"
                    )
                    2 -> PillarDetail(
                        title = "Daily Practice & Quizzes",
                        desc = "MCQ DPPs and Spaced Repetition flashcards that automatically capture mistakes into the Error Vault.",
                        stat = "120+ DPP Sets"
                    )
                    else -> PillarDetail(
                        title = "Official CBSE Question Bank",
                        desc = "357 official subjective questions across Physics, Chem, Maths, Bio, English & PE with step-by-step mark breakdown.",
                        stat = "357 Questions • 2m/3m/4m/5m"
                    )
                }
            }
        }
    }
}

@Composable
private fun PillarDetail(title: String, desc: String, stat: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stat,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = desc,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

/**
 * Interactive Demo 3: ClearPDF-Grade Smart Reader & Arc-Length Highlighter
 * Uses `PathMeasure` arc-length trimming for an authentic highlighter sweep,
 * then creases open in 3D (`rotationY = 55f`) to show CBSE marking rubrics.
 */
@Composable
fun DemoSmartReaderHighlight(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val p = rememberDemoLoop(isActive, riseMs = 1000, holdMs = 1600L)
    var showMarkingScheme by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E323E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Simulated PDF Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD740))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF69F0AE))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Physics-PQ.pdf (Page 4)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (showMarkingScheme) Color(0xFF00E676).copy(alpha = 0.2f)
                    else Color(0xFF6750A4).copy(alpha = 0.25f),
                    modifier = Modifier.clickable { showMarkingScheme = !showMarkingScheme }
                ) {
                    Text(
                        text = if (showMarkingScheme) "Hide Scheme" else "Split Scheme",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showMarkingScheme) Color(0xFF00E676) else Color(0xFFD0BCFF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Simulated Paper Content with PathMeasure Highlighter Sweep
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E212B))
                    .border(1.dp, Color(0xFF2A2E3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Q17. Explain why the hole concentration in an extrinsic semiconductor reduces when pentavalent doping is increased.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 16.sp
                    )

                    // Text with Animated Highlighter Sweep (Arc-Length Trim)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val sweepFraction = (p / 0.65f).coerceIn(0f, 1f)
                            drawRoundRect(
                                color = Color(0xFFFFD54F).copy(alpha = 0.35f),
                                size = Size(size.width * sweepFraction, size.height),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                        Text(
                            text = "Model: Thermally generated holes recombine with the copious electrons introduced by donor atoms, strictly satisfying the Mass Action Law.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFECB3),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(4.dp)
                        )
                    }

                    val splitFold = if (showMarkingScheme || p > 0.70f) 1f else 0f
                    if (splitFold > 0f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val f = (p - 0.70f) / 0.30f
                                    alpha = f.coerceIn(0f, 1f)
                                    rotationY = 30f * (1f - f.coerceIn(0f, 1f))
                                    cameraDistance = 12f
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CBSE Marking Rubric: Mentioning Recombination (+0.5m), Formula ne·nh=ni² (+1.5m)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB9F6CA),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "2/2 M",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                }
            }

            // Bottom CTA Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap 'Split Scheme' to inspect CBSE answers",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = "100% Offline Ready",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D)
                )
            }
        }
    }
}

/**
 * Interactive Demo 4: Subjective CBSE Question Bank & Step Rubric
 * Demonstrates active recall with step-wise marks and evaluator self-assessment.
 */
@Composable
fun DemoStepRubricReveal(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val p = rememberDemoLoop(isActive, riseMs = 900, holdMs = 1700L)
    var userRating by remember { mutableStateOf<String?>("full") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Question Tag & Marks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "CBSE PRACTICE • VSA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "2 Marks",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Question Text
            Text(
                text = "Explain the variation of hole concentration when an extrinsic semiconductor is doped with pentavalent atoms.",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 17.sp
            )

            // Step Rubric Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Step 1: Stating electron increase",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "+0.5m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Step 2: Recombination & hole drop",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "+1.0m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }
            }

            // 3-Tier Self-Assessment Buttons
            Column {
                Text(
                    text = if (userRating == null) "Self-Evaluate Your Answer:" else "Evaluated: $userRating marks awarded",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EvaluationButton(
                        label = "✓ Full (2m)",
                        color = Color(0xFF00C853),
                        isSelected = userRating == "full",
                        modifier = Modifier.weight(1f)
                    ) { userRating = "full" }

                    EvaluationButton(
                        label = "½ Half (1m)",
                        color = Color(0xFFFF9100),
                        isSelected = userRating == "half",
                        modifier = Modifier.weight(1f)
                    ) { userRating = "half" }

                    EvaluationButton(
                        label = "⟳ Retry (0m)",
                        color = Color(0xFFFF1744),
                        isSelected = userRating == "retry",
                        modifier = Modifier.weight(1f)
                    ) { userRating = "retry" }
                }
            }
        }
    }
}

@Composable
private fun EvaluationButton(
    label: String,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) color.copy(alpha = 0.25f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) color else color.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else color.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Interactive Demo 5: Error Vault & 5-Min Spaced Repetition (The Anti-Forgetting Shield)
 * Visualizes how quiz mistakes get captured and scheduled on the Ebbinghaus curve.
 */
@Composable
fun DemoErrorVaultSpacedReview(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val p = rememberDemoLoop(isActive, riseMs = 900, holdMs = 1500L)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161922)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282D3D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "THE ANTI-FORGETTING SHIELD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color(0xFFFF5252)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color(0xFFFF5252).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Auto-Capture",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Central Visual: Mistake Card Transitioning into Spaced Vault
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Vault Capsule
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(110.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF222736),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF5252).copy(0.4f), Color(0xFFFFD740).copy(0.4f))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mistake Vault #47",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Review in 24h",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFD740)
                            )
                        }

                        // Retention Curve graph simulation
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            val w = size.width
                            val h = size.height

                            val path = Path().apply {
                                moveTo(0f, h * 0.15f)
                                cubicTo(w * 0.35f, h * 0.85f, w * 0.65f, h * 0.40f, w, h * 0.20f)
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF00E5FF),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Text(
                            text = "Ebbinghaus Spaced Repetition Active",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            // Bottom summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wrong answers never get lost",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "5-Min Blitz",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF69F0AE)
                )
            }
        }
    }
}

/**
 * Interactive Demo 6: Stream Selection & CBSE Target Pledge
 * Personalizes student's target score and stream focus with a glossy pledge card.
 */
@Composable
fun DemoStreamPledge(
    isActive: Boolean,
    onStreamSelected: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedStream by remember { mutableStateOf("PCM") }
    var selectedTarget by remember { mutableIntStateOf(95) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stream Chips
            Column {
                Text(
                    text = "SELECT YOUR STREAM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("PCM", "PCB", "PCMB", "Commerce").forEach { stream ->
                        val isSel = (selectedStream == stream)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    selectedStream = stream
                                    onStreamSelected(stream, selectedTarget)
                                },
                            color = if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stream,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Target Marks Chips
            Column {
                Text(
                    text = "TARGET BOARD SCORE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(90, 95, 98).forEach { target ->
                        val isSel = (selectedTarget == target)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    selectedTarget = target
                                    onStreamSelected(selectedStream, target)
                                },
                            color = if (isSel) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSel) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$target%+",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Foil Holographic Pledge Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1E1B4B),
                                Color(0xFF311042),
                                Color(0xFF111827)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFD54F),
                                Color(0xFFE040FB),
                                Color(0xFF00E5FF),
                                Color(0xFFFFD54F)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CBSE 2027 ASPIRANT PLEDGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFFFFD54F)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Class 12 $selectedStream • Target: $selectedTarget%+",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Mastering Step Rubrics & Active Recall Daily",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.MilitaryTech,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
