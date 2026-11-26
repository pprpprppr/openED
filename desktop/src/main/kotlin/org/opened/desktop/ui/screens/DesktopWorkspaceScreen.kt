package org.opened.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.ai.AcademicDomainClassifier
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.core.tts.TtsPlaybackState
import org.opened.app.ui.components.InlineRefineBottomSheet
import org.opened.app.ui.components.OutgoingBacklinksSection
import org.opened.app.ui.components.RichMarkdownRenderer
import org.opened.app.ui.components.SocraticQaBottomSheet
import org.opened.app.ui.components.SocraticQaSection
import org.opened.app.ui.flashcards.FlashcardDeckBottomSheet
import org.opened.app.ui.laboratory.InteractiveLaboratorySheet
import org.opened.app.ui.mastery.MasteryBottomSheet
import org.opened.app.ui.platform.PlatformBackHandler
import org.opened.app.ui.platform.openEdViewModel
import org.opened.app.ui.workspace.CourseWorkspaceViewModel
import org.opened.app.ui.workspace.RevisionHistoryBottomSheet
import org.opened.app.ui.workspace.TtsMiniPlayer
import org.opened.desktop.ui.components.DesktopCard
import org.opened.desktop.ui.components.DesktopEmptyState
import org.opened.desktop.ui.components.DesktopPillBadge
import org.opened.desktop.ui.theme.LocalDesktopTokens

@Composable
fun DesktopWorkspaceScreen(
    pathId: String,
    initialChapterId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToMockTests: () -> Unit,
    viewModel: CourseWorkspaceViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSyllabusSidebar by remember { mutableStateOf(true) }
    val readerListState = rememberLazyListState()

    LaunchedEffect(pathId, initialChapterId) {
        viewModel.initWorkspace(pathId, initialChapterId)
    }

    LaunchedEffect(uiState.errorMessage, uiState.flashcardSnackMessage) {
        val msg = uiState.errorMessage ?: uiState.flashcardSnackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
            viewModel.clearFlashcardMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.ttsStop()
        }
    }

    PlatformBackHandler {
        if (uiState.isStreaming) {
            viewModel.cancelChapterStreaming()
        }
        onNavigateBack()
    }

    val currentChapter = uiState.chapterDetails?.chapter
    val chapterDetails = uiState.chapterDetails
    val hasContent = currentChapter != null && currentChapter.markdownContent.isNotBlank()
    val isTtsActive = uiState.showTtsPlayer ||
        ttsState.playbackState == TtsPlaybackState.PLAYING ||
        ttsState.playbackState == TtsPlaybackState.PAUSED

    Scaffold(
        containerColor = tokens.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            // Left Persistent Syllabus Tree Sidebar
            AnimatedVisibility(visible = showSyllabusSidebar && uiState.pathWithModules != null) {
                uiState.pathWithModules?.let { path ->
                    DesktopSyllabusPanel(
                        pathWithModules = path,
                        activeChapterId = uiState.activeChapterId,
                        isExpandingCourse = uiState.isExpandingCourse,
                        onSelectChapter = { viewModel.selectChapter(it) },
                        onExpandCourse = { viewModel.expandCourseSyllabus() },
                        onNavigateToMockTests = onNavigateToMockTests
                    )
                }
            }

            // Right Main Workspace Pane
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Dedicated Desktop Toolbar Header (Zero floating overlap on content)
                Surface(
                    color = tokens.header,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (uiState.isStreaming) viewModel.cancelChapterStreaming()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = tokens.textPrimary)
                                }
                                IconButton(
                                    onClick = { showSyllabusSidebar = !showSyllabusSidebar },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.MenuOpen, "Toggle Syllabus", tint = tokens.accent)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentChapter?.title ?: uiState.pathWithModules?.studyPath?.title ?: "Workspace",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = uiState.pathWithModules?.studyPath?.title.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Horizontal Scrollable Action Strip
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (hasContent && !uiState.isStreaming) {
                                    BadgedBox(
                                        badge = {
                                            if (uiState.chapterFlashcards.isNotEmpty()) {
                                                Badge { Text(uiState.chapterFlashcards.size.toString()) }
                                            }
                                        }
                                    ) {
                                        WorkspaceToolbarChip(
                                            icon = Icons.Default.Style,
                                            label = "Flashcards",
                                            busy = uiState.isGeneratingFlashcards,
                                            onClick = { viewModel.onFlashcardsClicked() }
                                        )
                                    }
                                    WorkspaceToolbarChip(
                                        icon = Icons.Default.School,
                                        label = "Mastery",
                                        onClick = { viewModel.openMasterySheet() }
                                    )
                                    WorkspaceToolbarChip(
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        label = "Ask AI",
                                        onClick = { viewModel.openSocraticQaSheet() }
                                    )
                                    WorkspaceToolbarChip(
                                        icon = Icons.Default.Tune,
                                        label = "Refine",
                                        onClick = { viewModel.openRefineSheet() }
                                    )
                                    WorkspaceToolbarChip(
                                        icon = Icons.Default.Science,
                                        label = "Lab",
                                        onClick = { viewModel.openLabSheet() }
                                    )
                                    WorkspaceToolbarChip(
                                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                                        label = "Audio",
                                        active = isTtsActive,
                                        onClick = { viewModel.toggleTtsPlayer() }
                                    )
                                    WorkspaceToolbarChip(
                                        icon = Icons.Default.History,
                                        label = "History",
                                        onClick = { viewModel.openRevisionSheet() }
                                    )
                                    WorkspaceToolbarChip(
                                        icon = Icons.Default.Refresh,
                                        label = "Regen",
                                        onClick = { viewModel.generateChapterContent() }
                                    )
                                }
                                WorkspaceToolbarChip(
                                    icon = Icons.AutoMirrored.Filled.Assignment,
                                    label = "Exams",
                                    onClick = onNavigateToMockTests
                                )
                            }
                        }
                        HorizontalDivider(color = tokens.border)
                    }
                }

                // Scrollable Editorial Reading Surface
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val pathWithModules = uiState.pathWithModules
                    when {
                        pathWithModules != null && pathWithModules.allChapters.isEmpty() -> {
                            DesktopEmptyState(
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                title = "Exam Prep Study Path",
                                description = "\"${pathWithModules.studyPath.title}\" was created for Mock Exams. Generate a full chapter curriculum or open Exam Prep.",
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = onNavigateToMockTests,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, tokens.border)
                                    ) {
                                        Text("Open Exam Prep")
                                    }
                                    Button(
                                        onClick = { viewModel.generateSyllabusForCourse() },
                                        enabled = !uiState.isExpandingCourse,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (uiState.isExpandingCourse) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Generate Curriculum")
                                    }
                                }
                            }
                        }

                        currentChapter == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        else -> {
                            val displayMarkdown = remember(
                                uiState.streamingContent,
                                uiState.isStreaming,
                                currentChapter.id,
                                currentChapter.markdownContent,
                                currentChapter.title
                            ) {
                                val raw = if (uiState.isStreaming && !uiState.streamingContent.isNullOrBlank()) {
                                    uiState.streamingContent.orEmpty()
                                } else {
                                    currentChapter.markdownContent
                                }
                                stripChapterTitleHeading(raw, currentChapter.title)
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                if (uiState.isStreaming) {
                                    Surface(color = tokens.accentSoft, modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = tokens.accent)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Streaming lesson via ${uiState.activeProviderName}...",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = tokens.accent,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.cancelChapterStreaming() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.danger),
                                                border = BorderStroke(1.dp, tokens.danger.copy(alpha = 0.45f))
                                            ) {
                                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Stop")
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    LazyColumn(
                                        state = readerListState,
                                        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
                                        verticalArrangement = Arrangement.spacedBy(18.dp)
                                    ) {
                                        if (displayMarkdown.isBlank() && !uiState.isStreaming) {
                                            item {
                                                DesktopEmptyState(
                                                    icon = Icons.Default.AutoAwesome,
                                                    title = currentChapter.title,
                                                    description = currentChapter.summary.ifBlank { "Ready to synthesize rich explanations, code/math blocks, diagrams, and interactive cross-links." }
                                                ) {
                                                    Button(
                                                        onClick = { viewModel.generateChapterContent() },
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Generate Full Lesson with AI")
                                                    }
                                                }
                                            }
                                        } else {
                                            item(key = "chapter_body_${currentChapter.id}") {
                                                DesktopCard(contentPadding = 26.dp) {
                                                    RichMarkdownRenderer(
                                                        markdown = displayMarkdown,
                                                        onChapterClick = { targetTitle ->
                                                            viewModel.navigateToChapterByTitle(targetTitle)
                                                        }
                                                    )
                                                }
                                            }

                                            if (chapterDetails != null && !uiState.isStreaming) {
                                                item(key = "backlinks_${currentChapter.id}") {
                                                    OutgoingBacklinksSection(
                                                        outgoingChapters = chapterDetails.outgoingChapters,
                                                        backlinkChapters = chapterDetails.backlinkChapters,
                                                        onChapterClick = { viewModel.navigateToChapterByTitle(it) },
                                                        onGenerateTangent = { viewModel.generateTangent(it) }
                                                    )
                                                }

                                                item(key = "socratic_${currentChapter.id}") {
                                                    SocraticQaSection(
                                                        qas = chapterDetails.qas,
                                                        isAskingQuestion = uiState.isAskingQuestion,
                                                        onAskQuestion = { viewModel.askSocraticQuestion(it) },
                                                        onAppendToChapter = { viewModel.appendQaToChapter(it) },
                                                        onChapterClick = { viewModel.navigateToChapterByTitle(it) }
                                                    )
                                                }

                                                item(key = "mastery_footer_${currentChapter.id}") {
                                                    DesktopCard(
                                                        borderColor = tokens.accent.copy(alpha = 0.4f),
                                                        contentPadding = 20.dp
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "Mastery Check & Recall",
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = tokens.textPrimary
                                                                )
                                                                Text(
                                                                    text = "Validate retention with an adaptive quiz, Feynman evaluation, or AI flashcards.",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = tokens.textMuted
                                                                )
                                                            }
                                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                                FilledTonalButton(
                                                                    onClick = { viewModel.generateFlashcards() },
                                                                    enabled = !uiState.isGeneratingFlashcards,
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Style, null, modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("Generate Flashcards")
                                                                }
                                                                Button(
                                                                    onClick = { viewModel.openMasterySheet() },
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ) {
                                                                    Icon(Icons.Default.School, null, modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("Test Mastery")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = isTtsActive) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.widthIn(max = 860.dp)) {
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Workspace Shared Interactive Tools
    val activeDomain = remember(uiState.pathWithModules, currentChapter) {
        AcademicDomainClassifier.detectAcademicDomain(
            topic = uiState.pathWithModules?.studyPath?.topic.orEmpty(),
            courseTitle = uiState.pathWithModules?.studyPath?.title.orEmpty(),
            chapterTitle = currentChapter?.title.orEmpty()
        )
    }

    if (uiState.showRefineSheet) {
        InlineRefineBottomSheet(
            selectedSnippet = uiState.refineSnippet,
            isRefining = uiState.isRefining,
            onDismiss = { viewModel.closeRefineSheet() },
            onApplyRefinement = { snippet, action, customPrompt ->
                viewModel.applyRefinement(action, customPrompt, snippet)
            },
            domain = activeDomain
        )
    }

    if (uiState.showMasterySheet && currentChapter != null) {
        MasteryBottomSheet(
            chapterId = currentChapter.id,
            onDismiss = { viewModel.closeMasterySheet() }
        )
    }

    if (uiState.showRevisionSheet && chapterDetails != null) {
        RevisionHistoryBottomSheet(
            revisions = chapterDetails.revisions,
            onRestoreRevision = { viewModel.restoreRevision(it) },
            onDismiss = { viewModel.closeRevisionSheet() }
        )
    }

    if (uiState.showSocraticQaSheet && chapterDetails != null) {
        SocraticQaBottomSheet(
            qas = chapterDetails.qas,
            isAskingQuestion = uiState.isAskingQuestion,
            onAskQuestion = { viewModel.askSocraticQuestion(it) },
            onAppendToChapter = { qa -> viewModel.appendQaToChapter(qa) },
            onChapterClick = { title -> viewModel.navigateToChapterByTitle(title) },
            onDismiss = { viewModel.closeSocraticQaSheet() }
        )
    }

    if (uiState.showFlashcardDeck) {
        FlashcardDeckBottomSheet(
            cards = uiState.chapterFlashcards,
            isGenerating = uiState.isGeneratingFlashcards,
            onDismiss = { viewModel.closeFlashcardDeck() },
            onRateCard = { card, rating -> viewModel.rateFlashcard(card, rating) },
            onGenerateMore = { viewModel.generateFlashcards() },
            onChapterClick = { title -> viewModel.navigateToChapterByTitle(title) }
        )
    }

    if (uiState.showLabSheet) {
        InteractiveLaboratorySheet(
            initialDomain = activeDomain,
            onDismiss = { viewModel.closeLabSheet() }
        )
    }
}

@Composable
private fun DesktopSyllabusPanel(
    pathWithModules: StudyPathWithModules,
    activeChapterId: String?,
    isExpandingCourse: Boolean,
    onSelectChapter: (String) -> Unit,
    onExpandCourse: () -> Unit,
    onNavigateToMockTests: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val expandedModules = remember(pathWithModules.studyPath.id) {
        mutableStateMapOf<String, Boolean>().apply {
            pathWithModules.modules.forEach { put(it.module.id, true) }
        }
    }
    val sortedModules = remember(pathWithModules) { pathWithModules.sortedModules }
    val progress = pathWithModules.calculatedProgress.coerceIn(0f, 1f)

    Row(modifier = Modifier.width(292.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(tokens.sidebar)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DesktopPillBadge(text = pathWithModules.studyPath.userLevel)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pathWithModules.studyPath.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${pathWithModules.completedChaptersCount}/${pathWithModules.totalChaptersCount} chapters",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.accent
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = tokens.accent,
                    trackColor = tokens.cardSubtle
                )
            }

            HorizontalDivider(color = tokens.border)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sortedModules, key = { it.module.id }) { mod ->
                    val isExpanded = expandedModules[mod.module.id] != false
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { expandedModules[mod.module.id] = !isExpanded },
                        shape = RoundedCornerShape(9.dp),
                        color = tokens.cardSubtle
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = mod.module.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = tokens.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        mod.sortedChapters.forEach { ch ->
                            val isActive = ch.id == activeChapterId
                            val statusIcon = when (ch.status) {
                                ChapterStatus.COMPLETED -> Icons.Default.CheckCircle
                                ChapterStatus.GENERATING -> Icons.Default.AutoAwesome
                                ChapterStatus.PENDING -> Icons.Default.RadioButtonUnchecked
                            }
                            val statusColor = when (ch.status) {
                                ChapterStatus.COMPLETED -> tokens.success
                                ChapterStatus.GENERATING -> tokens.warning
                                ChapterStatus.PENDING -> tokens.textMuted
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, top = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectChapter(ch.id) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) tokens.accentSoft else tokens.sidebar,
                                border = if (isActive) BorderStroke(1.dp, tokens.accent.copy(alpha = 0.5f)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = if (isActive) tokens.accent else statusColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) tokens.accent else tokens.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = tokens.border)

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExpandCourse,
                    enabled = !isExpandingCourse,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, tokens.border)
                ) {
                    if (isExpandingCourse) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Expanding...")
                    } else {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Expand +2 Modules", style = MaterialTheme.typography.labelMedium)
                    }
                }
                FilledTonalButton(
                    onClick = onNavigateToMockTests,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Course Mock Exams", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.border))
    }
}

@Composable
private fun WorkspaceToolbarChip(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !busy, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) tokens.accentSoft else tokens.cardSubtle,
        border = BorderStroke(1.dp, if (active) tokens.accent else tokens.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = tokens.accent)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) tokens.accent else tokens.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) tokens.accent else tokens.textPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private val DESKTOP_CHAPTER_PREFIX_REGEX = Regex("^Chapter\\s+\\d+[:\\.\\-]?\\s*", RegexOption.IGNORE_CASE)

private fun stripChapterTitleHeading(markdown: String, chapterTitle: String): String {
    val lines = markdown.trimStart().lines()
    if (lines.isEmpty()) return markdown
    val firstLine = lines.first().trim()
    if (firstLine.startsWith("#") && !firstLine.startsWith("##")) {
        val headingText = firstLine.removePrefix("#").trim()
        val normalizedHeading = headingText.replace(DESKTOP_CHAPTER_PREFIX_REGEX, "").trim()
        val normalizedTitle = chapterTitle.replace(DESKTOP_CHAPTER_PREFIX_REGEX, "").trim()
        if (normalizedHeading.equals(normalizedTitle, ignoreCase = true) ||
            headingText.equals(chapterTitle, ignoreCase = true) ||
            headingText.startsWith("Chapter", ignoreCase = true)
        ) {
            return lines.drop(1).joinToString("\n").trimStart()
        }
    }
    return markdown
}
