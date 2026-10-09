package com.boardsprep.onboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.boardsprep.onboard.core.theme.*
import com.boardsprep.onboard.data.models.DerivationItem
import com.boardsprep.onboard.data.models.NamedReactionItem
import com.boardsprep.onboard.data.repository.BoardsRepository
import com.boardsprep.onboard.ui.components.ExpressiveEmptyState
import com.boardsprep.onboard.ui.components.ExpressiveSegmentedTabs
import com.boardsprep.onboard.ui.components.ExpressiveTabItem
import com.boardsprep.onboard.ui.components.MathFormulaView
import com.boardsprep.onboard.ui.components.expressiveBounce
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandbookScreen(
    initialSubjectId: String = "physics",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { BoardsRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(if (initialSubjectId == "chemistry") 1 else 0) }
    var searchQuery by remember { mutableStateOf("") }

    val derivations = remember { repository.getPhysicsDerivations() }
    val reactions = remember { repository.getChemistryNamedReactions() }

    val masteredItemsList by repository.getAllMastered().collectAsState(initial = emptyList())
    val masteredIds = remember(masteredItemsList) { masteredItemsList.map { it.itemId }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CBSE Board Handbooks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Class 12 High-Yield Exam Decks • 2027",
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
        ) {
            val handbookTabs = listOf(
                ExpressiveTabItem(
                    title = "Physics Derivations",
                    icon = Icons.Default.Bolt,
                    badge = "${derivations.size}"
                ),
                ExpressiveTabItem(
                    title = "Chemistry Reactions",
                    icon = Icons.Default.Science,
                    badge = "${reactions.size}"
                )
            )

            ExpressiveSegmentedTabs(
                tabs = handbookTabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = {
                    selectedTabIndex = it
                    searchQuery = ""
                },
                accentColor = if (selectedTabIndex == 0) PhysicsAccent else ChemistryAccent
            )

            // Expressive Capsule Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = {
                    Text(
                        if (selectedTabIndex == 0) "Search derivations or chapters..."
                        else "Search reactions, reagents or chapters...",
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                shape = ExpressivePillSmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            if (selectedTabIndex == 0) {
                // Physics Derivations View
                val filteredDerivations = remember(searchQuery, derivations) {
                    if (searchQuery.isBlank()) derivations
                    else derivations.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.chapterName.contains(searchQuery, ignoreCase = true)
                    }
                }

                val masteredPhysicsCount = derivations.count { it.id in masteredIds }
                val progress = if (derivations.isNotEmpty()) masteredPhysicsCount.toFloat() / derivations.size else 0f

                MasterySummaryCard(
                    title = "Physics Derivations Mastered",
                    count = masteredPhysicsCount,
                    total = derivations.size,
                    progress = progress,
                    accentColor = PrimaryBlue
                )

                if (filteredDerivations.isEmpty()) {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Search,
                        title = "No Derivations Found",
                        description = "No physics derivations match '$searchQuery'. Try searching by law name (e.g. Gauss, Coulomb) or chapter name.",
                        tintColor = PhysicsAccent,
                        actionText = "Clear Search",
                        onActionClick = { searchQuery = "" }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredDerivations, key = { it.id }) { item ->
                            val isMastered = item.id in masteredIds
                            DerivationCard(
                                item = item,
                                isMastered = isMastered,
                                onToggleMastered = { checked ->
                                    scope.launch {
                                        repository.setMastered(item.id, "derivation", checked)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Chemistry Named Reactions View
                val filteredReactions = remember(searchQuery, reactions) {
                    if (searchQuery.isBlank()) reactions
                    else reactions.filter {
                        it.reactionName.contains(searchQuery, ignoreCase = true) ||
                                it.chapterName.contains(searchQuery, ignoreCase = true) ||
                                it.reagents.contains(searchQuery, ignoreCase = true)
                    }
                }

                val masteredChemCount = reactions.count { it.id in masteredIds }
                val progress = if (reactions.isNotEmpty()) masteredChemCount.toFloat() / reactions.size else 0f

                MasterySummaryCard(
                    title = "Organic Reactions Mastered",
                    count = masteredChemCount,
                    total = reactions.size,
                    progress = progress,
                    accentColor = SuccessGreen
                )

                if (filteredReactions.isEmpty()) {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Search,
                        title = "No Reactions Found",
                        description = "No organic named reactions match '$searchQuery'. Try searching by reaction name (e.g. Aldol, Sandmeyer) or reagent.",
                        tintColor = ChemistryAccent,
                        actionText = "Clear Search",
                        onActionClick = { searchQuery = "" }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredReactions, key = { it.id }) { item ->
                            val isMastered = item.id in masteredIds
                            ReactionCard(
                                item = item,
                                isMastered = isMastered,
                                onToggleMastered = { checked ->
                                    scope.launch {
                                        repository.setMastered(item.id, "reaction", checked)
                                    }
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
private fun MasterySummaryCard(
    title: String,
    count: Int,
    total: Int,
    progress: Float,
    accentColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = ExpressiveCardMedium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = ExpressivePillSmall,
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$count / $total (${(progress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(ExpressivePillSmall),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun DerivationCard(
    item: DerivationItem,
    isMastered: Boolean,
    onToggleMastered: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveCardLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isMastered) PrimaryBlue.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = ExpressivePillSmall
                    ) {
                        Text(
                            text = item.chapterName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = { onToggleMastered(!isMastered) }) {
                    Icon(
                        imageVector = if (isMastered) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = "Mastered",
                        tint = if (isMastered) PrimaryBlue else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Formula Box
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = ExpressiveCardMedium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "FORMULA / RESULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MathFormulaView(
                        latex = item.formulaStatement,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        fontSizeSp = 15,
                        height = 80.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step-by-Step Proof Outline
            Text(
                text = "Key Derivation Steps (CBSE Step-Marking):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            item.keySteps.forEachIndexed { idx, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${idx + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionCard(
    item: NamedReactionItem,
    isMastered: Boolean,
    onToggleMastered: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveCardLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isMastered) SuccessGreen.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.15f),
                        shape = ExpressivePillSmall
                    ) {
                        Text(
                            text = item.chapterName,
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.reactionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = { onToggleMastered(!isMastered) }) {
                    Icon(
                        imageVector = if (isMastered) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = "Mastered",
                        tint = if (isMastered) SuccessGreen else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chemical Equation Box
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = ExpressiveCardMedium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "CHEMICAL EQUATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MathFormulaView(
                        latex = item.equation,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        fontSizeSp = 14,
                        height = 64.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reagents Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Reagents: ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Text(
                    text = item.reagents,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Board Application Note
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Board Key: ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmber
                )
                Text(
                    text = item.keyApplication,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
