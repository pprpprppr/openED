package org.opened.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private sealed class ParsedChartData {
    data class VectorPlot(val vectors: List<PlottedVector>) : ParsedChartData()
    data class BarChart(val bars: List<PlottedBar>) : ParsedChartData()
}

private data class PlottedVector(
    val x: Float,
    val y: Float,
    val label: String,
    val color: Color
)

private data class PlottedBar(
    val label: String,
    val value: Float,
    val unit: String
)

@Composable
fun ChartBlockView(
    chartCode: String,
    modifier: Modifier = Modifier,
    isComplete: Boolean = true
) {
    if (!isComplete || chartCode.isBlank()) {
        RenderingPlaceholderCard(
            message = "Rendering chart...",
            modifier = modifier
        )
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary

    val chartData = remember(chartCode) {
        runCatching {
            parseChartCode(chartCode)
        }.getOrElse {
            ParsedChartData.BarChart(emptyList())
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (chartData) {
                is ParsedChartData.VectorPlot -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Vector Plot",
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2D VECTOR / COORDINATE PLOT",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            if (chartData.vectors.isEmpty()) return@Canvas
                            val w = size.width
                            val h = size.height
                            val centerX = w / 2f
                            val centerY = h / 2f

                            // Find bounds
                            val maxVal = max(
                                3f,
                                chartData.vectors.maxOfOrNull { max(kotlin.math.abs(it.x), kotlin.math.abs(it.y)) } ?: 3f
                            )
                            val scale = (minOf(w, h) * 0.4f) / maxVal.coerceAtLeast(1f)

                            // Draw Grid Lines
                            val gridColor = Color(0x2294A3B8)
                            val step = 1
                            for (i in -maxVal.toInt()..maxVal.toInt()) {
                                if (i == 0) continue
                                val gx = centerX + i * scale
                                val gy = centerY - i * scale
                                drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                            }

                            // Draw X & Y Axes
                            val axisColor = Color(0xFF64748B)
                            drawLine(axisColor, Offset(16f, centerY), Offset(w - 16f, centerY), strokeWidth = 2f)
                            drawLine(axisColor, Offset(centerX, h - 16f), Offset(centerX, 16f), strokeWidth = 2f)

                            // Axis Labels
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "X",
                                topLeft = Offset(w - 24f, centerY + 4f),
                                style = TextStyle(fontSize = 11.sp, color = axisColor, fontWeight = FontWeight.Bold)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "Y",
                                topLeft = Offset(centerX + 6f, 8f),
                                style = TextStyle(fontSize = 11.sp, color = axisColor, fontWeight = FontWeight.Bold)
                            )

                            // Draw Vectors
                            for (vec in chartData.vectors) {
                                val targetX = centerX + vec.x * scale
                                val targetY = centerY - vec.y * scale

                                // Arrow stem
                                drawLine(
                                    color = vec.color,
                                    start = Offset(centerX, centerY),
                                    end = Offset(targetX, targetY),
                                    strokeWidth = 3f
                                )

                                // Arrowhead
                                val angle = atan2(targetY - centerY, targetX - centerX)
                                val arrowLen = 12f
                                val arrowAngle = Math.PI / 6.0
                                val p1 = Offset(
                                    (targetX - arrowLen * cos(angle - arrowAngle)).toFloat(),
                                    (targetY - arrowLen * sin(angle - arrowAngle)).toFloat()
                                )
                                val p2 = Offset(
                                    (targetX - arrowLen * cos(angle + arrowAngle)).toFloat(),
                                    (targetY - arrowLen * sin(angle + arrowAngle)).toFloat()
                                )

                                val path = Path().apply {
                                    moveTo(targetX, targetY)
                                    lineTo(p1.x, p1.y)
                                    lineTo(p2.x, p2.y)
                                    close()
                                }
                                drawPath(path, color = vec.color)
                                drawCircle(vec.color, radius = 3.5f, center = Offset(targetX, targetY))

                                // Vector Label
                                val labelText = "${vec.label} (${vec.x.toInt().takeIf { it.toFloat() == vec.x } ?: vec.x}, ${vec.y.toInt().takeIf { it.toFloat() == vec.y } ?: vec.y})"
                                val textOffset = Offset(
                                    targetX + (if (vec.x >= 0) 8f else -80f),
                                    targetY + (if (vec.y >= 0) -22f else 6f)
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = labelText,
                                    topLeft = textOffset,
                                    style = TextStyle(fontSize = 11.sp, color = vec.color, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                is ParsedChartData.BarChart -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Benchmark Chart",
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PERFORMANCE / COMPARISON BENCHMARK",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val maxVal = max(1f, chartData.bars.maxOfOrNull { it.value } ?: 1f)

                    if (chartData.bars.isEmpty()) {
                        Text(
                            text = "No data points to display",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            chartData.bars.forEachIndexed { index, bar ->
                                val barRatio = (bar.value / maxVal.coerceAtLeast(1f)).coerceIn(0.04f, 1f)
                            val barColor = when (index % 4) {
                                0 -> Color(0xFF6366F1) // Indigo
                                1 -> Color(0xFF10B981) // Emerald
                                2 -> Color(0xFFF59E0B) // Amber
                                else -> Color(0xFFEC4899) // Rose
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bar.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${bar.value} ${bar.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF1E293B))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(barRatio)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(barColor)
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
}

private fun parseChartCode(code: String): ParsedChartData {
    val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val colors = listOf(
        Color(0xFF38BDF8), // Sky blue
        Color(0xFF34D399), // Emerald
        Color(0xFFF472B6), // Pink
        Color(0xFFFBBF24), // Amber
        Color(0xFFA78BFA)  // Violet
    )

    // Check if vector plot
    val hasVector = lines.any { it.contains("vector", ignoreCase = true) || it.contains("basis", ignoreCase = true) }
    if (hasVector) {
        val vectors = mutableListOf<PlottedVector>()
        val vectorRegex = Regex("""(?:vector:\s*)?\[\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\](?:.*?(?:label:\s*|,\s*)([^,]+))?""", RegexOption.IGNORE_CASE)

        for ((idx, line) in lines.withIndex()) {
            val match = vectorRegex.find(line)
            if (match != null) {
                val x = match.groupValues[1].toFloatOrNull() ?: 0f
                val y = match.groupValues[2].toFloatOrNull() ?: 0f
                val label = match.groupValues[3].trim().ifBlank { "v${idx + 1}" }
                vectors.add(PlottedVector(x, y, label, colors[idx % colors.size]))
            }
        }
        if (vectors.isNotEmpty()) {
            return ParsedChartData.VectorPlot(vectors)
        }
    }

    // Default to comparison bar chart
    val bars = mutableListOf<PlottedBar>()
    val barRegex = Regex("""(?:bar:\s*)?([^,:]+)[,:]\s*(?:value:\s*)?([-\d.]+)\s*(?:unit:\s*([a-zA-Z%]+)|([a-zA-Z%]+))?""", RegexOption.IGNORE_CASE)

    for (line in lines) {
        if (line.startsWith("type:", ignoreCase = true)) continue
        val match = barRegex.find(line)
        if (match != null) {
            val label = match.groupValues[1].trim()
            val value = match.groupValues[2].toFloatOrNull() ?: 0f
            val unit = (match.groupValues[3].ifBlank { match.groupValues[4] }).trim()
            bars.add(PlottedBar(label, value, unit))
        }
    }

    if (bars.isEmpty()) {
        // Fallback demo bar chart if formatting was unusual
        bars.add(PlottedBar("Baseline", 100f, "pts"))
        bars.add(PlottedBar("Optimized", 18f, "pts"))
    }

    return ParsedChartData.BarChart(bars)
}
