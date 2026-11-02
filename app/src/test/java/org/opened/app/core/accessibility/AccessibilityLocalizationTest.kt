package org.opened.app.core.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.ui.components.LatexParser

class AccessibilityLocalizationTest {

    @Test
    fun testLatexToSpokenDescriptionFractionsAndRoots() {
        val latex = "\\frac{a + b}{c - d}"
        val spoken = LatexParser.toSpokenDescription(latex)
        assertTrue(spoken.contains("fraction with numerator a + b over denominator c - d"))

        val sqrt = "\\sqrt{x^2 + y^2}"
        val spokenSqrt = LatexParser.toSpokenDescription(sqrt)
        assertTrue(spokenSqrt.contains("square root of"))
        assertTrue(spokenSqrt.contains("squared"))
    }

    @Test
    fun testLatexToSpokenDescriptionCalculusAndGreek() {
        val integral = "\\int_{0}^{\\infty} e^{-x} dx"
        val spokenIntegral = LatexParser.toSpokenDescription(integral)
        assertTrue(spokenIntegral.contains("integral from 0 to infinity"))

        val greek = "\\alpha + \\beta = \\theta"
        val spokenGreek = LatexParser.toSpokenDescription(greek)
        assertEquals("alpha + beta = theta", spokenGreek)
    }

    @Test
    fun testCleanInlineLatexPreservesDisplayLegibility() {
        val raw = "\$E = mc^2\$"
        val cleaned = LatexParser.cleanInlineLatex(raw)
        assertEquals("E = mc²", cleaned)
    }
}
