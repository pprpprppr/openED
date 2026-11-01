package org.opened.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownNode {
    data class Heading(val level: Int, val text: String) : MarkdownNode()
    data class Code(val language: String, val content: String) : MarkdownNode()
    data class Math(val expression: String) : MarkdownNode()
    data class Callout(val type: CalloutType, val title: String?, val content: String) : MarkdownNode()
    data class Quote(val text: String) : MarkdownNode()
    data class ListItem(val ordered: Boolean, val number: Int, val text: String) : MarkdownNode()
    data class Paragraph(val text: String) : MarkdownNode()
    object Divider : MarkdownNode()
}

@Composable
fun RichMarkdownRenderer(
    markdown: String,
    onChapterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes = remember(markdown) {
        parseMarkdown(markdown)
    }

    SelectionContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            nodes.forEachIndexed { index, node ->
                when (node) {
                    is MarkdownNode.Heading -> {
                        Spacer(modifier = Modifier.height(if (index == 0) 8.dp else 20.dp))
                        when (node.level) {
                            1 -> Text(
                                text = node.text,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            2 -> Text(
                                text = node.text,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            3 -> Text(
                                text = node.text,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            else -> Text(
                                text = node.text,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    is MarkdownNode.Code -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        CodeBlockView(code = node.content, language = node.language)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Math -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        MathBlockView(mathExpression = node.expression)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Callout -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        CalloutBlockView(
                            type = node.type,
                            customTitle = node.title,
                            content = {
                                InteractiveMarkdownText(
                                    text = node.content,
                                    onChapterClick = onChapterClick,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Quote -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    is MarkdownNode.ListItem -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (node.ordered) {
                                Text(
                                    text = "${node.number}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(26.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 9.dp, end = 12.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownNode.Paragraph -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        InteractiveMarkdownText(
                            text = node.text,
                            onChapterClick = onChapterClick,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    is MarkdownNode.Divider -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

private fun parseMarkdown(raw: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Horizontal rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            nodes.add(MarkdownNode.Divider)
            i++
            continue
        }

        // Code block
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing fence
            nodes.add(MarkdownNode.Code(language = language, content = codeLines.joinToString("\n")))
            continue
        }

        // Math block $$ ... $$
        if (trimmed.startsWith("$$")) {
            val mathLines = mutableListOf<String>()
            if (trimmed.endsWith("$$") && trimmed.length > 2) {
                // single line math block
                nodes.add(MarkdownNode.Math(trimmed.removePrefix("$$").removeSuffix("$$").trim()))
                i++
                continue
            }
            mathLines.add(trimmed.removePrefix("$$"))
            i++
            while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                mathLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                mathLines.add(lines[i].trim().removeSuffix("$$"))
                i++
            }
            nodes.add(MarkdownNode.Math(mathLines.joinToString("\n").trim()))
            continue
        }

        // Callout Alert: > [!NOTE], > [!TIP], > [!IMPORTANT], > [!WARNING], > [!CAUTION]
        if (trimmed.startsWith("> [!")) {
            val typeStr = trimmed.substringAfter("> [!").substringBefore("]").uppercase()
            val customTitle = trimmed.substringAfter("]", "").trim().ifBlank { null }
            val calloutType = try {
                CalloutType.valueOf(typeStr)
            } catch (e: Exception) {
                CalloutType.NOTE
            }

            val bodyLines = mutableListOf<String>()
            i++
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                val quoteLine = lines[i].trim().removePrefix(">").trimStart()
                bodyLines.add(quoteLine)
                i++
            }
            nodes.add(
                MarkdownNode.Callout(
                    type = calloutType,
                    title = customTitle,
                    content = bodyLines.joinToString("\n").trim()
                )
            )
            continue
        }

        // Standard blockquote: > ...
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            nodes.add(MarkdownNode.Quote(quoteLines.joinToString(" ")))
            continue
        }

        // Headings: #, ##, ###, ####
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val headingText = trimmed.removePrefix("#".repeat(level)).trim()
            nodes.add(MarkdownNode.Heading(level = level, text = headingText))
            i++
            continue
        }

        // Unordered list: - or *
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val itemText = trimmed.substring(2).trim()
            nodes.add(MarkdownNode.ListItem(ordered = false, number = 0, text = itemText))
            i++
            continue
        }

        // Ordered list: 1. 2.
        val orderedMatch = Regex("""^(\d+)\.\s+(.*)""").matchEntire(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            val itemText = orderedMatch.groupValues[2]
            nodes.add(MarkdownNode.ListItem(ordered = true, number = num, text = itemText))
            i++
            continue
        }

        // Paragraph - accumulate consecutive plain lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().isNotEmpty() &&
            !lines[i].trim().startsWith("#") &&
            !lines[i].trim().startsWith("```") &&
            !lines[i].trim().startsWith("$$") &&
            !lines[i].trim().startsWith(">") &&
            !lines[i].trim().startsWith("- ") &&
            !lines[i].trim().startsWith("* ") &&
            !Regex("""^(\d+)\.\s+(.*)""").matches(lines[i].trim()) &&
            lines[i].trim() != "---"
        ) {
            paragraphLines.add(lines[i].trim())
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            nodes.add(MarkdownNode.Paragraph(paragraphLines.joinToString(" ")))
        }
    }

    return nodes
}
