package org.opened.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class WikiLinkExtractorTest {

    private val wikiLinkPattern = Pattern.compile("\\[\\[(.*?)\\]\\]")

    private fun extractWikiLinks(markdown: String): List<String> {
        val matcher = wikiLinkPattern.matcher(markdown)
        val links = mutableListOf<String>()
        while (matcher.find()) {
            val title = matcher.group(1)?.trim().orEmpty()
            if (title.isNotEmpty()) {
                links.add(title)
            }
        }
        return links
    }

    @Test
    fun testBidirectionalWikiLinkExtraction() {
        val markdown = """
            # Attention Mechanisms in Transformers
            Self-attention allows tokens to attend to other tokens in the sequence.
            Refer to [[Multi-Head Attention]] and [[Positional Encoding]] for foundational context.
            For practical applications, see [[Transformer Architecture Overview]].
        """.trimIndent()

        val links = extractWikiLinks(markdown)
        assertEquals(3, links.size)
        assertEquals("Multi-Head Attention", links[0])
        assertEquals("Positional Encoding", links[1])
        assertEquals("Transformer Architecture Overview", links[2])
    }

    @Test
    fun testWikiLinksWithExtraWhitespace() {
        val markdown = "See [[  Neural Tangent Kernel  ]] and [[  Gradient Flow ]]."
        val links = extractWikiLinks(markdown)
        assertEquals(2, links.size)
        assertEquals("Neural Tangent Kernel", links[0])
        assertEquals("Gradient Flow", links[1])
    }

    @Test
    fun testNoWikiLinksInStandardMarkdown() {
        val markdown = "Check the [official docs](https://opened.org) or `InlineCode` for details."
        val links = extractWikiLinks(markdown)
        assertTrue(links.isEmpty())
    }
}
