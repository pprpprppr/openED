package org.opened.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRepairerTest {

    @Test
    fun testValidJsonUnchanged() {
        val input = """{"title": "Test Course", "modules": []}"""
        val result = JsonRepairer.cleanAndRepairJson(input)
        assertTrue(JsonRepairer.isValidJson(result))
    }

    @Test
    fun testMarkdownFenceExtracted() {
        val input = """
            Here is your curriculum:
            ```json
            {
              "title": "Quantum Mechanics",
              "modules": []
            }
            ```
            Hope this helps!
        """.trimIndent()
        val result = JsonRepairer.cleanAndRepairJson(input)
        assertTrue(JsonRepairer.isValidJson(result))
        assertTrue(result.contains("Quantum Mechanics"))
    }

    @Test
    fun testTrailingCommasRemoved() {
        val input = """
            {
              "title": "Algorithms",
              "modules": [
                {
                  "title": "Sorting",
                  "chapters": [
                    {"title": "Merge Sort",},
                  ],
                },
              ],
            }
        """.trimIndent()
        val result = JsonRepairer.cleanAndRepairJson(input)
        assertTrue(JsonRepairer.isValidJson(result))
    }

    @Test
    fun testTruncatedInsideStringAtEof() {
        val truncated = """{"title": "Deep Learning", "summary": "In this course we will explore architec"""
        val result = JsonRepairer.cleanAndRepairJson(truncated)
        assertTrue(JsonRepairer.isValidJson(result))
        assertTrue(result.contains("Deep Learning"))
    }

    @Test
    fun testTruncatedInsideArrayAtEof() {
        val truncated = """
            {
              "title": "Linear Algebra",
              "modules": [
                {
                  "title": "Vectors",
                  "chapters": [
                    {"title": "Vector Spaces"}
        """.trimIndent()
        val result = JsonRepairer.cleanAndRepairJson(truncated)
        assertTrue(JsonRepairer.isValidJson(result))
        assertTrue(result.contains("Linear Algebra"))
        assertTrue(result.contains("Vector Spaces"))
    }

    @Test
    fun testTruncatedPartialObjectBacktracked() {
        val truncated = """
            {
              "title": "Biochemistry",
              "modules": [
                {
                  "title": "Carbohydrates",
                  "chapters": [{"title": "Glucose"}]
                },
                {
                  "title": "Lipids",
                  "chapters": [
                    {"title": "Fatty Acids"},
                    {"title": "Triacylgly
        """.trimIndent()
        val result = JsonRepairer.cleanAndRepairJson(truncated)
        assertTrue(JsonRepairer.isValidJson(result))
        assertTrue(result.contains("Biochemistry"))
        assertTrue(result.contains("Carbohydrates"))
        assertTrue(result.contains("Glucose"))
    }

    @Test
    fun testLenientJsonDeserializesSyllabusResponseWithRepairedJson() {
        val truncated = """
            ```json
            {
              "title": "Organic Chemistry",
              "topic": "Organic Chemistry",
              "modules": [
                {
                  "title": "Module 1: Structure",
                  "chapters": [
                    {
                      "title": "Bonding and Molecular Orbitals",
                      "estimatedMinutes": 20
                    }
                  ]
                }
        """.trimIndent()
        val repaired = JsonRepairer.cleanAndRepairJson(truncated)
        assertTrue(JsonRepairer.isValidJson(repaired))
        val parsed = JsonRepairer.lenientJson.decodeFromString(SyllabusResponse.serializer(), repaired)
        assertEquals("Organic Chemistry", parsed.title)
        assertEquals(1, parsed.modules.size)
        assertEquals("Module 1: Structure", parsed.modules[0].title)
        assertEquals(1, parsed.modules[0].chapters.size)
    }
}
