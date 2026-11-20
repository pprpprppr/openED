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
    fun testInlineMathWithSquareRootAndFractions() {
        val input = "Consider \$\\sqrt{x^2 + y^2}\$ and \$\\frac{1}{2}\$ as inputs."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue("Should format square root with superscripts", annotated.text.contains("√(x² + y²)"))
        assertTrue("Should format fraction", annotated.text.contains("½"))
    }

    @Test
    fun testInlineMathWithChemicalFormulas() {
        val input = "Water is \$\\ce{H2O}\$ and sulfate is \$\\ce{SO4^{2-}}\$."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue("Should format H₂O", annotated.text.contains("H₂O"))
        assertTrue("Should format SO₄²⁻", annotated.text.contains("SO₄²⁻"))
    }

    @Test
    fun testWikiLinksAndMarkdownLinks() {
        val input = "See [[Quantum Electrodynamics]] and [OpenED](https://opened.org)."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue(annotated.text.contains("Quantum Electrodynamics"))
        assertFalse(annotated.text.contains("✦"))
        assertTrue(annotated.text.contains("OpenED"))

        val wikiAnnotations = annotated.getStringAnnotations(tag = TAG_WIKI_LINK, start = 0, end = annotated.length)
        assertEquals(1, wikiAnnotations.size)
        assertEquals("Quantum Electrodynamics", wikiAnnotations.first().item)

        val urlAnnotations = annotated.getStringAnnotations(tag = TAG_URL_LINK, start = 0, end = annotated.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals("https://opened.org", urlAnnotations.first().item)
    }

    @Test
    fun testBacktickWrappedWikiLinks() {
        val input = "Implementing `[[Resource Hierarchy and IAM Governance]]` using folders."
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue(annotated.text.contains("Resource Hierarchy and IAM Governance"))
        assertFalse(annotated.text.contains("[["))
        assertFalse(annotated.text.contains("]]"))
        assertFalse(annotated.text.contains("`"))

        val wikiAnnotations = annotated.getStringAnnotations(tag = TAG_WIKI_LINK, start = 0, end = annotated.length)
        assertEquals(1, wikiAnnotations.size)
        assertEquals("Resource Hierarchy and IAM Governance", wikiAnnotations.first().item)
    }

    @Test
    fun testEscapedSymbolsAndStarStripping() {
        val input = "CPU exceeds 80\\% for more than 5 minutes. ✦ Google Cloud"
        val annotated = buildInteractiveAnnotatedString(input, defaultColor, wikiColor, urlColor)
        assertTrue("Should clean 80\\% to 80%", annotated.text.contains("80%"))
        assertFalse("Should not contain escaped backslash", annotated.text.contains("80\\%"))
        assertFalse("Should strip star ✦", annotated.text.contains("✦"))
        assertTrue("Should contain Google Cloud", annotated.text.contains("Google Cloud"))
    }
}
