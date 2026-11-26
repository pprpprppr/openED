package org.opened.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.ui.components.RichMarkdownRenderer
import org.opened.app.ui.mocktest.MockTestListViewModel
import org.opened.app.ui.mocktest.MockTestResultViewModel
import org.opened.app.ui.mocktest.ResultFilter
import org.opened.app.ui.mocktest.TakeMockTestViewModel
import org.opened.app.ui.mocktest.TopicMasteryItem
import org.opened.app.ui.platform.PlatformBackHandler
import org.opened.app.ui.platform.openEdViewModel
import org.opened.desktop.ui.components.DesktopCard
import org.opened.desktop.ui.components.DesktopEmptyState
import org.opened.desktop.ui.components.DesktopPageHeader
import org.opened.desktop.ui.components.DesktopPillBadge
import org.opened.desktop.ui.components.formatRemainingClock
import org.opened.desktop.ui.components.sanitizeDisplayExamText
import org.opened.desktop.ui.theme.LocalDesktopTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DesktopTopicTestGroup(
    val topicName: String,
    val pathId: String?,
    val tests: List<MockTestEntity>
)

// ====================================
// 1. DESKTOP EXAM PREP & MOCK TEST HUB
// ====================================

@Composable
fun DesktopMockTestListScreen(
    pathId: String = "",
    onNavigateBack: (() -> Unit)? = null,
    onTakeTest: (String) -> Unit,
    onViewResult: (String) -> Unit,
    viewModel: MockTestListViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var testPendingDelete by remember { mutableStateOf<MockTestEntity?>(null) }

    LaunchedEffect(pathId) {
        viewModel.loadMockTests(pathId)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val completedTests = remember(uiState.allMockTests) {
        uiState.allMockTests.filter { it.status == MockTestStatus.COMPLETED }
    }
    val avgScore = remember(completedTests) {
        if (completedTests.isNotEmpty()) completedTests.map { it.score }.average().toInt() else 0
    }

    val collapsedTopics = remember { mutableStateMapOf<String, Boolean>() }
    val groupedMockTests = remember(uiState.mockTests, uiState.availableCourses) {
        val map = linkedMapOf<String, MutableList<MockTestEntity>>()
        val pathIdMap = mutableMapOf<String, String>()
        for (test in uiState.mockTests) {
            val matchedCourse = uiState.availableCourses.find { it.studyPath.id == test.pathId }
            val topicKey = matchedCourse?.studyPath?.topic?.ifBlank { null }
                ?: matchedCourse?.studyPath?.title?.ifBlank { null }
                ?: test.targetExamName.ifBlank { null }
                ?: "General Practice"
            map.getOrPut(topicKey) { mutableListOf() }.add(test)
            if (matchedCourse != null) {
                pathIdMap[topicKey] = matchedCourse.studyPath.id
            } else if (test.pathId.isNotBlank()) {
                pathIdMap[topicKey] = test.pathId
            }
        }
        map.map { (topic, tests) -> DesktopTopicTestGroup(topic, pathIdMap[topic], tests) }
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
                title = "Exam Prep & Mock Tests",
                subtitle = uiState.course?.studyPath?.title ?: "Entrance, Competitive, Certification & Topic Mastery Exams",
                onBack = onNavigateBack,
                actions = {
                    Button(
                        onClick = { viewModel.openCreateDialog() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Mock Exam")
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DesktopExamStatCard("Total Mock Exams", uiState.allMockTests.size.toString(), tokens.accent, Modifier.weight(1f))
                        DesktopExamStatCard("Completed", completedTests.size.toString(), tokens.success, Modifier.weight(1f))
                        DesktopExamStatCard("Average Score", if (completedTests.isNotEmpty()) "$avgScore%" else "--", tokens.secondaryAccent, Modifier.weight(1f))
                        DesktopExamStatCard("Topic Groups", groupedMockTests.size.toString(), tokens.warning, Modifier.weight(1f))
                    }
                }

                // Filters
                item {
                    DesktopCard(subtle = true, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("CATEGORY:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.accent)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(
                                            selected = uiState.selectedCategory == null,
                                            onClick = { viewModel.filterByCategory(null) },
                                            label = { Text("All (${uiState.allMockTests.size})") }
                                        )
                                    }
                                    items(ExamCategory.entries) { category ->
                                        FilterChip(
                                            selected = uiState.selectedCategory == category,
                                            onClick = { viewModel.filterByCategory(category) },
                                            label = { Text(category.displayName.split(" ")[0]) }
                                        )
                                    }
                                }
                            }

                            if (pathId.isBlank() && uiState.availableCourses.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("TOPIC / PATH:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            FilterChip(
                                                selected = uiState.selectedCourseFilter == null,
                                                onClick = { viewModel.filterByCourse(null) },
                                                label = { Text("All Courses") }
                                            )
                                        }
                                        items(uiState.availableCourses, key = { it.studyPath.id }) { course ->
                                            FilterChip(
                                                selected = uiState.selectedCourseFilter == course.studyPath.id,
                                                onClick = { viewModel.filterByCourse(course.studyPath.id) },
                                                label = { Text(course.studyPath.topic.ifBlank { course.studyPath.title }) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading && uiState.mockTests.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (groupedMockTests.isEmpty()) {
                    item {
                        DesktopEmptyState(
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            title = "No Mock Tests Found",
                            description = "Create a timed mock exam tailored to any certification, competitive standard, or enrolled study course."
                        ) {
                            Button(onClick = { viewModel.openCreateDialog() }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Mock Exam")
                            }
                        }
                    }
                } else {
                    groupedMockTests.forEach { group ->
                        val groupCompleted = group.tests.count { it.status == MockTestStatus.COMPLETED }
                        val groupAvg = if (groupCompleted > 0) {
                            group.tests.filter { it.status == MockTestStatus.COMPLETED }.map { it.score }.average().toInt()
                        } else 0
                        val isExpanded = collapsedTopics[group.topicName] != true

                        item(key = "grp_${group.topicName}") {
                            DesktopTopicHeader(
                                group = group,
                                completedTests = groupCompleted,
                                avgScore = groupAvg,
                                isExpanded = isExpanded,
                                onToggle = { collapsedTopics[group.topicName] = isExpanded },
                                onNewTest = { viewModel.openCreateDialog(preselectedCourseId = group.pathId) }
                            )
                        }

                        if (isExpanded) {
                            val rows = group.tests.chunked(2)
                            items(rows.size, key = { idx -> "row_${group.topicName}_$idx" }) { rowIndex ->
                                val rowItems = rows[rowIndex]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rowItems.forEach { test ->
                                        val course = uiState.availableCourses.find { it.studyPath.id == test.pathId }
                                        DesktopMockTestCard(
                                            test = test,
                                            courseName = course?.studyPath?.topic,
                                            onTakeTest = { onTakeTest(test.id) },
                                            onViewResult = { onViewResult(test.id) },
                                            onRegenerate = { viewModel.regenerateMockTestQuestions(test.id) },
                                            onDelete = { testPendingDelete = test },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showCreateDialog) {
        DesktopCreateMockTestDialog(
            availableCourses = uiState.availableCourses,
            initialCourseId = uiState.preselectedCourseId ?: uiState.pathId.ifBlank { uiState.selectedCourseFilter },
            isGeneratingPrompt = uiState.isGeneratingPrompt,
            generatedCustomPrompt = uiState.generatedCustomPrompt,
            promptGenerationError = uiState.promptGenerationError,
            onGeneratePrompt = viewModel::generateCustomPrompt,
            onClearGeneratedPrompt = viewModel::clearGeneratedPrompt,
            onDismiss = viewModel::dismissCreateDialog,
            onConfirm = viewModel::createMockTest
        )
    }

    if (uiState.isGenerating) {
        DesktopGenerationProgressDialog(
            progressText = uiState.generationProgressText,
            currentBatch = uiState.currentBatch,
            totalBatches = uiState.totalBatches,
            onCancel = viewModel::cancelGeneration
        )
    }

    testPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { testPendingDelete = null },
            title = { Text("Delete Mock Exam?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${pending.title}\" and all its saved answers and results?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMockTest(pending.id)
                        testPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.danger)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { testPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DesktopExamStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    DesktopCard(modifier = modifier, contentPadding = 14.dp) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun DesktopTopicHeader(
    group: DesktopTopicTestGroup,
    completedTests: Int,
    avgScore: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNewTest: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    DesktopCard(
        onClick = onToggle,
        subtle = true,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = tokens.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = group.topicName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "${group.tests.size} test(s) • $completedTests completed" +
                            if (completedTests > 0) " • Avg $avgScore%" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
            }
            FilledTonalButton(
                onClick = onNewTest,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Test", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DesktopMockTestCard(
    test: MockTestEntity,
    courseName: String?,
    onTakeTest: () -> Unit,
    onViewResult: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val statusColor = when (test.status) {
        MockTestStatus.COMPLETED -> tokens.success
        MockTestStatus.IN_PROGRESS -> tokens.warning
        MockTestStatus.NOT_STARTED -> tokens.accent
    }
    val statusLabel = when (test.status) {
        MockTestStatus.COMPLETED -> "Completed • ${test.score}%"
        MockTestStatus.IN_PROGRESS -> "In Progress (${test.totalAnswered}/${test.totalQuestions})"
        MockTestStatus.NOT_STARTED -> "Ready to Start"
    }

    DesktopCard(modifier = modifier, contentPadding = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopPillBadge(text = test.examCategory.displayName.split(" ")[0])
                DesktopPillBadge(text = statusLabel, color = statusColor)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = tokens.textMuted, modifier = Modifier.size(17.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = test.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${test.totalQuestions} Questions • ${test.timeLimitMinutes} mins" +
                if (!courseName.isNullOrBlank()) " • $courseName" else "" +
                " • ${sdf.format(Date(test.createdAt))}",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRegenerate,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, tokens.border),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Regen", style = MaterialTheme.typography.labelMedium)
            }

            if (test.status == MockTestStatus.COMPLETED) {
                FilledTonalButton(
                    onClick = onViewResult,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("View Score & Solutions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onTakeTest,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        if (test.status == MockTestStatus.IN_PROGRESS) "Resume Exam" else "Start Exam",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopCreateMockTestDialog(
    availableCourses: List<StudyPathWithModules>,
    initialCourseId: String?,
    isGeneratingPrompt: Boolean,
    generatedCustomPrompt: String?,
    promptGenerationError: String?,
    onGeneratePrompt: (topic: String, category: ExamCategory, targetExam: String, userNotes: String) -> Unit,
    onClearGeneratedPrompt: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        category: ExamCategory,
        targetExamName: String,
        questionCount: Int,
        timeLimitMinutes: Int,
        customTitle: String,
        targetPathId: String?,
        customCourseName: String,
        customPrompt: String
    ) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val initialCourse = remember(initialCourseId, availableCourses) {
        if (!initialCourseId.isNullOrBlank()) availableCourses.find { it.studyPath.id == initialCourseId }
        else availableCourses.firstOrNull()
    }

    var targetCourseText by remember { mutableStateOf(initialCourse?.studyPath?.topic.orEmpty()) }
    var selectedCourseId by remember { mutableStateOf(initialCourse?.studyPath?.id.orEmpty()) }
    var selectedCategory by remember { mutableStateOf(ExamCategory.ENTRANCE) }
    var targetExamName by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var customPrompt by remember { mutableStateOf("") }
    var questionCount by remember { mutableIntStateOf(10) }
    var isCustomCount by remember { mutableStateOf(false) }
    var customCountInput by remember { mutableStateOf("") }
    var timeLimitMinutes by remember { mutableIntStateOf(20) }

    LaunchedEffect(generatedCustomPrompt) {
        if (!generatedCustomPrompt.isNullOrBlank()) {
            customPrompt = generatedCustomPrompt
            onClearGeneratedPrompt()
        }
    }

    val quickExamRecs = remember(selectedCategory) {
        when (selectedCategory) {
            ExamCategory.ENTRANCE -> listOf("JEE Advanced", "NEET-UG", "GRE", "GATE CSE", "CAT", "MCAT")
            ExamCategory.COMPETITIVE -> listOf("UPSC Prelims Paper 1", "IBPS PO", "SSC CGL", "State PSC", "CDS")
            ExamCategory.CERTIFICATION -> listOf("AWS SAA-C03", "Google Cloud PCA", "Azure AZ-104", "CISSP", "CKA", "CompTIA Security+")
            ExamCategory.UNIVERSITY -> listOf("University Midterm", "Semester Final", "AP Exam", "College Honors")
            ExamCategory.GENERAL -> listOf("Full Course Assessment", "Topic Mastery Challenge")
        }
    }

    fun updateCount(c: Int) {
        questionCount = c
        timeLimitMinutes = (c * selectedCategory.defaultTimePerQuestionMinutes).toInt().coerceAtLeast(10)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.border),
            modifier = Modifier.widthIn(min = 560.dp, max = 760.dp).padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Configure & Generate Mock Exam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }

                // Target Course / Subject
                OutlinedTextField(
                    value = targetCourseText,
                    onValueChange = { input ->
                        targetCourseText = input
                        selectedCourseId = availableCourses.find { it.studyPath.topic.equals(input.trim(), ignoreCase = true) }?.studyPath?.id.orEmpty()
                    },
                    label = { Text("Target Course / Subject") },
                    placeholder = { Text("e.g. Quantum Computing, AWS Architecture, Organic Chemistry") },
                    leadingIcon = { Icon(Icons.Default.School, null) },
                    trailingIcon = {
                        if (targetCourseText.isNotEmpty()) {
                            IconButton(onClick = { targetCourseText = ""; selectedCourseId = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (availableCourses.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableCourses.forEach { course ->
                            val selected = selectedCourseId == course.studyPath.id || targetCourseText.equals(course.studyPath.topic, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCourseId = course.studyPath.id
                                    targetCourseText = course.studyPath.topic
                                },
                                label = { Text(course.studyPath.topic) },
                                leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp)) } } else null
                            )
                        }
                    }
                }

                // Exam Category
                Text("EXAM CATEGORY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.accent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExamCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                targetExamName = ""
                                timeLimitMinutes = (questionCount * cat.defaultTimePerQuestionMinutes).toInt().coerceAtLeast(10)
                            },
                            label = { Text(cat.displayName.split(" ")[0]) }
                        )
                    }
                }

                // Target Exam Standard
                OutlinedTextField(
                    value = targetExamName,
                    onValueChange = { targetExamName = it },
                    label = { Text("Target Exam Standard (Optional)") },
                    placeholder = { Text("e.g. JEE Advanced, AWS SAA-C03, UPSC...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickExamRecs.forEach { rec ->
                        FilterChip(selected = targetExamName == rec, onClick = { targetExamName = rec }, label = { Text(rec, fontSize = 11.sp) })
                    }
                }

                // Questions & Time Limit Side by Side
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("NUMBER OF QUESTIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15, 20, 30, 50).forEach { count ->
                                FilterChip(
                                    selected = !isCustomCount && questionCount == count,
                                    onClick = { isCustomCount = false; updateCount(count) },
                                    label = { Text("$count Qs") }
                                )
                            }
                            FilterChip(
                                selected = isCustomCount,
                                onClick = {
                                    isCustomCount = true
                                    if (customCountInput.isBlank()) customCountInput = questionCount.toString()
                                },
                                label = { Text("Custom") },
                                leadingIcon = if (isCustomCount) { { Icon(Icons.Default.Tune, null, modifier = Modifier.size(15.dp)) } } else null
                            )
                        }
                        if (isCustomCount) {
                            OutlinedTextField(
                                value = customCountInput,
                                onValueChange = { raw ->
                                    customCountInput = raw.filter { it.isDigit() }.take(3)
                                    customCountInput.toIntOrNull()?.let { if (it > 0) updateCount(it.coerceIn(1, 100)) }
                                },
                                label = { Text("Custom Count (1-100)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(0.7f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TIME LIMIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { timeLimitMinutes = (timeLimitMinutes - 5).coerceAtLeast(5) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(34.dp)
                            ) { Text("-5", fontWeight = FontWeight.Bold) }
                            Text("$timeLimitMinutes mins", fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                            OutlinedButton(
                                onClick = { timeLimitMinutes += 5 },
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(34.dp)
                            ) { Text("+5", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // Custom Prompt with AI Architect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CUSTOM PROMPT / FOCUS AREAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                    FilledTonalButton(
                        onClick = { onGeneratePrompt(targetCourseText.trim(), selectedCategory, targetExamName.trim(), customPrompt.trim()) },
                        enabled = !isGeneratingPrompt && targetCourseText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isGeneratingPrompt) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Architecting...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (customPrompt.isBlank()) "Generate from Topic" else "Polish with AI")
                        }
                    }
                }
                if (promptGenerationError != null) {
                    Text(promptGenerationError, style = MaterialTheme.typography.labelSmall, color = tokens.danger)
                }
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    placeholder = { Text("Optional focus areas, edge cases, difficulty balance...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                // Custom Title
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Custom Exam Title (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = tokens.border)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                selectedCategory,
                                targetExamName.trim(),
                                questionCount,
                                timeLimitMinutes,
                                customTitle.trim(),
                                selectedCourseId.ifBlank { null },
                                targetCourseText.trim(),
                                customPrompt.trim()
                            )
                        },
                        enabled = targetCourseText.isNotBlank() && questionCount > 0,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Generate Exam", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopGenerationProgressDialog(
    progressText: String,
    currentBatch: Int,
    totalBatches: Int,
    onCancel: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Dialog(onDismissRequest = {}) {
        DesktopCard(modifier = Modifier.width(420.dp), contentPadding = 24.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text("Generating Exam Questions...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                Text(progressText.ifBlank { "Synthesizing questions and explanations..." }, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                if (totalBatches > 0) {
                    LinearProgressIndicator(
                        progress = { currentBatch.toFloat() / totalBatches.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.danger)) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ====================================
// 2. DESKTOP CBT MOCK EXAM RUNNER
// ====================================

@Composable
fun DesktopTakeMockTestScreen(
    testId: String,
    onNavigateBack: () -> Unit,
    onTestSubmitted: (String) -> Unit,
    viewModel: TakeMockTestViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(testId) {
        viewModel.loadTest(testId)
    }

    val handleBack = {
        if (uiState.isGeneratingQuestions) {
            viewModel.cancelGeneration()
            onNavigateBack()
        } else {
            showExitDialog = true
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
                title = uiState.testWithQuestions?.test?.title ?: "Mock Examination",
                subtitle = if (uiState.totalQuestions > 0) {
                    "Question ${uiState.currentQuestionIndex + 1} of ${uiState.totalQuestions} • ${uiState.answeredCount} Answered • ${uiState.flaggedCount} Flagged"
                } else "Exam Runner",
                onBack = handleBack,
                actions = {
                    if (uiState.currentQuestion != null) {
                        val timerColor = if (uiState.remainingSeconds < 120) tokens.danger else tokens.accent
                        DesktopPillBadge(
                            text = "⏱ ${formatRemainingClock(uiState.remainingSeconds)}",
                            color = timerColor
                        )
                        Button(
                            onClick = { viewModel.showSubmitConfirmation() },
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("Submit Exam", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            val question = uiState.currentQuestion
            val allQuestions = uiState.testWithQuestions?.questions.orEmpty()

            when {
                uiState.isGeneratingQuestions -> {
                    DesktopGenerationStateBox(
                        title = "Generating Exam Questions...",
                        subtitle = uiState.generationProgressText.ifBlank { "Synthesizing questions and detailed solutions..." },
                        onCancel = { viewModel.cancelGeneration(); onNavigateBack() }
                    )
                }

                uiState.isLoading && question == null && uiState.errorMessage == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                question == null -> {
                    DesktopEmptyState(
                        icon = Icons.Default.AutoAwesome,
                        title = "Questions Not Generated Yet",
                        description = uiState.errorMessage ?: "Generate the question bank for this mock exam now.",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Button(onClick = { viewModel.generateMissingQuestions() }, shape = RoundedCornerShape(10.dp)) {
                            Text("Generate Exam Questions")
                        }
                    }
                }

                else -> {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Left Question Stage
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DesktopPillBadge(text = "Q${uiState.currentQuestionIndex + 1} / ${uiState.totalQuestions}")
                                        if (question.topic.isNotBlank()) {
                                            DesktopPillBadge(text = question.topic, color = tokens.secondaryAccent)
                                        }
                                        if (question.isMultiSelect) {
                                            DesktopPillBadge(text = "Multi-Select (Choose all that apply)", color = tokens.warning)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.toggleFlag() },
                                        shape = RoundedCornerShape(9.dp),
                                        border = BorderStroke(1.dp, if (question.isFlagged) tokens.warning else tokens.border),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (question.isFlagged) tokens.warning.copy(alpha = 0.14f) else Color.Transparent,
                                            contentColor = if (question.isFlagged) tokens.warning else tokens.textMuted
                                        )
                                    ) {
                                        Icon(Icons.Default.Flag, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(if (question.isFlagged) "Flagged" else "Flag for Review")
                                    }
                                }

                                DesktopCard(contentPadding = 22.dp) {
                                    RichMarkdownRenderer(
                                        markdown = sanitizeDisplayExamText(question.questionText),
                                        onChapterClick = {}
                                    )
                                }

                                val selectedIndices = question.effectiveSelectedIndices.toSet()
                                question.optionsList.forEachIndexed { idx, optText ->
                                    val isSelected = idx in selectedIndices
                                    val letter = ('A' + idx).toString()
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.selectOption(idx) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) tokens.accentSoft else tokens.card,
                                        border = BorderStroke(1.5.dp, if (isSelected) tokens.accent else tokens.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = if (question.isMultiSelect) RoundedCornerShape(6.dp) else CircleShape,
                                                color = if (isSelected) tokens.accent else tokens.cardSubtle,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = letter,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) Color.White else tokens.textPrimary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Box(modifier = Modifier.weight(1f)) {
                                                RichMarkdownRenderer(
                                                    markdown = sanitizeDisplayExamText(optText),
                                                    onChapterClick = {},
                                                    selectable = false,
                                                    isClickableCard = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = tokens.border)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.header)
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.prevQuestion() },
                                        enabled = uiState.currentQuestionIndex > 0,
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Previous")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.clearCurrentAnswer() },
                                        enabled = question.isAnswered,
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Text("Clear Answer")
                                    }
                                }

                                if (uiState.currentQuestionIndex < uiState.totalQuestions - 1) {
                                    Button(
                                        onClick = { viewModel.nextQuestion() },
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Text("Next Question")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.showSubmitConfirmation() },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.success)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Review & Submit Exam")
                                    }
                                }
                            }
                        }

                        // Right Persistent CBT Question Palette
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.border))
                        DesktopQuestionPaletteSidebar(
                            questions = allQuestions,
                            currentIndex = uiState.currentQuestionIndex,
                            onSelectQuestion = { viewModel.selectQuestion(it) },
                            onSubmit = { viewModel.showSubmitConfirmation() }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSubmitConfirmation() },
            title = { Text("Submit Mock Exam?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You answered ${uiState.answeredCount} of ${uiState.totalQuestions} questions (${uiState.flaggedCount} flagged). Submit now to grade your exam and view detailed solutions?")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitTest(onTestSubmitted) },
                    enabled = !uiState.isSubmitting
                ) {
                    Text(if (uiState.isSubmitting) "Submitting..." else "Submit Exam")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSubmitConfirmation() }) { Text("Continue Exam") }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Pause & Exit Exam?", fontWeight = FontWeight.Bold) },
            text = { Text("Your progress and selected answers are saved. You can resume this mock test anytime.") },
            confirmButton = {
                Button(onClick = { showExitDialog = false; onNavigateBack() }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Stay") }
            }
        )
    }
}

@Composable
private fun DesktopGenerationStateBox(title: String, subtitle: String, onCancel: () -> Unit) {
    val tokens = LocalDesktopTokens.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DesktopCard(modifier = Modifier.width(440.dp), contentPadding = 26.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun DesktopQuestionPaletteSidebar(
    questions: List<MockTestQuestionEntity>,
    currentIndex: Int,
    onSelectQuestion: (Int) -> Unit,
    onSubmit: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Column(
        modifier = Modifier
            .width(284.dp)
            .fillMaxHeight()
            .background(tokens.sidebar)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("QUESTION NAVIGATOR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.accent)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendChip("Answered", tokens.success)
            LegendChip("Flagged", tokens.warning)
            LegendChip("Current", tokens.accent)
        }

        HorizontalDivider(color = tokens.border)

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                val isCurrent = idx == currentIndex
                val bgColor = when {
                    isCurrent -> tokens.accent
                    q.isFlagged -> tokens.warning.copy(alpha = 0.22f)
                    q.isAnswered -> tokens.success.copy(alpha = 0.22f)
                    else -> tokens.card
                }
                val fgColor = when {
                    isCurrent -> Color.White
                    q.isFlagged -> tokens.warning
                    q.isAnswered -> tokens.success
                    else -> tokens.textPrimary
                }
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectQuestion(idx) },
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, if (isCurrent) tokens.accent else tokens.border)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = fgColor)
                    }
                }
            }
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Finish & Submit", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    val tokens = LocalDesktopTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = tokens.textMuted)
    }
}

// ====================================
// 3. DESKTOP MOCK TEST RESULTS
// ====================================

@Composable
fun DesktopMockTestResultScreen(
    testId: String,
    onNavigateBack: () -> Unit,
    onRetakeTest: (String) -> Unit,
    viewModel: MockTestResultViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(testId) {
        viewModel.loadResult(testId)
    }

    val test = uiState.testWithQuestions?.test

    Scaffold(containerColor = tokens.canvas) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            DesktopPageHeader(
                title = "Exam Score & Solution Analysis",
                subtitle = test?.title.orEmpty(),
                onBack = onNavigateBack,
                actions = {
                    if (test != null) {
                        Button(
                            onClick = { viewModel.retakeTest(onRetakeTest) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Exam")
                        }
                    }
                }
            )

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                test == null -> {
                    DesktopEmptyState(
                        icon = Icons.Default.Cancel,
                        title = "Results Not Found",
                        description = "Unable to load results for this mock test."
                    ) {
                        Button(onClick = onNavigateBack) { Text("Back") }
                    }
                }
                else -> {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Left Summary & Topic Breakdown Sidebar
                        Column(
                            modifier = Modifier
                                .width(340.dp)
                                .fillMaxHeight()
                                .background(tokens.sidebar)
                                .verticalScroll(rememberScrollState())
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DesktopCard(
                                borderColor = (if (test.score >= 70) tokens.success else tokens.warning).copy(alpha = 0.5f),
                                contentPadding = 20.dp
                            ) {
                                Text("FINAL SCORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${test.score}%",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (test.score >= 70) tokens.success else tokens.warning
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Correct: ${test.totalCorrect}/${test.totalQuestions}", style = MaterialTheme.typography.labelMedium, color = tokens.success)
                                    Text("Time: ${test.timeSpentSeconds / 60}m ${test.timeSpentSeconds % 60}s", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
                                }
                            }

                            DesktopCard(contentPadding = 16.dp) {
                                Text(
                                    text = "TOPIC MASTERY BREAKDOWN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.accent
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    uiState.topicMasteryList.forEach { item ->
                                        DesktopTopicMasteryRow(item)
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.border))

                        // Right Solution Breakdown
                        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ResultFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = uiState.selectedFilter == filter,
                                        onClick = { viewModel.setFilter(filter) },
                                        label = { Text(filter.label) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(uiState.filteredQuestions, key = { it.id }) { q ->
                                    DesktopResultQuestionCard(q)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopTopicMasteryRow(item: TopicMasteryItem) {
    val tokens = LocalDesktopTokens.current
    val pct = item.accuracyPercentage
    val color = when {
        pct >= 75 -> tokens.success
        pct >= 50 -> tokens.warning
        else -> tokens.danger
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = item.topic,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${item.correctCount}/${item.totalCount} ($pct%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = tokens.cardSubtle
        )
    }
}

@Composable
private fun DesktopResultQuestionCard(question: MockTestQuestionEntity) {
    val tokens = LocalDesktopTokens.current
    val isCorrect = question.isCorrect
    val badgeColor = if (isCorrect) tokens.success else tokens.danger
    val selectedSet = question.effectiveSelectedIndices.toSet()
    val correctSet = question.effectiveCorrectIndices.toSet()

    DesktopCard(
        borderColor = badgeColor.copy(alpha = 0.4f),
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopPillBadge(
                    text = if (isCorrect) "Q${question.questionIndex + 1} • Correct" else "Q${question.questionIndex + 1} • Incorrect",
                    color = badgeColor
                )
                if (question.topic.isNotBlank()) {
                    DesktopPillBadge(text = question.topic)
                }
            }
            if (question.isFlagged) {
                DesktopPillBadge(text = "Flagged", color = tokens.warning)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        RichMarkdownRenderer(markdown = sanitizeDisplayExamText(question.questionText), onChapterClick = {})
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            question.optionsList.forEachIndexed { idx, opt ->
                val picked = idx in selectedSet
                val valid = idx in correctSet
                val optBorder = when {
                    valid -> tokens.success
                    picked && !valid -> tokens.danger
                    else -> tokens.border
                }
                val optBg = when {
                    valid -> tokens.success.copy(alpha = 0.12f)
                    picked && !valid -> tokens.danger.copy(alpha = 0.12f)
                    else -> tokens.cardSubtle
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = optBg,
                    border = BorderStroke(1.dp, optBorder)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${'A' + idx}. ",
                            fontWeight = FontWeight.Bold,
                            color = when {
                                valid -> tokens.success
                                picked -> tokens.danger
                                else -> tokens.textMuted
                            }
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            RichMarkdownRenderer(
                                markdown = sanitizeDisplayExamText(opt),
                                onChapterClick = {},
                                selectable = false,
                                isClickableCard = true
                            )
                        }
                    }
                }
            }
        }

        if (question.explanation.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            DesktopCard(subtle = true, contentPadding = 12.dp) {
                Text("EXPLANATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.accent)
                Spacer(modifier = Modifier.height(4.dp))
                RichMarkdownRenderer(markdown = sanitizeDisplayExamText(question.explanation), onChapterClick = {})
            }
        }
    }
}
