package org.opened.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.ui.platform.openEdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.core.database.model.ChapterStatus
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private data class GraphNode(
    val id: String,
    val title: String,
    val status: ChapterStatus,
    val pathId: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val vx: Float = 0f,
    val vy: Float = 0f
)

private data class MeasuredNodeLabel(
    val textLayout: androidx.compose.ui.text.TextLayoutResult,
    val pillWidth: Float,
    val pillHeight: Float,
    val pillPadV: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToChapter: (pathId: String, chapterId: String) -> Unit,
    isTab: Boolean = false,
    viewModel: KnowledgeGraphViewModel = openEdViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (!isTab) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Knowledge Graph",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.selectedCourseTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTab) PaddingValues(0.dp) else padding)
        ) {
            // Course Selector Chips at the top: Focuses on one course at a time to eliminate confusing mega-graph
            if (uiState.courses.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(uiState.courses, key = { it.pathId }) { course ->
                            val isSelected = uiState.selectedPathId == course.pathId
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectCourse(course.pathId) },
                                label = {
                                    Text(
                                        text = "${course.topic} (${course.chapterCount})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        // All Courses Chip (for bird's-eye global view)
                        item {
                            val isAllSelected = uiState.selectedPathId == null
                            FilterChip(
                                selected = isAllSelected,
                                onClick = { viewModel.selectCourse(null) },
                                label = {
                                    Text(
                                        text = "All Topics (${uiState.allNodes.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isAllSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Graph Canvas or Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.courses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.BubbleChart,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No courses created yet.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Create a study path first to visualize its interactive knowledge graph.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (uiState.displayedNodes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.BubbleChart,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No chapters in ${uiState.selectedCourseTitle} yet.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Generate chapters in this course to visualize its concept graph.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    ForceDirectedGraph(
                        nodes = uiState.displayedNodes,
                        modifier = Modifier.fillMaxSize(),
                        onNodeTap = { node ->
                            onNavigateToChapter(node.pathId, node.chapterId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ForceDirectedGraph(
    nodes: List<ChapterLinkNode>,
    modifier: Modifier = Modifier,
    onNodeTap: (ChapterLinkNode) -> Unit
) {
    val nodeRadius = 38f
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }

    val adjacencyMap = remember(nodes) {
        nodes.associate { n -> n.chapterId to n.outgoingTargetIds }
    }

    val nodeIndexMap = remember(nodes) {
        nodes.mapIndexed { i, n -> n.chapterId to i }.toMap()
    }

    // Pre-measure node text layouts once. NEVER measure text inside DrawScope/Canvas!
    val measuredLabels = remember(nodes, textMeasurer) {
        nodes.associate { n ->
            val label = n.chapterTitle.take(16).let { if (n.chapterTitle.length > 16) "$it…" else it }
            val measured = textMeasurer.measure(label, labelStyle)
            val pillPadH = 8f
            val pillPadV = 3f
            val pillWidth = measured.size.width + pillPadH * 2
            val pillHeight = measured.size.height + pillPadV * 2
            n.chapterId to MeasuredNodeLabel(
                textLayout = measured,
                pillWidth = pillWidth,
                pillHeight = pillHeight,
                pillPadV = pillPadV
            )
        }
    }

    // Snapshot list for positions — updates trigger Canvas redraws
    val positions: SnapshotStateList<GraphNode> = remember(nodes) {
        mutableStateListOf<GraphNode>().also { list ->
            nodes.mapIndexed { i, n ->
                val angle = (i.toFloat() / nodes.size.coerceAtLeast(1)) * 2f * Math.PI.toFloat()
                val radius = 300f + (i * 73L % 100).toFloat()
                list.add(
                    GraphNode(
                        id = n.chapterId,
                        title = n.chapterTitle,
                        status = n.status,
                        pathId = n.pathId,
                        x = 500f + radius * cos(angle),
                        y = 500f + radius * sin(angle)
                    )
                )
            }
        }
    }

    // Run force simulation efficiently on Dispatchers.Default in coalesced batches
    LaunchedEffect(nodes) {
        withContext(Dispatchers.Default) {
            var currentList = positions.toList()
            // 4 batches of 15 iterations (60 total) settles the graph cleanly with only 4 UI updates
            for (batch in 0 until 4) {
                repeat(15) {
                    currentList = forceSimulationStepImmutable(currentList, adjacencyMap, nodeIndexMap, nodeRadius)
                }
                withContext(Dispatchers.Main) {
                    currentList.forEachIndexed { i, node ->
                        if (i < positions.size) {
                            positions[i] = node
                        }
                    }
                }
                delay(16L)
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val completedColor = remember { Color(0xFF2E7D32) }
    val pendingColor = remember { Color(0xFF1565C0) }
    val generatingColor = remember { Color(0xFFF57F17) }
    val edgeColor = remember { Color.Gray.copy(alpha = 0.35f) }
    val pillBgColor = remember { Color(0xBB000000) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.3f, 4f)
                    offset += pan
                }
            }
            .pointerInput(positions) {
                detectTapGestures { tapOffset ->
                    val canvasTap = (tapOffset - offset) / scale
                    val tapped = positions.firstOrNull { node ->
                        hypot(canvasTap.x - node.x, canvasTap.y - node.y) <= nodeRadius + 12f
                    }
                    if (tapped != null) {
                        val linkNode = nodes.find { it.chapterId == tapped.id }
                        if (linkNode != null) onNodeTap(linkNode)
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Draw edges first (with directional arrowheads)
                for (node in positions) {
                    val targets = adjacencyMap[node.id] ?: continue
                    for (targetId in targets) {
                        val targetIdx = nodeIndexMap[targetId] ?: continue
                        val target = positions.getOrNull(targetIdx) ?: continue

                        val dx = target.x - node.x
                        val dy = target.y - node.y
                        val dist = hypot(dx, dy).coerceAtLeast(1f)
                        val ux = dx / dist
                        val uy = dy / dist

                        // Edge starts/ends at node perimeters
                        val startX = node.x + ux * nodeRadius
                        val startY = node.y + uy * nodeRadius
                        val endX = target.x - ux * (nodeRadius + 8f)
                        val endY = target.y - uy * (nodeRadius + 8f)

                        drawLine(
                            color = edgeColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.5f
                        )

                        // Arrowhead at target end
                        val arrowLen = 10f
                        val arrowAngle = 0.4f
                        val ax1 = endX - arrowLen * (ux * cos(arrowAngle) - uy * sin(arrowAngle))
                        val ay1 = endY - arrowLen * (uy * cos(arrowAngle) + ux * sin(arrowAngle))
                        val ax2 = endX - arrowLen * (ux * cos(-arrowAngle) - uy * sin(-arrowAngle))
                        val ay2 = endY - arrowLen * (uy * cos(-arrowAngle) + ux * sin(-arrowAngle))

                        drawLine(
                            color = edgeColor,
                            start = Offset(endX, endY),
                            end = Offset(ax1, ay1),
                            strokeWidth = 1.5f
                        )
                        drawLine(
                            color = edgeColor,
                            start = Offset(endX, endY),
                            end = Offset(ax2, ay2),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // Draw nodes with glow halo + pre-measured pill label
                for (node in positions) {
                    val nodeColor = when (node.status) {
                        ChapterStatus.COMPLETED -> completedColor
                        ChapterStatus.GENERATING -> generatingColor
                        ChapterStatus.PENDING -> pendingColor
                    }
                    val center = Offset(node.x, node.y)

                    // 1. Outer glow
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.18f),
                        radius = nodeRadius + 10f,
                        center = center
                    )
                    // 2. Mid ring
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.45f),
                        radius = nodeRadius + 4f,
                        center = center
                    )
                    // 3. Main circle
                    drawCircle(
                        color = nodeColor,
                        radius = nodeRadius,
                        center = center
                    )
                    // 4. White inner highlight dot
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = nodeRadius * 0.6f,
                        center = center
                    )

                    // 5. Translucent pill label below node (zero allocation, using pre-measured text layout)
                    val measuredInfo = measuredLabels[node.id]
                    if (measuredInfo != null) {
                        val pillTop = node.y + nodeRadius + 4f
                        drawRoundRect(
                            color = pillBgColor,
                            topLeft = Offset(node.x - measuredInfo.pillWidth / 2f, pillTop),
                            size = Size(measuredInfo.pillWidth, measuredInfo.pillHeight),
                            cornerRadius = CornerRadius(9f, 9f)
                        )
                        drawText(
                            textLayoutResult = measuredInfo.textLayout,
                            topLeft = Offset(
                                node.x - measuredInfo.textLayout.size.width / 2f,
                                pillTop + measuredInfo.pillPadV
                            )
                        )
                    }
                }
            }
        }

        // Legend — BottomEnd to avoid overlap with other UI elements
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            shadowElevation = 4.dp
        ) {
            Column(Modifier.padding(12.dp)) {
                LegendItem(color = Color(0xFF2E7D32), label = "Completed")
                Spacer(Modifier.height(4.dp))
                LegendItem(color = Color(0xFF1565C0), label = "Pending")
                Spacer(Modifier.height(4.dp))
                LegendItem(color = Color(0xFFF57F17), label = "Generating")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Immutable force-simulation step. Runs on a background thread and returns a new list
 * of [GraphNode] with updated positions and velocities. This replaces the old mutable
 * [forceSimulationStep] which is no longer used.
 */
private fun forceSimulationStepImmutable(
    nodes: List<GraphNode>,
    adjacency: Map<String, List<String>>,
    indexById: Map<String, Int>,
    nodeRadius: Float
): List<GraphNode> {
    val minRepulsionDistance = 320f
    val repulsion = 28000f
    val attraction = 0.035f
    val idealEdgeLen = 380f
    val damping = 0.82f
    val maxDisplace = 25f

    val vx = FloatArray(nodes.size)
    val vy = FloatArray(nodes.size)

    // Repulsion between all pairs with strict minimum repulsion distance of 320f
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val dx = nodes[j].x - nodes[i].x
            val dy = nodes[j].y - nodes[i].y
            val dist = hypot(dx, dy).coerceAtLeast(0.1f)
            val distSq = (dist * dist).coerceAtLeast(1f)
            val baseRepulsion = repulsion / distSq
            val collisionPush = if (dist < minRepulsionDistance) {
                (minRepulsionDistance - dist) * 0.5f
            } else {
                0f
            }
            val force = baseRepulsion + collisionPush
            val fx = force * dx / dist
            val fy = force * dy / dist
            vx[i] -= fx; vy[i] -= fy
            vx[j] += fx; vy[j] += fy
        }
    }

    // Attraction along edges
    for ((nodeIdx, node) in nodes.withIndex()) {
        val targets = adjacency[node.id] ?: continue
        for (targetId in targets) {
            val tIdx = indexById[targetId] ?: continue
            val target = nodes[tIdx]
            val dx = target.x - node.x
            val dy = target.y - node.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val force = attraction * (dist - idealEdgeLen)
            val fx = force * dx / dist
            val fy = force * dy / dist
            vx[nodeIdx] += fx; vy[nodeIdx] += fy
            vx[tIdx] -= fx; vy[tIdx] -= fy
        }
    }

    // Apply velocities + center gravity, return new immutable nodes
    return nodes.mapIndexed { i, node ->
        val newVx = ((node.vx + vx[i]) + (500f - node.x) * 0.0025f) * damping
        val newVy = ((node.vy + vy[i]) + (500f - node.y) * 0.0025f) * damping
        val clampedVx = newVx.coerceIn(-maxDisplace, maxDisplace)
        val clampedVy = newVy.coerceIn(-maxDisplace, maxDisplace)
        node.copy(
            vx = clampedVx,
            vy = clampedVy,
            x = node.x + clampedVx,
            y = node.y + clampedVy
        )
    }
}
