package org.opened.app.ui.mocktest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.ui.components.RichMarkdownRenderer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeMockTestScreen(
    testId: String,
    onNavigateBack: () -> Unit,
    onTestSubmitted: (String) -> Unit,
    viewModel: TakeMockTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(testId) {
        viewModel.loadTest(testId)
    }

    // Handle Android system back press
    BackHandler {
        if (uiState.isGeneratingQuestions) {
            viewModel.cancelGeneration()
            onNavigateBack()
        } else {
            showExitDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isGeneratingQuestions) {
                            viewModel.cancelGeneration()
                            onNavigateBack()
                        } else {
                            showExitDialog = true
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Exam")
                    }
                },
                title = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = if (uiState.totalQuestions > 0) "Question ${uiState.currentQuestionIndex + 1} of ${uiState.totalQuestions}" else "Mock Exam",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val courseTitle = uiState.testWithQuestions?.test?.title
                        if (!courseTitle.isNullOrBlank()) {
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
                    if (uiState.currentQuestion != null) {
                        // Timer Chip
                        ExamTimerChip(remainingSeconds = uiState.remainingSeconds)

                        Spacer(modifier = Modifier.width(6.dp))

                        // Question Palette Toggle
                        IconButton(onClick = { viewModel.openPalette() }) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Question Palette"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState.currentQuestion != null) {
                ExamBottomNavigation(
                    currentIndex = uiState.currentQuestionIndex,
                    totalQuestions = uiState.totalQuestions,
                    hasSelectedOption = (uiState.currentQuestion?.userSelectedOption ?: -1) >= 0,
                    onPrev = { viewModel.prevQuestion() },
                    onNext = { viewModel.nextQuestion() },
                    onClear = { viewModel.clearCurrentAnswer() },
                    onSubmit = { viewModel.showSubmitConfirmation() }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val question = uiState.currentQuestion

            when {
                uiState.isGeneratingQuestions -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                                Text(
                                    text = "Generating Exam Questions...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.generationProgressText.ifBlank { "Synthesizing authentic questions, latex formulas, and detailed solutions..." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.cancelGeneration()
                                        onNavigateBack()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cancel Generation")
                                }
                            }
                        }
                    }
                }

                uiState.isLoading && question == null && uiState.errorMessage == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                            Text(
                                text = "Loading Mock Exam...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Preparing questions, formula renderers, and timer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                uiState.errorMessage != null || (uiState.testWithQuestions != null && uiState.totalQuestions == 0) -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Mock Exam Setup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.errorMessage ?: "This mock exam has no questions generated yet. Tap below to generate them with AI and begin your exam.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.generateMissingQuestions() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate Questions Now", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Back to Exam Prep")
                                }
                            }
                        }
                    }
                }

                question != null -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Question Header: Topic badge + Flag for Review
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = question.topic,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { viewModel.toggleFlag() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (question.isFlagged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (question.isFlagged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (question.isFlagged) Icons.Default.Flag else Icons.Default.OutlinedFlag,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (question.isFlagged) "Flagged" else "Flag",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                    // Question Text with 2D Math LaTeX Render Engine
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(modifier = Modifier.padding(18.dp)) {
                                RichMarkdownRenderer(
                                    markdown = question.questionText,
                                    onChapterClick = {},
                                    selectable = false
                                )
                            }
                        }
                    }

                    // Options List
                    val options = question.optionsList
                    val optionLetters = listOf("A", "B", "C", "D", "E", "F")

                    itemsIndexed(options) { index, optionText ->
                        val isSelected = question.userSelectedOption == index
                        val optionLetter = optionLetters.getOrElse(index) { "${index + 1}" }

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.selectOption(index) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectOption(index) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Option Letter Badge / Radio
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { viewModel.selectOption(index) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = optionLetter,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Option Content with Markdown & LaTeX support
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.selectOption(index) }
                                ) {
                                    RichMarkdownRenderer(
                                        markdown = optionText,
                                        onChapterClick = {},
                                        selectable = false,
                                        isClickableCard = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            // Question Palette Bottom Sheet
            if (uiState.showPalette) {
                QuestionPaletteBottomSheet(
                    testWithQuestions = uiState.testWithQuestions,
                    currentIndex = uiState.currentQuestionIndex,
                    onSelectQuestion = { viewModel.selectQuestion(it) },
                    onDismiss = { viewModel.closePalette() },
                    onSubmitClick = {
                        viewModel.closePalette()
                        viewModel.showSubmitConfirmation()
                    }
                )
            }

            // Submit Confirmation Dialog
            if (uiState.showSubmitConfirmDialog) {
                val total = uiState.totalQuestions
                val answered = uiState.answeredCount
                val unanswered = total - answered
                val flagged = uiState.flaggedCount

                AlertDialog(
                    onDismissRequest = { viewModel.dismissSubmitConfirmation() },
                    title = { Text("Submit Mock Exam?", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Summary of your responses:")
                            Text("• Total Questions: $total", fontWeight = FontWeight.SemiBold)
                            Text("• Answered: $answered", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            if (unanswered > 0) {
                                Text("• Unanswered: $unanswered", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                            if (flagged > 0) {
                                Text("• Marked for Review: $flagged", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                            }

                            if (unanswered > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ You still have $unanswered unanswered question(s). Unanswered questions will receive 0 points.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.submitTest { testIdResult ->
                                    onTestSubmitted(testIdResult)
                                }
                            },
                            enabled = !uiState.isSubmitting
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Confirm & Submit")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissSubmitConfirmation() }) {
                            Text("Keep Testing")
                        }
                    }
                )
            }

            // Exit Warning Dialog
            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("Exit Exam?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Your responses and time spent are saved offline. You can resume this exam anytime from the course Exam Prep screen.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitDialog = false
                                onNavigateBack()
                            }
                        ) {
                            Text("Exit Exam")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text("Resume")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExamTimerChip(remainingSeconds: Int) {
    val isUrgent = remainingSeconds <= 300 // Under 5 mins
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60

    val timeText = if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExamBottomNavigation(
    currentIndex: Int,
    totalQuestions: Int,
    hasSelectedOption: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Button
            OutlinedButton(
                onClick = onPrev,
                enabled = currentIndex > 0,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Prev", maxLines = 1)
            }

            // Clear Button
            TextButton(
                onClick = onClear,
                enabled = hasSelectedOption,
                modifier = Modifier.weight(0.9f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", maxLines = 1)
            }

            // Next or Submit Button
            if (currentIndex < totalQuestions - 1) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Next", maxLines = 1)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1.4f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionPaletteBottomSheet(
    testWithQuestions: org.opened.app.core.database.model.MockTestWithQuestions?,
    currentIndex: Int,
    onSelectQuestion: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSubmitClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val questions = testWithQuestions?.questions.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question Palette (${questions.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onSubmitClick,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Submit Exam")
                }
            }

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Answered")
                LegendItem(color = MaterialTheme.colorScheme.tertiary, label = "Flagged")
                LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "Unanswered")
            }

            // Grid of Question Numbers
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(260.dp)
            ) {
                gridItemsIndexed(questions) { index, question ->
                    val isCurrent = index == currentIndex
                    val isAnswered = question.userSelectedOption >= 0
                    val isFlagged = question.isFlagged

                    Surface(
                        shape = CircleShape,
                        color = when {
                            isFlagged -> MaterialTheme.colorScheme.tertiaryContainer
                            isAnswered -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onSelectQuestion(index) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                color = when {
                                    isFlagged -> MaterialTheme.colorScheme.onTertiaryContainer
                                    isAnswered -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
