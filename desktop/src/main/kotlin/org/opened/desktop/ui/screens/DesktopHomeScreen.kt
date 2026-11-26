package org.opened.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.database.model.SearchResultItem
import org.opened.app.core.database.model.SearchResultType
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.ui.home.HomeViewModel
import org.opened.app.ui.platform.openEdViewModel
import org.opened.desktop.ui.components.DesktopCard
import org.opened.desktop.ui.components.DesktopEmptyState
import org.opened.desktop.ui.components.DesktopPageHeader
import org.opened.desktop.ui.components.DesktopPillBadge
import org.opened.desktop.ui.theme.LocalDesktopTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DesktopHomeScreen(
    onNavigateToWizard: () -> Unit,
    onNavigateToWorkspace: (String, String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFlashcards: () -> Unit,
    onNavigateToMockTests: (String) -> Unit,
    onOpenLab: () -> Unit,
    viewModel: HomeViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var courseToDelete by remember { mutableStateOf<StudyPathWithModules?>(null) }

    LaunchedEffect(Unit) {
        viewModel.checkApiKey()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        containerColor = tokens.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            DesktopPageHeader(
                title = "Courses & Knowledge Library",
                subtitle = "${uiState.studyPaths.size} courses • Engine: ${uiState.activeProviderName}",
                actions = {
                    OutlinedButton(
                        onClick = onOpenLab,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, tokens.border)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Interactive Lab")
                    }
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, tokens.border)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settings")
                    }
                    Button(
                        onClick = onNavigateToWizard,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Course")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Contextual Status Banners Row
                if (!uiState.hasApiKey || uiState.dueFlashcardCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AnimatedVisibility(
                            visible = !uiState.hasApiKey,
                            modifier = Modifier.weight(1f)
                        ) {
                            DesktopCard(
                                onClick = onNavigateToSettings,
                                borderColor = tokens.danger.copy(alpha = 0.55f),
                                contentPadding = 14.dp
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = tokens.danger.copy(alpha = 0.14f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Key, null, tint = tokens.danger, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${uiState.activeProviderName} API Key Required",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.textPrimary
                                        )
                                        Text(
                                            text = "Click to configure your BYOK key in Settings.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textMuted
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.dueFlashcardCount > 0) {
                            DesktopCard(
                                onClick = onNavigateToFlashcards,
                                modifier = Modifier.weight(1f),
                                borderColor = tokens.accent.copy(alpha = 0.45f),
                                contentPadding = 14.dp
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = tokens.accentSoft,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Style, null, tint = tokens.accent, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${uiState.dueFlashcardCount} Flashcards Due for Review",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.textPrimary
                                        )
                                        Text(
                                            text = "Launch spaced repetition session now.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textMuted
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tokens.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Global Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search across all chapters, revision snapshots, notes & Socratic Q&A...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textMuted) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.card,
                        unfocusedContainerColor = tokens.card,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.border
                    )
                )

                // Content Grid
                when {
                    uiState.searchQuery.isNotBlank() -> {
                        DesktopSearchResultsGrid(
                            results = uiState.searchResults,
                            isSearching = uiState.isSearching,
                            onResultClick = { item -> onNavigateToWorkspace(item.pathId, item.chapterId) }
                        )
                    }
                    uiState.isLoading && uiState.studyPaths.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.studyPaths.isEmpty() -> {
                        DesktopEmptyState(
                            icon = Icons.Default.AutoAwesome,
                            title = "Your Knowledge Studio Is Ready",
                            description = "Create your first AI-personalized curriculum or launch Exam Prep mock tests to start building your local knowledge base.",
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = onNavigateToWizard,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Course")
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(uiState.studyPaths, key = { it.studyPath.id }) { item ->
                                DesktopCourseCard(
                                    item = item,
                                    onOpenCourse = { onNavigateToWorkspace(item.studyPath.id, null) },
                                    onOpenMockTests = { onNavigateToMockTests(item.studyPath.id) },
                                    onDeleteRequest = { courseToDelete = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    courseToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("Delete Course?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This permanently removes \"${pending.studyPath.title}\", its chapters, flashcards, revisions, and mock tests from your local library.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStudyPath(pending.studyPath.id)
                        courseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.danger)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DesktopCourseCard(
    item: StudyPathWithModules,
    onOpenCourse: () -> Unit,
    onOpenMockTests: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val path = item.studyPath
    val totalChapters = item.totalChaptersCount
    val completedChapters = item.completedChaptersCount
    val progress = item.calculatedProgress.coerceIn(0f, 1f)
    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    DesktopCard(
        onClick = onOpenCourse,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DesktopPillBadge(text = path.userLevel.replace('_', ' '))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sdf.format(Date(path.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
                IconButton(
                    onClick = onDeleteRequest,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete course",
                        tint = tokens.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = path.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (path.topic.isNotBlank() && !path.topic.equals(path.title, ignoreCase = true)) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = path.topic,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = tokens.textMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${item.modules.size} modules • $totalChapters chapters",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.accent
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = tokens.accent,
            trackColor = tokens.cardSubtle
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenMockTests,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, tokens.border),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Assignment, null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Exams", style = MaterialTheme.typography.labelMedium)
            }

            FilledTonalButton(
                onClick = onOpenCourse,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Icon(Icons.Default.School, null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (completedChapters > 0) "Continue" else "Open Course",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DesktopSearchResultsGrid(
    results: List<SearchResultItem>,
    isSearching: Boolean,
    onResultClick: (SearchResultItem) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    when {
        isSearching -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        results.isEmpty() -> {
            DesktopEmptyState(
                icon = Icons.Default.Search,
                title = "No Matches Found",
                description = "Try another keyword across your chapter notes, revision history, or Socratic Q&A threads."
            )
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(results, key = { "${it.type}_${it.id}" }) { result ->
                    val typeLabel = when (result.type) {
                        SearchResultType.CHAPTER -> "Chapter"
                        SearchResultType.REVISION_NOTE -> "Revision Note"
                        SearchResultType.QA_THREAD -> "Socratic Q&A"
                    }
                    DesktopCard(
                        onClick = { onResultClick(result) },
                        contentPadding = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DesktopPillBadge(text = typeLabel)
                            Text(
                                text = result.pathTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.chapterTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
