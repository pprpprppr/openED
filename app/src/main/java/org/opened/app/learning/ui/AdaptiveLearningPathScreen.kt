package org.opened.app.learning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Adaptive Scaffold for viewing and editing learning paths:
 * - On Android phones (< 600dp): single-pane navigation (Timeline Outline -> Chapter Editor).
 * - On Android tablets / foldables (>= 600dp): split-pane layout with outline on left, editor on right.
 */
@Composable
fun AdaptiveLearningPathScreen(
    pathId: String,
    onNavigateBack: () -> Unit,
    viewModel: LearningPathViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(pathId) {
        viewModel.loadLearningPath(pathId)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletOrExpanded = maxWidth >= 600.dp

        if (uiState.isLoading && uiState.pathWithChapters == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BoxWithConstraints
        }

        if (isTabletOrExpanded) {
            // Tablet / Desktop Split-Pane Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Pane: Chapter Outline / Timeline (38% width)
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.38f)) {
                    ChapterListView(
                        pathWithChapters = uiState.pathWithChapters,
                        chapters = uiState.localChapters,
                        activeChapterId = uiState.activeChapterId,
                        onChapterSelect = { chapterId ->
                            viewModel.selectChapter(chapterId)
                        },
                        onChapterMove = { from, to ->
                            viewModel.onChapterDragMove(from, to)
                        },
                        onChapterDrop = {
                            viewModel.onChapterDropCompleted()
                        },
                        onAddChapter = { title ->
                            viewModel.addChapter(title)
                        },
                        onDeleteChapter = { chapterId ->
                            viewModel.deleteChapter(chapterId)
                        }
                    )
                }

                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Right Pane: Active Chapter Editor (62% width)
                Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                    ChapterEditorView(
                        chapterWithBlocks = uiState.activeChapterWithBlocks,
                        blocks = uiState.localContentBlocks,
                        isCompactScreen = false,
                        onBackToList = {},
                        onBlockMove = { from, to ->
                            viewModel.onContentBlockDragMove(from, to)
                        },
                        onBlockDrop = {
                            viewModel.onContentBlockDropCompleted()
                        },
                        onUpdateBlock = { block ->
                            viewModel.updateContentBlock(block)
                        },
                        onDeleteBlock = { block ->
                            viewModel.deleteContentBlock(block)
                        },
                        onAddBlock = { type ->
                            viewModel.addContentBlock(type)
                        },
                        onGenerateAi = { chapterId ->
                            viewModel.generateChapterContentWithAi(chapterId)
                        }
                    )
                }
            }
        } else {
            // Phone Single-Pane Navigation
            BackHandler(enabled = uiState.activePane == LearningPathPane.DETAIL) {
                viewModel.navigateToPane(LearningPathPane.LIST)
            }

            when (uiState.activePane) {
                LearningPathPane.LIST -> {
                    ChapterListView(
                        pathWithChapters = uiState.pathWithChapters,
                        chapters = uiState.localChapters,
                        activeChapterId = uiState.activeChapterId,
                        onChapterSelect = { chapterId ->
                            viewModel.selectChapter(chapterId)
                        },
                        onChapterMove = { from, to ->
                            viewModel.onChapterDragMove(from, to)
                        },
                        onChapterDrop = {
                            viewModel.onChapterDropCompleted()
                        },
                        onAddChapter = { title ->
                            viewModel.addChapter(title)
                        },
                        onDeleteChapter = { chapterId ->
                            viewModel.deleteChapter(chapterId)
                        }
                    )
                }

                LearningPathPane.DETAIL -> {
                    ChapterEditorView(
                        chapterWithBlocks = uiState.activeChapterWithBlocks,
                        blocks = uiState.localContentBlocks,
                        isCompactScreen = true,
                        onBackToList = {
                            viewModel.navigateToPane(LearningPathPane.LIST)
                        },
                        onBlockMove = { from, to ->
                            viewModel.onContentBlockDragMove(from, to)
                        },
                        onBlockDrop = {
                            viewModel.onContentBlockDropCompleted()
                        },
                        onUpdateBlock = { block ->
                            viewModel.updateContentBlock(block)
                        },
                        onDeleteBlock = { block ->
                            viewModel.deleteContentBlock(block)
                        },
                        onAddBlock = { type ->
                            viewModel.addContentBlock(type)
                        },
                        onGenerateAi = { chapterId ->
                            viewModel.generateChapterContentWithAi(chapterId)
                        }
                    )
                }
            }
        }
    }
}
