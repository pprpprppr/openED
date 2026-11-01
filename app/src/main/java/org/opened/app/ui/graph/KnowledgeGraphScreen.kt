package org.opened.app.ui.graph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.core.database.model.ChapterStatus
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class GraphNode(
    val id: String,
    val title: String,
    val status: ChapterStatus,
    val pathId: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (pathId: String, chapterId: String) -> Unit,
    viewModel: KnowledgeGraphViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Graph") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.nodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BubbleChart,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No chapters yet.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Generate some course content to visualize your knowledge graph.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            ForceDirectedGraph(
                nodes = uiState.nodes,
                modifier = Modifier.fillMaxSize().padding(padding),
                onNodeTap = { node ->
                    onNavigateToChapter(node.pathId, node.chapterId)
                }
            )
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

    // Build mutable node positions with stable random seed
    val graphNodes = remember(nodes) {
        nodes.mapIndexed { i, n ->
            val angle = (i.toFloat() / nodes.size) * 2f * Math.PI.toFloat()
            val radius = 300f + Random(i.toLong()).nextFloat() * 100f
            GraphNode(
                id = n.chapterId,
                title = n.chapterTitle,
                status = n.status,
                pathId = n.pathId,
                x = 500f + radius * cos(angle),
                y = 500f + radius * sin(angle)
            )
        }.toMutableList()
    }

    val indexById = remember(graphNodes) { graphNodes.associateBy { it.id } }
    val adjacencyMap = remember(nodes) {
        nodes.associate { n -> n.chapterId to n.outgoingTargetIds }
    }

    // Run force simulation
    LaunchedEffect(nodes) {
        repeat(120) {
            forceSimulationStep(graphNodes, adjacencyMap, nodeRadius)
            delay(16L)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val completedColor = Color(0xFF2E7D32)
    val pendingColor = Color(0xFF1565C0)
    val generatingColor = Color(0xFFF57F17)
    val edgeColor = Color.Gray.copy(alpha = 0.35f)
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White)

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.3f, 4f)
                    offset += pan
                }
            }
            .pointerInput(graphNodes) {
                detectTapGestures { tapOffset ->
                    // Map tap to canvas coordinates
                    val canvasTap = (tapOffset - offset) / scale
                    val tapped = graphNodes.firstOrNull { node ->
                        hypot(canvasTap.x - node.x, canvasTap.y - node.y) <= nodeRadius + 8f
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
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            // Draw edges first
            for (node in graphNodes) {
                val targets = adjacencyMap[node.id] ?: continue
                for (targetId in targets) {
                    val target = indexById[targetId] ?: continue
                    drawLine(
                        color = edgeColor,
                        start = Offset(node.x, node.y),
                        end = Offset(target.x, target.y),
                        strokeWidth = 2f
                    )
                }
            }

            // Draw nodes
            for (node in graphNodes) {
                val color = when (node.status) {
                    ChapterStatus.COMPLETED -> completedColor
                    ChapterStatus.GENERATING -> generatingColor
                    ChapterStatus.PENDING -> pendingColor
                }

                drawCircle(
                    color = color.copy(alpha = 0.25f),
                    radius = nodeRadius + 6f,
                    center = Offset(node.x, node.y)
                )
                drawCircle(
                    color = color,
                    radius = nodeRadius,
                    center = Offset(node.x, node.y)
                )

                // Label inside node
                val label = node.title.take(14).let { if (node.title.length > 14) "$it…" else it }
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        node.x - measured.size.width / 2f,
                        node.y - measured.size.height / 2f
                    )
                )
            }
        }

        // Legend
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomStart)
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

private fun forceSimulationStep(
    nodes: MutableList<GraphNode>,
    adjacency: Map<String, List<String>>,
    nodeRadius: Float
) {
    val repulsion = 12000f
    val attraction = 0.04f
    val idealEdgeLen = nodeRadius * 5f
    val damping = 0.8f

    // Repulsion between all pairs
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val dx = nodes[j].x - nodes[i].x
            val dy = nodes[j].y - nodes[i].y
            val distSq = (dx * dx + dy * dy).coerceAtLeast(1f)
            val dist = sqrt(distSq)
            val force = repulsion / distSq
            val fx = force * dx / dist
            val fy = force * dy / dist
            nodes[i].vx -= fx; nodes[i].vy -= fy
            nodes[j].vx += fx; nodes[j].vy += fy
        }
    }

    // Attraction along edges
    val indexById = nodes.associateBy { it.id }
    for (node in nodes) {
        val targets = adjacency[node.id] ?: continue
        for (targetId in targets) {
            val target = indexById[targetId] ?: continue
            val dx = target.x - node.x
            val dy = target.y - node.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val force = attraction * (dist - idealEdgeLen)
            val fx = force * dx / dist
            val fy = force * dy / dist
            node.vx += fx; node.vy += fy
            target.vx -= fx; target.vy -= fy
        }
    }

    // Center gravity
    val cx = 500f; val cy = 500f
    for (node in nodes) {
        node.vx += (cx - node.x) * 0.002f
        node.vy += (cy - node.y) * 0.002f
        node.vx *= damping; node.vy *= damping
        node.x += node.vx; node.y += node.vy
    }
}
