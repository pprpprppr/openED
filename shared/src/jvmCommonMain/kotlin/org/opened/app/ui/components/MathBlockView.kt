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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.opened.app.ui.platform.rememberPlatformCopyFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MathBlockView(
    mathExpression: String,
    modifier: Modifier = Modifier,
    isComplete: Boolean = true
) {
    if (!isComplete || mathExpression.isBlank()) {
        RenderingPlaceholderCard(
            message = "Rendering formula...",
            modifier = modifier
        )
        return
    }

    val copyWithFeedback = rememberPlatformCopyFeedback()
    val scope = rememberCoroutineScope()
    var showRawLatex by remember { mutableStateOf(false) }
    var copiedLatex by remember { mutableStateOf(false) }
    var copiedText by remember { mutableStateOf(false) }

    val mathAst = remember(mathExpression) {
        runCatching {
            LatexParser.parseDisplayMath(mathExpression)
        }.getOrElse {
            Math2DNode.Text(mathExpression)
        }
    }

    // Determine domain category badge
    val isChemistry = mathExpression.contains("\\ce{") || mathExpression.contains("->") || mathExpression.contains("<=>")
    val isMatrix = mathExpression.contains("matrix")
    val isCalculus = mathExpression.contains("\\int") || mathExpression.contains("\\sum") || mathExpression.contains("\\lim") || mathExpression.contains("\\partial")

    val badgeLabel = when {
        isChemistry -> "CHEMICAL NOTATION"
        isMatrix -> "LINEAR ALGEBRA"
        isCalculus -> "CALCULUS & ANALYSIS"
        else -> "MATHEMATICAL FORMULATION"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "Mathematical formulation: " + LatexParser.toSpokenDescription(mathExpression)
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isChemistry) Icons.Default.AutoAwesome else Icons.Default.Functions,
                        contentDescription = badgeLabel,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Actions: Copy LaTeX & Toggle View
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy LaTeX Button
                    IconButton(
                        onClick = {
                            copyWithFeedback("latex", mathExpression.trim(), "LaTeX formula copied")
                            copiedLatex = true
                            scope.launch {
                                delay(2000)
                                copiedLatex = false
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedLatex) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy LaTeX formula",
                            tint = if (copiedLatex) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // View Source / Show Rendered Toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showRawLatex = !showRawLatex }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showRawLatex) "Rendered" else "LaTeX",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (showRawLatex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = mathExpression.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFF93C5FD),
                        lineHeight = 18.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KaTeXMathView(
                        latex = mathExpression,
                        isDisplayMode = true,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun Math2DRenderer(
    node: Math2DNode,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 17.sp
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    when (node) {
        is Math2DNode.Text -> {
            val content = node.content
            val isSingleVariable = content.length == 1 && content.first().isLetter()
            Text(
                text = content,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = baseFontSize,
                    fontStyle = if (node.isVariable || isSingleVariable) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
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
                    Math2DRenderer(node = child, baseFontSize = baseFontSize)
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
                Math2DRenderer(node = node.numerator, baseFontSize = (baseFontSize.value * 0.85f).sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(onSurface.copy(alpha = 0.75f))
                )
                Math2DRenderer(node = node.denominator, baseFontSize = (baseFontSize.value * 0.85f).sp)
            }
        }

        is Math2DNode.Power -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Math2DRenderer(node = node.base, baseFontSize = baseFontSize)
                Box(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .padding(bottom = 6.dp, start = 1.dp)
                ) {
                    Math2DRenderer(node = node.exponent, baseFontSize = (baseFontSize.value * 0.72f).sp)
                }
            }
        }

        is Math2DNode.Subscript -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Math2DRenderer(node = node.base, baseFontSize = baseFontSize)
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(top = 6.dp, start = 1.dp)
                ) {
                    Math2DRenderer(node = node.subscript, baseFontSize = (baseFontSize.value * 0.72f).sp)
                }
            }
        }

        is Math2DNode.SubSup -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Math2DRenderer(node = node.base, baseFontSize = baseFontSize)
                Column(
                    modifier = Modifier.padding(start = 1.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Box(modifier = Modifier.padding(bottom = 1.dp)) {
                        Math2DRenderer(node = node.superscript, baseFontSize = (baseFontSize.value * 0.68f).sp)
                    }
                    Box(modifier = Modifier.padding(top = 1.dp)) {
                        Math2DRenderer(node = node.subscript, baseFontSize = (baseFontSize.value * 0.68f).sp)
                    }
                }
            }
        }

        is Math2DNode.Sqrt -> {
            Row(
                modifier = modifier.padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.rootIndex != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(top = 2.dp, end = 1.dp)
                    ) {
                        Math2DRenderer(node = node.rootIndex, baseFontSize = (baseFontSize.value * 0.65f).sp)
                    }
                }
                Text(
                    text = "√",
                    fontSize = (baseFontSize.value * 1.4f).sp,
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
                    Math2DRenderer(node = node.inner, baseFontSize = baseFontSize)
                }
            }
        }

        is Math2DNode.Delimited -> {
            Row(
                modifier = modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.leftDelim,
                    fontSize = (baseFontSize.value * 1.25f).sp,
                    fontFamily = FontFamily.Serif,
                    color = primaryColor
                )
                Math2DRenderer(node = node.content, baseFontSize = baseFontSize)
                Text(
                    text = node.rightDelim,
                    fontSize = (baseFontSize.value * 1.25f).sp,
                    fontFamily = FontFamily.Serif,
                    color = primaryColor
                )
            }
        }

        is Math2DNode.Binomial -> {
            Row(
                modifier = modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "(",
                    fontSize = (baseFontSize.value * 1.35f).sp,
                    fontFamily = FontFamily.Serif,
                    color = primaryColor
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Math2DRenderer(node = node.top, baseFontSize = (baseFontSize.value * 0.85f).sp)
                    Math2DRenderer(node = node.bottom, baseFontSize = (baseFontSize.value * 0.85f).sp)
                }
                Text(
                    text = ")",
                    fontSize = (baseFontSize.value * 1.35f).sp,
                    fontFamily = FontFamily.Serif,
                    color = primaryColor
                )
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
                                    Math2DRenderer(node = colNode, baseFontSize = (baseFontSize.value * 0.9f).sp)
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
                        fontSize = (baseFontSize.value * 1.6f).sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        lineHeight = (baseFontSize.value * 1.7f).sp
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
                    Math2DRenderer(node = node.expression, baseFontSize = baseFontSize)
                }
            }
        }

        is Math2DNode.AlignedEquations -> {
            Column(
                modifier = modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                node.lines.forEach { lineParts ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lineParts.forEach { part ->
                            Math2DRenderer(node = part, baseFontSize = baseFontSize)
                        }
                    }
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
