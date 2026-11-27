package org.opened.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
    data class Code(val language: String, val content: String, val isComplete: Boolean = true) : MarkdownNode()
    data class Math(val expression: String, val isComplete: Boolean = true) : MarkdownNode()
    data class Diagram(val content: String, val isComplete: Boolean = true) : MarkdownNode()
    data class Chart(val content: String, val isComplete: Boolean = true) : MarkdownNode()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownNode()
    data class Callout(val type: CalloutType, val title: String?, val content: String) : MarkdownNode()
    data class Quote(val text: String) : MarkdownNode()
    data class ListItem(val ordered: Boolean, val number: Int, val text: String) : MarkdownNode()
    data class TaskItem(val checked: Boolean, val text: String) : MarkdownNode()
    data class Paragraph(val text: String) : MarkdownNode()
    object Divider : MarkdownNode()
}

@Composable
fun RenderingPlaceholderCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RichMarkdownRenderer(
    markdown: String,
    onChapterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
    isClickableCard: Boolean = false
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val nodes by androidx.compose.runtime.produceState<List<MarkdownNode>>(
        initialValue = emptyList(),
        key1 = markdown
    ) {
        value = if (markdown.isBlank()) {
            emptyList()
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    parseMarkdown(markdown).filter { node ->
                        when (node) {
                            is MarkdownNode.Paragraph -> node.text.isNotBlank()
                            else -> true
                        }
                    }
                }.getOrElse { emptyList() }
            }
        }
    }

    if (nodes.isEmpty()) {
        return
    }

    val content = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            nodes.forEachIndexed { index, node ->
                when (node) {
                    is MarkdownNode.Heading -> {
                        Spacer(modifier = Modifier.height(if (index == 0) 8.dp else 20.dp))
                        when (node.level) {
                            1 -> InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(bottom = 6.dp),
                                isClickableCard = isClickableCard
                            )
                            2 -> InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(bottom = 6.dp),
                                isClickableCard = isClickableCard
                            )
                            3 -> InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(bottom = 4.dp),
                                isClickableCard = isClickableCard
                            )
                            else -> InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(bottom = 4.dp),
                                isClickableCard = isClickableCard
                            )
                        }
                    }

                    is MarkdownNode.Code -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        val cleanLang = node.language.trim().lowercase()
                        if (cleanLang.startsWith("interactive-lab:") || cleanLang == "interactive-lab") {
                            val labType = cleanLang.removePrefix("interactive-lab:").trim()
                            when (labType) {
                                "math" -> org.opened.app.ui.laboratory.InteractiveMathPlotCanvas()
                                "physics" -> org.opened.app.ui.laboratory.InteractivePhysicsSimulationCanvas()
                                "economics", "economy" -> org.opened.app.ui.laboratory.InteractiveEconomicsMarketCanvas()
                                "biology", "bio" -> org.opened.app.ui.laboratory.InteractiveBiologicalPathwayCanvas()
                                "timeline", "history" -> org.opened.app.ui.laboratory.InteractiveHistoricalTimelineCanvas()
                                "argument", "logic" -> org.opened.app.ui.laboratory.InteractiveLogicalArgumentMapCanvas()
                                else -> org.opened.app.ui.laboratory.InteractiveMathPlotCanvas()
                            }
                        } else {
                            CodeBlockView(code = node.content, language = node.language)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Math -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        MathBlockView(mathExpression = node.expression, isComplete = node.isComplete)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Diagram -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        DiagramBlockView(diagramCode = node.content, isComplete = node.isComplete)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Chart -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        ChartBlockView(chartCode = node.content, isComplete = node.isComplete)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is MarkdownNode.Table -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        TableBlockView(
                            headers = node.headers,
                            rows = node.rows,
                            onChapterClick = onChapterClick,
                            isClickableCard = isClickableCard
                        )
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    isClickableCard = isClickableCard
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
                                ),
                                modifier = Modifier.weight(1f),
                                isClickableCard = isClickableCard
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
                                modifier = Modifier.weight(1f),
                                isClickableCard = isClickableCard
                            )
                        }
                    }

                    is MarkdownNode.TaskItem -> {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (node.checked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (node.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(5.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (node.checked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = if (node.checked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (node.checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                                ),
                                modifier = Modifier.weight(1f),
                                isClickableCard = isClickableCard
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    is MarkdownNode.Paragraph -> {
                        if (node.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            InteractiveMarkdownText(
                                text = node.text,
                                onChapterClick = onChapterClick,
                                style = MaterialTheme.typography.bodyLarge,
                                isClickableCard = isClickableCard
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
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

    if (selectable) {
        SelectionContainer(modifier = modifier) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun TableBlockView(
    headers: List<String>,
    rows: List<List<String>>,
    onChapterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isClickableCard: Boolean = false
) {
    if (headers.isEmpty()) return
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val headerBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val altRowBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(headerBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.forEachIndexed { colIdx, header ->
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // Rows
                rows.forEachIndexed { rowIdx, rowCols ->
                    val bg = if (rowIdx % 2 == 1) altRowBg else Color.Transparent
                    Row(
                        modifier = Modifier
                            .background(bg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headers.indices.forEach { colIdx ->
                            val cellText = rowCols.getOrNull(colIdx) ?: ""
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .padding(horizontal = 6.dp)
                            ) {
                                InteractiveMarkdownText(
                                    text = cellText,
                                    onChapterClick = onChapterClick,
                                    style = MaterialTheme.typography.bodyMedium,
                                    isClickableCard = isClickableCard
                                )
                            }
                        }
                    }
                    if (rowIdx < rows.size - 1) {
                        HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val first = lines[index].trim()
    val second = lines[index + 1].trim()
    return first.startsWith("|") && first.endsWith("|") &&
           second.startsWith("|") && second.contains("---")
}

private val TASK_ITEM_REGEX = Regex("""^[-*]\s+\[([ xX])\]\s+(.*)""")
private val ORDERED_LIST_REGEX = Regex("""^(\d+)\.\s+(.*)""")
private val TASK_PREFIX_REGEX = Regex("""^[-*]\s+\[([ xX])\]""")
private val ORDERED_PREFIX_REGEX = Regex("""^(\d+)\.\s+(.*)""")
private val DISPLAY_MATH_REGEX = Regex("""(\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\])""")

private val markdownAstCache = SimpleLruCache<String, List<MarkdownNode>>(64)

fun parseMarkdown(raw: String): List<MarkdownNode> {
    if (raw.isBlank()) return emptyList()
    return markdownAstCache.getOrPut(raw) {
        parseMarkdownInternal(raw)
    }
}

private fun parseMarkdownInternal(raw: String): List<MarkdownNode> = runCatching {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val startI = i
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

        // Code, Diagram, or Chart block
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            var closed = false
            while (i < lines.size) {
                if (lines[i].trim().startsWith("```")) {
                    closed = true
                    i++ // skip closing fence
                    break
                }
                codeLines.add(lines[i])
                i++
            }
            val content = codeLines.joinToString("\n")
            when (language.lowercase()) {
                "mermaid", "diagram" -> nodes.add(MarkdownNode.Diagram(content, isComplete = closed))
                "chart", "plot" -> nodes.add(MarkdownNode.Chart(content, isComplete = closed))
                "math", "latex", "tex" -> nodes.add(MarkdownNode.Math(content, isComplete = closed))
                else -> nodes.add(MarkdownNode.Code(language = language, content = content, isComplete = closed))
            }
            continue
        }

        // LaTeX environment: \begin{equation}, \begin{align}, \begin{gather}, \begin{aligned}
        if (trimmed.startsWith("\\begin{equation") || trimmed.startsWith("\\begin{align") ||
            trimmed.startsWith("\\begin{gather") || trimmed.startsWith("\\begin{aligned") ||
            trimmed.startsWith("\\begin{multline")
        ) {
            val mathLines = mutableListOf<String>()
            mathLines.add(line)
            i++
            var closed = false
            while (i < lines.size) {
                val cur = lines[i]
                mathLines.add(cur)
                if (cur.contains("\\end{equation") || cur.contains("\\end{align") ||
                    cur.contains("\\end{gather") || cur.contains("\\end{aligned") ||
                    cur.contains("\\end{multline")
                ) {
                    closed = true
                    i++
                    break
                }
                i++
            }
            nodes.add(MarkdownNode.Math(mathLines.joinToString("\n").trim(), isComplete = closed))
            continue
        }

        // Math block $$ ... $$ or \[ ... \]
        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
            val isBracket = trimmed.startsWith("\\[")
            val closingDelimiter = if (isBracket) "\\]" else "$$"
            val prefix = if (isBracket) "\\[" else "$$"
            val mathLines = mutableListOf<String>()
            if (trimmed.endsWith(closingDelimiter) && trimmed.length > prefix.length + closingDelimiter.length) {
                // single line math block
                nodes.add(MarkdownNode.Math(trimmed.removePrefix(prefix).removeSuffix(closingDelimiter).trim(), isComplete = true))
                i++
                continue
            }
            mathLines.add(trimmed.removePrefix(prefix))
            i++
            var closed = false
            while (i < lines.size) {
                if (lines[i].trim().endsWith(closingDelimiter)) {
                    mathLines.add(lines[i].trim().removeSuffix(closingDelimiter))
                    closed = true
                    i++
                    break
                }
                mathLines.add(lines[i])
                i++
            }
            nodes.add(MarkdownNode.Math(mathLines.joinToString("\n").trim(), isComplete = closed))
            continue
        }

        // Markdown Table: | Header 1 | Header 2 |
        if (isTableStart(lines, i)) {
            val headers = trimmed.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            i += 2 // skip header and delimiter

            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                val cells = lines[i].trim().split("|")
                    .map { it.trim() }
                    .filterIndexed { index, _ -> index > 0 && index <= headers.size }
                rows.add(cells)
                i++
            }
            nodes.add(MarkdownNode.Table(headers = headers, rows = rows))
            continue
        }

        // Callout Alert: > [!NOTE], > [!TIP], > [!IMPORTANT], > [!WARNING], > [!CAUTION]
        if (trimmed.startsWith("> [!")) {
            val typeStr = trimmed.substringAfter("> [!").substringBefore("]").uppercase()
            val customTitle = trimmed.substringAfter("]", "").trim().ifBlank { null }
            val calloutType = try {
                CalloutType.valueOf(typeStr)
            } catch (e: Exception) {
                when (typeStr) {
                    "INFO" -> CalloutType.NOTE
                    "EXAMPLE", "HINT" -> CalloutType.TIP
                    "QUESTION", "FAQ" -> CalloutType.NOTE
                    "SUMMARY", "KEY" -> CalloutType.IMPORTANT
                    "DANGER", "CRITICAL" -> CalloutType.CAUTION
                    else -> CalloutType.NOTE
                }
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

        // Task list item: - [ ] or - [x] or * [ ] or * [x]
        val taskMatch = TASK_ITEM_REGEX.matchEntire(trimmed)
        if (taskMatch != null) {
            val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
            val itemText = taskMatch.groupValues[2]
            nodes.add(MarkdownNode.TaskItem(checked = isChecked, text = itemText))
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
        val orderedMatch = ORDERED_LIST_REGEX.matchEntire(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            val itemText = orderedMatch.groupValues[2]
            nodes.add(MarkdownNode.ListItem(ordered = true, number = num, text = itemText))
            i++
            continue
        }

        // Paragraph - accumulate consecutive plain lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val currentTrimmed = lines[i].trim()
            if (currentTrimmed.isEmpty()) break
            if (currentTrimmed == "---" || currentTrimmed == "***" || currentTrimmed == "___") break
            if (currentTrimmed.startsWith("#")) break
            if (currentTrimmed.startsWith("```")) break
            if (currentTrimmed.startsWith("$$") || currentTrimmed.startsWith("\\[")) break
            if (currentTrimmed.startsWith("\\begin{equation") || currentTrimmed.startsWith("\\begin{align") ||
                currentTrimmed.startsWith("\\begin{gather") || currentTrimmed.startsWith("\\begin{aligned") ||
                currentTrimmed.startsWith("\\begin{multline")
            ) break
            if (currentTrimmed.startsWith(">")) break
            if (TASK_PREFIX_REGEX.containsMatchIn(currentTrimmed)) break
            if (currentTrimmed.startsWith("- ") || currentTrimmed.startsWith("* ")) break
            if (ORDERED_PREFIX_REGEX.matches(currentTrimmed)) break
            if (isTableStart(lines, i)) break

            paragraphLines.add(currentTrimmed)
            i++
        }

        if (paragraphLines.isNotEmpty()) {
            val paragraphContent = paragraphLines.joinToString(" ").trim()
            if (paragraphContent.isNotBlank()) {
                if (DISPLAY_MATH_REGEX.containsMatchIn(paragraphContent)) {
                    var lastIdx = 0
                    for (match in DISPLAY_MATH_REGEX.findAll(paragraphContent)) {
                        if (match.range.first > lastIdx) {
                            val part = paragraphContent.substring(lastIdx, match.range.first).trim()
                            if (part.isNotEmpty()) {
                                nodes.add(MarkdownNode.Paragraph(part))
                            }
                        }
                        val mathContent = match.value
                            .removePrefix("$$").removeSuffix("$$")
                            .removePrefix("\\[").removeSuffix("\\]")
                            .trim()
                        if (mathContent.isNotEmpty()) {
                            nodes.add(MarkdownNode.Math(mathContent, isComplete = true))
                        }
                        lastIdx = match.range.last + 1
                    }
                    if (lastIdx < paragraphContent.length) {
                        val remaining = paragraphContent.substring(lastIdx).trim()
                        if (remaining.isNotEmpty()) {
                            nodes.add(MarkdownNode.Paragraph(remaining))
                        }
                    }
                } else {
                    nodes.add(MarkdownNode.Paragraph(paragraphContent))
                }
            }
        }

        // Guaranteed progress safeguard: ensure index i advances to avoid any infinite loop
        if (i == startI) {
            val fallbackLine = lines[i].trim()
            if (fallbackLine.isNotEmpty()) {
                nodes.add(MarkdownNode.Paragraph(fallbackLine))
            }
            i++
        }
    }

    nodes
}.getOrElse { emptyList() }
