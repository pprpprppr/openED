package org.opened.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.ui.components.ensureDelimiters
import org.opened.app.ui.components.hasMathContent

class KaTeXViewTest {

    @Test
    fun testHasMathContentDetectsInlineMath() {
        assertTrue(hasMathContent("Find the value of ${'$'}x^2 + y^2 = r^2${'$'}."))
        assertTrue(hasMathContent("Consider ${'$'}\\frac{1}{2}${'$'} as a probability."))
        assertTrue(hasMathContent("The eigenvalue satisfies ${'$'}\\lambda \\in \\mathbb{R}${'$'}."))
    }

    @Test
    fun testHasMathContentDetectsDisplayMath() {
        assertTrue(hasMathContent("$$\\int_{-\\infty}^\\infty e^{-x^2} dx = \\sqrt{\\pi}$$"))
        assertTrue(hasMathContent("\\[E = mc^2\\]"))
        assertTrue(hasMathContent("\\begin{equation}x=y\\end{equation}"))
        assertTrue(hasMathContent("\\begin{pmatrix}1 & 0\\\\ 0 & 1\\end{pmatrix}"))
    }

    @Test
    fun testHasMathContentDetectsBareLatexCommands() {
        assertTrue(hasMathContent("\\frac{\\sqrt{3}}{2}"))
        assertTrue(hasMathContent("\\sum_{i=1}^n x_i"))
        assertTrue(hasMathContent("\\lim_{x \\to 0} \\frac{\\sin x}{x}"))
        assertTrue(hasMathContent("\\ce{2H2 + O2 -> 2H2O}"))
        assertTrue(hasMathContent("\\sqrt{N}"))
        assertTrue(hasMathContent("\\nabla \\times \\mathbf{B}"))
    }

    @Test
    fun testHasMathContentReturnsFalseForPlainText() {
        assertFalse(hasMathContent("This is standard text with no math whatsoever."))
        assertFalse(hasMathContent("What is the capital of France?"))
        assertFalse(hasMathContent("Paris, London, Berlin, or Madrid."))
        assertFalse(hasMathContent("The price was 50 dollars.")) // single dollar sign, no closing
        assertFalse(hasMathContent(""))
    }

    @Test
    fun testEnsureDelimitersWrapsBareLatex() {
        assertEquals("${'$'}\\frac{1}{2}${'$'}", ensureDelimiters("\\frac{1}{2}"))
        assertEquals("${'$'}\\sqrt{3}${'$'}", ensureDelimiters("\\sqrt{3}"))
        assertEquals("${'$'}\\frac{\\sqrt{3}}{2}${'$'}", ensureDelimiters("\\frac{\\sqrt{3}}{2}"))
    }

    @Test
    fun testEnsureDelimitersLeavesExistingDelimitersIntact() {
        val existingInline = "${'$'}x + y = z${'$'}"
        assertEquals(existingInline, ensureDelimiters(existingInline))

        val existingDisplay = "$$\\int_0^1 x dx$$"
        assertEquals(existingDisplay, ensureDelimiters(existingDisplay))

        val bracketDisplay = "\\[E = mc^2\\]"
        assertEquals(bracketDisplay, ensureDelimiters(bracketDisplay))

        val plain = "Normal text"
        assertEquals(plain, ensureDelimiters(plain))
    }
}
