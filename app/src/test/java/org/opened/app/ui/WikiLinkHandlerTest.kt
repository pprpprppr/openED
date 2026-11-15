package org.opened.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.ui.components.TAG_URL_LINK
import org.opened.app.ui.components.TAG_WIKI_LINK
import org.opened.app.ui.components.buildInteractiveAnnotatedString

class WikiLinkHandlerTest {

    private val defaultColor = Color.Black
    private val wikiColor = Color.Blue
    private val urlColor = Color.Cyan

    @Test
    fun testStrikethroughFormatting() {
        val input = "This is ~~deprecated code~~ in the lesson."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue(annotated.text.contains("deprecated code"))
        val span = annotated.spanStyles.find { it.item.textDecoration == TextDecoration.LineThrough }
        assertTrue("Should have LineThrough decoration", span != null)
    }

    @Test
    fun testUnderlineFormatting() {
        val input = "This is <u>important emphasis</u> in the lesson."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue(annotated.text.contains("important emphasis"))
        val span = annotated.spanStyles.find { it.item.textDecoration == TextDecoration.Underline }
        assertTrue("Should have Underline decoration", span != null)
    }

    @Test
    fun testInlineMathRendering() {
        val input = "The mass-energy equivalence is \$E = mc^2\$ as stated by Einstein."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue("Should contain cleaned formula E = mc²", annotated.text.contains("E = mc²"))
        assertFalse("Should not contain raw dollar signs", annotated.text.contains("\$E"))
    }

    @Test
    fun testWikiLinksAndMarkdownLinks() {
        val input = "See [[Quantum Electrodynamics]] and [OpenED](https://opened.org)."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue(annotated.text.contains("✦ Quantum Electrodynamics"))
        assertTrue(annotated.text.contains("OpenED"))

        val wikiAnnotations = annotated.getStringAnnotations(tag = TAG_WIKI_LINK, start = 0, end = annotated.length)
        assertEquals(1, wikiAnnotations.size)
        assertEquals("Quantum Electrodynamics", wikiAnnotations.first().item)

        val urlAnnotations = annotated.getStringAnnotations(tag = TAG_URL_LINK, start = 0, end = annotated.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals("https://opened.org", urlAnnotations.first().item)
    }
}
