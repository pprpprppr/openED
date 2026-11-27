package org.opened.app.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val TAG_WIKI_LINK = "TAG_WIKI_LINK"
const val TAG_URL_LINK = "TAG_URL_LINK"

// Pre-compiled token regex for high-performance interactive parsing
private val TOKEN_REGEX = Regex(
    """(`?\[\[(.*?)\]\]`?)|(\[(.*?)\]\((.*?)\))|(`(.*?)`)|(\\\((.*?)\\\))|(\\\[(.*?)\\\])|(\$([^\$\s\n](?:[^\$\n]*?[^\$\s\n])?)\$)|(~~(.*?)~~)|(<u>(.*?)</u>)|(\*\*(.*?)\*\*)|(\*(.*?)\*)"""
)

@Composable
fun InteractiveMarkdownText(
    text: String,
    onChapterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isClickableCard: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val annotatedString = remember(text, primaryColor, secondaryColor, color) {
        runCatching {
            buildInteractiveAnnotatedString(
                text = text,
                defaultColor = color,
                wikiLinkColor = primaryColor,
                urlColor = secondaryColor,
                mathColor = primaryColor
            )
        }.getOrElse { AnnotatedString(text) }
    }

    val hasLinks = remember(annotatedString) {
        annotatedString.getStringAnnotations(tag = TAG_WIKI_LINK, start = 0, end = annotatedString.length).isNotEmpty() ||
        annotatedString.getStringAnnotations(tag = TAG_URL_LINK, start = 0, end = annotatedString.length).isNotEmpty()
    }

    if (isClickableCard || !hasLinks) {
        Text(
            text = annotatedString,
            modifier = modifier,
            style = style.copy(color = color)
        )
    } else {
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
}

fun buildInteractiveAnnotatedString(
    text: String,
    defaultColor: Color,
    wikiLinkColor: Color,
    urlColor: Color,
    mathColor: Color = wikiLinkColor
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0

        fun appendCleanedText(raw: String) {
            if (raw.isEmpty()) return
            val unescaped = raw
                .replace("\\%", "%")
                .replace("\\$", "$")
                .replace("\\&", "&")
                .replace("\\#", "#")
                .replace("✦", "")
                .replace("★", "")
            // Never allow raw LaTeX leaks, unparsed math, or stray braces in body text
            val cleaned = if (
                unescaped.contains('\\') ||
                (unescaped.contains('{') && unescaped.contains('}')) ||
                unescaped.contains('^') ||
                unescaped.contains('_') ||
                unescaped.contains("sqrt") ||
                unescaped.contains("--")
            ) {
                LatexParser.cleanInlineLatex(unescaped)
            } else {
                unescaped
            }
            append(cleaned)
        }

        var iter = 0
        for (match in TOKEN_REGEX.findAll(text)) {
            if (iter++ > 3000) break

            // Append plain text before match
            if (match.range.first > lastIndex) {
                appendCleanedText(text.substring(lastIndex, match.range.first))
            }

            val fullMatch = match.value
            when {
                // [[Wiki Link]] (supports [[Chapter Title]] and `[[Chapter Title]]`)
                (fullMatch.startsWith("[[") || fullMatch.startsWith("`[[")) && (fullMatch.endsWith("]]") || fullMatch.endsWith("]]`")) -> {
                    val chapterTitle = match.groupValues[2].trim().removePrefix("[[").removeSuffix("]]").trim()
                    pushStringAnnotation(tag = TAG_WIKI_LINK, annotation = chapterTitle)
                    pushStyle(
                        SpanStyle(
                            color = wikiLinkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(chapterTitle)
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
                    val rawCode = fullMatch.removePrefix("`").removeSuffix("`")
                    val code = if (rawCode.startsWith("[[") && rawCode.endsWith("]]")) {
                        rawCode.removePrefix("[[").removeSuffix("]]").trim()
                    } else {
                        rawCode
                    }
                    if (rawCode.startsWith("[[") && rawCode.endsWith("]]")) {
                        pushStringAnnotation(tag = TAG_WIKI_LINK, annotation = code)
                        pushStyle(
                            SpanStyle(
                                color = wikiLinkColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(code)
                        pop()
                        pop()
                    } else {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x22888888),
                                color = Color(0xFFF43F5E),
                                fontSize = 13.sp
                            )
                        )
                        append(" $code ")
                        pop()
                    }
                }

                // \(inline math\)
                fullMatch.startsWith("\\(") && fullMatch.endsWith("\\)") && fullMatch.length > 4 -> {
                    val mathExpr = fullMatch.removePrefix("\\(").removeSuffix("\\)")
                    val formatted = LatexParser.cleanInlineLatex(mathExpr)
                    pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = mathColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    )
                    append(formatted)
                    pop()
                }

                // \[display math\]
                fullMatch.startsWith("\\[") && fullMatch.endsWith("\\]") && fullMatch.length > 4 -> {
                    val mathExpr = fullMatch.removePrefix("\\[").removeSuffix("\\]")
                    val formatted = LatexParser.cleanInlineLatex(mathExpr)
                    pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = mathColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    )
                    append(formatted)
                    pop()
                }

                // $inline math$
                fullMatch.startsWith("$") && fullMatch.endsWith("$") && fullMatch.length > 2 -> {
                    val mathExpr = fullMatch.removePrefix("$").removeSuffix("$")
                    val formatted = LatexParser.cleanInlineLatex(mathExpr)
                    pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = mathColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    )
                    append(formatted)
                    pop()
                }

                // ~~strikethrough~~
                fullMatch.startsWith("~~") && fullMatch.endsWith("~~") && fullMatch.length >= 4 -> {
                    val strikeText = fullMatch.removePrefix("~~").removeSuffix("~~")
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(strikeText)
                    pop()
                }

                // <u>underline</u>
                fullMatch.startsWith("<u>") && fullMatch.endsWith("</u>") && fullMatch.length >= 7 -> {
                    val underlineText = fullMatch.removePrefix("<u>").removeSuffix("</u>")
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(underlineText)
                    pop()
                }

                // **bold**
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val boldText = fullMatch.removePrefix("**").removeSuffix("**")
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }

                // *italic*
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val italicText = fullMatch.removePrefix("*").removeSuffix("*")
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
