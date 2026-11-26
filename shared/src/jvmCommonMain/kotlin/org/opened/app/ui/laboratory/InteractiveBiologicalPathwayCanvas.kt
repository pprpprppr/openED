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
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

data class PathwayNode(
    val id: String,
    val name: String,
    val compartment: String,
    val x: Float, // normalized 0..1
    val y: Float, // normalized 0..1
    val atpYield: String,
    val description: String,
    val color: Color
)

@Composable
fun InteractiveBiologicalPathwayCanvas(
    modifier: Modifier = Modifier
) {
    val nodes = remember {
        listOf(
            PathwayNode("glyc", "Glycolysis", "Cytosol", 0.15f, 0.35f, "+2 ATP, +2 NADH", "Glucose is split into two 3-carbon pyruvate molecules without requiring oxygen.", Color(0xFF4CAF50)),
            PathwayNode("pyrv", "Pyruvate Oxidation", "Mitochondrial Matrix", 0.40f, 0.35f, "+2 NADH, +2 CO₂", "Pyruvate is decarboxylated and linked to Coenzyme A forming Acetyl-CoA.", Color(0xFF00BCD4)),
            PathwayNode("krebs", "Citric Acid (Krebs) Cycle", "Mitochondrial Matrix", 0.65f, 0.65f, "+2 ATP, +6 NADH, +2 FADH₂", "Acetyl-CoA oxidizes completely, generating high-energy electron carriers.", Color(0xFFFF9800)),
            PathwayNode("etc", "Oxidative Phosphorylation", "Inner Membrane", 0.85f, 0.25f, "~28 ATP, H₂O", "Electron transport chain generates a proton gradient driving ATP Synthase.", Color(0xFFE91E63))
        )
    }

    var selectedNode by remember { mutableStateOf(nodes.first()) }
    var inhibitorActive by remember { mutableStateOf(false) } // e.g. Cyanide/Oligomycin
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

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
                    text = "🧬 Cellular Bioenergetics & Respiration Pathway",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap nodes to inspect ATP yields, electron flux, and organelle boundaries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { zoomScale = (zoomScale * 1.2f).coerceAtMost(2.5f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { zoomScale = (zoomScale * 0.8f).coerceAtLeast(0.6f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    zoomScale = 1.0f
                    panX = 0f
                    panY = 0f
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Regulation / Inhibitor Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = inhibitorActive,
                onClick = { inhibitorActive = !inhibitorActive },
                label = { Text(if (inhibitorActive) "⚠️ ETC Inhibitor Active (Cyanide)" else "Enzyme Kinetics: Normal Flux", fontSize = 11.sp) }
            )
        }

        // Active Node Inspector Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedNode.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = selectedNode.color)
                    Text("Yield: ${selectedNode.atpYield}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                Text("Compartment: ${selectedNode.compartment}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(selectedNode.description, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A24)) // Dark biology slate
                .clipToBounds()
                .semantics {
                    contentDescription = "Interactive Cellular Respiration Pathway. Selected stage: ${selectedNode.name} in ${selectedNode.compartment}. Yield: ${selectedNode.atpYield}."
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.6f, 2.5f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
                .pointerInput(nodes, zoomScale, panX, panY) {
                    detectTapGestures { tapOffset ->
                        val width = size.width
                        val height = size.height
                        for (node in nodes) {
                            val nx = (node.x * width + panX) * zoomScale
                            val ny = (node.y * height + panY) * zoomScale
                            val distSq = (tapOffset.x - nx) * (tapOffset.x - nx) + (tapOffset.y - ny) * (tapOffset.y - ny)
                            if (distSq < (50f * zoomScale) * (50f * zoomScale)) {
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

                // Draw Mitochondrial Membrane boundary (soft rounded background container)
                val mitoLeft = (0.32f * width + panX) * zoomScale
                val mitoTop = (0.15f * height + panY) * zoomScale
                val mitoWidth = (0.64f * width) * zoomScale
                val mitoHeight = (0.75f * height) * zoomScale

                drawRoundRect(
                    color = Color(0xFF263238).copy(alpha = 0.7f),
                    topLeft = Offset(mitoLeft, mitoTop),
                    size = Size(mitoWidth, mitoHeight),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "Mitochondrion (Matrix & Inner Membrane)",
                    topLeft = Offset(mitoLeft + 12f, mitoTop + 8f),
                    style = TextStyle(fontSize = 9.sp, color = Color.LightGray.copy(alpha = 0.6f))
                )

                // Metabolic Flux Connecting Curves (Glycolysis -> Pyruvate -> Krebs -> ETC)
                fun nodeCenter(node: PathwayNode): Offset {
                    return Offset((node.x * width + panX) * zoomScale, (node.y * height + panY) * zoomScale)
                }

                val p1 = nodeCenter(nodes[0]) // Glycolysis
                val p2 = nodeCenter(nodes[1]) // Pyruvate Ox
                val p3 = nodeCenter(nodes[2]) // Krebs
                val p4 = nodeCenter(nodes[3]) // ETC

                val fluxColor = if (inhibitorActive) Color(0xFFFF5252) else Color(0xFF64B5F6)

                // Curve 1: Glycolysis -> Pyruvate
                drawLine(fluxColor, p1, p2, strokeWidth = 3.dp.toPx())

                // Curve 2: Pyruvate -> Krebs (Cubic curve)
                val path2 = Path().apply {
                    moveTo(p2.x, p2.y)
                    cubicTo(p2.x + 30f, p2.y + 60f, p3.x - 40f, p3.y - 40f, p3.x, p3.y)
                }
                drawPath(path2, fluxColor, style = Stroke(width = 3.dp.toPx()))

                // Curve 3: Krebs -> ETC (High-energy NADH/FADH2 transfer)
                val path3 = Path().apply {
                    moveTo(p3.x, p3.y)
                    cubicTo(p3.x + 40f, p3.y - 60f, p4.x - 30f, p4.y + 60f, p4.x, p4.y)
                }
                drawPath(path3, if (inhibitorActive) Color.Red else Color(0xFFFFD54F), style = Stroke(width = if (inhibitorActive) 1.5.dp.toPx() else 4.dp.toPx()))

                // Draw Nodes
                for (node in nodes) {
                    val center = nodeCenter(node)
                    val isSelected = selectedNode.id == node.id
                    val radius = if (isSelected) 26.dp.toPx() * zoomScale else 20.dp.toPx() * zoomScale

                    // Selection glow halo
                    if (isSelected) {
                        drawCircle(color = node.color.copy(alpha = 0.35f), radius = radius + 8.dp.toPx(), center = center)
                    }

                    drawCircle(color = node.color, radius = radius, center = center)
                    drawCircle(color = Color.White, radius = radius * 0.35f, center = center)

                    // Node Title text below
                    drawText(
                        textMeasurer = textMeasurer,
                        text = node.name,
                        topLeft = Offset(center.x - 35f, center.y + radius + 4f),
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
            }
        }
    }
}
