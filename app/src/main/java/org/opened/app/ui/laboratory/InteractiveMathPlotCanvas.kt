package org.opened.app.ui.laboratory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

enum class MathFunctionPreset(val label: String, val formula: (Float) -> Float, val derivative: (Float) -> Float) {
    POLYNOMIAL("f(x) = x³ - 3x", { x -> x * x * x - 3f * x }, { x -> 3f * x * x - 3f }),
    SINE("f(x) = sin(x)", { x -> sin(x) }, { x -> cos(x) }),
    SIGMOID("f(x) = 1/(1+e⁻ˣ)", { x -> 1f / (1f + exp(-x)) }, { x ->
        val s = 1f / (1f + exp(-x))
        s * (1f - s)
    }),
    DAMPED_SINC("f(x) = sin(x)/x", { x -> if (x == 0f) 1f else sin(x) / x }, { x ->
        if (x == 0f) 0f else (x * cos(x) - sin(x)) / (x * x)
    }),
    GAUSSIAN("f(x) = e^(-x²)", { x -> exp(-x * x) }, { x -> -2f * x * exp(-x * x) })
}

@Composable
fun InteractiveMathPlotCanvas(
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(MathFunctionPreset.POLYNOMIAL) }
    var zoomScale by remember { mutableFloatStateOf(40f) } // pixels per unit
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var cursorX by remember { mutableStateOf<Float?>(0f) }
    var showTangent by remember { mutableStateOf(true) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val tangentColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Header & Function Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "📐 2D Function Analysis Laboratory",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Touch/drag to inspect coordinates and tangent slope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(120f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { zoomScale = (zoomScale * 0.8f).coerceAtLeast(15f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    zoomScale = 40f
                    panOffsetX = 0f
                    panOffsetY = 0f
                    cursorX = 0f
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Plot", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MathFunctionPreset.values().take(3).forEach { preset ->
                AssistChip(
                    onClick = { selectedPreset = preset },
                    label = { Text(preset.label, fontSize = 11.sp) },
                    colors = if (selectedPreset == preset) {
                        AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
        }

        // Active Coordinate Readout Box
        val currentX = cursorX ?: 0f
        val currentY = selectedPreset.formula(currentX)
        val currentSlope = selectedPreset.derivative(currentX)

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "x = ${"%.2f".format(currentX)}   |   f(x) = ${"%.2f".format(currentY)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Slope f'(x) = ${"%.2f".format(currentSlope)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tangentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Interactive Canvas Plot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clipToBounds()
                .semantics {
                    contentDescription = "Interactive 2D Math Plot of ${selectedPreset.label}. Zoom scale is ${zoomScale.toInt()} pixels per unit. Current inspection at x = ${"%.2f".format(currentX)}, y = ${"%.2f".format(currentY)}."
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(15f, 150f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                .pointerInput(zoomScale, panOffsetX) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val originX = size.width / 2f + panOffsetX
                        val touchX = change.position.x
                        cursorX = ((touchX - originX) / zoomScale).coerceIn(-15f, 15f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height
                val originX = width / 2f + panOffsetX
                val originY = height / 2f + panOffsetY

                // Draw Grid lines
                val step = if (zoomScale > 70f) 1f else if (zoomScale > 30f) 2f else 5f
                var xVal = -20f
                while (xVal <= 20f) {
                    val px = originX + xVal * zoomScale
                    if (px in 0f..width) {
                        drawLine(gridColor, Offset(px, 0f), Offset(px, height), strokeWidth = 1f)
                        if (xVal != 0f) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${xVal.toInt()}",
                                topLeft = Offset(px + 4f, originY.coerceIn(10f, height - 25f)),
                                style = TextStyle(fontSize = 10.sp, color = axisColor)
                            )
                        }
                    }
                    xVal += step
                }

                var yVal = -20f
                while (yVal <= 20f) {
                    val py = originY - yVal * zoomScale
                    if (py in 0f..height) {
                        drawLine(gridColor, Offset(0f, py), Offset(width, py), strokeWidth = 1f)
                    }
                    yVal += step
                }

                // Draw Main Axes
                drawLine(axisColor, Offset(0f, originY), Offset(width, originY), strokeWidth = 2f)
                drawLine(axisColor, Offset(originX, 0f), Offset(originX, height), strokeWidth = 2f)

                // Plot the Mathematical Curve
                val curvePath = Path()
                var firstPoint = true
                val numSamples = (width / 2f).toInt().coerceAtLeast(100)

                for (i in 0..numSamples) {
                    val screenX = (i.toFloat() / numSamples) * width
                    val mathX = (screenX - originX) / zoomScale
                    val mathY = selectedPreset.formula(mathX)
                    val screenY = originY - mathY * zoomScale

                    if (screenY.isFinite() && screenY in -500f..(height + 500f)) {
                        if (firstPoint) {
                            curvePath.moveTo(screenX, screenY)
                            firstPoint = false
                        } else {
                            curvePath.lineTo(screenX, screenY)
                        }
                    }
                }
                drawPath(curvePath, color = primaryColor, style = Stroke(width = 3.dp.toPx()))

                // Draw Touch Cursor & Tangent Line
                cursorX?.let { cx ->
                    val cy = selectedPreset.formula(cx)
                    val ptX = originX + cx * zoomScale
                    val ptY = originY - cy * zoomScale

                    if (ptX in 0f..width && ptY in 0f..height) {
                        // Vertical guideline
                        drawLine(
                            color = axisColor.copy(alpha = 0.4f),
                            start = Offset(ptX, 0f),
                            end = Offset(ptX, height),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Tangent Line: y - y0 = m * (x - x0)
                        if (showTangent) {
                            val slope = selectedPreset.derivative(cx)
                            val tanDx = 2f // units to left and right
                            val tanX1 = cx - tanDx
                            val tanY1 = cy - slope * tanDx
                            val tanX2 = cx + tanDx
                            val tanY2 = cy + slope * tanDx

                            val screenTanX1 = originX + tanX1 * zoomScale
                            val screenTanY1 = originY - tanY1 * zoomScale
                            val screenTanX2 = originX + tanX2 * zoomScale
                            val screenTanY2 = originY - tanY2 * zoomScale

                            drawLine(
                                color = tangentColor,
                                start = Offset(screenTanX1, screenTanY1),
                                end = Offset(screenTanX2, screenTanY2),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // Cursor point
                        drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = Offset(ptX, ptY))
                        drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(ptX, ptY))
                    }
                }
            }
        }
    }
}
