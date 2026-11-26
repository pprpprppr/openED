package org.opened.app.ui.laboratory

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class GravityPreset(val label: String, val g: Float) {
    EARTH("Earth (9.8 m/s²)", 9.8f),
    MOON("Moon (1.6 m/s²)", 1.62f),
    MARS("Mars (3.7 m/s²)", 3.71f),
    JUPITER("Jupiter (24.8 m/s²)", 24.79f)
}

@Composable
fun InteractivePhysicsSimulationCanvas(
    modifier: Modifier = Modifier
) {
    var v0 by remember { mutableFloatStateOf(25f) } // Initial velocity m/s
    var launchAngleDeg by remember { mutableFloatStateOf(45f) } // degrees
    var selectedGravity by remember { mutableFloatStateOf(9.8f) }
    var timeScrubProgress by remember { mutableFloatStateOf(0.5f) } // 0.0 to 1.0 of flight time

    val thetaRad = (launchAngleDeg * PI / 180.0).toFloat()
    val vx0 = v0 * cos(thetaRad)
    val vy0 = v0 * sin(thetaRad)

    // Physics flight parameters
    val totalFlightTime = max(0.01f, (2f * vy0) / selectedGravity)
    val maxRange = vx0 * totalFlightTime
    val maxHeight = (vy0 * vy0) / (2f * selectedGravity)

    // Current time-based point
    val tCurrent = timeScrubProgress * totalFlightTime
    val xCurrent = vx0 * tCurrent
    val yCurrent = max(0f, vy0 * tCurrent - 0.5f * selectedGravity * tCurrent * tCurrent)
    val vxCurrent = vx0
    val vyCurrent = vy0 - selectedGravity * tCurrent
    val vCurrent = sqrt(vxCurrent * vxCurrent + vyCurrent * vyCurrent)

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Header & Diagnostics
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "🚀 Projectile Kinematics Laboratory",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vary parameters to observe vector decomposition and parabolic trajectory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                v0 = 25f
                launchAngleDeg = 45f
                selectedGravity = 9.8f
                timeScrubProgress = 0.5f
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Simulation", modifier = Modifier.size(18.dp))
            }
        }

        // Celestial Body Presets
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GravityPreset.values().forEach { preset ->
                AssistChip(
                    onClick = { selectedGravity = preset.g },
                    label = { Text(preset.label.substringBefore(" ("), fontSize = 11.sp) },
                    colors = if (selectedGravity == preset.g) {
                        AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
        }

        // Telemetry Summary Card
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Max Height (H)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.1f".format(maxHeight)} m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Range (R)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.1f".format(maxRange)} m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Total Time (T)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.2f".format(totalFlightTime)} s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Velocity |v(t)|", style = MaterialTheme.typography.labelSmall, color = tertiaryColor)
                    Text("${"%.1f".format(vCurrent)} m/s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tertiaryColor)
                }
            }
        }

        // Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clipToBounds()
                .semantics {
                    contentDescription = "Projectile trajectory simulation. Launch velocity $v0 m/s at $launchAngleDeg degrees. Max height ${"%.1f".format(maxHeight)} meters, total range ${"%.1f".format(maxRange)} meters. Current projectile at x=${"%.1f".format(xCurrent)} m, y=${"%.1f".format(yCurrent)} m."
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                val paddingLeft = 40f
                val paddingBottom = 40f
                val plotWidth = width - paddingLeft - 30f
                val plotHeight = height - paddingBottom - 30f

                val scaleX = if (maxRange > 0.01f) plotWidth / maxRange else 1f
                val scaleY = if (maxHeight > 0.01f) (plotHeight * 0.85f) / maxHeight else 1f
                val originX = paddingLeft
                val originY = height - paddingBottom

                // Draw ground & axes
                drawLine(Color.Gray.copy(alpha = 0.5f), Offset(originX, originY), Offset(width - 15f, originY), strokeWidth = 2.dp.toPx())
                drawLine(Color.Gray.copy(alpha = 0.5f), Offset(originX, 15f), Offset(originX, originY), strokeWidth = 2.dp.toPx())

                // Draw trajectory arc
                val trajectoryPath = Path()
                val steps = 60
                for (i in 0..steps) {
                    val t = (i.toFloat() / steps) * totalFlightTime
                    val x = vx0 * t
                    val y = max(0f, vy0 * t - 0.5f * selectedGravity * t * t)
                    val sx = originX + x * scaleX
                    val sy = originY - y * scaleY
                    if (i == 0) trajectoryPath.moveTo(sx, sy) else trajectoryPath.lineTo(sx, sy)
                }
                drawPath(trajectoryPath, color = primaryColor.copy(alpha = 0.8f), style = Stroke(width = 3.dp.toPx()))

                // Apex Marker
                val apexX = originX + (maxRange / 2f) * scaleX
                val apexY = originY - maxHeight * scaleY
                drawCircle(color = secondaryColor, radius = 4.dp.toPx(), center = Offset(apexX, apexY))

                // Current Projectile Position
                val curSx = originX + xCurrent * scaleX
                val curSy = originY - yCurrent * scaleY
                drawCircle(color = surfaceColor, radius = 9.dp.toPx(), center = Offset(curSx, curSy))
                drawCircle(color = tertiaryColor, radius = 6.dp.toPx(), center = Offset(curSx, curSy))

                // Real-time Velocity Vector Decomposition Arrows
                val arrowScale = 2.0f
                val vEndX = curSx + vxCurrent * arrowScale
                val vEndY = curSy - vyCurrent * arrowScale

                // Total Velocity Vector (Tertiary)
                drawLine(color = tertiaryColor, start = Offset(curSx, curSy), end = Offset(vEndX, vEndY), strokeWidth = 2.dp.toPx())
                // Horizontal Component vx (Cyan)
                drawLine(color = Color(0xFF00ACC1), start = Offset(curSx, curSy), end = Offset(curSx + vxCurrent * arrowScale, curSy), strokeWidth = 1.5.dp.toPx())
                // Vertical Component vy (Orange)
                drawLine(color = Color(0xFFFF9800), start = Offset(curSx, curSy), end = Offset(curSx, curSy - vyCurrent * arrowScale), strokeWidth = 1.5.dp.toPx())

                // Labels
                drawText(
                    textMeasurer = textMeasurer,
                    text = "Launch (0,0)",
                    topLeft = Offset(originX - 10f, originY + 8f),
                    style = TextStyle(fontSize = 9.sp, color = Color.Gray)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "Impact: ${"%.1f".format(maxRange)}m",
                    topLeft = Offset(originX + maxRange * scaleX - 35f, originY + 8f),
                    style = TextStyle(fontSize = 9.sp, color = Color.Gray)
                )
            }
        }

        // Real-time Interactive Sliders
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Initial Velocity: ${v0.toInt()} m/s", style = MaterialTheme.typography.bodySmall)
                Text("Launch Angle: ${launchAngleDeg.toInt()}°", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = v0,
                    onValueChange = { v0 = it },
                    valueRange = 5f..50f,
                    modifier = Modifier.weight(1f)
                )
                Slider(
                    value = launchAngleDeg,
                    onValueChange = { launchAngleDeg = it },
                    valueRange = 10f..85f,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Time Scrub: t = ${"%.2f".format(tCurrent)}s / ${"%.2f".format(totalFlightTime)}s", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = timeScrubProgress,
                onValueChange = { timeScrubProgress = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
