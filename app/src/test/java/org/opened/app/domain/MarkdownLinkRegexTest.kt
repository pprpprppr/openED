package org.opened.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinkRegexTest {

    @Test
    fun testWikiLinkExtraction() {
        val content = """
            # eBPF Architecture
            To understand the BPF virtual machine, review [[Linux Kernel Architecture]] first.
            We will also use [[XDP Network Drivers]] and [[Memory Mapping with BPF Ring Buffers]].
        """.trimIndent()

        val regex = Regex("""\[\[(.*?)\]\]""")
        val matches = regex.findAll(content).map { it.groupValues[1].trim() }.toList()

        assertEquals(3, matches.size)
        assertEquals("Linux Kernel Architecture", matches[0])
        assertEquals("XDP Network Drivers", matches[1])
        assertEquals("Memory Mapping with BPF Ring Buffers", matches[2])
    }

    @Test
    fun testNoWikiLinksFound() {
        val content = "This is plain markdown with [a normal link](https://example.com) and `inline code`."
        val regex = Regex("""\[\[(.*?)\]\]""")
        val matches = regex.findAll(content).toList()
        assertTrue(matches.isEmpty())
    }
}
