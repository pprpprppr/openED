package org.opened.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MathBlockView(
    mathExpression: String,
    modifier: Modifier = Modifier,
    isComplete: Boolean = true
) {
    if (!isComplete || mathExpression.isBlank()) {
        RenderingPlaceholderCard(
            message = "Rendering equation...",
            modifier = modifier
        )
        return
    }

    var showRawLatex by remember { mutableStateOf(false) }

    val mathAst = remember(mathExpression) {
        runCatching {
            LatexParser.parseDisplayMath(mathExpression)
        }.getOrElse {
            Math2DNode.Text(mathExpression)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Math Block",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2D FORMULATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = if (showRawLatex) "Show Rendered" else "View Source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { showRawLatex = !showRawLatex }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (showRawLatex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = mathExpression.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF93C5FD)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Math2DRenderer(node = mathAst)
                }
            }
        }
    }
}

@Composable
fun Math2DRenderer(
    node: Math2DNode,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    when (node) {
        is Math2DNode.Text -> {
            Text(
                text = node.content,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = onSurface,
                modifier = modifier.padding(horizontal = 2.dp)
            )
        }

        is Math2DNode.Row -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                node.children.forEach { child ->
                    Math2DRenderer(node = child)
                }
            }
        }

        is Math2DNode.Fraction -> {
            Column(
                modifier = modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Math2DRenderer(node = node.numerator)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(Color(0xFFE2E8F0))
                )
                Math2DRenderer(node = node.denominator)
            }
        }

        is Math2DNode.Matrix -> {
            Row(
                modifier = modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Bracket
                DrawMatrixBracket(isLeft = true, bracketType = node.bracketType, tint = primaryColor)

                // Matrix Cells Grid
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    node.rows.forEach { rowCols ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowCols.forEach { colNode ->
                                Box(contentAlignment = Alignment.Center) {
                                    Math2DRenderer(node = colNode)
                                }
                            }
                        }
                    }
                }

                // Right Bracket
                DrawMatrixBracket(isLeft = false, bracketType = node.bracketType, tint = primaryColor)
            }
        }

        is Math2DNode.BigOp -> {
            Row(
                modifier = modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (!node.upperLimit.isNullOrBlank()) {
                        Text(
                            text = node.upperLimit,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = primaryColor
                        )
                    }

                    Text(
                        text = node.opSymbol,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        lineHeight = 30.sp
                    )

                    if (!node.lowerLimit.isNullOrBlank()) {
                        Text(
                            text = node.lowerLimit,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = primaryColor
                        )
                    }
                }

                if (node.expression != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Math2DRenderer(node = node.expression)
                }
            }
        }

        is Math2DNode.Sqrt -> {
            Row(
                modifier = modifier.padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "√",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                    color = primaryColor
                )
                Box(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = primaryColor,
                                start = Offset(0f, 2.dp.toPx()),
                                end = Offset(size.width, 2.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        .padding(top = 4.dp, start = 2.dp, end = 4.dp)
                ) {
                    Math2DRenderer(node = node.inner)
                }
            }
        }
    }
}

@Composable
private fun DrawMatrixBracket(
    isLeft: Boolean,
    bracketType: BracketType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .drawBehind {
                val stroke = 2.dp.toPx()
                val lip = 6.dp.toPx()
                when (bracketType) {
                    BracketType.SQUARE -> {
                        if (isLeft) {
                            drawLine(tint, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), strokeWidth = stroke)
                            drawLine(tint, Offset(0f, stroke / 2), Offset(lip, stroke / 2), strokeWidth = stroke)
                            drawLine(tint, Offset(0f, size.height - stroke / 2), Offset(lip, size.height - stroke / 2), strokeWidth = stroke)
                        } else {
                            drawLine(tint, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), strokeWidth = stroke)
                            drawLine(tint, Offset(size.width - lip, stroke / 2), Offset(size.width, stroke / 2), strokeWidth = stroke)
                            drawLine(tint, Offset(size.width - lip, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), strokeWidth = stroke)
                        }
                    }
                    BracketType.DETERMINANT -> {
                        val x = if (isLeft) stroke / 2 else size.width - stroke / 2
                        drawLine(tint, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    }
                    BracketType.ROUND -> {
                        // Curved bracket representation
                        if (isLeft) {
                            drawLine(tint, Offset(lip, 0f), Offset(stroke / 2, size.height / 2), strokeWidth = stroke)
                            drawLine(tint, Offset(stroke / 2, size.height / 2), Offset(lip, size.height), strokeWidth = stroke)
                        } else {
                            drawLine(tint, Offset(size.width - lip, 0f), Offset(size.width - stroke / 2, size.height / 2), strokeWidth = stroke)
                            drawLine(tint, Offset(size.width - stroke / 2, size.height / 2), Offset(size.width - lip, size.height), strokeWidth = stroke)
                        }
                    }
                }
            }
    )
}

fun formatLatexForDisplay(raw: String): String {
    return LatexParser.cleanInlineLatex(raw)
}
