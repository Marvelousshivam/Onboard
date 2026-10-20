package com.boardsprep.onboard.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.ai.*
import com.boardsprep.onboard.core.glass.components.LiquidGlassBackButton
import com.boardsprep.onboard.core.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val prefsManager = remember { AiPreferencesManager.getInstance(context) }
    val aiEngine = remember { AiProviderEngine() }

    var selectedProvider by remember { mutableStateOf(prefsManager.getSelectedProvider()) }
    var selectedModel by remember { mutableStateOf(prefsManager.getSelectedModel(selectedProvider)) }
    var currentApiKey by remember { mutableStateOf(prefsManager.getApiKey(selectedProvider)) }
    var customBaseUrl by remember { mutableStateOf(prefsManager.getBaseUrl(AiProvider.CUSTOM)) }
    var isWebSearchEnabled by remember { mutableStateOf(prefsManager.isWebSearchEnabled()) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultIsSuccess by remember { mutableStateOf(false) }

    // Dialog States
    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Engine & Study Companion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.5.sp
                        )
                        Text(
                            text = "Multi-Provider • Socratic Models • BYOK",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    LiquidGlassBackButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dynamic Helper Card for Active Provider
            val (bannerDesc, bannerButtonLabel, bannerUrl) = remember(selectedProvider) {
                when (selectedProvider) {
                    AiProvider.GROQ -> Triple(
                        "Groq LPU delivers lightning-fast inference (300+ tps) with free open-source models like GPT-OSS 20B and Qwen.",
                        "Get Free Groq API Key",
                        "https://console.groq.com/keys"
                    )
                    AiProvider.GOOGLE_STUDIO -> Triple(
                        "Google AI Studio offers a generous free tier (15 requests/min) for Gemini 2.5 Flash and Pro with any Google account.",
                        "Get Free Google Gemini Key",
                        "https://aistudio.google.com/app/apikey"
                    )
                    AiProvider.OPEN_ROUTER -> Triple(
                        "OpenRouter provides access to 100+ models with free community routing and state-of-the-art open weights.",
                        "Get Free OpenRouter Key",
                        "https://openrouter.ai/keys"
                    )
                    AiProvider.PERPLEXITY -> Triple(
                        "Perplexity Sonar combines search engine grounding with generative AI for real-time citations.",
                        "Get Perplexity API Key",
                        "https://www.perplexity.ai/settings/api"
                    )
                    AiProvider.OPENAI -> Triple(
                        "OpenAI models provide versatile intelligence and broad multi-subject accuracy.",
                        "Get OpenAI API Key",
                        "https://platform.openai.com/api-keys"
                    )
                    AiProvider.CUSTOM -> Triple(
                        "Connect any OpenAI-compatible local or custom LLM endpoint (Ollama, LM Studio, vLLM).",
                        "View Custom API Setup Guide",
                        "https://ollama.ai"
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = M3EBentoTileShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f)),
                border = null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CardGiftcard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI Key Configuration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bannerDesc,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val url = bannerUrl.ifBlank { selectedProvider.helpUrl }
                            if (url.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(bannerButtonLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // Group 1: Provider & Model Configuration
            Text(
                text = "Provider & Model Configuration",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = M3EBentoTileShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = null
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Provider Row
                    SettingsTileItem(
                        icon = Icons.Default.Hub,
                        title = "Active AI Provider",
                        subtitle = selectedProvider.displayName,
                        onClick = { showProviderDialog = true }
                    )

                    // Model Row
                    SettingsTileItem(
                        icon = Icons.Default.Psychology,
                        title = "Active Model",
                        subtitle = selectedModel,
                        onClick = { showModelDialog = true }
                    )

                    // API Key Row with Monospaced Key
                    SettingsTileItem(
                        icon = Icons.Default.VpnKey,
                        title = "API Key",
                        subtitle = if (currentApiKey.isNotBlank()) "••••••••••••${currentApiKey.takeLast(4)}" else "Not Configured (Tap to enter)",
                        isMonospaceSubtitle = currentApiKey.isNotBlank(),
                        trailing = {
                            if (currentApiKey.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = { showApiKeyDialog = true }
                    )

                    if (selectedProvider == AiProvider.CUSTOM) {
                        SettingsTileItem(
                            icon = Icons.Default.Link,
                            title = "Custom Base URL",
                            subtitle = customBaseUrl.ifBlank { "e.g. http://localhost:11434/v1/chat/completions" },
                            onClick = { showCustomUrlDialog = true }
                        )
                    }
                }
            }

            // Group 2: Web Search & Grounding
            Text(
                text = "Live Internet Grounding",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = M3EBentoTileShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enable Internet Search Grounding", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grounds answers with live web crawling for latest CBSE 2026-27 circulars, syllabus deletions, and SQP marking changes.",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isWebSearchEnabled,
                        onCheckedChange = {
                            isWebSearchEnabled = it
                            prefsManager.setWebSearchEnabled(it)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
            }

            // Group 3: Connection Diagnostics
            Text(
                text = "Diagnostics & Benchmark",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = M3EBentoTileShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val latencyBadge = remember(testResultText, testResultIsSuccess) {
                        if (testResultIsSuccess && testResultText != null) {
                            Regex("""\((\d+\s*ms)\)""").find(testResultText!!)?.groupValues?.get(1)
                        } else null
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Test API Connectivity", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                if (latencyBadge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = SuccessGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = latencyBadge,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text("Verifies API key validity and measures latency.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                if (currentApiKey.isBlank() && selectedProvider != AiProvider.CUSTOM) {
                                    Toast.makeText(context, "Please enter an API Key first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isTestingConnection = true
                                testResultText = null
                                coroutineScope.launch {
                                    val result = aiEngine.testConnection(
                                        provider = selectedProvider,
                                        baseUrl = if (selectedProvider == AiProvider.CUSTOM) customBaseUrl else selectedProvider.defaultBaseUrl,
                                        apiKey = currentApiKey,
                                        model = selectedModel
                                    )
                                    isTestingConnection = false
                                    testResultIsSuccess = result.isSuccess
                                    testResultText = result.getOrElse { it.message ?: "Connection failed" }
                                }
                            },
                            enabled = !isTestingConnection,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test")
                            }
                        }
                    }

                    if (testResultText != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (testResultIsSuccess) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (testResultIsSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (testResultIsSuccess) SuccessGreen else ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = testResultText.orEmpty(),
                                    fontSize = 11.5.sp,
                                    color = if (testResultIsSuccess) SuccessGreen else ErrorRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // ─── DIALOGS ────────────────────────────────────────────────────────────────

    // 1. Provider Dialog
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("Select AI Provider", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(AiProvider.entries) { provider ->
                        val isSelected = provider == selectedProvider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProvider = provider
                                    prefsManager.setSelectedProvider(provider)
                                    selectedModel = prefsManager.getSelectedModel(provider)
                                    currentApiKey = prefsManager.getApiKey(provider)
                                    showProviderDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedProvider = provider
                                    prefsManager.setSelectedProvider(provider)
                                    selectedModel = prefsManager.getSelectedModel(provider)
                                    currentApiKey = prefsManager.getApiKey(provider)
                                    showProviderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(provider.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                if (provider.isSearchSupported) {
                                    Text("Supports Google/Web Grounding", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // 2. Model Dialog
    if (showModelDialog) {
        val models = AiModelCatalog.modelsByProvider[selectedProvider] ?: emptyList()
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Select ${selectedProvider.displayName} Model", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(models) { modelInfo ->
                        val isSelected = modelInfo.id == selectedModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedModel = modelInfo.id
                                    prefsManager.setSelectedModel(selectedProvider, modelInfo.id)
                                    showModelDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedModel = modelInfo.id
                                    prefsManager.setSelectedModel(selectedProvider, modelInfo.id)
                                    showModelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(modelInfo.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.5.sp)
                                    if (modelInfo.tag.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val tagColor = when {
                                            modelInfo.tag.contains("FAST") || modelInfo.tag.contains("LOW") -> SuccessGreen
                                            modelInfo.tag.contains("REASONING") -> AccentAmber
                                            modelInfo.tag.contains("RECOMMENDED") -> PrimaryBlue
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = tagColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = modelInfo.tag,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tagColor,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (modelInfo.description.isNotBlank()) {
                                    Text(modelInfo.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(
                            onClick = {
                                showModelDialog = false
                                showCustomModelDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter Custom Model ID...")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 3. API Key Dialog
    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf(currentApiKey) }
        var isPasswordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("${selectedProvider.displayName} API Key", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        text = "Enter your personal API key for ${selectedProvider.displayName}. The key is stored securely in your private device preferences.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste key here...") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    currentApiKey = keyInput.trim()
                    prefsManager.setApiKey(selectedProvider, keyInput.trim())
                    showApiKeyDialog = false
                    Toast.makeText(context, "API Key saved for ${selectedProvider.displayName}", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 4. Custom Model Dialog
    if (showCustomModelDialog) {
        var customModelInput by remember { mutableStateOf(selectedModel) }
        AlertDialog(
            onDismissRequest = { showCustomModelDialog = false },
            title = { Text("Enter Custom Model ID", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    placeholder = { Text("e.g. meta-llama/llama-3.3-70b-instruct") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (customModelInput.isNotBlank()) {
                        selectedModel = customModelInput.trim()
                        prefsManager.setSelectedModel(selectedProvider, customModelInput.trim())
                    }
                    showCustomModelDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomModelDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 5. Custom URL Dialog
    if (showCustomUrlDialog) {
        var urlInput by remember { mutableStateOf(customBaseUrl) }
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Custom Base URL", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://your-server.com/v1/chat/completions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    customBaseUrl = urlInput.trim()
                    prefsManager.setCustomBaseUrl(urlInput.trim())
                    showCustomUrlDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsTileItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isMonospaceSubtitle: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                fontFamily = if (isMonospaceSubtitle) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
