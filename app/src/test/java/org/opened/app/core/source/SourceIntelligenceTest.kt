package org.opened.app.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.ai.AcademicDomain

class SourceIntelligenceTest {

    @Test
    fun testParseReferencesAndEvidenceQuality() {
        val markdown = """
            # Mitochondria and Cellular Energetics
            
            Mitochondria generate ATP through oxidative phosphorylation.
            
            ## 📚 Sources & Evidence Quality
            - [1] Mitchell, P. (1961). Coupling of phosphorylation to electron and hydrogen transfer. Nature. [Quality: Established Fact] - Section 2.
            - [2] Lane, N. (2015). The Vital Question: Energy, Evolution and the Origins of Complex Life. [Quality: Working Hypothesis]
            - [3] Modern Mitochondrial Dynamics Consortium (2023). [Quality: Academic Interpretation]
        """.trimIndent()

        val profile = SourceIntelligenceParser.parseEvidenceProfile(markdown, AcademicDomain.LIFE_SCIENCES_AND_MEDICINE)
        assertEquals(3, profile.references.size)

        val ref1 = profile.references[0]
        assertEquals("[1]", ref1.label)
        assertEquals(1961, ref1.year)
        assertEquals(EvidenceQuality.ESTABLISHED_FACT, ref1.evidenceQuality)
        assertNotNull(ref1.pageOrSection)

        val ref2 = profile.references[1]
        assertEquals(EvidenceQuality.HYPOTHESIS, ref2.evidenceQuality)
        assertEquals(2015, ref2.year)

        val ref3 = profile.references[2]
        assertEquals(EvidenceQuality.INTERPRETATION, ref3.evidenceQuality)
        assertEquals(2023, ref3.year)
    }

    @Test
    fun testEducationalDisclaimers() {
        // Medical disclaimer for life sciences
        val bioDisclaimer = EducationalDisclaimerManager.getDisclaimerForDomain(AcademicDomain.LIFE_SCIENCES_AND_MEDICINE)
        assertNotNull(bioDisclaimer)
        assertTrue(bioDisclaimer!!.contains("Medical Education Disclaimer"))

        // Legal disclaimer for history & geopolitics & law
        val legalDisclaimer = EducationalDisclaimerManager.getDisclaimerForDomain(AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL)
        assertNotNull(legalDisclaimer)
        assertTrue(legalDisclaimer!!.contains("Legal Education Disclaimer"))

        // Financial disclaimer for commerce & economics
        val finDisclaimer = EducationalDisclaimerManager.getDisclaimerForDomain(AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE)
        assertNotNull(finDisclaimer)
        assertTrue(finDisclaimer!!.contains("Financial & Investment Disclaimer"))

        // CS requires no medical/legal disclaimer
        val csDisclaimer = EducationalDisclaimerManager.getDisclaimerForDomain(AcademicDomain.SOFTWARE_AND_CS)
        assertEquals(null, csDisclaimer)
    }

    @Test
    fun testDisclaimerInjectionWithoutDuplication() {
        val rawLesson = "# Cellular Biology\n\nLesson text here..."
        val injected = EducationalDisclaimerManager.injectDisclaimerIfNeeded(rawLesson, AcademicDomain.LIFE_SCIENCES_AND_MEDICINE)
        assertTrue(injected.contains("Medical Education Disclaimer"))
        assertTrue(injected.contains(rawLesson))

        // Running injection again should not duplicate
        val doubleInjected = EducationalDisclaimerManager.injectDisclaimerIfNeeded(injected, AcademicDomain.LIFE_SCIENCES_AND_MEDICINE)
        assertEquals(injected, doubleInjected)
    }

    @Test
    fun testFormatBibliographyMarkdown() {
        val refs = listOf(
            SourceReference(
                index = 1,
                label = "[1]",
                citation = "Darwin, C. (1859). On the Origin of Species.",
                evidenceQuality = EvidenceQuality.ESTABLISHED_FACT,
                pageOrSection = "Chapter 4"
            )
        )
        val formatted = SourceIntelligenceParser.formatBibliographyMarkdown(refs)
        assertTrue(formatted.contains("## 📚 Sources & Evidence Quality"))
        assertTrue(formatted.contains("[1]"))
        assertTrue(formatted.contains(EvidenceQuality.ESTABLISHED_FACT.badgeText))
        assertTrue(formatted.contains("Chapter 4"))
    }
}
