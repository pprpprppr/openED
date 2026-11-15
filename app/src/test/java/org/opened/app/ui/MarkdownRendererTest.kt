package org.opened.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.ui.components.MarkdownNode
import org.opened.app.ui.components.parseMarkdown

class MarkdownRendererTest {

    @Test
    fun testStreamingIncompleteTableHeaderDoesNotInfiniteLoop() {
        // A single line of pipes as emitted during SSE streaming
        val streamingChunk = "| Column 1 | Column 2 |"
        val nodes = parseMarkdown(streamingChunk)
        assertNotNull(nodes)
        assertEquals(1, nodes.size)
        assertTrue(nodes.first() is MarkdownNode.Paragraph)
        assertEquals("| Column 1 | Column 2 |", (nodes.first() as MarkdownNode.Paragraph).text)
    }

    @Test
    fun testStreamingIncompleteTableWithDelimiterDoesNotInfiniteLoop() {
        val streamingTable = """
            | Column 1 | Column 2 |
            | --- | --- |
        """.trimIndent()
        val nodes = parseMarkdown(streamingTable)
        assertNotNull(nodes)
        assertEquals(1, nodes.size)
        assertTrue(nodes.first() is MarkdownNode.Table)
        val table = nodes.first() as MarkdownNode.Table
        assertEquals(listOf("Column 1", "Column 2"), table.headers)
        assertEquals(0, table.rows.size)
    }

    @Test
    fun testCompleteTableParsedCorrectly() {
        val markdown = """
            | Concept | Definition |
            | --- | --- |
            | Gradient Descent | Optimization algorithm |
            | Backpropagation | Gradient calculation |
        """.trimIndent()
        val nodes = parseMarkdown(markdown)
        assertEquals(1, nodes.size)
        assertTrue(nodes.first() is MarkdownNode.Table)
        val table = nodes.first() as MarkdownNode.Table
        assertEquals(listOf("Concept", "Definition"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Gradient Descent", "Optimization algorithm"), table.rows[0])
    }

    @Test
    fun testUnclosedCodeFenceDoesNotCrashOrLoop() {
        val streamingCode = """
            ```python
            def train_model(epochs):
                for epoch in range(epochs):
                    loss = compute_loss()
        """.trimIndent()
        val nodes = parseMarkdown(streamingCode)
        assertEquals(1, nodes.size)
        assertTrue(nodes.first() is MarkdownNode.Code)
        val codeNode = nodes.first() as MarkdownNode.Code
        assertEquals("python", codeNode.language)
        assertEquals(false, codeNode.isComplete)
    }

    @Test
    fun testUnclosedMathBlockDoesNotCrashOrLoop() {
        val streamingMath = """
            $$
            \mathcal{L}_{total} = \frac{1}{N} \sum_{i=1}^N (y_i - \hat{y}_i)^2
        """.trimIndent()
        val nodes = parseMarkdown(streamingMath)
        assertEquals(1, nodes.size)
        assertTrue(nodes.first() is MarkdownNode.Math)
        val mathNode = nodes.first() as MarkdownNode.Math
        assertEquals(false, mathNode.isComplete)
    }

    @Test
    fun testUnclosedBracketMathBlockDoesNotCrashOrLoop() {
        val streamingMath = """
            \[
            E = mc^2
        """.trimIndent()
        val nodes = parseMarkdown(streamingMath)
        assertEquals(1, nodes.size)
        assertTrue(nodes.first() is MarkdownNode.Math)
        val mathNode = nodes.first() as MarkdownNode.Math
        assertEquals(false, mathNode.isComplete)
    }

    @Test
    fun testHorizontalRulesParsedCorrectly() {
        val markdown = """
            Section 1
            ---
            Section 2
            ***
            Section 3
            ___
            End
        """.trimIndent()
        val nodes = parseMarkdown(markdown)
        assertTrue(nodes.any { it is MarkdownNode.Divider })
    }

    @Test
    fun testArbitraryMalformedStreamingCharactersAlwaysTerminates() {
        val weirdInputs = listOf(
            "|",
            "|||",
            "| | |",
            "> [!",
            "> [!NOTE",
            "#",
            "1.",
            "-",
            "$$",
            "\\[",
            "```",
            "```\n\n\n",
            "| Col 1 |\n| --- |\n| row 1 |",
            "| Incomplete row |"
        )
        for (input in weirdInputs) {
            val startTime = System.currentTimeMillis()
            val nodes = parseMarkdown(input)
            val duration = System.currentTimeMillis() - startTime
            assertNotNull(nodes)
            assertTrue("Parsing '$input' took too long ($duration ms) - possible infinite loop!", duration < 1000)
        }
    }

    @Test
    fun testMathCodeFenceParsedAsMathNode() {
        val markdown = """
            ```math
            \int_{0}^{1} x^2 dx = \frac{1}{3}
            ```
        """.trimIndent()
        val nodes = parseMarkdown(markdown)
        assertEquals(1, nodes.size)
        assertTrue("Node should be Math", nodes.first() is MarkdownNode.Math)
        val mathNode = nodes.first() as MarkdownNode.Math
        assertTrue("Math expression should contain integral", mathNode.expression.contains("\\int"))
        assertTrue("Math block should be closed", mathNode.isComplete)
    }

    @Test
    fun testLatexEnvironmentParsedAsMathNode() {
        val markdown = """
            \begin{equation}
            E = mc^2
            \end{equation}
        """.trimIndent()
        val nodes = parseMarkdown(markdown)
        assertEquals(1, nodes.size)
        assertTrue("Node should be Math", nodes.first() is MarkdownNode.Math)
        val mathNode = nodes.first() as MarkdownNode.Math
        assertTrue("Expression should contain equation", mathNode.expression.contains("E = mc^2"))
        assertTrue("Block should be complete", mathNode.isComplete)
    }

    @Test
    fun testTaskListItemsParsedCorrectly() {
        val markdown = """
            - [ ] Task incomplete
            - [x] Task complete
            * [X] Task also complete
        """.trimIndent()
        val nodes = parseMarkdown(markdown)
        assertEquals(3, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.TaskItem)
        assertEquals(false, (nodes[0] as MarkdownNode.TaskItem).checked)
        assertEquals("Task incomplete", (nodes[0] as MarkdownNode.TaskItem).text)

        assertTrue(nodes[1] is MarkdownNode.TaskItem)
        assertEquals(true, (nodes[1] as MarkdownNode.TaskItem).checked)
        assertEquals("Task complete", (nodes[1] as MarkdownNode.TaskItem).text)

        assertTrue(nodes[2] is MarkdownNode.TaskItem)
        assertEquals(true, (nodes[2] as MarkdownNode.TaskItem).checked)
        assertEquals("Task also complete", (nodes[2] as MarkdownNode.TaskItem).text)
    }

    @Test
    fun testParagraphWithDisplayMathSplitsCorrectly() {
        val markdown = "Before the equation \$\$E = mc^2\$\$ after the equation."
        val nodes = parseMarkdown(markdown)
        assertEquals(3, nodes.size)
        assertTrue("First node should be Paragraph", nodes[0] is MarkdownNode.Paragraph)
        assertEquals("Before the equation", (nodes[0] as MarkdownNode.Paragraph).text)
        assertTrue("Second node should be Math", nodes[1] is MarkdownNode.Math)
        assertEquals("E = mc^2", (nodes[1] as MarkdownNode.Math).expression)
        assertTrue("Third node should be Paragraph", nodes[2] is MarkdownNode.Paragraph)
        assertEquals("after the equation.", (nodes[2] as MarkdownNode.Paragraph).text)
    }

    @Test(timeout = 2000L)
    fun testParseMarkdownPerformanceOn10000CharHalfStreamedWithUnclosedBraces() {
        val sb = StringBuilder()
        while (sb.length < 10000) {
            sb.append("This is an unclosed streaming text with LaTeX \\frac{numerator without closing brace and {nested {broken braces. ")
            sb.append("Also inline math \$x + y = \\sqrt{incomplete math and \\mathbf{bold without close. ")
            sb.append("| Column 1 | Column 2 |\n")
            sb.append("```python\ndef half_streamed():\n")
        }
        val text = sb.toString()
        assertTrue(text.length >= 10000)

        val startTime = System.currentTimeMillis()
        val nodes = parseMarkdown(text)
        val duration = System.currentTimeMillis() - startTime

        assertNotNull(nodes)
        assertTrue("Expected parsing to complete in < 100ms, took $duration ms", duration < 100)
    }
}
