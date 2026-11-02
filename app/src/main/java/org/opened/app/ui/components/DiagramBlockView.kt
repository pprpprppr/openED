package org.opened.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private data class ParsedDiagramNode(
    val id: String,
    val label: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 130f,
    var height: Float = 56f,
    var layer: Int = 0,
    var textLayout: TextLayoutResult? = null
) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

private data class ParsedDiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String? = null
)

private data class DiagramLayoutResult(
    val nodes: List<ParsedDiagramNode>,
    val edges: List<ParsedDiagramEdge>,
    val graphWidth: Float,
    val graphHeight: Float
)

private fun cleanNodeLabel(raw: String): String {
    var text = raw.trim()
    if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
        if (text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
        }
    }
    text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
    text = text.replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    return text.trim()
}

@Composable
fun DiagramBlockView(
    diagramCode: String,
    modifier: Modifier = Modifier,
    isComplete: Boolean = true
) {
    if (!isComplete || diagramCode.isBlank()) {
        RenderingPlaceholderCard(
            message = "Rendering diagram...",
            modifier = modifier
        )
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    val labelStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFF1F5F9)
    )

    val parsedData = remember(diagramCode, textMeasurer) {
        runCatching {
            layoutDiagram(diagramCode, textMeasurer, labelStyle)
        }.getOrElse {
            DiagramLayoutResult(emptyList(), emptyList(), 0f, 0f)
        }
    }

    val nodes = parsedData.nodes
    val edges = parsedData.edges
    val graphWidth = parsedData.graphWidth
    val graphHeight = parsedData.graphHeight

    if (nodes.isEmpty()) {
        RenderingPlaceholderCard(
            message = "Rendering diagram...",
            modifier = modifier
        )
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = "Architecture Diagram",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONCEPT ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Area with interactive controls and gesture isolation
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
                    .background(Color(0xFF0F172A))
            ) {
                val density = LocalDensity.current
                val canvasWidth = with(density) { maxWidth.toPx() }
                val canvasHeight = with(density) { maxHeight.toPx() }

                val autoScale = remember(parsedData, canvasWidth, canvasHeight) {
                    if (graphWidth > 0f && graphHeight > 0f && canvasWidth > 0f && canvasHeight > 0f) {
                        minOf(canvasWidth / graphWidth, canvasHeight / graphHeight, 1.0f).coerceIn(0.45f, 1.0f)
                    } else {
                        1.0f
                    }
                }

                val defaultPanOffset = remember(parsedData, canvasWidth, autoScale) {
                    val panX = ((canvasWidth - graphWidth * autoScale) / 2f).coerceAtLeast(0f)
                    Offset(panX, 16f)
                }

                var zoomScale by remember(diagramCode, autoScale) { mutableFloatStateOf(autoScale) }
                var panOffset by remember(diagramCode, defaultPanOffset) { mutableStateOf(defaultPanOffset) }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(diagramCode) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var isPinchingOrPanning = false
                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val isMultiTouch = event.changes.size > 1

                                        if (isMultiTouch || zoomChange != 1f) {
                                            isPinchingOrPanning = true
                                        } else if (!isPinchingOrPanning && event.changes.size == 1) {
                                            if (kotlin.math.abs(panChange.x) > kotlin.math.abs(panChange.y) * 1.5f && kotlin.math.abs(panChange.x) > 6f) {
                                                isPinchingOrPanning = true
                                            }
                                        }

                                        if (isPinchingOrPanning) {
                                            zoomScale = (zoomScale * zoomChange).coerceIn(0.4f, 3.5f)
                                            panOffset += panChange
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .clipToBounds()
                    ) {
                        if (nodes.isEmpty()) return@Canvas

                        withTransform({
                            translate(panOffset.x, panOffset.y)
                            scale(zoomScale, zoomScale, pivot = Offset.Zero)
                        }) {
                            // Draw lightweight coordinate dot grid in world space
                            val dotSpacing = 48f
                            var gx = 0f
                            while (gx <= graphWidth) {
                                var gy = 0f
                                while (gy <= graphHeight) {
                                    drawCircle(gridColor, radius = 1.2f, center = Offset(gx, gy))
                                    gy += dotSpacing
                                }
                                gx += dotSpacing
                            }

                            val nodeMap = nodes.associateBy { it.id }

                            // Draw Edges with Arrows
                            for (edge in edges) {
                                val fromNode = nodeMap[edge.fromId] ?: continue
                                val toNode = nodeMap[edge.toId] ?: continue

                                val fromCenter = Offset(fromNode.centerX, fromNode.centerY)
                                val toCenter = Offset(toNode.centerX, toNode.centerY)

                                val angle = atan2(toCenter.y - fromCenter.y, toCenter.x - fromCenter.x)
                                val halfW = toNode.width / 2f
                                val halfH = toNode.height / 2f
                                val arrowTarget = Offset(
                                    toCenter.x - cos(angle) * (halfW + 4f),
                                    toCenter.y - sin(angle) * (halfH + 4f)
                                )

                                drawLine(
                                    color = edgeColor,
                                    start = fromCenter,
                                    end = arrowTarget,
                                    strokeWidth = 2.5f
                                )

                                val arrowLen = 10f
                                val arrowAngle = Math.PI / 6.0
                                val p1 = Offset(
                                    (arrowTarget.x - arrowLen * cos(angle - arrowAngle)).toFloat(),
                                    (arrowTarget.y - arrowLen * sin(angle - arrowAngle)).toFloat()
                                )
                                val p2 = Offset(
                                    (arrowTarget.x - arrowLen * cos(angle + arrowAngle)).toFloat(),
                                    (arrowTarget.y - arrowLen * sin(angle + arrowAngle)).toFloat()
                                )

                                val arrowPath = Path().apply {
                                    moveTo(arrowTarget.x, arrowTarget.y)
                                    lineTo(p1.x, p1.y)
                                    lineTo(p2.x, p2.y)
                                    close()
                                }
                                drawPath(arrowPath, color = edgeColor)

                                if (!edge.label.isNullOrBlank()) {
                                    val midX = (fromCenter.x + arrowTarget.x) / 2f
                                    val midY = (fromCenter.y + arrowTarget.y) / 2f
                                    val cleanEdgeLabel = cleanNodeLabel(edge.label)
                                    val labelResult = textMeasurer.measure(
                                        text = cleanEdgeLabel,
                                        style = TextStyle(fontSize = 11.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.SemiBold)
                                    )
                                    drawRoundRect(
                                        color = Color(0xDD1E293B),
                                        topLeft = Offset(midX - labelResult.size.width / 2f - 4f, midY - labelResult.size.height / 2f - 2f),
                                        size = Size(labelResult.size.width + 8f, labelResult.size.height + 4f),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                    drawText(
                                        textLayoutResult = labelResult,
                                        topLeft = Offset(midX - labelResult.size.width / 2f, midY - labelResult.size.height / 2f)
                                    )
                                }
                            }

                            // Draw Nodes
                            for (node in nodes) {
                                val topLeft = Offset(node.x, node.y)
                                val nodeSize = Size(node.width, node.height)

                                drawRoundRect(
                                    color = Color(0xFF1E293B),
                                    topLeft = topLeft,
                                    size = nodeSize,
                                    cornerRadius = CornerRadius(10f, 10f)
                                )

                                drawRoundRect(
                                    color = primaryColor.copy(alpha = 0.85f),
                                    topLeft = topLeft,
                                    size = nodeSize,
                                    cornerRadius = CornerRadius(10f, 10f),
                                    style = Stroke(width = 2f)
                                )

                                // Draw measured & stored TextLayoutResult centered inside box
                                node.textLayout?.let { layout ->
                                    val textX = node.centerX - layout.size.width / 2f
                                    val textY = node.centerY - layout.size.height / 2f
                                    drawText(
                                        textLayoutResult = layout,
                                        topLeft = Offset(textX, textY)
                                    )
                                }
                            }
                        }
                    }

                    // Top-right pill buttons for +, -, and Reset
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xEE1E293B),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.4f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
                            }

                            TextButton(
                                onClick = {
                                    zoomScale = autoScale
                                    panOffset = defaultPanOffset
                                },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall, color = primaryColor, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(3.5f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun layoutDiagram(
    code: String,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle
): DiagramLayoutResult = runCatching {
    val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val nodesMap = mutableMapOf<String, ParsedDiagramNode>()
    val edges = mutableListOf<ParsedDiagramEdge>()

    val arrowRegex = Regex("""([A-Za-z0-9_-]+)(?:\[(.*?)\])?\s*-->\s*(?:\|(.*?)\|\s*)?([A-Za-z0-9_-]+)(?:\[(.*?)\])?""")
    val singleNodeRegex = Regex("""^([A-Za-z0-9_-]+)\[(.*?)\]$""")

    for (line in lines) {
        val arrowMatch = arrowRegex.find(line)
        if (arrowMatch != null) {
            val fromId = arrowMatch.groupValues[1]
            val fromLabel = arrowMatch.groupValues[2].ifBlank { fromId }
            val edgeLabel = arrowMatch.groupValues[3].ifBlank { null }
            val toId = arrowMatch.groupValues[4]
            val toLabel = arrowMatch.groupValues[5].ifBlank { toId }

            if (!nodesMap.containsKey(fromId)) {
                nodesMap[fromId] = ParsedDiagramNode(fromId, fromLabel)
            } else if (arrowMatch.groupValues[2].isNotBlank()) {
                nodesMap[fromId] = nodesMap[fromId]!!.copy(label = fromLabel)
            }

            if (!nodesMap.containsKey(toId)) {
                nodesMap[toId] = ParsedDiagramNode(toId, toLabel)
            } else if (arrowMatch.groupValues[5].isNotBlank()) {
                nodesMap[toId] = nodesMap[toId]!!.copy(label = toLabel)
            }

            edges.add(ParsedDiagramEdge(fromId, toId, edgeLabel))
            continue
        }

        val singleMatch = singleNodeRegex.find(line)
        if (singleMatch != null) {
            val id = singleMatch.groupValues[1]
            val label = singleMatch.groupValues[2]
            nodesMap[id] = ParsedDiagramNode(id, label)
        }
    }

    val nodes = nodesMap.values.toList()
    if (nodes.isEmpty()) return@runCatching DiagramLayoutResult(emptyList(), emptyList(), 0f, 0f)

    // 1. Clean labels and measure every node's label text with Constraints(maxWidth = 340)
    for (node in nodes) {
        val cleanLabel = cleanNodeLabel(node.label)
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(cleanLabel),
            style = labelStyle,
            constraints = Constraints(maxWidth = 340)
        )
        node.textLayout = textLayout
        node.width = (textLayout.size.width + 44f).coerceAtLeast(130f)
        node.height = (textLayout.size.height + 36f).coerceAtLeast(56f)
    }

    // 2. Compute node layers (BFS / topological levels)
    val inDegree = mutableMapOf<String, Int>()
    nodes.forEach { inDegree[it.id] = 0 }
    edges.forEach { edge ->
        inDegree[edge.toId] = (inDegree[edge.toId] ?: 0) + 1
    }

    val queue = ArrayDeque<Pair<String, Int>>()
    inDegree.forEach { (id, deg) ->
        if (deg == 0) queue.add(Pair(id, 0))
    }

    if (queue.isEmpty() && nodes.isNotEmpty()) {
        queue.add(Pair(nodes.first().id, 0))
    }

    val nodeLayers = mutableMapOf<String, Int>()
    while (queue.isNotEmpty()) {
        val (currId, layer) = queue.removeFirst()
        if (nodeLayers.containsKey(currId)) continue
        nodeLayers[currId] = layer

        for (edge in edges.filter { it.fromId == currId }) {
            if (!nodeLayers.containsKey(edge.toId)) {
                queue.add(Pair(edge.toId, layer + 1))
            }
        }
    }

    nodes.forEach { node ->
        node.layer = nodeLayers[node.id] ?: 0
    }

    // 3. Strict hierarchical layout with sibling spacing
    val levelsMap = nodes.groupBy { it.layer }.toSortedMap()

    levelsMap.forEach { (layer: Int, levelNodes: List<ParsedDiagramNode>) ->
        val maxBoxHeightInLevel = levelNodes.maxOfOrNull { it.height } ?: 56f
        val levelY = layer * (maxBoxHeightInLevel + 90f)

        var prevCenterX = 0f
        var prevBoxWidth = 0f
        val centerXs = mutableListOf<Float>()

        levelNodes.forEachIndexed { idx, node ->
            val currCenterX = if (idx == 0) {
                0f
            } else {
                prevCenterX + (prevBoxWidth / 2f) + (node.width / 2f) + 64f
            }
            centerXs.add(currCenterX)
            prevCenterX = currCenterX
            prevBoxWidth = node.width
        }

        val firstNode = levelNodes.firstOrNull()
        val lastNode = levelNodes.lastOrNull()
        val levelLeft = (centerXs.firstOrNull() ?: 0f) - (firstNode?.width ?: 0f) / 2f
        val levelRight = (centerXs.lastOrNull() ?: 0f) + (lastNode?.width ?: 0f) / 2f
        val levelWidth = (levelRight - levelLeft).coerceAtLeast(1f)
        val shiftX = -levelLeft - (levelWidth / 2f)

        levelNodes.forEachIndexed { idx, node ->
            val cx = centerXs[idx] + shiftX
            node.x = cx - node.width / 2f
            node.y = levelY
        }
    }

    // 4. Normalize coordinates so all nodes sit strictly in positive space (x >= 48f, y >= 48f)
    val minX = nodes.minOf { it.x }
    val minY = nodes.minOf { it.y }
    val shiftNormX = -minX + 48f
    val shiftNormY = -minY + 48f
    nodes.forEach { node ->
        node.x += shiftNormX
        node.y += shiftNormY
    }

    val maxX = nodes.maxOf { it.x + it.width }
    val maxY = nodes.maxOf { it.y + it.height }
    val graphWidth = maxX + 48f
    val graphHeight = maxY + 48f

    DiagramLayoutResult(nodes, edges.toList(), graphWidth, graphHeight)
}.getOrElse { DiagramLayoutResult(emptyList(), emptyList(), 0f, 0f) }
