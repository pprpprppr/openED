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
}
