package org.opened.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicDomainClassifierTest {

    @Test
    fun testConfidenceScoringForClearTopics() {
        val csResult = AcademicDomainClassifier.classify("Advanced Rust Concurrency and Tokio Runtime")
        assertEquals(AcademicDomain.SOFTWARE_AND_CS, csResult.primaryDomain)
        assertTrue("Confidence should be high (> 0.7)", csResult.confidence >= 0.7f)
        assertFalse(csResult.isInterdisciplinary)

        val bioResult = AcademicDomainClassifier.classify("Cellular Respiration and Mitochondrial Bioenergetics")
        assertEquals(AcademicDomain.LIFE_SCIENCES_AND_MEDICINE, bioResult.primaryDomain)
        assertTrue(bioResult.confidence >= 0.7f)

        val econResult = AcademicDomainClassifier.classify("Macroeconomics, Inflation, and Central Bank Monetary Policy")
        assertEquals(AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE, econResult.primaryDomain)
        assertTrue(econResult.confidence >= 0.7f)
    }

    @Test
    fun testInterdisciplinaryDetection() {
        val bioinfo = AcademicDomainClassifier.classify("Bioinformatics and Genomic Algorithms with Python")
        assertTrue("Should detect interdisciplinary nature", bioinfo.isInterdisciplinary)
        assertTrue("Should have secondary domains", bioinfo.secondaryDomains.isNotEmpty())
        val domains = listOf(bioinfo.primaryDomain) + bioinfo.secondaryDomains
        assertTrue(domains.contains(AcademicDomain.SOFTWARE_AND_CS))
        assertTrue(domains.contains(AcademicDomain.LIFE_SCIENCES_AND_MEDICINE))
    }

    @Test
    fun testUserAndChapterOverrides() {
        val overridden = AcademicDomainClassifier.classify(
            topic = "Python Programming",
            userOverride = AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES
        )
        assertEquals(AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES, overridden.primaryDomain)
        assertEquals(1.0f, overridden.confidence, 0.01f)
    }

    @Test
    fun testAdaptiveCurriculumSizing() {
        // Narrow topics should generate 3-4 modules and 9-16 chapters
        val narrow1 = AcademicDomainClassifier.determineCurriculumSize("Derivation of Black-Scholes Formula")
        assertTrue(narrow1.isNarrowTopic)
        assertEquals(3, narrow1.minModules)
        assertEquals(4, narrow1.maxModules)
        assertEquals(9..16, narrow1.totalChaptersRange)

        val narrow2 = AcademicDomainClassifier.determineCurriculumSize("Krebs Cycle")
        assertTrue(narrow2.isNarrowTopic)

        // Standard broad courses should generate 6-8 modules and 24-40 chapters
        val broad = AcademicDomainClassifier.determineCurriculumSize("Introduction to Organic Chemistry")
        assertFalse(broad.isNarrowTopic)
        assertEquals(6, broad.minModules)
        assertEquals(8, broad.maxModules)
        assertEquals(24..40, broad.totalChaptersRange)
    }

    @Test
    fun testStrictNonCsCodeProhibitionInPrompts() {
        val nonCsDomains = listOf(
            AcademicDomain.LIFE_SCIENCES_AND_MEDICINE,
            AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE,
            AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL,
            AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES,
            AcademicDomain.MATH_AND_PHYSICAL_SCIENCES,
            AcademicDomain.GENERAL_INTERDISCIPLINARY
        )

        for (domain in nonCsDomains) {
            val blueprint = Prompts.buildDomainPedagogicalBlueprint(domain, "BEGINNER")
            assertTrue("Blueprint for ${domain.name} must mandate non-programming",
                blueprint.contains("STRICT NON-PROGRAMMING RULE") || blueprint.contains("Strictly forbid"))
            assertFalse("Blueprint for ${domain.name} should not contain python code block examples",
                blueprint.contains("```python\ndef"))
        }
    }

    @Test
    fun testPrerequisiteDependencyAnalysis() {
        val modules = listOf(
            SyllabusModuleItem(
                title = "Module 1: Foundations",
                summary = "Basic setup",
                chapters = listOf(
                    SyllabusChapterItem(title = "Chapter 1", summary = "Intro", keyConcepts = listOf("Concept A"))
                )
            ),
            SyllabusModuleItem(
                title = "Module 2: Advanced Topics",
                summary = "Advanced mechanics",
                chapters = listOf(
                    SyllabusChapterItem(title = "Chapter 2", summary = "Deep dive", keyConcepts = listOf("Advanced Concept Z"))
                )
            )
        )

        val result = AcademicDomainClassifier.analyzePrerequisites(modules)
        // Advanced Concept Z in Module 2 was not introduced earlier
        assertTrue(result.missingPrerequisites.contains("Advanced Concept Z") || result.dependencies.isNotEmpty())
    }
}
