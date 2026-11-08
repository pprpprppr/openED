package org.opened.app.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import org.opened.app.core.ai.TutorPersonaRegistry
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.ai.DiagnosticQuestion
import org.opened.app.core.ai.SyllabusResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticWizardScreen(
    onNavigateBack: () -> Unit,
    onCourseCreated: (String) -> Unit,
    viewModel: DiagnosticWizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.currentStep) {
                            WizardStep.ENTER_TOPIC -> "Design Learning Path"
                            WizardStep.GENERATING_QUESTIONS -> "Analyzing Subject..."
                            WizardStep.ANSWERING_QUESTIONS -> "Diagnostic Interview"
                            WizardStep.GENERATING_SYLLABUS -> "Synthesizing Curriculum..."
                            WizardStep.REVIEW_SYLLABUS -> "Curriculum Preview"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep == WizardStep.ENTER_TOPIC) {
                            onNavigateBack()
                        } else {
                            viewModel.stepBack()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when (uiState.currentStep) {
                WizardStep.ENTER_TOPIC -> {
                    EnterTopicContent(
                        topic = uiState.topic,
                        userLevel = uiState.userLevel,
                        selectedTutorPersonaId = uiState.selectedTutorPersonaId,
                        customInstructions = uiState.customInstructions,
                        isCustomInstructionsExpanded = uiState.isCustomInstructionsExpanded,
                        isGeneratingPrompt = uiState.isGeneratingPrompt,
                        promptGenerationError = uiState.promptGenerationError,
                        errorMessage = uiState.errorMessage,
                        onTopicChange = { viewModel.setTopic(it) },
                        onLevelChange = { viewModel.setUserLevel(it) },
                        onPersonaChange = { viewModel.selectTutorPersona(it) },
                        onCustomInstructionsChange = { viewModel.setCustomInstructions(it) },
                        onToggleCustomInstructions = { viewModel.toggleCustomInstructions() },
                        onToggleTemplate = { viewModel.toggleCustomTemplate(it) },
                        onGeneratePrompt = { viewModel.generateCustomPromptFromTopic() },
                        onStart = { viewModel.startDiagnosticInterview() }
                    )
                }

                WizardStep.GENERATING_QUESTIONS -> {
                    LoadingStepIndicator(
                        title = "Generating Diagnostic Interview",
                        subtitle = "${uiState.activeProviderName} is generating 3-5 targeted assessment questions tailored to '${uiState.topic}'..."
                    )
                }

                WizardStep.ANSWERING_QUESTIONS -> {
                    AnsweringQuestionsContent(
                        questions = uiState.questions,
                        selectedAnswers = uiState.selectedAnswers,
                        customAnswers = uiState.customTextAnswers,
                        errorMessage = uiState.errorMessage,
                        providerName = uiState.activeProviderName,
                        onToggleOption = { qId, opt, isMulti ->
                            viewModel.toggleAnswerOption(qId, opt, isMulti)
                        },
                        onCustomAnswer = { qId, text ->
                            viewModel.setCustomAnswer(qId, text)
                        },
                        onSubmit = { viewModel.generateSyllabus() }
                    )
                }

                WizardStep.GENERATING_SYLLABUS -> {
                    LoadingStepIndicator(
                        title = "Structuring Your Curriculum",
                        subtitle = "Synthesizing your assessment profile, learning goals, and building modular chapters..."
                    )
                }

                WizardStep.REVIEW_SYLLABUS -> {
                    uiState.generatedSyllabus?.let { syllabus ->
                        ReviewSyllabusContent(
                            syllabus = syllabus,
                            isBusy = uiState.isBusy,
                            onAccept = {
                                viewModel.saveAndStartCourse(onSuccess = onCourseCreated)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnterTopicContent(
    topic: String,
    userLevel: String,
    selectedTutorPersonaId: String,
    customInstructions: String,
    isCustomInstructionsExpanded: Boolean,
    isGeneratingPrompt: Boolean,
    promptGenerationError: String?,
    errorMessage: String?,
    onTopicChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onPersonaChange: (String) -> Unit,
    onCustomInstructionsChange: (String) -> Unit,
    onToggleCustomInstructions: () -> Unit,
    onToggleTemplate: (String) -> Unit,
    onGeneratePrompt: () -> Unit,
    onStart: () -> Unit
) {
    val sampleTopics = listOf(
        "eBPF Kernel Programming",
        "Nutritional Biochemistry",
        "Distributed Systems & Raft Consensus",
        "Deep Learning Transformer Architectures",
        "Quantum Computing & Qiskit"
    )
    val activePersona = TutorPersonaRegistry.getById(selectedTutorPersonaId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "What do you want to master?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter any field, skill, or discipline. openED's AI will conduct a quick diagnostic interview to customize your curriculum.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Text(
            text = "DIFFICULTY LEVEL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        val levelOptions = listOf(
            "BEGINNER" to "🌱 Beginner\n(From Scratch)",
            "INTERMEDIATE" to "⚡ Intermediate\n(Practical)",
            "ADVANCED" to "🔬 Advanced\n(Deep Dive)"
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            levelOptions.forEachIndexed { index, (lvl, label) ->
                val selected = userLevel.equals(lvl, ignoreCase = true)
                SegmentedButton(
                    selected = selected,
                    onClick = { onLevelChange(lvl) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = levelOptions.size),
                    icon = { SegmentedButtonDefaults.Icon(active = selected) }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tutor Subject Persona Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TUTOR SUBJECT PERSONA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${activePersona.icon} ${activePersona.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val personas = TutorPersonaRegistry.ALL_PERSONAS.filter { it.id != "CUSTOM" }
            personas.forEach { persona ->
                val isSelected = persona.id.equals(selectedTutorPersonaId, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPersonaChange(persona.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = persona.icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = persona.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            label = { Text("Topic or Subject") },
            placeholder = { Text("e.g. Molecular Biology, French Revolution, Constitutional Law") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = errorMessage != null
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "OR CHOOSE A POPULAR DOMAIN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sampleTopics.forEach { sample ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTopicChange(sample) },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom Instructions Expandable Section
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleCustomInstructions() },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom System Instructions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (customInstructions.isNotBlank()) "Active (${customInstructions.length} chars)" else "Optional custom prompt rules",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isCustomInstructionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = isCustomInstructionsExpanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "QUICK TEMPLATES (TAP TO TOGGLE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val templates = listOf(
                        "✨ 3 Takeaways" to "Always conclude each lesson with 3 concise summary takeaways and bulleted key points.",
                        "❓ Self-Test Quiz" to "At the end of each chapter, provide 3 multiple-choice self-test questions with detailed answer explanations.",
                        "🔬 Case Studies" to "Ground every conceptual theory in an authentic real-world case study or empirical experiment.",
                        "⚡ Socratic Inquirer" to "Include thought-provoking Socratic reflection questions throughout to stimulate critical thinking."
                    )
                    templates.forEach { (label, snippet) ->
                        val isSelected = customInstructions.contains(snippet, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleTemplate(snippet) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customInstructions,
                    onValueChange = onCustomInstructionsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom Prompt Instructions") },
                    placeholder = { Text("e.g. Always structure lessons with real-world case studies and 3 self-test questions.") },
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                if (promptGenerationError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = promptGenerationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onGeneratePrompt,
                        enabled = !isGeneratingPrompt,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isGeneratingPrompt) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Architecting prompt...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (customInstructions.isBlank()) "✨ Generate from Topic" else "✨ Polish with AI")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStart,
            enabled = topic.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Begin Diagnostic Interview")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnsweringQuestionsContent(
    questions: List<DiagnosticQuestion>,
    selectedAnswers: Map<String, List<String>>,
    customAnswers: Map<String, String>,
    errorMessage: String?,
    providerName: String = "AI",
    onToggleOption: (String, String, Boolean) -> Unit,
    onCustomAnswer: (String, String) -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        item {
            Text(
                text = "Diagnostic Personalization",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Answer these assessment questions so $providerName can adapt technical depth, pacing, and real-world practical examples.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(questions, key = { it.id }) { q ->
            DiagnosticQuestionCard(
                question = q,
                selected = selectedAnswers[q.id].orEmpty(),
                customText = customAnswers[q.id].orEmpty(),
                onToggle = { opt -> onToggleOption(q.id, opt, q.isMultiSelect) },
                onCustomText = { text -> onCustomAnswer(q.id, text) }
            )
        }

        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.School, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Customized Curriculum")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticQuestionCard(
    question: DiagnosticQuestion,
    selected: List<String>,
    customText: String,
    onToggle: (String) -> Unit,
    onCustomText: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (question.rationale.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Goal: ${question.rationale}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                question.options.forEach { option ->
                    val isChecked = selected.contains(option)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggle(option) },
                        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = customText,
                onValueChange = onCustomText,
                placeholder = { Text("Add specific notes or goals for this question...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun ReviewSyllabusContent(
    syllabus: SyllabusResponse,
    isBusy: Boolean,
    onAccept: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = syllabus.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = syllabus.targetAudience,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${syllabus.estimatedTotalHours} Total Hours",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${syllabus.modules.size} Modules (${syllabus.modules.sumOf { it.chapters.size }} Chapters)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "COURSE MODULES & CHAPTERS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(syllabus.modules) { module ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = module.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))

                    module.chapters.forEachIndexed { idx, ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(24.dp)
                                )
                                Column {
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = ch.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = "${ch.estimatedMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onAccept,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving Course to Studio...")
                } else {
                    Icon(imageVector = Icons.Default.School, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept Curriculum & Begin Learning")
                }
            }
        }
    }
}

@Composable
private fun LoadingStepIndicator(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(54.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
