package org.opened.app.ui.laboratory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ArgumentNodeType(val label: String, val color: Color) {
    CONCLUSION("Conclusion / Thesis", Color(0xFF7C4DFF)),
    SUPPORTING_PREMISE("Supporting Premise", Color(0xFF00C853)),
    OBJECTION("Objection", Color(0xFFFF5252)),
    REBUTTAL("Rebuttal", Color(0xFFFFAB00))
}

data class ArgumentNode(
    val id: String,
    val type: ArgumentNodeType,
    val text: String,
    val normX: Float, // 0..1
    val normY: Float, // 0..1
    var weight: Float = 0.8f, // 0..1
    val evidenceQuality: String,
    val fallaciesChecked: String
)

@Composable
fun InteractiveLogicalArgumentMapCanvas(
    modifier: Modifier = Modifier
) {
    val initialNodes = remember {
        listOf(
            ArgumentNode("c1", ArgumentNodeType.CONCLUSION, "Open-source AI models accelerate scientific innovation.", 0.50f, 0.20f, 1.0f, "Central Thesis", "No formal fallacies detected"),
            ArgumentNode("p1", ArgumentNodeType.SUPPORTING_PREMISE, "Public model weights enable reproducible peer review.", 0.25f, 0.55f, 0.85f, "Peer-Reviewed Studies (Nature 2024)", "Valid inductive premise"),
            ArgumentNode("p2", ArgumentNodeType.SUPPORTING_PREMISE, "Decentralized fine-tuning reduces corporate monopolization.", 0.75f, 0.55f, 0.75f, "Market Anticompetitive Analysis", "Empirical economic evidence"),
            ArgumentNode("o1", ArgumentNodeType.OBJECTION, "Open weights risk unaligned downstream weaponization.", 0.35f, 0.85f, 0.60f, "National Security Whitepaper", "Valid risk objection"),
            ArgumentNode("r1", ArgumentNodeType.REBUTTAL, "Defensive fine-tuning & hardware governance mitigate misuse.", 0.70f, 0.85f, 0.70f, "Cybersecurity Defense Benchmark", "Targeted technical rebuttal")
        )
    }

    var nodes by remember { mutableStateOf(initialNodes) }
    var selectedNode by remember { mutableStateOf(nodes.first()) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Dynamic Epistemic Confidence Calculation:
    val sup1 = nodes.find { it.id == "p1" }?.weight ?: 0.8f
    val sup2 = nodes.find { it.id == "p2" }?.weight ?: 0.8f
    val obj1 = nodes.find { it.id == "o1" }?.weight ?: 0.6f
    val reb1 = nodes.find { it.id == "r1" }?.weight ?: 0.7f

    // Net strength formula
    val netSupport = (sup1 + sup2) / 2f
    val effectiveObjection = (obj1 * (1f - reb1 * 0.7f)).coerceAtLeast(0f)
    val conclusionValidity = (0.5f + (netSupport - effectiveObjection) * 0.5f).coerceIn(0.1f, 0.99f)

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "⚖️ Epistemic Argument & Logic Map",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dial premise weights to observe real-time Bayesian conclusion validity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                nodes = initialNodes
                selectedNode = nodes.first()
                zoomScale = 1.0f
                panX = 0f
                panY = 0f
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Argument", modifier = Modifier.size(18.dp))
            }
        }

        // Validity Meter Banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conclusion Net Confidence", style = MaterialTheme.typography.labelSmall)
                    Text("${(conclusionValidity * 100).toInt()}% (High Cogency)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = ArgumentNodeType.CONCLUSION.color)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { conclusionValidity },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = ArgumentNodeType.CONCLUSION.color
                )
            }
        }

        // Active Node Inspector
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedNode.type.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = selectedNode.type.color)
                    Text("Weight: ${(selectedNode.weight * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Text(selectedNode.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Grounding: ${selectedNode.evidenceQuality}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Fallacy Audit: ${selectedNode.fallaciesChecked}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Canvas Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF13141C)) // Dark logical canvas
                .clipToBounds()
                .semantics {
                    contentDescription = "Epistemic argument map. Conclusion validity ${(conclusionValidity * 100).toInt()}%. Selected node: ${selectedNode.text}."
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.7f, 2.0f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
                .pointerInput(nodes, zoomScale, panX, panY) {
                    detectTapGestures { tapOffset ->
                        val width = size.width
                        val height = size.height
                        for (node in nodes) {
                            val nx = (node.normX * width + panX) * zoomScale
                            val ny = (node.normY * height + panY) * zoomScale
                            val distSq = (tapOffset.x - nx) * (tapOffset.x - nx) + (tapOffset.y - ny) * (tapOffset.y - ny)
                            if (distSq < (60f * zoomScale) * (60f * zoomScale)) {
                                selectedNode = node
                                break
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                fun nodePos(node: ArgumentNode): Offset {
                    return Offset((node.normX * width + panX) * zoomScale, (node.normY * height + panY) * zoomScale)
                }

                val cPos = nodePos(nodes[0])
                val p1Pos = nodePos(nodes[1])
                val p2Pos = nodePos(nodes[2])
                val o1Pos = nodePos(nodes[3])
                val r1Pos = nodePos(nodes[4])

                // Supporting Lines (Green)
                drawLine(ArgumentNodeType.SUPPORTING_PREMISE.color.copy(alpha = sup1), p1Pos, cPos, strokeWidth = (3.dp * sup1).toPx())
                drawLine(ArgumentNodeType.SUPPORTING_PREMISE.color.copy(alpha = sup2), p2Pos, cPos, strokeWidth = (3.dp * sup2).toPx())

                // Objection Line to Conclusion (Red)
                drawLine(ArgumentNodeType.OBJECTION.color.copy(alpha = obj1), o1Pos, cPos, strokeWidth = (3.dp * obj1).toPx())

                // Rebuttal Line targeting Objection (Amber)
                drawLine(ArgumentNodeType.REBUTTAL.color.copy(alpha = reb1), r1Pos, o1Pos, strokeWidth = (2.5.dp * reb1).toPx())

                // Draw Nodes as rounded pill boxes
                for (node in nodes) {
                    val pos = nodePos(node)
                    val isSelected = selectedNode.id == node.id
                    val cardW = 110.dp.toPx() * zoomScale
                    val cardH = 34.dp.toPx() * zoomScale

                    val left = pos.x - cardW / 2f
                    val top = pos.y - cardH / 2f

                    if (isSelected) {
                        drawRoundRect(
                            color = node.type.color.copy(alpha = 0.35f),
                            topLeft = Offset(left - 4f, top - 4f),
                            size = Size(cardW + 8f, cardH + 8f),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }

                    drawRoundRect(
                        color = Color(0xFF222430),
                        topLeft = Offset(left, top),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )

                    drawRoundRect(
                        color = node.type.color,
                        topLeft = Offset(left, top),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx())
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = node.type.label.take(16),
                        topLeft = Offset(left + 8f, top + 8f),
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = node.type.color)
                    )
                }
            }
        }

        // Selected Premise Weight Slider
        if (selectedNode.type != ArgumentNodeType.CONCLUSION) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Adjust ${selectedNode.type.label} Credibility: ${(selectedNode.weight * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = selectedNode.weight,
                    onValueChange = { newWeight ->
                        nodes = nodes.map { if (it.id == selectedNode.id) it.copy(weight = newWeight) else it }
                        selectedNode = selectedNode.copy(weight = newWeight)
                    },
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
