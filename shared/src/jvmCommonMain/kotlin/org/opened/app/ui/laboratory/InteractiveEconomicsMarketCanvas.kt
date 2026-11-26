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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

enum class MarketPolicy {
    FREE_MARKET,
    PRICE_CEILING,
    PRICE_FLOOR
}

@Composable
fun InteractiveEconomicsMarketCanvas(
    modifier: Modifier = Modifier
) {
    var demandShift by remember { mutableFloatStateOf(0f) } // -20 to +20
    var supplyShift by remember { mutableFloatStateOf(0f) } // -20 to +20
    var selectedPolicy by remember { mutableStateOf(MarketPolicy.FREE_MARKET) }
    var policyPrice by remember { mutableFloatStateOf(40f) }

    // Demand: P = (80 + demandShift) - 0.8 * Q
    // Supply: P = (10 + supplyShift) + 0.6 * Q
    val a = (80f + demandShift).coerceAtLeast(40f)
    val b = 0.8f
    val c = (10f + supplyShift).coerceAtLeast(0f)
    val d = 0.6f

    // Free market equilibrium
    val qStar = ((a - c) / (b + d)).coerceAtLeast(1f)
    val pStar = (c + d * qStar).coerceAtLeast(1f)

    // Actual transaction price & quantity given policy
    val actualP: Float
    val actualQ: Float
    val marketState: String

    when (selectedPolicy) {
        MarketPolicy.FREE_MARKET -> {
            actualP = pStar
            actualQ = qStar
            marketState = "Equilibrium (Q* = ${"%.1f".format(qStar)}, P* = $${"%.1f".format(pStar)})"
        }
        MarketPolicy.PRICE_CEILING -> {
            val ceiling = policyPrice.coerceAtMost(pStar)
            actualP = ceiling
            // Under ceiling, suppliers determine quantity: Q_s = (P - c) / d
            val qs = max(0f, (ceiling - c) / d)
            val qd = max(0f, (a - ceiling) / b)
            actualQ = qs
            marketState = "Price Ceiling: Shortage of ${"%.1f".format(qd - qs)} units (Deadweight loss present)"
        }
        MarketPolicy.PRICE_FLOOR -> {
            val floor = policyPrice.coerceAtLeast(pStar)
            actualP = floor
            // Under floor, buyers determine quantity: Q_d = (a - P) / b
            val qd = max(0f, (a - floor) / b)
            val qs = max(0f, (floor - c) / d)
            actualQ = qd
            marketState = "Price Floor: Surplus of ${"%.1f".format(qs - qd)} units (Deadweight loss present)"
        }
    }

    val demandColor = Color(0xFF2979FF) // Blue
    val supplyColor = Color(0xFF00E676) // Green
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
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
                    text = "📈 Supply & Demand Market Equilibrium",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Visualize consumer surplus, producer surplus, and deadweight loss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                demandShift = 0f
                supplyShift = 0f
                selectedPolicy = MarketPolicy.FREE_MARKET
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Curves", modifier = Modifier.size(18.dp))
            }
        }

        // Policy Chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarketPolicy.values().forEach { policy ->
                AssistChip(
                    onClick = {
                        selectedPolicy = policy
                        if (policy == MarketPolicy.PRICE_CEILING) policyPrice = (pStar * 0.75f).coerceAtLeast(15f)
                        if (policy == MarketPolicy.PRICE_FLOOR) policyPrice = (pStar * 1.25f).coerceAtMost(90f)
                    },
                    label = { Text(policy.name.replace("_", " "), fontSize = 11.sp) },
                    colors = if (selectedPolicy == policy) {
                        AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
        }

        // Market State Banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = marketState,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                // Legend dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(demandColor))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Demand", fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(supplyColor))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Supply", fontSize = 10.sp)
                    }
                }
            }
        }

        // Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clipToBounds()
                .semantics {
                    contentDescription = "Supply and Demand Marshallian cross. Free market equilibrium at quantity ${"%.1f".format(qStar)}, price $${"%.1f".format(pStar)}. $marketState."
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                val paddingLeft = 45f
                val paddingBottom = 40f
                val plotWidth = width - paddingLeft - 25f
                val plotHeight = height - paddingBottom - 25f

                val maxQ = 100f
                val maxP = 100f
                val scaleQ = plotWidth / maxQ
                val scaleP = plotHeight / maxP

                val originX = paddingLeft
                val originY = height - paddingBottom

                fun toScreen(q: Float, p: Float): Offset {
                    val sx = originX + q.coerceIn(0f, maxQ) * scaleQ
                    val sy = originY - p.coerceIn(0f, maxP) * scaleP
                    return Offset(sx, sy)
                }

                // Shaded Consumer Surplus Triangle
                val csPath = Path()
                val csTop = toScreen(0f, a)
                val csEq = toScreen(actualQ, if (selectedPolicy == MarketPolicy.PRICE_CEILING) a - b * actualQ else actualP)
                val csBase = toScreen(0f, actualP)
                csPath.moveTo(csTop.x, csTop.y)
                csPath.lineTo(csEq.x, csEq.y)
                csPath.lineTo(csBase.x, csBase.y)
                csPath.close()
                drawPath(csPath, color = demandColor.copy(alpha = 0.15f))

                // Shaded Producer Surplus Triangle
                val psPath = Path()
                val psBottom = toScreen(0f, c)
                val psEq = toScreen(actualQ, actualP)
                val psBase = toScreen(0f, actualP)
                psPath.moveTo(psBottom.x, psBottom.y)
                psPath.lineTo(psEq.x, psEq.y)
                psPath.lineTo(psBase.x, psBase.y)
                psPath.close()
                drawPath(psPath, color = supplyColor.copy(alpha = 0.15f))

                // Deadweight Loss Shading (if ceiling or floor active)
                if (selectedPolicy != MarketPolicy.FREE_MARKET && actualQ < qStar) {
                    val dwlPath = Path()
                    val pDemandAtQ = a - b * actualQ
                    val pSupplyAtQ = c + d * actualQ
                    val dwlTop = toScreen(actualQ, pDemandAtQ)
                    val dwlBottom = toScreen(actualQ, pSupplyAtQ)
                    val dwlEq = toScreen(qStar, pStar)
                    dwlPath.moveTo(dwlTop.x, dwlTop.y)
                    dwlPath.lineTo(dwlEq.x, dwlEq.y)
                    dwlPath.lineTo(dwlBottom.x, dwlBottom.y)
                    dwlPath.close()
                    drawPath(dwlPath, color = Color(0xFFFF5252).copy(alpha = 0.35f))
                }

                // Draw Axes
                drawLine(Color.Gray.copy(alpha = 0.6f), Offset(originX, originY), Offset(width - 15f, originY), strokeWidth = 2.dp.toPx())
                drawLine(Color.Gray.copy(alpha = 0.6f), Offset(originX, 15f), Offset(originX, originY), strokeWidth = 2.dp.toPx())

                // Draw Demand Line (P = a - bQ)
                val dStart = toScreen(0f, a)
                val dEnd = toScreen(a / b, 0f)
                drawLine(demandColor, dStart, dEnd, strokeWidth = 3.dp.toPx())

                // Draw Supply Line (P = c + dQ)
                val sStart = toScreen(0f, c)
                val sEnd = toScreen((maxP - c) / d, maxP)
                drawLine(supplyColor, sStart, sEnd, strokeWidth = 3.dp.toPx())

                // Draw Equilibrium Crosshairs (Dashed)
                val eqPoint = toScreen(qStar, pStar)
                drawLine(Color.Gray.copy(alpha = 0.5f), Offset(originX, eqPoint.y), Offset(eqPoint.x, eqPoint.y), strokeWidth = 1.dp.toPx())
                drawLine(Color.Gray.copy(alpha = 0.5f), Offset(eqPoint.x, originY), Offset(eqPoint.x, eqPoint.y), strokeWidth = 1.dp.toPx())
                drawCircle(color = onSurfaceColor, radius = 5.dp.toPx(), center = eqPoint)

                // Draw Policy Line (Ceiling / Floor)
                if (selectedPolicy != MarketPolicy.FREE_MARKET) {
                    val polY = toScreen(0f, actualP).y
                    val polColor = if (selectedPolicy == MarketPolicy.PRICE_CEILING) Color(0xFFFF9800) else Color(0xFFE91E63)
                    drawLine(polColor, Offset(originX, polY), Offset(width - 20f, polY), strokeWidth = 2.dp.toPx())
                }

                // Axis Labels
                drawText(textMeasurer, "P ($)", Offset(originX - 35f, 15f), TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray))
                drawText(textMeasurer, "Q", Offset(width - 25f, originY + 8f), TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray))
                drawText(textMeasurer, "P*=$${"%.0f".format(pStar)}", Offset(originX - 42f, eqPoint.y - 8f), TextStyle(fontSize = 9.sp, color = Color.Gray))
                drawText(textMeasurer, "Q*=${"%.0f".format(qStar)}", Offset(eqPoint.x - 12f, originY + 8f), TextStyle(fontSize = 9.sp, color = Color.Gray))
            }
        }

        // Sliders for Shift Shifters
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demand Shift (Buyer Income): ${demandShift.toInt()}", style = MaterialTheme.typography.bodySmall)
                Text("Supply Shift (Production Costs): ${supplyShift.toInt()}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = demandShift,
                    onValueChange = { demandShift = it },
                    valueRange = -20f..20f,
                    modifier = Modifier.weight(1f)
                )
                Slider(
                    value = supplyShift,
                    onValueChange = { supplyShift = it },
                    valueRange = -20f..20f,
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedPolicy != MarketPolicy.FREE_MARKET) {
                Text("Price Control Level: $${policyPrice.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Slider(
                    value = policyPrice,
                    onValueChange = { policyPrice = it },
                    valueRange = 15f..85f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
