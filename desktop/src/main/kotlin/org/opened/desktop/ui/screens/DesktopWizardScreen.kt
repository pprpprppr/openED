package org.opened.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.ai.DiagnosticQuestion
import org.opened.app.core.ai.SyllabusResponse
import org.opened.app.ui.platform.PlatformBackHandler
import org.opened.app.ui.platform.openEdViewModel
import org.opened.app.ui.wizard.DiagnosticWizardUiState
import org.opened.app.ui.wizard.DiagnosticWizardViewModel
import org.opened.app.ui.wizard.WizardStep
import org.opened.desktop.ui.components.DesktopCard
import org.opened.desktop.ui.components.DesktopPageHeader
import org.opened.desktop.ui.components.DesktopPillBadge
import org.opened.desktop.ui.theme.LocalDesktopTokens

@Composable
fun DesktopWizardScreen(
    onNavigateBack: () -> Unit,
    onCourseCreated: (String) -> Unit,
    viewModel: DiagnosticWizardViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val handleBack = {
        when (uiState.currentStep) {
            WizardStep.ENTER_TOPIC -> onNavigateBack()
            WizardStep.GENERATING_QUESTIONS, WizardStep.GENERATING_SYLLABUS -> viewModel.cancelCurrentGeneration()
            WizardStep.ANSWERING_QUESTIONS, WizardStep.REVIEW_SYLLABUS -> viewModel.stepBack()
        }
    }

    PlatformBackHandler(onBack = handleBack)

    Scaffold(containerColor = tokens.canvas) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            DesktopPageHeader(
                title = "Create Personalized Course",
                subtitle = "Engine: ${uiState.activeProviderName} • Adaptive Diagnostic & Curriculum Architect",
                onBack = handleBack,
                actions = {
                    DesktopWizardStepPills(currentStep = uiState.currentStep)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(modifier = Modifier.widthIn(max = 1080.dp).fillMaxSize()) {
                    when (uiState.currentStep) {
                        WizardStep.ENTER_TOPIC -> {
                            DesktopWizardTopicStep(
                                uiState = uiState,
                                onTopicChange = viewModel::setTopic,
                                onLevelChange = viewModel::setUserLevel,
                                onCustomInstructionsChange = viewModel::setCustomInstructions,
                                onToggleTemplate = viewModel::toggleCustomTemplate,
                                onGeneratePrompt = viewModel::generateCustomPromptFromTopic,
                                onStart = viewModel::startDiagnosticInterview
                            )
                        }

                        WizardStep.GENERATING_QUESTIONS -> {
                            DesktopWizardLoadingStep(
                                title = "Designing Diagnostic Interview...",
                                subtitle = "Analyzing \"${uiState.topic}\" (${uiState.userLevel.lowercase()}) via ${uiState.activeProviderName}",
                                onCancel = viewModel::cancelCurrentGeneration
                            )
                        }

                        WizardStep.ANSWERING_QUESTIONS -> {
                            DesktopWizardQuestionsStep(
                                questions = uiState.questions,
                                selectedAnswers = uiState.selectedAnswers,
                                customTextAnswers = uiState.customTextAnswers,
                                errorMessage = uiState.errorMessage,
                                onToggleOption = viewModel::toggleAnswerOption,
                                onCustomAnswer = viewModel::setCustomAnswer,
                                onBack = viewModel::stepBack,
                                onSubmit = viewModel::generateSyllabus
                            )
                        }

                        WizardStep.GENERATING_SYLLABUS -> {
                            DesktopWizardLoadingStep(
                                title = "Synthesizing Tailored Curriculum...",
                                subtitle = "Structuring prerequisite modules, lessons, and estimated reading times...",
                                onCancel = viewModel::cancelCurrentGeneration
                            )
                        }

                        WizardStep.REVIEW_SYLLABUS -> {
                            uiState.generatedSyllabus?.let { syllabus ->
                                DesktopWizardReviewStep(
                                    syllabus = syllabus,
                                    isBusy = uiState.isBusy,
                                    errorMessage = uiState.errorMessage,
                                    onBack = viewModel::stepBack,
                                    onAccept = { viewModel.saveAndStartCourse(onCourseCreated) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopWizardStepPills(currentStep: WizardStep) {
    val activeIdx = when (currentStep) {
        WizardStep.ENTER_TOPIC, WizardStep.GENERATING_QUESTIONS -> 0
        WizardStep.ANSWERING_QUESTIONS, WizardStep.GENERATING_SYLLABUS -> 1
        WizardStep.REVIEW_SYLLABUS -> 2
    }
    val labels = listOf("1. Topic & Persona", "2. Diagnostic", "3. Syllabus")
    val tokens = LocalDesktopTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { idx, label ->
            val color = if (idx <= activeIdx) tokens.accent else tokens.textMuted
            DesktopPillBadge(
                text = label,
                color = color,
                containerColor = if (idx == activeIdx) tokens.accentSoft else tokens.cardSubtle
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopWizardTopicStep(
    uiState: DiagnosticWizardUiState,
    onTopicChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onCustomInstructionsChange: (String) -> Unit,
    onToggleTemplate: (String) -> Unit,
    onGeneratePrompt: () -> Unit,
    onStart: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val sampleTopics = listOf(
        "eBPF Kernel Programming",
        "Nutritional Biochemistry",
        "Distributed Systems & Raft Consensus",
        "Deep Learning Transformer Architectures",
        "Quantum Computing & Qiskit"
    )
    val levelOptions = listOf(
        "BEGINNER" to "Beginner",
        "INTERMEDIATE" to "Intermediate",
        "ADVANCED" to "Advanced"
    )
    val templates = listOf(
        "Socratic Inquirer" to "Act as a rigorous Socratic mentor: prompt me with thought-provoking questions and challenge my assumptions rather than just presenting facts.",
        "Rigorous Derivations" to "Emphasize first-principles derivations, formal mathematical and theoretical proofs, and step-by-step logic.",
        "Intuitive Analogies" to "Explain complex concepts through intuitive, vivid real-world analogies before diving into technical details.",
        "Practical Engineering" to "Ground every concept in practical engineering trade-offs, architecture patterns, and production scenarios.",
        "3 Takeaways" to "Always conclude each lesson with 3 concise summary takeaways and bulleted key points.",
        "Self-Test Quiz" to "At the end of each chapter, provide 3 multiple-choice self-test questions with detailed answer explanations."
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Pane: Subject & Difficulty
        DesktopCard(
            modifier = Modifier.weight(1.05f),
            contentPadding = 22.dp
        ) {
            Text(
                text = "What do you want to master?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter any discipline, skill, or certification goal. AI will run a tailored diagnostic interview to design your syllabus.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "DIFFICULTY LEVEL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.accent
            )
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                levelOptions.forEachIndexed { index, (lvl, label) ->
                    val selected = uiState.userLevel.equals(lvl, ignoreCase = true)
                    SegmentedButton(
                        selected = selected,
                        onClick = { onLevelChange(lvl) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = levelOptions.size)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = uiState.topic,
                onValueChange = onTopicChange,
                label = { Text("Topic or Subject") },
                placeholder = { Text("e.g. Molecular Biology, Constitutional Law, Rust Concurrency") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                isError = uiState.errorMessage != null
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.danger
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "POPULAR DOMAINS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sampleTopics.forEach { sample ->
                    val active = uiState.topic.equals(sample, ignoreCase = true)
                    FilterChip(
                        selected = active,
                        onClick = { onTopicChange(sample) },
                        label = { Text(sample, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onStart,
                enabled = uiState.topic.isNotBlank() && !uiState.isBusy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Start Diagnostic Interview", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }

        // Right Pane: Custom System Prompt / Tutor Persona
        DesktopCard(
            modifier = Modifier.weight(0.95f),
            contentPadding = 22.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = tokens.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Tutor Persona (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "Customize teaching tone, depth, and examples for this course.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "QUICK TEMPLATES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.accent
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEach { (label, snippet) ->
                    val isSelected = uiState.customInstructions.contains(snippet, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleTemplate(snippet) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = uiState.customInstructions,
                onValueChange = onCustomInstructionsChange,
                label = { Text("Custom System Prompt") },
                placeholder = { Text("Optional mentorship rules, case study focus, mathematical depth...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 9,
                shape = RoundedCornerShape(10.dp)
            )

            if (uiState.promptGenerationError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.promptGenerationError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.danger
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onGeneratePrompt,
                enabled = !uiState.isGeneratingPrompt && !uiState.isBusy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isGeneratingPrompt) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating Persona Prompt...")
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Custom Prompt with AI")
                }
            }
        }
    }
}

@Composable
private fun DesktopWizardLoadingStep(
    title: String,
    subtitle: String,
    onCancel: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DesktopCard(
            modifier = Modifier.width(460.dp),
            contentPadding = 28.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, tokens.border)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopWizardQuestionsStep(
    questions: List<DiagnosticQuestion>,
    selectedAnswers: Map<String, List<String>>,
    customTextAnswers: Map<String, String>,
    errorMessage: String?,
    onToggleOption: (String, String, Boolean) -> Unit,
    onCustomAnswer: (String, String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            if (errorMessage != null) {
                item {
                    DesktopCard(borderColor = tokens.danger, contentPadding = 12.dp) {
                        Text(errorMessage, color = tokens.danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            itemsIndexed(questions, key = { index, q -> "${index}_${q.id}" }) { index, q ->
                val selected = selectedAnswers[q.id].orEmpty()
                val customText = customTextAnswers[q.id].orEmpty()
                DesktopCard(contentPadding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DesktopPillBadge(text = "Question ${index + 1} of ${questions.size}")
                        if (q.isMultiSelect) {
                            DesktopPillBadge(text = "Multi-Select", color = tokens.secondaryAccent)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = q.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                    if (q.rationale.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Goal: ${q.rationale}",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.accent
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        q.options.forEach { option ->
                            val isChecked = selected.contains(option)
                            Surface(
                                onClick = { onToggleOption(q.id, option, q.isMultiSelect) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isChecked) tokens.accentSoft else tokens.cardSubtle,
                                border = BorderStroke(1.dp, if (isChecked) tokens.accent else tokens.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isChecked) {
                                        Icon(Icons.Default.Check, null, tint = tokens.accent, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChecked) tokens.accent else tokens.textPrimary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { onCustomAnswer(q.id, it) },
                        placeholder = { Text("Optional personal context or specific goal...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(9.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = tokens.border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, tokens.border)
            ) {
                Text("Edit Topic")
            }
            Button(
                onClick = onSubmit,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Generate Tailored Curriculum", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DesktopWizardReviewStep(
    syllabus: SyllabusResponse,
    isBusy: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onAccept: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            if (errorMessage != null) {
                item {
                    DesktopCard(borderColor = tokens.danger, contentPadding = 12.dp) {
                        Text(errorMessage, color = tokens.danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                DesktopCard(borderColor = tokens.accent.copy(alpha = 0.45f), contentPadding = 22.dp) {
                    Text(
                        text = syllabus.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = syllabus.targetAudience,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = tokens.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${syllabus.estimatedTotalHours} Total Hours", style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = tokens.secondaryAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${syllabus.modules.size} Modules • ${syllabus.modules.sumOf { it.chapters.size }} Chapters",
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.textPrimary
                            )
                        }
                    }
                }
            }

            itemsIndexed(syllabus.modules) { mIdx, module ->
                DesktopCard(contentPadding = 18.dp) {
                    Text(
                        text = "Module ${mIdx + 1}: ${module.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    if (module.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(module.summary, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        module.chapters.forEachIndexed { cIdx, ch ->
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = tokens.cardSubtle,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${mIdx + 1}.${cIdx + 1}  ${ch.title}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.textPrimary
                                        )
                                        if (ch.summary.isNotBlank()) {
                                            Text(
                                                text = ch.summary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.textMuted
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${ch.estimatedMinutes} min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.accent,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = tokens.border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, tokens.border)
            ) {
                Text("Back to Interview")
            }
            Button(
                onClick = onAccept,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating Course...")
                } else {
                    Text("Create Course & Start Learning", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
