package org.opened.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.ui.components.BracketType
import org.opened.app.ui.components.LatexParser
import org.opened.app.ui.components.Math2DNode

class LatexParserTest {

    @Test
    fun testBlackboardBoldSetsConvertedToUnicode() {
        val input = "Let \$x \\in \\mathbb{R}^n\$ and \$k \\in \\mathbb{Z}\$."
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertTrue("Should contain ℝ", cleaned.contains("ℝ"))
        assertTrue("Should contain ℤ", cleaned.contains("ℤ"))
        assertFalse("Should not leak \\mathbb", cleaned.contains("\\mathbb"))
    }

    @Test
    fun testBoldAndFormattingBracesStrippedWithoutLeak() {
        val input = "\$\\mathbf{X}^T \\mathbf{X} = \\mathbf{I}\$"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain raw braces", cleaned.contains("{") || cleaned.contains("}"))
        assertFalse("Should not contain \\mathbf", cleaned.contains("\\mathbf"))
        assertTrue("Should contain X", cleaned.contains("X"))
    }

    @Test
    fun testHatAndVectorAccentsConverted() {
        val input = "\$\\hat{i} + \\hat{j} = \\vec{v}\$"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertTrue("Should contain î", cleaned.contains("î"))
        assertTrue("Should contain ĵ", cleaned.contains("ĵ"))
        assertTrue("Should contain v⃗", cleaned.contains("v⃗"))
    }

    @Test
    fun testInlineMatrixConvertedToVectorNotation() {
        val input = "\$\\begin{bmatrix} a \\\\ b \\end{bmatrix}\$"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertEquals("[a, b]ᵀ", cleaned)
    }

    @Test
    fun testParseDisplayMatrix() {
        val latex = """\begin{bmatrix} 1 & 2 \\ 3 & 4 \end{bmatrix}"""
        val node = LatexParser.parseDisplayMath(latex)
        assertTrue("Root should be Matrix", node is Math2DNode.Matrix)
        val matrix = node as Math2DNode.Matrix
        assertEquals(BracketType.SQUARE, matrix.bracketType)
        assertEquals(2, matrix.rows.size)
        assertEquals(2, matrix.rows[0].size)
    }

    @Test
    fun testParseDisplayFraction() {
        val latex = """\frac{-b \pm \sqrt{D}}{2a}"""
        val node = LatexParser.parseDisplayMath(latex)
        assertTrue("Node should contain fraction", node is Math2DNode.Fraction || node is Math2DNode.Row)
    }

    @Test
    fun testParseDisplaySummation() {
        val latex = """\sum_{i=1}^{n} x_i"""
        val node = LatexParser.parseDisplayMath(latex)
        assertTrue("Node should contain BigOp", node is Math2DNode.BigOp || node is Math2DNode.Row)
    }

    @Test
    fun testComplexSetDimensionAndStrayBraces() {
        val input = "{X} \\in \\mathbb{R}^{M \\times D}"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain raw {", cleaned.contains("{"))
        assertFalse("Should not contain raw }", cleaned.contains("}"))
        assertFalse("Should not contain \\mathbb", cleaned.contains("\\mathbb"))
        assertFalse("Should not contain \\in", cleaned.contains("\\in"))
        assertFalse("Should not contain \\times", cleaned.contains("\\times"))
        assertTrue("Should contain ℝ", cleaned.contains("ℝ"))
        assertTrue("Should contain X", cleaned.contains("X"))
    }

    @Test
    fun testVectorBraceStripping() {
        val input = """{vector}({"King"})"""
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertEquals("vector(\"King\")", cleaned)
    }

    @Test
    fun testZeroLeakAcrossCombinedString() {
        val input = """\begin{bmatrix} 1 & 2 \\ 3 & 4 \end{bmatrix}, {X} \in \mathbb{R}^{M \times D}, $\mathbf{X}$, \hat{i}, {vector}({"King"})"""
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not leak \\begin", cleaned.contains("\\begin"))
        assertFalse("Should not leak \\mathbb", cleaned.contains("\\mathbb"))
        assertFalse("Should not leak \\mathbf", cleaned.contains("\\mathbf"))
        assertFalse("Should not leak \\hat", cleaned.contains("\\hat"))
        assertFalse("Should not leak raw {", cleaned.contains("{"))
        assertFalse("Should not leak raw }", cleaned.contains("}"))
        assertTrue("Should contain î", cleaned.contains("î"))
    }

    @Test
    fun testLeftarrowNotCorruptedIntoLeArrow() {
        val input = "perturb \\leftarrow x"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertTrue("Should contain ←", cleaned.contains("←"))
        assertFalse("Should not contain ≤ftarrow", cleaned.contains("≤ftarrow"))
        assertFalse("Should not contain \\leftarrow", cleaned.contains("\\leftarrow"))
        assertEquals("perturb ← x", cleaned)
    }

    @Test
    fun testEscapedUnderscoreAndMuchGreaterThan() {
        val input = "new\\_size \\gg 3"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertEquals("new_size ≫ 3", cleaned)
        assertFalse("Should not contain subscript s", cleaned.contains("ₛ"))
        assertFalse("Should not contain raw backslash", cleaned.contains("\\"))
    }

    @Test
    fun testFloorAndLogExpressionWithoutRawBackslashes() {
        val input = "2^{\\lfloor \\log_2 N \\rfloor}"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain raw backslash", cleaned.contains("\\"))
        assertTrue("Should contain floor opening", cleaned.contains("⌊"))
        assertTrue("Should contain floor closing", cleaned.contains("⌋"))
        assertTrue("Should contain log", cleaned.contains("log"))
        assertEquals("2^(⌊log₂ N⌋)", cleaned)
    }

    @Test
    fun testOperatornameRankConvertedCleanly() {
        val input = "\\operatorname{rank}(A) + \\operatorname{diag}(B)"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain operatornamerank", cleaned.contains("operatornamerank"))
        assertFalse("Should not contain operatornamediag", cleaned.contains("operatornamediag"))
        assertTrue("Should contain rank", cleaned.contains("rank"))
        assertTrue("Should contain diag", cleaned.contains("diag"))
    }

    @Test
    fun testMathsfTransposeConverted() {
        val input = "V^\\mathsf{T} = V^{\\mathsf{T}}"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain mathsfT", cleaned.contains("mathsfT"))
        assertTrue("Should contain ᵀ", cleaned.contains("ᵀ"))
        assertEquals("Vᵀ = Vᵀ", cleaned)
    }

    @Test
    fun testBoxedExpressionStripped() {
        val input = "\\boxed{A}"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain boxedA", cleaned.contains("boxedA"))
        assertEquals("A", cleaned)
    }

    @Test
    fun testChemicalRadicalBulletConverted() {
        val input = "NO^\\bullet + \\ce{NO^\\bullet}"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not contain bullet word", cleaned.contains("bullet"))
        assertTrue("Should contain •", cleaned.contains("•"))
    }

    @Test
    fun testLongrightarrowConverted() {
        val input = "A \\longrightarrow B \\implies C"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertFalse("Should not leak longrightarrow", cleaned.contains("longrightarrow"))
        assertTrue("Should contain ⟶", cleaned.contains("⟶"))
        assertTrue("Should contain ⇒", cleaned.contains("⇒"))
    }

    @Test
    fun testChemistrySubscriptsBh4() {
        val input = "\$BH_4\$"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertEquals("BH₄", cleaned)
    }

    @Test
    fun testInlineParensMathDelimitersCleaned() {
        val input = "\\( x + y \\)"
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertEquals("x + y", cleaned)
    }

    @Test
    fun testPreservesBoundarySpacesPreventingEatenSpaces() {
        val input = " the list "
        val cleaned = LatexParser.cleanInlineLatex(input)
        assertEquals(" the list ", cleaned)
    }

    @Test(timeout = 2000L)
    fun testLatexParserCompletesOn10000CharHalfStreamedWithUnclosedBracesUnder100ms() {
        val sb = StringBuilder()
        while (sb.length < 10000) {
            sb.append("Let \\frac{numerator {nested and \\sqrt{inner without closing brace and \\mathbf{bold unclosed. ")
            sb.append("Consider \\ce{H2O and \\operatorname{rank of \\mathbb{R. ")
        }
        val text = sb.toString()
        assertTrue(text.length >= 10000)

        val startTime = System.currentTimeMillis()
        val cleaned = LatexParser.cleanInlineLatex(text)
        val duration = System.currentTimeMillis() - startTime

        assertTrue("Expected non-empty output", cleaned.isNotEmpty())
        assertTrue("LatexParser.cleanInlineLatex took $duration ms, expected < 100ms", duration < 100)
    }
}
