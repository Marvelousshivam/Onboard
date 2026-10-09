package com.boardsprep.onboard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp
import com.boardsprep.onboard.core.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Dynamic Shimmer Brush.
 * Sweeps an organic dual-harmonic luminance wave across components
 * to reduce perceived wait times and cognitive anxiety.
 */
@Composable
fun rememberExpressiveShimmerBrush(
    targetValue: Float = 2000f,
    durationMillis: Int = 1400
): Brush {
    val transition = rememberInfiniteTransition(label = "expressive_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -800f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryHighlight = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)

    val shimmerColors = listOf(
        surfaceVariant.copy(alpha = 0.35f),
        surfaceVariant.copy(alpha = 0.65f),
        primaryHighlight,
        surfaceVariant.copy(alpha = 0.65f),
        surfaceVariant.copy(alpha = 0.35f)
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim, y = 0f),
        end = Offset(x = translateAnim + 450f, y = 450f)
    )
}

/**
 * Asymmetric organic shape shimmer placeholder block.
 */
@Composable
fun AsymmetricShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = AsymmetricLeafHero
) {
    val shimmerBrush = rememberExpressiveShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush)
    )
}

/**
 * Google Material 3 Expressive Animated Morphing Organic Blob.
 * Uses smooth bezier curves fluctuating organically to signify live synchronization
 * or background loading without sterile, robotic spinning wheels.
 */
@Composable
fun MorphingOrganicLoader(
    modifier: Modifier = Modifier.size(96.dp),
    tintColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "organic_morph")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "organic_morph_phase"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "organic_morph_scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2.3f) * pulseScale
            val path = Path()

            val points = 8
            for (i in 0 until points) {
                val angle = (i * 2 * PI / points).toFloat()
                // Asymmetric harmonic fluctuation
                val rOffset = sin(angle * 2 + phase) * 10.dp.toPx() +
                        cos(angle * 3 - phase) * 6.dp.toPx()
                val r = baseRadius + rOffset
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    // Smooth quadratic curve towards next anchor
                    val prevAngle = ((i - 1) * 2 * PI / points).toFloat()
                    val midAngle = (angle + prevAngle) / 2f
                    val midR = baseRadius + sin(midAngle * 2 + phase) * 8.dp.toPx()
                    val cx = center.x + midR * cos(midAngle)
                    val cy = center.y + midR * sin(midAngle)
                    path.quadraticTo(cx, cy, x, y)
                }
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        tintColor.copy(alpha = 0.85f),
                        tintColor.copy(alpha = 0.25f)
                    ),
                    center = center,
                    radius = baseRadius * 1.2f
                )
            )
        }
    }
}

/**
 * Material 3 Expressive Bento Skeleton Loader for Dashboard Screen.
 */
@Composable
fun DashboardBentoSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = rememberExpressiveShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bento Hero Skeleton (Asymmetric Leaf shape)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = AsymmetricLeafHero,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush)
            )
        }

        // Sub-tier Deck Cards Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .clip(M3EBentoTileShape)
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .clip(M3EBentoTileShape)
                    .background(brush)
            )
        }

        // Subject Card Skeletons
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(M3ESubjectCardShape)
                    .background(brush)
            )
        }
    }
}

/**
 * Skeleton Loader for Subject Hub Screen Chapter Items.
 */
@Composable
fun ChapterListSkeleton(
    count: Int = 4,
    modifier: Modifier = Modifier
) {
    val brush = rememberExpressiveShimmerBrush()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left circle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(brush)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Google Material 3 Expressive Asymmetrical Loading Screen for PDF Viewer.
 * Replaces generic spinning circles with asymmetric morphing documents,
 * breathing bezier shapes, and organic page skeletons.
 */
@Composable
fun PdfAsymmetricLoadingView(
    title: String = "CBSE Document",
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    val brush = rememberExpressiveShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Asymmetric Morphing Organic Centerpiece
        MorphingOrganicLoader(
            modifier = Modifier.size(88.dp),
            tintColor = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Asymmetric Document Mockup Skeleton
        Card(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(220.dp),
            shape = AsymmetricLeafHero,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(14.dp)
                            .clip(ExpressivePillSmall)
                            .background(brush)
                    )
                }

                // Document text line skeletons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush)
                    )
                }

                // Asymmetric formula / diagram box skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(AsymmetricOptionCard)
                        .background(brush)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (progress != null) "Downloading textbook… ${(progress * 100).toInt()}%" else "Rendering High-Res Textbook Pages...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Cached locally for offline study & instant search",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Google Material 3 Expressive Asymmetrical Loading Screen for DPP & Mock Quizzes.
 */
@Composable
fun QuizAsymmetricLoadingView(
    modifier: Modifier = Modifier
) {
    val brush = rememberExpressiveShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Question Card Skeleton (Asymmetric Leaf shape)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = AsymmetricLeafHero,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(16.dp)
                        .clip(ExpressivePillSmall)
                        .background(brush)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(brush)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
            }
        }

        // 4 Option Skeletons with Asymmetric Pill styling
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(AsymmetricOptionCard)
                    .background(brush)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MorphingOrganicLoader(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Loading CBSE Board Questions...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Asymmetrical Shimmer Skeleton for an individual PDF Page.
 * Displays textbook-like paragraph bars, headers, and formula boxes
 * with an organic dual-harmonic shimmer wave while the page bitmap renders.
 */
@Composable
fun PdfPageShimmerSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = rememberExpressiveShimmerBrush()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .aspectRatio(0.707f), // Standard A4 / CBSE textbook page ratio (1 : 1.414)
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Page Header line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(ExpressivePillSmall)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }

            // Paragraph 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.95f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.82f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.65f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }

            // Asymmetric Diagram / Equation Callout Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(AsymmetricOptionCard)
                    .background(brush)
            )

            // Paragraph 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.92f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }

            // Page Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(brush)
                )
            }
        }
    }
}

