package org.opened.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Checks if the given text contains LaTeX math notation, formulas, or delimiters.
 */
fun hasMathContent(text: String): Boolean {
    if (text.isEmpty()) return false
    if (text.contains("$$") || text.contains("\\[") || text.contains("\\(") || text.contains("\\begin{")) {
        return true
    }
    if (text.contains('$')) {
        val first = text.indexOf('$')
        if (first in 0 until text.length - 1) {
            val second = text.indexOf('$', first + 1)
            if (second > first) return true
        }
    }
    if (text.contains("\\frac") || text.contains("\\dfrac") || text.contains("\\tfrac") ||
        text.contains("\\sqrt") || text.contains("\\sum") || text.contains("\\int") ||
        text.contains("\\prod") || text.contains("\\lim") || text.contains("\\partial") ||
        text.contains("\\nabla") || text.contains("\\infty") || text.contains("\\times") ||
        text.contains("\\cdot") || text.contains("\\pm") || text.contains("\\mp") ||
        text.contains("\\leq") || text.contains("\\geq") || text.contains("\\neq") ||
        text.contains("\\approx") || text.contains("\\equiv") || text.contains("\\to") ||
        text.contains("\\rightarrow") || text.contains("\\leftarrow") || text.contains("\\Rightarrow") ||
        text.contains("\\Leftarrow") || text.contains("\\Leftrightarrow") || text.contains("\\vec") ||
        text.contains("\\hat") || text.contains("\\mathbf") || text.contains("\\mathbb") ||
        text.contains("\\mathcal") || text.contains("\\ce{") || text.contains("\\binom") ||
        text.contains("\\matrix") || text.contains("\\pmatrix") || text.contains("\\bmatrix") ||
        text.contains("\\cases")
    ) {
        return true
    }
    return false
}

/**
 * Ensures bare LaTeX expressions (like \frac{1}{2} or \sqrt{3}) are wrapped in $...$
 * so KaTeX auto-render recognizes them.
 */
fun ensureDelimiters(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    if (trimmed.startsWith("$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\(") || trimmed.startsWith("\\begin{")) {
        return raw
    }
    if (trimmed.startsWith("\\") && (trimmed.contains("{") || trimmed.contains(" "))) {
        return "\$$trimmed\$"
    }
    return raw
}

@Composable
expect fun KaTeXMathView(
    latex: String,
    modifier: Modifier = Modifier,
    isDisplayMode: Boolean = true,
    fontSize: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    minHeight: Dp = 48.dp
)

@Composable
expect fun KaTeXRichTextView(
    text: String,
    modifier: Modifier = Modifier,
    onChapterClick: (String) -> Unit = {},
    isClickableCard: Boolean = false,
    fontSize: TextUnit = 16.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    selectable: Boolean = true,
    minHeight: Dp = 24.dp
)
