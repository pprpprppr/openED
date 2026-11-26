package org.opened.app.ui.laboratory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

enum class EventCategory(val label: String, val color: Color) {
    DIPLOMATIC("Diplomatic", Color(0xFF29B6F6)),
    CONFLICT("Conflict & Geopolitics", Color(0xFFEF5350)),
    SCIENTIFIC("Scientific Discovery", Color(0xFFAB47BC)),
    CULTURAL("Philosophy & Law", Color(0xFFFFA726))
}

data class HistoricalMilestone(
    val id: String,
    val year: Int,
    val title: String,
    val category: EventCategory,
    val cause: String,
    val significance: String,
    val primaryQuote: String
)

@Composable
fun InteractiveHistoricalTimelineCanvas(
    modifier: Modifier = Modifier
) {
    val milestones = remember {
        listOf(
            HistoricalMilestone("m1", 1648, "Peace of Westphalia", EventCategory.DIPLOMATIC, "Culmination of the Thirty Years' War in Central Europe.", "Established the legal doctrine of national sovereignty and non-interference in domestic affairs.", "\"Cuius regio, eius religio — Sovereignty shall reside in territorial states.\""),
            HistoricalMilestone("m2", 1687, "Newton's Principia", EventCategory.SCIENTIFIC, "Mathematical synthesis of Kepler's planetary laws and Galileo's mechanics.", "Founded classical physics and universal gravitation, inaugurating the Scientific Enlightenment.", "\"Hypotheses non fingo — I frame no hypotheses; nature is governed by mathematical laws.\""),
            HistoricalMilestone("m3", 1789, "French Revolution & Rights of Man", EventCategory.CULTURAL, "Fiscal insolvency of the Ancien Régime and popular Enlightenment ideals.", "Abolished absolute monarchy and established universal legal equality and democratic constitutionalism.", "\"Men are born and remain free and equal in rights.\""),
            HistoricalMilestone("m4", 1815, "Congress of Vienna", EventCategory.DIPLOMATIC, "Fall of the Napoleonic Empire in Europe.", "Engineered the 'Concert of Europe' balance of power framework preserving continent-wide stability.", "\"Reconstructing the equilibrium of European powers.\""),
            HistoricalMilestone("m5", 1914, "Outbreak of World War I", EventCategory.CONFLICT, "Interlocking alliance treaties, imperial rivalries, and the July Crisis in Sarajevo.", "Dissolved four major empires (Ottoman, Austro-Hungarian, Russian, German) and reshaped global borders.", "\"The lamps are going out all over Europe; we shall not see them lit again in our lifetime.\""),
            HistoricalMilestone("m6", 1945, "Founding of the United Nations", EventCategory.DIPLOMATIC, "Catastrophic global devastation of World War II and the dawn of the nuclear era.", "Created the multilateral collective security framework and Universal Declaration of Human Rights.", "\"To save succeeding generations from the scourge of war.\""),
            HistoricalMilestone("m7", 1989, "Fall of the Berlin Wall", EventCategory.DIPLOMATIC, "Decline of Soviet hegemony and civic resistance in Eastern Europe.", "Ended the Cold War, reunified Germany, and integrated European political and monetary architecture.", "\"Mr. Gorbachev, tear down this wall!\"")
        )
    }

    var selectedMilestone by remember { mutableStateOf(milestones[2]) }
    var zoomScale by remember { mutableFloatStateOf(1.8f) } // pixels per year
    var panOffsetX by remember { mutableFloatStateOf(-150f) }

    val startYear = 1600
    val endYear = 2025
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
                    text = "⏳ Interactive Historical & Geopolitical Timeline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pan and zoom across centuries; tap markers to inspect primary sources",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(6.0f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { zoomScale = (zoomScale * 0.7f).coerceAtLeast(0.6f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    zoomScale = 1.8f
                    panOffsetX = -150f
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Timeline", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Selected Milestone Detail Card
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
                    Text(
                        text = "${selectedMilestone.year}: ${selectedMilestone.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = selectedMilestone.category.color
                    )
                    Text(
                        text = selectedMilestone.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Significance: ${selectedMilestone.significance}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(selectedMilestone.primaryQuote, style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Timeline Ribbon Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clipToBounds()
                .semantics {
                    contentDescription = "Historical timeline ribbon. Selected: ${selectedMilestone.year} ${selectedMilestone.title}. Significance: ${selectedMilestone.significance}."
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.6f, 6.0f)
                        panOffsetX += pan.x
                    }
                }
                .pointerInput(milestones, zoomScale, panOffsetX) {
                    detectTapGestures { tapOffset ->
                        val originX = 50f + panOffsetX
                        val axisY = size.height * 0.55f
                        for (m in milestones) {
                            val mx = originX + (m.year - startYear) * zoomScale
                            if (abs(tapOffset.x - mx) < 35f && abs(tapOffset.y - axisY) < 45f) {
                                selectedMilestone = m
                                break
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height
                val axisY = height * 0.55f
                val originX = 50f + panOffsetX

                // Main Timeline Horizontal Ribbon Axis
                drawLine(Color.Gray.copy(alpha = 0.6f), Offset(0f, axisY), Offset(width, axisY), strokeWidth = 3.dp.toPx())

                // Century / Decade Tick Marks
                val tickStep = if (zoomScale > 3.0f) 25 else 50
                for (yr in startYear..endYear step tickStep) {
                    val tx = originX + (yr - startYear) * zoomScale
                    if (tx in -50f..(width + 50f)) {
                        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(tx, axisY - 10f), Offset(tx, axisY + 10f), strokeWidth = 1.dp.toPx())
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "$yr",
                            topLeft = Offset(tx - 15f, axisY + 14f),
                            style = TextStyle(fontSize = 10.sp, color = Color.Gray)
                        )
                    }
                }

                // Milestone Nodes along timeline
                milestones.forEach { m ->
                    val mx = originX + (m.year - startYear) * zoomScale
                    if (mx in -40f..(width + 40f)) {
                        val isSelected = selectedMilestone.id == m.id
                        val nodeRadius = if (isSelected) 10.dp.toPx() else 7.dp.toPx()

                        // Stagger top/bottom connector
                        val isTop = (m.year % 2 == 0)
                        val tagY = if (isTop) axisY - 45f else axisY + 35f

                        // Stem line
                        drawLine(m.category.color.copy(alpha = 0.8f), Offset(mx, axisY), Offset(mx, tagY), strokeWidth = 1.5.dp.toPx())

                        // Dot marker on axis
                        drawCircle(color = m.category.color, radius = nodeRadius, center = Offset(mx, axisY))
                        drawCircle(color = Color.White, radius = nodeRadius * 0.4f, center = Offset(mx, axisY))

                        // Year Badge Text near marker
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${m.year}",
                            topLeft = Offset(mx - 15f, if (isTop) tagY - 18f else tagY + 4f),
                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = m.category.color)
                        )
                    }
                }
            }
        }
    }
}
