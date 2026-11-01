package org.opened.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MathBlockView(
    mathExpression: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRawLatex by remember { mutableStateOf(false) }

    val formattedMath = remember(mathExpression) {
        formatLatexForDisplay(mathExpression)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable {
                showRawLatex = !showRawLatex
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                        text = "MATH / LATEX",
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
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formattedMath,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatLatexForDisplay(raw: String): String {
    var text = raw.trim()
        .removePrefix("$$").removeSuffix("$$")
        .removePrefix("\\[").removeSuffix("\\]")
        .removePrefix("$").removeSuffix("$")
        .trim()

    // Common LaTeX replacements for mathematical readability
    val replacements = listOf(
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\pi" to "π",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\omega" to "ω",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Sigma" to "Σ",
        "\\Omega" to "Ω",
        "\\times" to " × ",
        "\\cdot" to " · ",
        "\\pm" to " ± ",
        "\\leq" to " ≤ ",
        "\\geq" to " ≥ ",
        "\\neq" to " ≠ ",
        "\\approx" to " ≈ ",
        "\\equiv" to " ≡ ",
        "\\rightarrow" to " → ",
        "\\to" to " → ",
        "\\infty" to "∞",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\in" to " ∈ ",
        "\\notin" to " ∉ ",
        "\\subset" to " ⊂ ",
        "\\cup" to " ∪ ",
        "\\cap" to " ∩ ",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\sqrt" to "√",
        "\\text" to "",
        "\\mathbf" to "",
        "\\mathit" to "",
        "\\mathrm" to "",
        "\\," to " ",
        "\\;" to " ",
        "\\quad" to "   "
    )

    for ((latex, symbol) in replacements) {
        text = text.replace(latex, symbol)
    }

    // Replace \frac{a}{b} with (a)/(b)
    val fracRegex = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
    text = fracRegex.replace(text) { match ->
        "${match.groupValues[1]} / ${match.groupValues[2]}"
    }

    // Replace superscript powers ^2 -> ², ^3 -> ³, etc.
    val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ'
    )
    val powerRegex = Regex("""\^\{?([0-9nix+\-()]+)\}?""")
    text = powerRegex.replace(text) { match ->
        val inner = match.groupValues[1]
        inner.map { superscripts[it] ?: it }.joinToString("")
    }

    // Replace subscript _0 -> ₀, _1 -> ₁
    val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'm' to 'ₘ', 'n' to 'ₙ',
        '+' to '₊', '-' to '₋'
    )
    val subRegex = Regex("""_\{?([0-9ijkm+\-]+)\}?""")
    text = subRegex.replace(text) { match ->
        val inner = match.groupValues[1]
        inner.map { subscripts[it] ?: it }.joinToString("")
    }

    return text
}
