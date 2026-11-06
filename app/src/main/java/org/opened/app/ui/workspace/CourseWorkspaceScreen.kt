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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.opened.app.core.ai.AcademicDomainClassifier
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.tts.TtsPlaybackState
import org.opened.app.ui.components.InlineRefineBottomSheet
import org.opened.app.ui.components.OutgoingBacklinksSection
import org.opened.app.ui.components.RichMarkdownRenderer
import org.opened.app.ui.components.SocraticQaBottomSheet
import org.opened.app.ui.components.SocraticQaSection
import org.opened.app.ui.flashcards.FlashcardDeckBottomSheet
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
    val isTtsActive = uiState.showTtsPlayer || ttsState.playbackState == TtsPlaybackState.PLAYING || ttsState.playbackState == TtsPlaybackState.PAUSED
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOverflowMenu by remember { mutableStateOf(false) }

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
                isExpandingCourse = uiState.isExpandingCourse,
                onChapterSelect = { chapterId ->
                    scope.launch { drawerState.close() }
                    viewModel.selectChapter(chapterId)
                },
                onExpandCourse = {
                    viewModel.expandCourseSyllabus()
                }
            )
        }
    ) {
        val chapterDetails = uiState.chapterDetails
        val currentChapter = chapterDetails?.chapter

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f),
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
                        if (currentChapter != null && currentChapter.markdownContent.isNotBlank() && !uiState.isStreaming) {
                            // Flashcards BadgedBox icon
                            BadgedBox(
                                badge = {
                                    if (uiState.chapterFlashcards.isNotEmpty()) {
                                        Badge { Text(uiState.chapterFlashcards.size.toString()) }
                                    }
                                }
                            ) {
                                IconButton(
                                    onClick = { viewModel.onFlashcardsClicked() },
                                    enabled = !uiState.isGeneratingFlashcards
                                ) {
                                    if (uiState.isGeneratingFlashcards) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Default.Style, contentDescription = "Flashcards")
                                    }
                                }
                            }

                            // TTS VolumeUp toggle
                            IconButton(onClick = { viewModel.toggleTtsPlayer() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Commute Audio Reader",
                                    tint = if (isTtsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Interactive Knowledge Lab toggle
                            IconButton(onClick = { viewModel.openLabSheet() }) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = "Interactive Knowledge Lab",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // MoreVert overflow menu containing History, Socratic Q&A, Refresh
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Interactive Lab") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Science, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.openLabSheet()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Revision History") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.History, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.openRevisionSheet()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Socratic Q&A") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.openSocraticQaSheet()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh Content") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.generateChapterContent()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f)
                    )
                )
            },
            bottomBar = {}
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
                    val cleanMarkdown = remember(currentChapter.id, currentChapter.markdownContent, currentChapter.title) {
                        stripLeadingH1(currentChapter.markdownContent, currentChapter.title)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
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

                        // Case 1: Unwritten / Blank Content (regardless of status PENDING or GENERATING)
                        if (currentChapter.markdownContent.isBlank() && !uiState.isStreaming) {
                            item {
                                EmptyChapterCard(
                                    chapterTitle = currentChapter.title,
                                    providerName = uiState.activeProviderName,
                                    onGenerate = { viewModel.generateChapterContent() }
                                )
                            }
                        }

                        // Case 2: Actively Streaming Content
                        if (uiState.isStreaming) {
                            item {
                                StreamingHeaderBanner(providerName = uiState.activeProviderName)
                            }
                            item {
                                val currentMarkdown = uiState.streamingContent.orEmpty()
                                if (currentMarkdown.isNotBlank()) {
                                    RichMarkdownRenderer(
                                        markdown = stripLeadingH1(currentMarkdown, currentChapter.title),
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

                        // Case 3: Chapter Reader with Content
                        if (currentChapter.markdownContent.isNotBlank() && !uiState.isStreaming) {
                            item {
                                RichMarkdownRenderer(
                                    markdown = cleanMarkdown,
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

                // Floating bottom pill overlay — only shown when chapter has content and not streaming
                if (currentChapter != null && currentChapter.markdownContent.isNotBlank() && !uiState.isStreaming) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // TTS mini-player sits above the pill
                        AnimatedVisibility(visible = isTtsActive) {
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

                        // Floating pill action bar
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Refine
                                TextButton(onClick = { viewModel.openRefineSheet() }) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Refine",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Mastery
                                TextButton(onClick = { viewModel.openMasterySheet() }) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Mastery",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Cards (with badge count when > 0)
                                TextButton(onClick = { viewModel.onFlashcardsClicked() }) {
                                    Icon(
                                        Icons.Default.Style,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.chapterFlashcards.isNotEmpty()) "Cards (${uiState.chapterFlashcards.size})" else "Cards",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Q&A
                                TextButton(onClick = { viewModel.openSocraticQaSheet() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Q&A",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Inline Refine Bottom Sheet Dialog
        if (uiState.showRefineSheet) {
            val domain = AcademicDomainClassifier.detectAcademicDomain(
                topic = uiState.pathWithModules?.studyPath?.topic.orEmpty(),
                courseTitle = uiState.pathWithModules?.studyPath?.title.orEmpty(),
                chapterTitle = currentChapter?.title.orEmpty()
            )
            InlineRefineBottomSheet(
                selectedSnippet = uiState.refineSnippet.orEmpty(),
                isRefining = uiState.isRefining,
                onDismiss = { viewModel.closeRefineSheet() },
                onApplyRefinement = { snippet, action, customPrompt ->
                    viewModel.applyRefinement(action, customPrompt, snippet)
                },
                domain = domain
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

        // Socratic Q&A Bottom Sheet Dialog
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

        // Chapter Flashcard Deck Bottom Sheet
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

        // Interactive Knowledge Laboratory Sheet (Phase 7)
        if (uiState.showLabSheet) {
            val domain = AcademicDomainClassifier.detectAcademicDomain(
                topic = uiState.pathWithModules?.studyPath?.topic.orEmpty(),
                courseTitle = uiState.pathWithModules?.studyPath?.title.orEmpty(),
                chapterTitle = currentChapter?.title.orEmpty()
            )
            org.opened.app.ui.laboratory.InteractiveLaboratorySheet(
                initialDomain = domain,
                onDismiss = { viewModel.closeLabSheet() }
            )
        }
    }
}

/**
 * Strips a leading H1 heading from markdown content when it matches the chapter title,
 * preventing duplicate headings since the title is already rendered by the UI header.
 */
private fun stripLeadingH1(markdown: String, chapterTitle: String): String {
    val lines = markdown.trimStart().lines()
    if (lines.isEmpty()) return markdown
    val firstLine = lines.first().trim()
    // Match # Title or # Chapter N: Title patterns
    if (firstLine.startsWith("#") && !firstLine.startsWith("##")) {
        val headingText = firstLine.removePrefix("#").trim()
        // Strip if heading text matches chapter title (case-insensitive, ignoring "Chapter N: " prefix)
        val normalizedHeading = headingText.replace(Regex("^Chapter\\s+\\d+[:\\.\\-]?\\s*", RegexOption.IGNORE_CASE), "").trim()
        val normalizedTitle = chapterTitle.replace(Regex("^Chapter\\s+\\d+[:\\.\\-]?\\s*", RegexOption.IGNORE_CASE), "").trim()
        if (normalizedHeading.equals(normalizedTitle, ignoreCase = true) ||
            headingText.equals(chapterTitle, ignoreCase = true) ||
            headingText.startsWith("Chapter", ignoreCase = true)) {
            return lines.drop(1).joinToString("\n").trimStart()
        }
    }
    return markdown
}

@Composable
private fun EmptyChapterCard(
    chapterTitle: String,
    providerName: String,
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
                Text("Generate Lesson with $providerName")
            }
        }
    }
}

@Composable
private fun StreamingHeaderBanner(providerName: String) {
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
                text = "Streaming live lesson content from $providerName...",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
