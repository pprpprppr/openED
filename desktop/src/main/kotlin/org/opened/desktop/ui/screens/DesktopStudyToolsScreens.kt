package org.opened.desktop.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.ui.components.KaTeXRichTextView
import org.opened.app.ui.flashcards.FlashcardReviewViewModel
import org.opened.app.ui.graph.KnowledgeGraphViewModel
import org.opened.app.ui.laboratory.InteractiveBiologicalPathwayCanvas
import org.opened.app.ui.laboratory.InteractiveEconomicsMarketCanvas
import org.opened.app.ui.laboratory.InteractiveHistoricalTimelineCanvas
import org.opened.app.ui.laboratory.InteractiveLogicalArgumentMapCanvas
import org.opened.app.ui.laboratory.InteractiveMathPlotCanvas
import org.opened.app.ui.laboratory.InteractivePhysicsSimulationCanvas
import org.opened.app.ui.laboratory.LabCategory
import org.opened.app.ui.platform.openEdViewModel
import org.opened.desktop.ui.components.DesktopCard
import org.opened.desktop.ui.components.DesktopEmptyState
import org.opened.desktop.ui.components.DesktopPageHeader
import org.opened.desktop.ui.components.DesktopPillBadge
import org.opened.desktop.ui.theme.LocalDesktopTokens
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ========================
// 1. DESKTOP FLASHCARD STUDIO
// ========================

@Composable
fun DesktopFlashcardReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: FlashcardReviewViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
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
                title = "Flashcard Studio (Spaced Repetition)",
                subtitle = if (state.dueCards.isNotEmpty() && !state.isSessionComplete) {
                    "Card ${state.currentIndex + 1} of ${state.dueCards.size} due • Reviewed ${state.totalReviewed} today"
                } else {
                    "Adaptive FSRS / SM-2 Recall Engine"
                },
                actions = {
                    if (state.totalReviewed > 0 || state.isSessionComplete) {
                        OutlinedButton(
                            onClick = { viewModel.restartSession() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, tokens.border)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reload Queue")
                        }
                    }
                }
            )

            Box(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator()

                    state.dueCards.isEmpty() && !state.isSessionComplete -> {
                        DesktopEmptyState(
                            icon = Icons.Default.Style,
                            title = "All Caught Up!",
                            description = "No flashcards are due right now. Open any chapter inside a course and generate flashcards to expand your deck."
                        ) {
                            Button(onClick = onNavigateBack, shape = RoundedCornerShape(10.dp)) {
                                Text("Back to Courses")
                            }
                        }
                    }

                    state.isSessionComplete -> {
                        DesktopEmptyState(
                            icon = Icons.Default.CheckCircle,
                            title = "Session Complete",
                            description = "You reviewed ${state.totalReviewed} flashcards in this session. Intervals are updated automatically for optimal memory retention."
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.restartSession() },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, tokens.border)
                                ) {
                                    Text("Review Again")
                                }
                                Button(onClick = onNavigateBack, shape = RoundedCornerShape(10.dp)) {
                                    Text("Back to Courses")
                                }
                            }
                        }
                    }

                    else -> {
                        val card = state.dueCards.getOrNull(state.currentIndex) ?: return@Box
                        val progress = if (state.dueCards.isNotEmpty()) {
                            (state.currentIndex + 1).toFloat() / state.dueCards.size.toFloat()
                        } else 0f

                        Column(
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                                    color = tokens.accent,
                                    trackColor = tokens.cardSubtle
                                )
                                Text(
                                    text = "${state.currentIndex + 1} / ${state.dueCards.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                            }

                            DesktopFlipCard(
                                front = card.front,
                                back = card.back,
                                isFlipped = state.isFlipped,
                                onClick = { viewModel.flipCard() },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )

                            if (!state.isFlipped) {
                                Button(
                                    onClick = { viewModel.flipCard() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.width(240.dp).height(46.dp)
                                ) {
                                    Text("Reveal Answer", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Rate how easily you recalled this concept:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textMuted
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        DesktopSrsButton("Again (<1m)", tokens.danger, Modifier.weight(1f)) { viewModel.rateCard(0) }
                                        DesktopSrsButton("Hard (1d)", tokens.warning, Modifier.weight(1f)) { viewModel.rateCard(1) }
                                        DesktopSrsButton("Good (3d)", tokens.secondaryAccent, Modifier.weight(1f)) { viewModel.rateCard(2) }
                                        DesktopSrsButton("Easy (7d)", tokens.success, Modifier.weight(1f)) { viewModel.rateCard(3) }
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

@Composable
private fun DesktopFlipCard(
    front: String,
    back: String,
    isFlipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "pcCardFlip"
    )
    val isFront = rotation <= 90f

    DesktopCard(
        onClick = onClick,
        borderColor = if (isFront) tokens.accent.copy(alpha = 0.45f) else tokens.secondaryAccent.copy(alpha = 0.55f),
        contentPadding = 32.dp,
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { if (!isFront) it.graphicsLayer { rotationY = 180f } else it },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DesktopPillBadge(
                    text = if (isFront) "PROMPT / QUESTION" else "SOLUTION / ANSWER",
                    color = if (isFront) tokens.accent else tokens.secondaryAccent
                )
                KaTeXRichTextView(
                    text = if (isFront) front else back,
                    fontSize = 18.sp,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isFront) "Click card to flip to answer" else "Click card to flip back",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
    }
}

@Composable
private fun DesktopSrsButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ========================
// 2. DESKTOP KNOWLEDGE GRAPH
// ========================

private data class DesktopGraphNodePos(
    val node: ChapterLinkNode,
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f
)

private data class DesktopMeasuredLabel(
    val textLayout: TextLayoutResult,
    val pillWidth: Float,
    val pillHeight: Float
)

@Composable
fun DesktopKnowledgeGraphScreen(
    onNavigateToChapter: (pathId: String, chapterId: String) -> Unit,
    viewModel: KnowledgeGraphViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedNode by remember(uiState.selectedPathId, uiState.displayedNodes) {
        mutableStateOf<ChapterLinkNode?>(uiState.displayedNodes.firstOrNull())
    }
    var zoomScale by remember(uiState.selectedPathId) { mutableFloatStateOf(1f) }
    var panOffset by remember(uiState.selectedPathId) { mutableStateOf(Offset.Zero) }

    Scaffold(containerColor = tokens.canvas) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            DesktopPageHeader(
                title = "Knowledge Graph & Concept Map",
                subtitle = "${uiState.selectedCourseTitle} • ${uiState.displayedNodes.size} nodes",
                actions = {
                    OutlinedButton(
                        onClick = { zoomScale = (zoomScale / 1.2f).coerceIn(0.4f, 3f) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, tokens.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Remove, "Zoom out", modifier = Modifier.size(16.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, tokens.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, "Reset view", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset View", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { zoomScale = (zoomScale * 1.2f).coerceIn(0.4f, 3f) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, tokens.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, "Zoom in", modifier = Modifier.size(16.dp))
                    }
                }
            )

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Graph Canvas Stage
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        uiState.isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.displayedNodes.isEmpty() -> {
                            DesktopEmptyState(
                                icon = Icons.Default.BubbleChart,
                                title = "No Graph Nodes Available",
                                description = "Create or study a course with chapters and [[WikiLinks]] to visualize concept relationships here."
                            )
                        }
                        else -> {
                            DesktopForceDirectedCanvas(
                                nodes = uiState.displayedNodes,
                                selectedChapterId = selectedNode?.chapterId,
                                scale = zoomScale,
                                offset = panOffset,
                                onScaleOffsetChange = { newScale, newOffset ->
                                    zoomScale = newScale
                                    panOffset = newOffset
                                },
                                onSelectNode = { selectedNode = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Right Sidebar Inspector
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.border))
                Column(
                    modifier = Modifier
                        .width(316.dp)
                        .fillMaxHeight()
                        .background(tokens.sidebar)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "COURSE FILTER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.accent
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedPathId == null,
                            onClick = { viewModel.selectCourse(null) },
                            label = { Text("All (${uiState.allNodes.size})", fontSize = 11.sp) }
                        )
                        uiState.courses.forEach { course ->
                            FilterChip(
                                selected = uiState.selectedPathId == course.pathId,
                                onClick = { viewModel.selectCourse(course.pathId) },
                                label = {
                                    Text(
                                        course.topic.take(18),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = tokens.border)

                    // Status Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDot(tokens.success, "Completed")
                        LegendDot(tokens.accent, "Pending")
                        LegendDot(tokens.warning, "Generating")
                    }

                    HorizontalDivider(color = tokens.border)

                    // Selected Node Detail Card
                    selectedNode?.let { node ->
                        DesktopCard(contentPadding = 14.dp) {
                            DesktopPillBadge(
                                text = node.status.name,
                                color = when (node.status) {
                                    ChapterStatus.COMPLETED -> tokens.success
                                    ChapterStatus.GENERATING -> tokens.warning
                                    ChapterStatus.PENDING -> tokens.accent
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = node.chapterTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${node.outgoingTargetIds.size} outgoing concept link(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onNavigateToChapter(node.pathId, node.chapterId) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Text("Open Chapter", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    Text(
                        text = "CHAPTER NODES (${uiState.displayedNodes.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textMuted
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.displayedNodes, key = { it.chapterId }) { node ->
                            val isSelected = selectedNode?.chapterId == node.chapterId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedNode = node },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) tokens.accentSoft else tokens.card,
                                border = BorderStroke(1.dp, if (isSelected) tokens.accent else tokens.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (node.status) {
                                                    ChapterStatus.COMPLETED -> tokens.success
                                                    ChapterStatus.GENERATING -> tokens.warning
                                                    ChapterStatus.PENDING -> tokens.accent
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = node.chapterTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
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

@Composable
private fun LegendDot(color: Color, label: String) {
    val tokens = LocalDesktopTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    }
}

@Composable
private fun DesktopForceDirectedCanvas(
    nodes: List<ChapterLinkNode>,
    selectedChapterId: String?,
    scale: Float,
    offset: Offset,
    onScaleOffsetChange: (Float, Offset) -> Unit,
    onSelectNode: (ChapterLinkNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    val nodeRadius = 32f
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(tokens.isDark) {
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }

    val adjacencyMap = remember(nodes) { nodes.associate { it.chapterId to it.outgoingTargetIds } }
    val nodeIndexMap = remember(nodes) { nodes.mapIndexed { i, n -> n.chapterId to i }.toMap() }

    val measuredLabels = remember(nodes, textMeasurer, labelStyle) {
        nodes.associate { n ->
            val short = n.chapterTitle.take(20).let { if (n.chapterTitle.length > 20) "$it..." else it }
            val layout = textMeasurer.measure(short, labelStyle)
            n.chapterId to DesktopMeasuredLabel(
                textLayout = layout,
                pillWidth = layout.size.width + 16f,
                pillHeight = layout.size.height + 8f
            )
        }
    }

    val positions = remember(nodes) {
        mutableStateListOf<DesktopGraphNodePos>().also { list ->
            nodes.forEachIndexed { i, n ->
                val angle = (i.toFloat() / nodes.size.coerceAtLeast(1)) * 2f * Math.PI.toFloat()
                val radius = 240f + (i * 59 % 90).toFloat()
                list.add(DesktopGraphNodePos(n, 460f + radius * cos(angle), 360f + radius * sin(angle)))
            }
        }
    }

    LaunchedEffect(nodes) {
        withContext(Dispatchers.Default) {
            var current = positions.toList()
            for (batch in 0 until 4) {
                repeat(15) {
                    current = stepDesktopGraph(current, adjacencyMap, nodeIndexMap, nodeRadius)
                }
                withContext(Dispatchers.Main) {
                    current.forEachIndexed { idx, p ->
                        if (idx < positions.size) positions[idx] = p
                    }
                }
                delay(16L)
            }
        }
    }

    Box(
        modifier = modifier
            .background(tokens.canvas)
            .pointerInput(scale, offset) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onScaleOffsetChange((scale * zoom).coerceIn(0.4f, 3f), offset + pan)
                }
            }
            .pointerInput(nodes, scale, offset) {
                detectTapGestures { tap ->
                    val hit = positions.firstOrNull { pos ->
                        val sx = pos.x * scale + offset.x
                        val sy = pos.y * scale + offset.y
                        val dx = tap.x - sx
                        val dy = tap.y - sy
                        sqrt(dx * dx + dy * dy) <= nodeRadius * scale * 1.4f
                    }
                    if (hit != null) onSelectNode(hit.node)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val posById = positions.associateBy { it.node.chapterId }

            for (pos in positions) {
                val start = Offset(pos.x * scale + offset.x, pos.y * scale + offset.y)
                for (targetId in pos.node.outgoingTargetIds) {
                    val target = posById[targetId] ?: continue
                    val end = Offset(target.x * scale + offset.x, target.y * scale + offset.y)
                    drawLine(
                        color = tokens.borderStrong.copy(alpha = 0.7f),
                        start = start,
                        end = end,
                        strokeWidth = 2f
                    )
                }
            }

            for (pos in positions) {
                val cx = pos.x * scale + offset.x
                val cy = pos.y * scale + offset.y
                val center = Offset(cx, cy)
                val r = nodeRadius * scale
                val isSelected = pos.node.chapterId == selectedChapterId
                val nodeColor = when (pos.node.status) {
                    ChapterStatus.COMPLETED -> tokens.success
                    ChapterStatus.GENERATING -> tokens.warning
                    ChapterStatus.PENDING -> tokens.accent
                }

                if (isSelected) {
                    drawCircle(color = nodeColor.copy(alpha = 0.28f), radius = r * 1.45f, center = center)
                }
                drawCircle(color = nodeColor, radius = r, center = center)

                measuredLabels[pos.node.chapterId]?.let { lbl ->
                    val topLeft = Offset(cx - lbl.pillWidth / 2f, cy + r + 8f)
                    drawRoundRect(
                        color = Color(0xCC0B0F19),
                        topLeft = topLeft,
                        size = Size(lbl.pillWidth, lbl.pillHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawText(
                        textLayoutResult = lbl.textLayout,
                        topLeft = Offset(topLeft.x + 8f, topLeft.y + 4f)
                    )
                }
            }
        }
    }
}

private fun stepDesktopGraph(
    current: List<DesktopGraphNodePos>,
    adjacencyMap: Map<String, List<String>>,
    nodeIndexMap: Map<String, Int>,
    nodeRadius: Float
): List<DesktopGraphNodePos> {
    if (current.isEmpty()) return current
    val count = current.size
    val fx = FloatArray(count)
    val fy = FloatArray(count)

    for (i in 0 until count) {
        val a = current[i]
        for (j in i + 1 until count) {
            val b = current[j]
            val dx = a.x - b.x
            val dy = a.y - b.y
            val distSq = (dx * dx + dy * dy).coerceAtLeast(100f)
            val dist = sqrt(distSq)
            val force = 28000f / distSq
            val px = (dx / dist) * force
            val py = (dy / dist) * force
            fx[i] += px; fy[i] += py
            fx[j] -= px; fy[j] -= py
        }
    }

    for (i in 0 until count) {
        val a = current[i]
        for (targetId in adjacencyMap[a.node.chapterId].orEmpty()) {
            val j = nodeIndexMap[targetId] ?: continue
            val b = current[j]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dist = sqrt((dx * dx + dy * dy).coerceAtLeast(1f))
            val attraction = (dist - nodeRadius * 4.5f) * 0.045f
            val px = (dx / dist) * attraction
            val py = (dy / dist) * attraction
            fx[i] += px; fy[i] += py
            fx[j] -= px; fy[j] -= py
        }
    }

    return current.mapIndexed { idx, item ->
        val cx = (460f - item.x) * 0.014f
        val cy = (360f - item.y) * 0.014f
        val vx = (item.vx + fx[idx] + cx) * 0.72f
        val vy = (item.vy + fy[idx] + cy) * 0.72f
        item.copy(
            x = (item.x + vx).coerceIn(80f, 1100f),
            y = (item.y + vy).coerceIn(80f, 740f),
            vx = vx,
            vy = vy
        )
    }
}

// ========================
// 3. DESKTOP INTERACTIVE LAB
// ========================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DesktopInteractiveLabScreen() {
    val tokens = LocalDesktopTokens.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val categories = LabCategory.entries

    Scaffold(containerColor = tokens.canvas) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            DesktopPageHeader(
                title = "Interactive Knowledge Laboratory",
                subtitle = "Hands-on domain simulations across Mathematics, Physics, Economics, Biology, History & Logic"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.card,
                    border = BorderStroke(1.dp, tokens.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        edgePadding = 12.dp,
                        divider = {}
                    ) {
                        categories.forEachIndexed { index, cat ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = "${cat.icon} ${cat.label}",
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTabIndex == index) tokens.accent else tokens.textMuted
                                    )
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    DesktopCard(
                        modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
                        contentPadding = 22.dp
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            when (categories[selectedTabIndex]) {
                                LabCategory.MATH -> InteractiveMathPlotCanvas()
                                LabCategory.PHYSICS -> InteractivePhysicsSimulationCanvas()
                                LabCategory.ECONOMICS -> InteractiveEconomicsMarketCanvas()
                                LabCategory.BIOLOGY -> InteractiveBiologicalPathwayCanvas()
                                LabCategory.HISTORY -> InteractiveHistoricalTimelineCanvas()
                                LabCategory.LOGIC -> InteractiveLogicalArgumentMapCanvas()
                            }
                        }
                    }
                }
            }
        }
    }
}
