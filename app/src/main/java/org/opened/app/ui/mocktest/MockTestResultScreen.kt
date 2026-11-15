package org.opened.app.ui.mocktest

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MockTestResultScreen(
    testId: String,
    onNavigateBack: () -> Unit,
    onRetakeTest: (String) -> Unit,
    viewModel: MockTestResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showTopicBreakdown by remember { mutableStateOf(false) }

    LaunchedEffect(testId) {
        viewModel.loadResult(testId)
    }

    val test = uiState.testWithQuestions?.test

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Exam Score & Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        test?.title?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.retakeTest(onRetakeTest) }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retake Test")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (test == null || uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Scorecard Hero
                    item {
                        ScorecardHeroCard(
                            score = test.score,
                            totalCorrect = test.totalCorrect,
                            totalQuestions = test.totalQuestions,
                            timeSpentSeconds = test.timeSpentSeconds,
                            timeLimitMinutes = test.timeLimitMinutes,
                            totalAnswered = test.totalAnswered
                        )
                    }

                    // Topic Breakdown Accordion Card
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTopicBreakdown = !showTopicBreakdown },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Topic Mastery Breakdown",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${uiState.topicMasteryList.size} topics assessed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showTopicBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }

                                AnimatedVisibility(visible = showTopicBreakdown) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        uiState.topicMasteryList.forEach { item ->
                                            TopicMasteryRow(item = item)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Filter Chips Row
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SOLUTIONS & IN-DEPTH EXPLANATIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ResultFilter.values().forEach { filter ->
                                    val count = when (filter) {
                                        ResultFilter.ALL -> uiState.questions.size
                                        ResultFilter.INCORRECT -> uiState.questions.count { it.userSelectedOption != it.correctOptionIndex }
                                        ResultFilter.CORRECT -> uiState.questions.count { it.userSelectedOption >= 0 && it.userSelectedOption == it.correctOptionIndex }
                                        ResultFilter.FLAGGED -> uiState.questions.count { it.isFlagged }
                                    }

                                    FilterChip(
                                        selected = uiState.selectedFilter == filter,
                                        onClick = { viewModel.setFilter(filter) },
                                        label = { Text("${filter.label} ($count)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = when (filter) {
                                                ResultFilter.INCORRECT -> MaterialTheme.colorScheme.errorContainer
                                                ResultFilter.CORRECT -> MaterialTheme.colorScheme.primaryContainer
                                                ResultFilter.FLAGGED -> MaterialTheme.colorScheme.tertiaryContainer
                                                ResultFilter.ALL -> MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Detailed Questions Review
                    itemsIndexed(uiState.filteredQuestions, key = { _, q -> q.id }) { index, question ->
                        QuestionSolutionCard(
                            questionNumber = question.questionIndex + 1,
                            question = question
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScorecardHeroCard(
    score: Int,
    totalCorrect: Int,
    totalQuestions: Int,
    timeSpentSeconds: Int,
    timeLimitMinutes: Int,
    totalAnswered: Int
) {
    val performanceVerdict = when {
        score >= 85 -> "🏆 Outstanding! Exam-Ready Mastery"
        score >= 70 -> "✅ Strong Pass! Solid Conceptual Base"
        score >= 50 -> "⚡ Borderline. Target Weak Topics Below"
        else -> "⚠️ Needs Topic Review & Remedial Study"
    }

    val minutes = timeSpentSeconds / 60
    val seconds = timeSpentSeconds % 60
    val timeFormatted = "${minutes}m ${seconds}s"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (score >= 70) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Radial Score Badge
            Surface(
                shape = CircleShape,
                color = if (score >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$score%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Score",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Text(
                text = performanceVerdict,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Metrics Grid
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricColumn(label = "Correct", value = "$totalCorrect / $totalQuestions", color = MaterialTheme.colorScheme.primary)
                    MetricColumn(label = "Attempted", value = "$totalAnswered / $totalQuestions", color = MaterialTheme.colorScheme.onSurface)
                    MetricColumn(label = "Time Spent", value = timeFormatted, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TopicMasteryRow(item: TopicMasteryItem) {
    val progressFloat = item.accuracyPercentage / 100f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.topic,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.accuracyPercentage}% (${item.correctCount}/${item.totalCount})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.accuracyPercentage >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        LinearProgressIndicator(
            progress = { progressFloat },
            color = if (item.accuracyPercentage >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun QuestionSolutionCard(
    questionNumber: Int,
    question: MockTestQuestionEntity
) {
    val isCorrect = question.userSelectedOption >= 0 && question.userSelectedOption == question.correctOptionIndex
    val isUnanswered = question.userSelectedOption < 0
    val optionLetters = listOf("A", "B", "C", "D", "E", "F")

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Question Number, Topic Badge, Result Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Q$questionNumber",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = question.topic,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isCorrect -> MaterialTheme.colorScheme.primaryContainer
                        isUnanswered -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isCorrect -> Icons.Default.CheckCircle
                                isUnanswered -> Icons.Default.Cancel
                                else -> Icons.Default.Cancel
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when {
                                isCorrect -> MaterialTheme.colorScheme.onPrimaryContainer
                                isUnanswered -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = when {
                                isCorrect -> "Correct"
                                isUnanswered -> "Skipped"
                                else -> "Incorrect"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCorrect -> MaterialTheme.colorScheme.onPrimaryContainer
                                isUnanswered -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            // Question Text with LaTeX & Markdown
            Box(modifier = Modifier.fillMaxWidth()) {
                RichMarkdownRenderer(
                    markdown = question.questionText,
                    onChapterClick = {}
                )
            }

            // Options with Correct vs Selected indicators
            val options = question.optionsList
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { optIdx, optText ->
                    val isOptionSelected = question.userSelectedOption == optIdx
                    val isOptionCorrect = question.correctOptionIndex == optIdx
                    val optionLetter = optionLetters.getOrElse(optIdx) { "${optIdx + 1}" }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isOptionCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            isOptionSelected && !isOptionCorrect -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = when {
                            isOptionCorrect -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            isOptionSelected && !isOptionCorrect -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                            else -> null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isOptionCorrect -> MaterialTheme.colorScheme.primary
                                    isOptionSelected && !isOptionCorrect -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = optionLetter,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isOptionCorrect -> MaterialTheme.colorScheme.onPrimary
                                            isOptionSelected && !isOptionCorrect -> MaterialTheme.colorScheme.onError
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                RichMarkdownRenderer(markdown = optText, onChapterClick = {})
                            }

                            if (isOptionCorrect) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Correct Answer",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (isOptionSelected) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Your Answer",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Pedagogical Breakdown & Solution Box
            if (question.explanation.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pedagogical Breakdown & Derivation",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        RichMarkdownRenderer(
                            markdown = question.explanation,
                            onChapterClick = {}
                        )
                    }
                }
            }
        }
    }
}
