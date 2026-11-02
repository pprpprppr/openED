package org.opened.app.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

const val TAG_WIKI_LINK = "TAG_WIKI_LINK"
const val TAG_URL_LINK = "TAG_URL_LINK"

@Composable
fun InteractiveMarkdownText(
    text: String,
    onChapterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val annotatedString = remember(text, primaryColor, secondaryColor, color) {
        buildInteractiveAnnotatedString(
            text = text,
            defaultColor = color,
            wikiLinkColor = primaryColor,
            urlColor = secondaryColor
        )
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = color),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = TAG_WIKI_LINK, start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onChapterClick(annotation.item)
                    return@ClickableText
                }

            annotatedString.getStringAnnotations(tag = TAG_URL_LINK, start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    // External link handling
                }
        }
    )
}

fun buildInteractiveAnnotatedString(
    text: String,
    defaultColor: Color,
    wikiLinkColor: Color,
    urlColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        // Regex pattern to match:
        // 1. [[Wiki Links]]
        // 2. [Markdown Links](url)
        // 3. `inline code`
        // 4. $inline math$ (handling nested braces)
        // 5. **bold**
        // 6. *italic*
        val tokenRegex = Regex("""(\[\[(.*?)\]\])|(\[(.*?)\]\((.*?)\))|(`(.*?)`)|(\$([^\$\n]+?)\$)|(\*\*(.*?)\*\*)|(\*(.*?)\*)""")
        var lastIndex = 0

        fun appendCleanedText(raw: String) {
            // Never allow raw LaTeX leaks or stray braces in body text
            val cleaned = if (raw.contains('\\') || (raw.contains('{') && raw.contains('}'))) {
                LatexParser.cleanInlineLatex(raw)
            } else {
                raw
            }
            append(cleaned)
        }

        for (match in tokenRegex.findAll(text)) {
            // Append plain text before match
            if (match.range.first > lastIndex) {
                appendCleanedText(text.substring(lastIndex, match.range.first))
            }

            val fullMatch = match.value
            when {
                // [[Wiki Link]]
                fullMatch.startsWith("[[") && fullMatch.endsWith("]]") -> {
                    val chapterTitle = match.groupValues[2].trim()
                    val start = length
                    pushStringAnnotation(tag = TAG_WIKI_LINK, annotation = chapterTitle)
                    pushStyle(
                        SpanStyle(
                            color = wikiLinkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append("✦ $chapterTitle")
                    pop()
                    pop()
                }

                // [Markdown Link](url)
                fullMatch.startsWith("[") && fullMatch.contains("](") -> {
                    val label = match.groupValues[4]
                    val url = match.groupValues[5]
                    pushStringAnnotation(tag = TAG_URL_LINK, annotation = url)
                    pushStyle(
                        SpanStyle(
                            color = urlColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(label)
                    pop()
                    pop()
                }

                // `inline code`
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val code = match.groupValues[7]
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x33888888),
                            color = Color(0xFFF43F5E),
                            fontSize = 13.sp
                        )
                    )
                    append(" $code ")
                    pop()
                }

                // $inline math$
                fullMatch.startsWith("$") && fullMatch.endsWith("$") && fullMatch.length > 2 -> {
                    val mathExpr = match.groupValues[9]
                    val formatted = LatexParser.cleanInlineLatex(mathExpr)
                    pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF818CF8),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    )
                    append(formatted)
                    pop()
                }

                // **bold**
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val boldText = match.groupValues[11]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }

                // *italic*
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val italicText = match.groupValues[13]
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
            }

            lastIndex = match.range.last + 1
        }

        // Append remaining text
        if (lastIndex < text.length) {
            appendCleanedText(text.substring(lastIndex))
        }
    }
}
