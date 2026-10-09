package com.boardsprep.onboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.R
import com.boardsprep.onboard.core.sync.FirebaseSyncManager
import com.boardsprep.onboard.core.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// Web Client ID from Firebase project onboards2027
private const val GOOGLE_WEB_CLIENT_ID = "47299135156-kuu5gp6kbl42q9br8cuasthu4akq8v7g.apps.googleusercontent.com"
private const val DEBUG_KEYSTORE_SHA1 = "31:0E:CB:4B:6B:82:F3:A3:EA:E7:FA:A0:4B:07:86:BB:7A:9F:8F:9F"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSyncScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val syncManager = remember { FirebaseSyncManager.getInstance(context) }

    val accountInfo by syncManager.accountState.collectAsState()
    val streakInfo by syncManager.streakState.collectAsState()

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    var isManualSyncing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showSha1Helper by remember { mutableStateOf(false) }

    // Email auth state
    var showEmailForm by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Countdown to CBSE 2027
    val daysRemaining = remember {
        val examCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2027, Calendar.FEBRUARY, 15, 10, 30, 0)
        }
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val diffMillis = examCal.timeInMillis - nowCal.timeInMillis
        TimeUnit.MILLISECONDS.toDays(diffMillis).coerceAtLeast(0)
    }

    // Google Sign-In Client & ActivityResult launcher
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                isGoogleLoading = true
                errorMessage = null
                syncManager.signInWithGoogle(idToken) { success, err ->
                    isGoogleLoading = false
                    if (success) {
                        successMessage = "Signed in as ${account.displayName ?: account.email}!"
                        Toast.makeText(context, "Welcome back! Cloud sync active.", Toast.LENGTH_SHORT).show()
                    } else {
                        errorMessage = err ?: "Google sign-in could not be completed."
                    }
                }
            } else {
                isGoogleLoading = false
                errorMessage = "Google returned an empty token. Please check internet connection."
            }
        } catch (e: ApiException) {
            isGoogleLoading = false
            if (e.statusCode == 12501) {
                // User dismissed/cancelled sign-in dialog
                return@rememberLauncherForActivityResult
            }
            if (e.statusCode == 10 || e.statusCode == 12500) {
                showSha1Helper = true
                errorMessage = "Google Play Services configuration notice (Code ${e.statusCode}). You can use Email/Password sign-in instantly, or register the SHA-1 below in Firebase."
            } else {
                errorMessage = "Sign-in error (${e.statusCode}): ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    // Rotating animation for sync icon when syncing
    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Cloud Sync & Onboarding",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Live Cloud Status Indicator Pill
                    Surface(
                        shape = ExpressivePillSmall,
                        color = if (accountInfo.isAnonymous) AccentAmberLight else SuccessGreenLight,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (accountInfo.isAnonymous) AccentAmber else SuccessGreen
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (accountInfo.isAnonymous) AccentAmberOnBg else SuccessGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (accountInfo.isAnonymous) "Guest Mode" else "Cloud Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (accountInfo.isAnonymous) AccentAmberOnBg else Color(0xFF0F5132)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            // ─── 1. Cross-Device Connectivity Hero Banner ─────────────────────
            item {
                Surface(
                    shape = M3EBentoHeroShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Visual Bi-directional Hardware Sync Node
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Phone Node
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = "Android App",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Dynamic Sync Bridge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .then(if (accountInfo.isSyncing || isManualSyncing) Modifier.rotate(rotationAngle) else Modifier)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(2.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    PhysicsAccent
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = PhysicsAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Laptop/Web Node
                            Surface(
                                shape = CircleShape,
                                color = PhysicsAccent,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Laptop,
                                        contentDescription = "Web Companion",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Unified Study Sync",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Study on phone on the move, continue on your laptop at home. Your video timestamps, solved DPPs, and CBSE streak stay synchronized 24/7.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = ExpressivePillSmall,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Web Companion: http://localhost:3000",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ─── 2. Success / Error Feedback Banners ─────────────────────────
            if (errorMessage != null) {
                item {
                    Surface(
                        shape = M3EBentoTileShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            if (successMessage != null) {
                item {
                    Surface(
                        shape = M3EBentoTileShape,
                        color = SuccessGreenLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = successMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F5132),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { successMessage = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFF0F5132)
                                )
                            }
                        }
                    }
                }
            }

            // ─── 3. Identity & Authentication Control Station ─────────────────
            item {
                if (!accountInfo.isAnonymous && !accountInfo.email.isNullOrBlank()) {
                    // Profile Overview Card (User is Authenticated with Google / Email)
                    Surface(
                        shape = M3EBentoTileShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Avatar circle
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (accountInfo.displayName?.firstOrNull() ?: accountInfo.email?.firstOrNull() ?: 'U').uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = accountInfo.displayName ?: "CBSE 2027 Aspirant",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = accountInfo.email ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Verified Cloud Account",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Sync Status Telemetry
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Cloud Sync Telemetry",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val formattedTime = remember(accountInfo.lastSyncedTime) {
                                        SimpleDateFormat("hh:mm a, dd MMM", Locale.US).format(Date(accountInfo.lastSyncedTime))
                                    }
                                    Text(
                                        text = "Last synced: $formattedTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Quick Sync Button
                                Button(
                                    onClick = {
                                        isManualSyncing = true
                                        syncManager.triggerManualSync {
                                            isManualSyncing = false
                                            Toast.makeText(context, "Cloud sync complete!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isManualSyncing && !accountInfo.isSyncing,
                                    shape = ExpressivePillSmall
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .then(if (isManualSyncing || accountInfo.isSyncing) Modifier.rotate(rotationAngle) else Modifier)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isManualSyncing || accountInfo.isSyncing) "Syncing..." else "Sync Now",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Sign Out Option
                            OutlinedButton(
                                onClick = {
                                    syncManager.signOutUser {
                                        Toast.makeText(context, "Signed out. Switched to local guest mode.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = ExpressivePillSmall,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out to Guest Mode", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Guest / Unauthenticated State - High-Converting, Low-Friction Onboarding
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // ─── GOOGLE SIGN-IN PRIMARY BUTTON ───────────────────
                        Surface(
                            onClick = {
                                if (!isGoogleLoading) {
                                    try {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        }
                                    } catch (e: Exception) {
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    }
                                }
                            },
                            shape = ExpressivePillSmall,
                            color = Color.White,
                            shadowElevation = 3.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isGoogleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = Color(0xFF4285F4)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Connecting Google Account...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3C4043)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_google),
                                        contentDescription = "Google Icon",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Continue with Google",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1F1F1F)
                                    )
                                }
                            }
                        }

                        // Divider with text "OR"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Text(
                                text = "OR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp),
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }

                        // Email & Password Toggle Button
                        OutlinedButton(
                            onClick = { showEmailForm = !showEmailForm },
                            shape = ExpressivePillSmall,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (showEmailForm) Icons.Default.ExpandLess else Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showEmailForm) "Hide Email Form" else "Sign in with Email & Password",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Expandable Email Form
                        AnimatedVisibility(visible = showEmailForm) {
                            Surface(
                                shape = M3EBentoTileShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    // Mode Switcher Tabs
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        FilterChip(
                                            selected = !isRegisterMode,
                                            onClick = { isRegisterMode = false },
                                            label = { Text("Sign In") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilterChip(
                                            selected = isRegisterMode,
                                            onClick = { isRegisterMode = true },
                                            label = { Text("Create Account") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = emailInput,
                                        onValueChange = { emailInput = it },
                                        label = { Text("Email Address") },
                                        placeholder = { Text("student@gmail.com") },
                                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                        singleLine = true,
                                        shape = ExpressivePillSmall,
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it },
                                        label = { Text("Password") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        singleLine = true,
                                        shape = ExpressivePillSmall,
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            if (emailInput.isBlank() || passwordInput.length < 6) {
                                                errorMessage = "Please enter a valid email and 6+ character password."
                                                return@Button
                                            }
                                            isEmailLoading = true
                                            errorMessage = null
                                            if (isRegisterMode) {
                                                syncManager.registerWithEmail(emailInput, passwordInput) { success, err ->
                                                    isEmailLoading = false
                                                    if (success) {
                                                        successMessage = "Account created and synced with cloud!"
                                                    } else {
                                                        errorMessage = err ?: "Registration failed"
                                                    }
                                                }
                                            } else {
                                                syncManager.signInWithEmail(emailInput, passwordInput) { success, err ->
                                                    isEmailLoading = false
                                                    if (success) {
                                                        successMessage = "Signed in successfully!"
                                                    } else {
                                                        errorMessage = err ?: "Sign-in failed"
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isEmailLoading,
                                        shape = ExpressivePillSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isEmailLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text(
                                                text = if (isRegisterMode) "Register & Sync Cloud" else "Sign In & Sync",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Autonomy Preservation: Stay in Guest Mode
                        TextButton(
                            onClick = onBackClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Continue studying in Guest Mode (Offline)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ─── 4. SHA-1 Fingerprint Helper (Developer & Play Services Aid) ──
            item {
                AnimatedVisibility(visible = showSha1Helper) {
                    Surface(
                        shape = M3EBentoTileShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Firebase Google Sign-In Setup",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "To enable 1-tap Google Sign-In on physical Android devices, your debug keystore SHA-1 must be added to Firebase Console under 'onboards2027' Android App:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = DEBUG_KEYSTORE_SHA1,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(DEBUG_KEYSTORE_SHA1))
                                            Toast.makeText(context, "SHA-1 copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy SHA1",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💡 Tip: Email/Password login works immediately without any SHA-1 registration.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ─── 5. Psychological Value Proposition Bento Grid ───────────────
            item {
                Text(
                    text = "Why Connect BoardsPrep Cloud?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Bento Tile 1: Universal Video Resumption
            item {
                SyncFeatureBentoCard(
                    icon = Icons.Default.PlayCircleOutline,
                    accentColor = PhysicsAccent,
                    title = "Continuous Lecture Timeline",
                    description = "Watch one-shots on your laptop browser and pause at 32:40. Open your phone and tap play — your lecture continues at the exact same millisecond.",
                    badge = "Smart Resume"
                )
            }

            // Bento Tile 2: CBSE 2027 Streak Protection
            item {
                SyncFeatureBentoCard(
                    icon = Icons.Default.LocalFireDepartment,
                    accentColor = AccentAmber,
                    title = "Streak & Habit Lock",
                    description = "Studying on laptop or phone both advance your CBSE 2027 streak. Loss-aversion protection prevents accidental reset when switching workstations.",
                    badge = "${streakInfo.currentStreak}d Current"
                )
            }

            // Bento Tile 3: 40 Chapter DPP & Mastery Synced
            item {
                SyncFeatureBentoCard(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    accentColor = ChemistryAccent,
                    title = "NCERT & PYQ Mastery Matrix",
                    description = "Check off theory, NCERT line-by-line questions, and exemplar problems. Every checked item is instantly backed up to Google Cloud Firestore.",
                    badge = "40 Quizzes"
                )
            }

            // Bento Tile 4: Offline-First Reliability
            item {
                SyncFeatureBentoCard(
                    icon = Icons.Default.CloudDone,
                    accentColor = BiologyAccent,
                    title = "Offline-First Resilience",
                    description = "No internet in school or coaching? Continue watching downloaded lectures and solving quizzes. As soon as you connect, all data merges automatically.",
                    badge = "Zero Data Loss"
                )
            }

            // ─── 6. CBSE 2027 Target Runway ──────────────────────────────────
            item {
                Surface(
                    shape = M3EBentoTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CBSE Class 12 Board Exam 2027",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$daysRemaining Days remaining until Feb 15, 2027. Maintain daily consistency to achieve 95%+ aggregate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncFeatureBentoCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    badge: String
) {
    Surface(
        shape = M3EBentoTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = M3EIconContainerShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = ExpressivePillSmall,
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
