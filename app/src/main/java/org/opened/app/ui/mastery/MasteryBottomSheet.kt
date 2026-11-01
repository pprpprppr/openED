package org.opened.app.ui.mastery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.ai.FeynmanEvaluationResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasteryBottomSheet(
    chapterId: String,
    onDismiss: () -> Unit,
    viewModel: MasteryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(
                    "Test Mastery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = state.selectedTabIndex) {
                Tab(
                    selected = state.selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Quiz, null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Adaptive Quiz") }
                )
                Tab(
                    selected = state.selectedTabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Feynman Mode") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                when (state.selectedTabIndex) {
                    0 -> QuizTab(state = state, chapterId = chapterId, viewModel = viewModel)
                    1 -> FeynmanTab(state = state, chapterId = chapterId, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun QuizTab(state: MasteryUiState, chapterId: String, viewModel: MasteryViewModel) {
    val quiz = state.quizResult
    when {
        state.quizLoading -> {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Generating quiz...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        quiz == null -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Generate 5 scenario-based multiple-choice questions tailored to this chapter's content.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { viewModel.generateQuiz(chapterId) }) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Text("  Generate Quiz")
                }
            }
        }

        else -> {
            // Show questions
            quiz.questions.forEachIndexed { qi, question ->
                Text(
                    "Q${qi + 1}: ${question.question}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                question.options.forEachIndexed { oi, option ->
                    val isSelected = state.quizAnswers[qi] == oi
                    val isCorrect = oi == question.correctIndex
                    val containerColor = when {
                        !state.quizSubmitted && isSelected -> MaterialTheme.colorScheme.primaryContainer
                        state.quizSubmitted && isCorrect -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                        state.quizSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    val borderColor = when {
                        !state.quizSubmitted && isSelected -> MaterialTheme.colorScheme.primary
                        state.quizSubmitted && isCorrect -> Color(0xFF2E7D32)
                        state.quizSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                        else -> Color.Transparent
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .then(
                                if (!state.quizSubmitted)
                                    Modifier.clickable { viewModel.answerQuestion(qi, oi) }
                                else Modifier
                            ),
                        color = containerColor,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "${('A' + oi)}. $option",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (state.quizSubmitted) {
                    Surface(
                        color = Color(0xFF1B5E20).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "✓ ${question.explanation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
            }

            if (!state.quizSubmitted) {
                Button(
                    onClick = { viewModel.submitQuiz(chapterId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.quizAnswers.size == quiz.questions.size
                ) { Text("Submit Quiz") }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Your Score: ${state.quizScore}%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { viewModel.generateQuiz(chapterId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Retake Quiz") }
            }
        }
    }
}

@Composable
private fun FeynmanTab(state: MasteryUiState, chapterId: String, viewModel: MasteryViewModel) {
    val result = state.feynmanResult
    if (result == null) {
        Column {
            Text(
                "Explain the core concept of this chapter in your own words, as if teaching a 10-year-old.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.feynmanText,
                onValueChange = viewModel::updateFeynmanText,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text("Your explanation...") },
                maxLines = 8
            )
            state.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.evaluateFeynman(chapterId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.feynmanLoading && state.feynmanText.isNotBlank()
            ) {
                if (state.feynmanLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Evaluating...")
                } else {
                    Icon(Icons.Default.AutoAwesome, null)
                    Text("  Evaluate My Explanation")
                }
            }
        }
    } else {
        FeynmanResultCard(result = result, onTryAgain = { viewModel.updateFeynmanText(""); viewModel.clearError() })
    }
}

@Composable
private fun FeynmanResultCard(result: FeynmanEvaluationResponse, onTryAgain: () -> Unit) {
    Column {
        // Score
        Surface(
            color = when {
                result.score >= 80 -> Color(0xFF1B5E20).copy(alpha = 0.12f)
                result.score >= 60 -> Color(0xFFE65100).copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.errorContainer
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mastery Score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${result.score}/100",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        result.score >= 80 -> Color(0xFF2E7D32)
                        result.score >= 60 -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        if (result.whatYouGotRight.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("✅ What you got right", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            Spacer(Modifier.height(6.dp))
            result.whatYouGotRight.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (result.missingConcepts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("⚠️ Missing concepts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
            Spacer(Modifier.height(6.dp))
            result.missingConcepts.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (result.refinedExplanation.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("📖 Refined Explanation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    result.refinedExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Try Again")
        }
    }
}
