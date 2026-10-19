package com.boardsprep.onboard.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.ui.components.*
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val badge: String
)

private val PageAccents = listOf(
    Color(0xFF6750A4), // Royal Violet - Curriculum Engine / Document Assembly
    Color(0xFF00B0FF), // Vivid Cyan - Study Architecture / 4 Pillars
    Color(0xFFFFB300), // Amber Gold - Smart PDF Viewer
    Color(0xFFFF4081), // Rose Pink - Step Rubrics Studio
    Color(0xFF7C4DFF), // Deep Violet - Error Vault Anti-Forgetting
    Color(0xFFFF5722)  // Sunset Orange - Stream Pledge
)

/**
 * Premium 6-Slide Onboarding Tour for OnBoard CBSE Prep.
 * Built following ClearPDF animation principles:
 * 1. Stationary liquid-glass bottom chrome for 120 FPS swipe performance.
 * 2. `isActive` loop gating on every interactive demo.
 * 3. Step-by-step psychological momentum leading to student's board goal pledge.
 */
@Composable
fun OnboardingTourScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 6 })

    var userStream by remember { mutableStateOf("PCM") }
    var targetGoal by remember { mutableIntStateOf(95) }

    val pages = remember {
        listOf(
            OnboardingPageData(
                title = "Class 12 Boards,\nDeconstructed.",
                subtitle = "Official CBSE syllabus, chapter weightage, and high-yield topics mapped to 2026-27 blueprints.",
                badge = "CURRICULUM ENGINE"
            ),
            OnboardingPageData(
                title = "From Core Concept to\nBoard Perfection.",
                subtitle = "The 4-Pillar System: One-Shot Lectures, NCERT Line-by-Line, Exemplar DPPs & Question Banks.",
                badge = "STUDY ARCHITECTURE"
            ),
            OnboardingPageData(
                title = "Read Faster.\nRetain Deeper.",
                subtitle = "Smart offline PDF reader with instant CBSE marking scheme splits and active recall highlighters.",
                badge = "SMART PDF VIEWER"
            ),
            OnboardingPageData(
                title = "Score Full Marks in\nSubjective Answers.",
                subtitle = "357 official CBSE 2m, 3m & 5m questions with step-by-step mark breakdown (+0.5m, +1.0m rubrics).",
                badge = "QUESTION BANK STUDIO"
            ),
            OnboardingPageData(
                title = "Zero Mistakes Forgotten.\nAutomatic Mastery.",
                subtitle = "Every error you make is logged into the Error Vault and scheduled on the Ebbinghaus forgetting curve.",
                badge = "ERROR VAULT"
            ),
            OnboardingPageData(
                title = "Make Your 95%+\nBoard Pledge.",
                subtitle = "Lock in your stream and daily target. Your personalized road to CBSE excellence begins now.",
                badge = "PERSONALIZED GOAL"
            )
        )
    }

    fun completeOnboarding() {
        val prefs = context.getSharedPreferences("onboard_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("onboarding_completed", true)
            .putString("user_stream", userStream)
            .putInt("target_score_goal", targetGoal)
            .apply()
        onFinishOnboarding()
    }

    val currentAccent = PageAccents[pagerState.currentPage.coerceIn(0, PageAccents.lastIndex)]
    val animatedAccentColor by animateColorAsState(
        targetValue = currentAccent,
        animationSpec = tween(350),
        label = "animatedAccent"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Navigation Bar (Logo / Badge & Skip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = animatedAccentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = animatedAccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OnBoard XII",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Skip button (visible on pages 0..4)
                if (pagerState.currentPage < 5) {
                    TextButton(
                        onClick = { completeOnboarding() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Pager Content: Fluid Swipe Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                val isActive = (pagerState.currentPage == pageIndex)
                val pageAccent = PageAccents[pageIndex]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = pageAccent.copy(alpha = 0.12f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = page.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.1.sp,
                                color = pageAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = page.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 30.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = page.subtitle,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    // Interactive Physics & Canvas Demo Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (pageIndex) {
                            0 -> DemoDocumentAssembly(isActive = isActive)
                            1 -> DemoFourPillarsEngine(isActive = isActive)
                            2 -> DemoSmartReaderHighlight(isActive = isActive)
                            3 -> DemoStepRubricReveal(isActive = isActive)
                            4 -> DemoErrorVaultSpacedReview(isActive = isActive)
                            5 -> DemoStreamPledge(
                                isActive = isActive,
                                onStreamSelected = { stream, target ->
                                    userStream = stream
                                    targetGoal = target
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Stationary Glass Bottom Chrome (Never re-measured on swipe!)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Expandable Page Indicator Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) { dotIndex ->
                            val isSelected = (pagerState.currentPage == dotIndex)
                            val width by animateDpAsState(
                                targetValue = if (isSelected) 26.dp else 8.dp,
                                animationSpec = tween(300),
                                label = "dot_width_$dotIndex"
                            )
                            val color by animateColorAsState(
                                targetValue = if (isSelected) PageAccents[dotIndex]
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                label = "dot_color_$dotIndex"
                            )

                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(width)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(color)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(dotIndex)
                                        }
                                    }
                            )
                        }
                    }

                    // Primary Action Button with Elevated Brand Styling
                    val isLastPage = (pagerState.currentPage == 5)
                    Button(
                        onClick = {
                            if (isLastPage) {
                                completeOnboarding()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isLastPage) "Launch Board Prep" else "Next",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isLastPage) Icons.Default.RocketLaunch else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
