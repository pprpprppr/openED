package org.opened.app.ui.workspace

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.ui.components.InlineRefineBottomSheet
import org.opened.app.ui.components.OutgoingBacklinksSection
import org.opened.app.ui.components.RichMarkdownRenderer
import org.opened.app.ui.components.SocraticQaSection
import org.opened.app.ui.mastery.MasteryBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseWorkspaceScreen(
    pathId: String,
    initialChapterId: String?,
    onNavigateBack: () -> Unit,
    viewModel: CourseWorkspaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pathId, initialChapterId) {
        viewModel.initWorkspace(pathId, initialChapterId)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    LaunchedEffect(uiState.flashcardSnackMessage) {
        uiState.flashcardSnackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFlashcardMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CourseTreeDrawer(
                pathWithModules = uiState.pathWithModules,
                activeChapterId = uiState.activeChapterId,
                onChapterSelect = { chapterId ->
                    viewModel.selectChapter(chapterId)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        val chapterDetails = uiState.chapterDetails
        val currentChapter = chapterDetails?.chapter

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Syllabus Drawer")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = currentChapter?.title ?: "Select Chapter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            uiState.pathWithModules?.studyPath?.title?.let { courseTitle ->
                                Text(
                                    text = courseTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentChapter != null && currentChapter.status == ChapterStatus.COMPLETED && !uiState.isStreaming) {
                            IconButton(
                                onClick = { viewModel.generateFlashcards() },
                                enabled = !uiState.isGeneratingFlashcards
                            ) {
                                if (uiState.isGeneratingFlashcards) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Style, contentDescription = "Generate Flashcards")
                                }
                            }
                            IconButton(onClick = { viewModel.toggleTtsPlayer() }) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Commute Audio Reader",
                                    tint = if (uiState.showTtsPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { viewModel.openRevisionSheet() }) {
                                Icon(imageVector = Icons.Default.History, contentDescription = "Revision History")
                            }
                            IconButton(onClick = { viewModel.openRefineSheet() }) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Refine Passage")
                            }
                            IconButton(onClick = { viewModel.generateChapterContent() }) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Regenerate Content")
                            }
                        }
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to Library")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                // Floating Contextual Action Bar & TTS Mini Player
                if (currentChapter != null && currentChapter.status == ChapterStatus.COMPLETED && !uiState.isStreaming) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AnimatedVisibility(visible = uiState.showTtsPlayer) {
                            TtsMiniPlayer(
                                ttsState = ttsState,
                                onPlay = { viewModel.ttsPlay() },
                                onPause = { viewModel.ttsPause() },
                                onResume = { viewModel.ttsResume() },
                                onStop = { viewModel.ttsStop() },
                                onSetSpeed = { viewModel.ttsSetSpeed(it) },
                                onDismiss = { viewModel.toggleTtsPlayer() }
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            tonalElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = { viewModel.openRefineSheet() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refine", style = MaterialTheme.typography.labelMedium)
                                    }

                                    FilledTonalButton(
                                        onClick = { viewModel.openMasterySheet() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mastery", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.openRevisionSheet() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.History,
                                                contentDescription = "Revision History",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Revisions (${chapterDetails.revisions.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Q&As: ${chapterDetails.qas.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                if (currentChapter == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
                    ) {
                        // Chapter Title and Summary Header
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Text(
                                    text = currentChapter.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (currentChapter.summary.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentChapter.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

                        // Case 1: Unwritten / Pending Chapter
                        if (currentChapter.status == ChapterStatus.PENDING && !uiState.isStreaming) {
                            item {
                                EmptyChapterCard(
                                    chapterTitle = currentChapter.title,
                                    onGenerate = { viewModel.generateChapterContent() }
                                )
                            }
                        }

                        // Case 2: Actively Streaming Content
                        if (uiState.isStreaming) {
                            item {
                                StreamingHeaderBanner()
                            }
                            item {
                                val currentMarkdown = uiState.streamingContent.orEmpty()
                                if (currentMarkdown.isNotBlank()) {
                                    RichMarkdownRenderer(
                                        markdown = currentMarkdown,
                                        onChapterClick = { viewModel.navigateToChapterByTitle(it) }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        // Case 3: Completed Chapter Reader
                        if (currentChapter.status == ChapterStatus.COMPLETED && !uiState.isStreaming) {
                            item {
                                RichMarkdownRenderer(
                                    markdown = currentChapter.markdownContent,
                                    onChapterClick = { targetTitle ->
                                        viewModel.navigateToChapterByTitle(targetTitle)
                                    }
                                )
                            }

                            // Knowledge Graph Links & Backlinks Section
                            item {
                                Spacer(modifier = Modifier.height(28.dp))
                                OutgoingBacklinksSection(
                                    outgoingChapters = chapterDetails.outgoingChapters,
                                    backlinkChapters = chapterDetails.backlinkChapters,
                                    onChapterClick = { viewModel.navigateToChapterByTitle(it) },
                                    onGenerateTangent = { concept ->
                                        viewModel.generateTangent(concept)
                                    }
                                )
                            }

                            // Socratic Q&A Section
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                SocraticQaSection(
                                    qas = chapterDetails.qas,
                                    isAskingQuestion = uiState.isAskingQuestion,
                                    onAskQuestion = { viewModel.askSocraticQuestion(it) },
                                    onAppendToChapter = { qa -> viewModel.appendQaToChapter(qa) },
                                    onChapterClick = { viewModel.navigateToChapterByTitle(it) }
                                )
                            }

                            // Mastery Challenge Card (Adaptive Quiz & Feynman Technique)
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(40.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.School,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    "Mastery Challenge",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Test your retention with adaptive quiz & Feynman challenge",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.openMasterySheet() },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Test Mastery", style = MaterialTheme.typography.labelMedium)
                                            }

                                            FilledTonalButton(
                                                onClick = { viewModel.generateFlashcards() },
                                                enabled = !uiState.isGeneratingFlashcards,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (uiState.isGeneratingFlashcards) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Flashcards", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Inline Refine Bottom Sheet Dialog
        if (uiState.showRefineSheet) {
            InlineRefineBottomSheet(
                selectedSnippet = uiState.refineSnippet,
                isRefining = uiState.isRefining,
                onDismiss = { viewModel.closeRefineSheet() },
                onApplyRefinement = { action, customPrompt ->
                    viewModel.applyRefinement(action, customPrompt)
                }
            )
        }

        // Mastery Challenge Bottom Sheet (Adaptive Quiz & Feynman Mode)
        if (uiState.showMasterySheet && currentChapter != null) {
            MasteryBottomSheet(
                chapterId = currentChapter.id,
                onDismiss = { viewModel.closeMasterySheet() }
            )
        }

        // Revision History Bottom Sheet Dialog
        if (uiState.showRevisionSheet && chapterDetails != null) {
            RevisionHistoryBottomSheet(
                revisions = chapterDetails.revisions,
                onRestoreRevision = { viewModel.restoreRevision(it) },
                onDismiss = { viewModel.closeRevisionSheet() }
            )
        }
    }
}

@Composable
private fun EmptyChapterCard(
    chapterTitle: String,
    onGenerate: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Lesson Ready to Generate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap below to stream a personalized, comprehensive lesson for '$chapterTitle' complete with LaTeX derivations, code examples, and knowledge-graph links.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onGenerate,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Lesson with Gemini")
            }
        }
    }
}

@Composable
private fun StreamingHeaderBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Streaming live lesson content from Gemini...",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
