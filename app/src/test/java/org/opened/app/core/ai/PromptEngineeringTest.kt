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
        val prompt = Prompts.syllabusGenerationPrompt(topic, summary)

        assertTrue(prompt.contains(topic))
        assertTrue(prompt.contains(summary))
        assertTrue(prompt.contains("\"modules\":"))
        assertTrue(prompt.contains("\"chapters\":"))
        assertTrue(prompt.contains("\"estimatedTotalHours\":"))
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
}
