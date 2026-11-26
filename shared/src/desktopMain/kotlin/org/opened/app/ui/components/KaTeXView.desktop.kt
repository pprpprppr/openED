package org.opened.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Pure-Compose Desktop mathematical formulation renderer using openED's 2D Math AST engine.
 */
@Composable
actual fun KaTeXMathView(
    latex: String,
    modifier: Modifier,
    isDisplayMode: Boolean,
    fontSize: TextUnit,
    color: Color,
    minHeight: Dp
) {
    val mathAst = remember(latex) {
        runCatching {
            val cleaned = latex.trim()
                .removePrefix("$$").removeSuffix("$$")
                .removePrefix("\\[").removeSuffix("\\]")
                .trim()
            LatexParser.parseDisplayMath(cleaned)
        }.getOrElse {
            Math2DNode.Text(LatexParser.cleanInlineLatex(latex))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Math2DRenderer(
            node = mathAst,
            baseFontSize = fontSize
        )
    }
}

/**
 * Pure-Compose Desktop rich text renderer combining inline LaTeX typesetting, syntax highlighting, and wiki-links.
 */
@Composable
actual fun KaTeXRichTextView(
    text: String,
    modifier: Modifier,
    onChapterClick: (String) -> Unit,
    isClickableCard: Boolean,
    fontSize: TextUnit,
    color: Color,
    primaryColor: Color,
    selectable: Boolean,
    minHeight: Dp
) {
    InteractiveMarkdownText(
        text = text,
        onChapterClick = onChapterClick,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
        color = color
    )
}
