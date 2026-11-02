package org.opened.app.core.ai

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
}
