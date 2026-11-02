package org.opened.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEngineeringTest {

    @Test
    fun testDiagnosticInterviewPromptContainsSchema() {
        val topic = "Nutritional Biochemistry"
        val prompt = Prompts.diagnosticInterviewPrompt(topic)

        assertTrue(prompt.contains(topic))
        assertTrue(prompt.contains("\"questions\":"))
        assertTrue(prompt.contains("\"isMultiSelect\":"))
        assertTrue(prompt.contains("\"rationale\":"))
    }

    @Test
    fun testSyllabusGenerationPromptContainsStructure() {
        val topic = "eBPF Kernel Programming"
        val summary = "Beginner to advanced C & kernel knowledge"
        val prompt = Prompts.syllabusGenerationPrompt(topic, "BEGINNER", summary)

        assertTrue(prompt.contains(topic))
        assertTrue(prompt.contains(summary))
        assertTrue(prompt.contains("\"modules\":"))
        assertTrue(prompt.contains("\"chapters\":"))
        assertTrue(prompt.contains("6 to 8 Modules"))
        assertTrue(prompt.contains("4 to 6 Chapters per Module"))
        assertTrue(prompt.contains("24 to 40 bite-sized, focused chapters"))
    }

    @Test
    fun testChapterContentPromptIncludesWikiLinkingRule() {
        val availableChapters = listOf("Chapter 1: Intro", "Chapter 2: Verifier", "Chapter 3: Maps")
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Deep eBPF",
            topic = "Linux Kernel",
            userLevel = "ADVANCED",
            moduleTitle = "Module 1",
            chapterTitle = "Chapter 2: Verifier",
            chapterSummary = "Learn the safety guarantees",
            keyConcepts = listOf("Safety checks", "DAG acyclicity"),
            otherChapterTitles = availableChapters
        )

        assertTrue(prompt.contains("KNOWLEDGE GRAPH WIKI-LINKING RULE"))
        assertTrue(prompt.contains("[[Exact Chapter Title]]"))
        assertTrue(prompt.contains("Chapter 1: Intro"))
    }

    @Test
    fun testBeginnerChapterContentPromptIncludesStrictPedagogyMandates() {
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Python Fundamentals",
            topic = "Python",
            userLevel = "BEGINNER",
            moduleTitle = "Module 1",
            chapterTitle = "Chapter 1: Lists & Dictionaries",
            chapterSummary = "Learn list and dict basics",
            keyConcepts = listOf("Lists", "Dictionaries"),
            otherChapterTitles = emptyList()
        )

        assertTrue("Should include Beginner profile", prompt.contains("PEDAGOGY PROFILE: BEGINNER"))
        assertTrue("Should teach from zero assumptions", prompt.contains("ZERO ASSUMPTIONS"))
        assertTrue("Should forbid obscure C-struct internals", prompt.contains("PyObject*") || prompt.contains("obscure compiler/C-struct internals"))
        assertTrue("Section 1 required", prompt.contains("## 🌟 The Big Picture (In Plain English)"))
        assertTrue("Section 2 required", prompt.contains("## 🧩 Key Building Blocks"))
        assertTrue("Section 3 required", prompt.contains("## 💻 Step-by-Step Examples (From Zero to Working Code)"))
        assertTrue("Section 4 required", prompt.contains("## ⚠️ Common Beginner Mistakes (And How to Avoid Them)"))
        assertTrue("Section 5 required", prompt.contains("## 🎯 Quick Hands-On Practice"))
        assertTrue("Should advise against artificial math", prompt.contains("Do NOT force artificial LaTeX math blocks"))
    }

    @Test
    fun testDedicatedPromptBuilders() {
        val beginner = Prompts.buildBeginnerChapterPrompt(
            courseTitle = "Kotlin 101",
            topic = "Kotlin",
            moduleTitle = "Module 1",
            chapterTitle = "Variables",
            chapterSummary = "val vs var",
            keyConcepts = listOf("val", "var"),
            otherChapterTitles = emptyList()
        )
        assertTrue(beginner.contains("BEGINNER (Zero Assumptions)"))

        val intermediate = Prompts.buildIntermediateChapterPrompt(
            courseTitle = "Kotlin Coroutines",
            topic = "Kotlin",
            moduleTitle = "Module 2",
            chapterTitle = "Flows",
            chapterSummary = "Cold vs hot streams",
            keyConcepts = listOf("Flow", "SharedFlow"),
            otherChapterTitles = emptyList()
        )
        assertTrue(intermediate.contains("INTERMEDIATE (Practical & Architectural)"))

        val advanced = Prompts.buildAdvancedChapterPrompt(
            courseTitle = "JVM Internals",
            topic = "JVM",
            moduleTitle = "Module 3",
            chapterTitle = "Bytecode Generation",
            chapterSummary = "Stack operations",
            keyConcepts = listOf("invokevirtual", "invokedynamic"),
            otherChapterTitles = emptyList()
        )
        assertTrue(advanced.contains("ADVANCED (Systems Rigor & Deep Dive)"))
    }

    @Test
    fun testExpandCoursePrompt() {
        val prompt = Prompts.expandCoursePrompt(
            courseTitle = "Python 101",
            topic = "Python",
            userLevel = "BEGINNER",
            existingModulesWithChapters = listOf(
                "Module 1: Intro" to listOf("Chapter 1: Print", "Chapter 2: Variables")
            )
        )
        assertTrue(prompt.contains("EXACTLY 2 NEW progressive Modules"))
        assertTrue(prompt.contains("4 to 6 bite-sized, single-concept Chapters"))
        assertTrue(prompt.contains("Module 1: Intro"))
    }

    @Test
    fun testInlineRefinePromptFullChapterRewriteWhenSelectedSnippetBlank() {
        val fullPrompt = Prompts.inlineRefinePrompt(
            chapterContext = "# Chapter 1 Content",
            selectedText = "",
            action = RefineAction.CUSTOM,
            customInstruction = "Rewrite this entire chapter from zero assumptions for a beginner."
        )

        assertTrue("Should state full chapter rewrite", fullPrompt.contains("rewriting the entire chapter") || fullPrompt.contains("Rewrite the entire chapter"))
        assertTrue("Should contain custom instruction", fullPrompt.contains("zero assumptions for a beginner"))
    }

    @Test
    fun testAcademicDomainClassifierDetection() {
        assertEquals(
            AcademicDomain.LIFE_SCIENCES_AND_MEDICINE,
            AcademicDomainClassifier.detectAcademicDomain("Nutritional Biochemistry")
        )
        assertEquals(
            AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL,
            AcademicDomainClassifier.detectAcademicDomain("The Cold War & Geopolitics")
        )
        assertEquals(
            AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE,
            AcademicDomainClassifier.detectAcademicDomain("Corporate Valuation & DCF")
        )
        assertEquals(
            AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES,
            AcademicDomainClassifier.detectAcademicDomain("Kantian Ethics & Epistemology")
        )
        assertEquals(
            AcademicDomain.MATH_AND_PHYSICAL_SCIENCES,
            AcademicDomainClassifier.detectAcademicDomain("Differential Equations & Fluid Dynamics")
        )
        assertEquals(
            AcademicDomain.SOFTWARE_AND_CS,
            AcademicDomainClassifier.detectAcademicDomain("Python Fundamentals")
        )
        assertEquals(
            AcademicDomain.GENERAL_INTERDISCIPLINARY,
            AcademicDomainClassifier.detectAcademicDomain("Culinary Arts & Gastronomy")
        )
    }

    @Test
    fun testHistoryGeopoliticsChapterContentPrompt() {
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Cold War Geopolitics",
            topic = "The Cold War & International Relations",
            userLevel = "BEGINNER",
            moduleTitle = "Module 1: Origins",
            chapterTitle = "The Yalta & Potsdam Conferences",
            chapterSummary = "Post-war partition and competing spheres of influence",
            keyConcepts = listOf("Spheres of influence", "Iron Curtain"),
            otherChapterTitles = emptyList()
        )

        assertTrue("Should contain non-programming rule", prompt.contains("STRICT NON-PROGRAMMING RULE"))
        assertFalse("Must NOT contain coding walkthrough header", prompt.contains("Step-by-Step Examples (From Zero to Working Code)"))
        assertTrue("Section 1 required", prompt.contains("## 🌍 Context & The Big Picture"))
        assertTrue("Section 2 required", prompt.contains("## 🏛️ Key Actors, Geopolitical Interests & Driving Forces"))
        assertTrue("Section 3 required", prompt.contains("## ⏳ Chronological Walkthrough & Pivotal Turning Points"))
        assertTrue("Section 4 required", prompt.contains("## ⚖️ Multiple Perspectives & Historiography"))
        assertTrue("Section 5 required", prompt.contains("## 🌐 Lasting Impact & Modern Parallels"))
    }

    @Test
    fun testLifeSciencesChapterContentPrompt() {
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Cellular Bioenergetics",
            topic = "Mitochondria & ATP Synthesis",
            userLevel = "INTERMEDIATE",
            moduleTitle = "Module 2: Cellular Respiration",
            chapterTitle = "Electron Transport Chain & Chemiosmosis",
            chapterSummary = "Proton gradient dynamics and ATP synthase mechanics",
            keyConcepts = listOf("ATP Synthase", "Proton Gradient"),
            otherChapterTitles = emptyList()
        )

        assertTrue("Should contain non-programming rule", prompt.contains("STRICT NON-PROGRAMMING RULE"))
        assertFalse("Must NOT contain coding header", prompt.contains("Working Code"))
        assertTrue("Section 1 required", prompt.contains("## 🔬 Biological Foundations & Intuitive Analogy"))
        assertTrue("Section 2 required", prompt.contains("## 🧬 Structural & Molecular Breakdown"))
        assertTrue("Section 3 required", prompt.contains("## ⚙️ Step-by-Step Mechanistic Pathway"))
        assertTrue("Section 4 required", prompt.contains("## 🏥 Clinical & Real-World Case Studies"))
        assertTrue("Section 5 required", prompt.contains("## ⚠️ Common Misconceptions & Summary Comparison"))
    }

    @Test
    fun testCommerceEconomicsChapterContentPrompt() {
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Corporate Finance",
            topic = "Corporate Valuation & DCF",
            userLevel = "BEGINNER",
            moduleTitle = "Module 1: Fundamentals of Value",
            chapterTitle = "Discounted Cash Flow (DCF) Analysis",
            chapterSummary = "Time value of money and weighted average cost of capital",
            keyConcepts = listOf("WACC", "Free Cash Flow"),
            otherChapterTitles = emptyList()
        )

        assertTrue("Should contain non-programming rule", prompt.contains("STRICT NON-PROGRAMMING RULE"))
        assertFalse("Must NOT contain coding header", prompt.contains("Working Code"))
        assertTrue("Section 1 required", prompt.contains("## 💡 Core Economic & Business Intuition"))
        assertTrue("Section 2 required", prompt.contains("## ⚙️ How the Mechanism Works (Incentives & Flows)"))
        assertTrue("Section 3 required", prompt.contains("## 📊 Worked Numerical / Ledger Example & Real-World Case Study"))
        assertTrue("Section 4 required", prompt.contains("## ⚠️ Risks, Trade-Offs & Market Failures"))
    }

    @Test
    fun testPhilosophyHumanitiesChapterContentPrompt() {
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Moral Philosophy",
            topic = "Kantian Ethics & Epistemology",
            userLevel = "BEGINNER",
            moduleTitle = "Module 1: Deontology",
            chapterTitle = "The Categorical Imperative",
            chapterSummary = "Universalizability and duty vs consequentialism",
            keyConcepts = listOf("Categorical Imperative", "Maxims"),
            otherChapterTitles = emptyList()
        )

        assertTrue("Should contain non-programming rule", prompt.contains("STRICT NON-PROGRAMMING RULE"))
        assertFalse("Must NOT contain coding header", prompt.contains("Working Code"))
        assertTrue("Section 1 required", prompt.contains("## 🧠 The Central Question & Human Intuition"))
        assertTrue("Section 2 required", prompt.contains("## 🧩 Core Framework & Theory Breakdown"))
        assertTrue("Section 3 required", prompt.contains("## 🔬 Classic Thought Experiments & Landmark Studies"))
        assertTrue("Section 4 required", prompt.contains("## ⚖️ Competing Schools of Thought & Critiques"))
        assertTrue("Section 5 required", prompt.contains("## 🌟 Practical Application to Everyday Thinking & Life"))
    }

    @Test
    fun testMathPhysicalSciencesChapterContentPrompt() {
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = "Vector Calculus",
            topic = "Differential Equations & Fluid Dynamics",
            userLevel = "BEGINNER",
            moduleTitle = "Module 1: Vector Fields",
            chapterTitle = "Divergence and Stokes' Theorem",
            chapterSummary = "Flux through surfaces and circulation",
            keyConcepts = listOf("Divergence", "Curl"),
            otherChapterTitles = emptyList()
        )

        assertTrue("Section 1 required", prompt.contains("## 📐 The Big Picture & Geometric/Physical Intuition"))
        assertTrue("Section 2 required", prompt.contains("## 🧩 Core Mathematical Formulations & Definitions"))
        assertTrue("Section 3 required", prompt.contains("## 📝 Step-by-Step Derivation & Worked Numerical Problems"))
        assertTrue("Section 4 required", prompt.contains("## ⚠️ Common Pitfalls & Boundary Conditions"))
    }

    @Test
    fun testNonCsInlineRefinePromptAdaptsAction() {
        val historyPrompt = Prompts.inlineRefinePrompt(
            chapterContext = "The Treaty of Versailles was signed in 1919.",
            selectedText = "The Treaty of Versailles was signed in 1919.",
            action = RefineAction.ADD_CODE_MATH,
            customInstruction = null,
            userLevel = "INTERMEDIATE",
            domain = AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL
        )

        assertTrue("Should include non-programming rule", historyPrompt.contains("STRICT NON-PROGRAMMING RULE"))
        assertFalse("Must NOT mention code examples", historyPrompt.contains("concrete code examples"))
        assertTrue("Should mention real-world case studies or historical examples", historyPrompt.contains("real-world case studies") || historyPrompt.contains("historical examples"))
    }
}
