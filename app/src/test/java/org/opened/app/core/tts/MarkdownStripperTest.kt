package org.opened.app.core.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStripperTest {

    @Test
    fun testStripsDiagramAndChartBlocks() {
        val markdown = """
            # Architecture Overview
            Here is the flow of data:
            ```mermaid
            graph TD;
                A-->B;
                B-->C;
            ```
            ```diagram
            [Client] -> [Server]
            ```
            ```chart
            x: [1, 2, 3], y: [4, 5, 6]
            ```
            The final step completes the process.
        """.trimIndent()

        val clean = MarkdownStripper.cleanMarkdownSync(markdown)

        assertFalse("Should not contain mermaid fence", clean.contains("```mermaid"))
        assertFalse("Should not contain diagram fence", clean.contains("```diagram"))
        assertFalse("Should not contain chart fence", clean.contains("```chart"))
        assertTrue("Should include spoken cue for omitted diagram", clean.contains("Diagram omitted"))
        assertTrue("Should retain narrative text", clean.contains("Architecture Overview"))
        assertTrue("Should retain final step", clean.contains("The final step completes the process."))
    }

    @Test
    fun testReplacesFencedCodeBlocksWithSpokenCue() {
        val markdown = """
            Let's see an implementation in Python:
            ```python
            def compute_loss(y_true, y_pred):
                return ((y_true - y_pred) ** 2).mean()
            ```
            This loss function penalizes large deviations.
        """.trimIndent()

        val clean = MarkdownStripper.cleanMarkdownSync(markdown)

        assertFalse("Should not contain python code fence", clean.contains("```python"))
        assertFalse("Should not read raw code punctuation aloud", clean.contains("def compute_loss"))
        assertTrue("Should summarize code block", clean.contains("Code example omitted during audio playback"))
        assertTrue("Should retain explanation text", clean.contains("This loss function penalizes large deviations."))
    }

    @Test
    fun testConvertsMathSymbolsToWords() {
        val markdown = "A → B, where x ≥ 0 and y ≤ 10, so x ≠ y and a ≈ b."
        val clean = MarkdownStripper.cleanMarkdownSync(markdown)

        assertTrue("Should convert → to 'leads to'", clean.contains("leads to"))
        assertTrue("Should convert ≥ to 'greater than or equal to'", clean.contains("greater than or equal to"))
        assertTrue("Should convert ≤ to 'less than or equal to'", clean.contains("less than or equal to"))
        assertTrue("Should convert ≠ to 'not equal to'", clean.contains("not equal to"))
        assertTrue("Should convert ≈ to 'approximately equals'", clean.contains("approximately equals"))
    }

    @Test
    fun testSplits12000CharChapterIntoChunksStrictlyUnder2000Chars() {
        // Build a realistic 12,000-character chapter with headings, paragraphs, diagrams, and math
        val sb = StringBuilder()
        sb.append("# Comprehensive Guide to Machine Learning Optimization\n\n")
        sb.append("Optimization is the mathematical core of modern machine learning algorithms.\n\n")

        var paragraphIndex = 1
        while (sb.length < 12000) {
            sb.append("## Section $paragraphIndex: Core Principles\n\n")
            sb.append("In this segment of the curriculum, we analyze gradient-based updates where w_{t+1} = w_t - η ∇L(w_t). ")
            sb.append("When evaluating deep convex structures, the parameter updates satisfy x ≥ 0 and y ≤ 10. ")
            sb.append("Notice that the loss objective function guarantees that error → 0 as iterations increase. ")
            sb.append("We also note that empirical risk minimization provides statistical bounds across validation splits.\n\n")

            if (paragraphIndex % 3 == 0) {
                sb.append("```mermaid\ngraph LR;\n    Init-->Forward-->Backward-->Update;\n```\n\n")
            } else if (paragraphIndex % 4 == 0) {
                sb.append("```python\nfor epoch in range(epochs):\n    opt.zero_grad()\n    loss.backward()\n```\n\n")
            }
            paragraphIndex++
        }

        val rawText = sb.toString()
        assertTrue("Input test text should be at least 12,000 characters", rawText.length >= 12000)

        val clean = MarkdownStripper.cleanMarkdownSync(rawText)
        val chunks = MarkdownStripper.chunkForSpeech(clean, maxChunkChars = 1800)

        assertTrue("Should produce multiple chunks", chunks.size >= 6)
        for ((idx, chunk) in chunks.withIndex()) {
            assertTrue("Chunk #$idx length (${chunk.length}) must be strictly < 2000 chars", chunk.length < 2000)
            assertTrue("Chunk #$idx must be <= 1800 chars target limit", chunk.length <= 1800)
            assertFalse("Chunk #$idx must not contain mermaid", chunk.contains("```mermaid"))
        }
    }
}
