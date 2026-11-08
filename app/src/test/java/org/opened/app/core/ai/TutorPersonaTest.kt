package org.opened.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.datastore.UserPreferences

class TutorPersonaTest {

    @Test
    fun testAllRequiredSubjectPersonasAreRegistered() {
        val requiredIds = listOf(
            "BIOLOGY",
            "HISTORY",
            "POLITICS",
            "CHEMISTRY",
            "PHYSICS",
            "MATHEMATICS",
            "COMPUTER_SCIENCE",
            "ECONOMICS",
            "PSYCHOLOGY",
            "PHILOSOPHY",
            "ADAPTIVE_POLYMATH",
            "BEGINNER",
            "CUSTOM"
        )

        for (id in requiredIds) {
            val persona = TutorPersonaRegistry.getById(id)
            assertNotNull("Persona $id must exist", persona)
            assertEquals("ID must match", id, persona.id)
            if (id != "CUSTOM") {
                assertTrue("Instruction must not be blank for $id", persona.instruction.isNotBlank())
            }
        }
    }

    @Test
    fun testCategoryFiltering() {
        val stemPersonas = TutorPersonaRegistry.getByCategory(TutorPersonaCategory.STEM)
        assertTrue("STEM category must include BIOLOGY", stemPersonas.any { it.id == "BIOLOGY" })
        assertTrue("STEM category must include CHEMISTRY", stemPersonas.any { it.id == "CHEMISTRY" })
        assertTrue("STEM category must include PHYSICS", stemPersonas.any { it.id == "PHYSICS" })
        assertTrue("STEM category must include MATHEMATICS", stemPersonas.any { it.id == "MATHEMATICS" })
        assertTrue("STEM category must include COMPUTER_SCIENCE", stemPersonas.any { it.id == "COMPUTER_SCIENCE" })

        val humanitiesPersonas = TutorPersonaRegistry.getByCategory(TutorPersonaCategory.HUMANITIES_SOCIAL)
        assertTrue("Humanities must include HISTORY", humanitiesPersonas.any { it.id == "HISTORY" })
        assertTrue("Humanities must include POLITICS", humanitiesPersonas.any { it.id == "POLITICS" })
        assertTrue("Humanities must include ECONOMICS", humanitiesPersonas.any { it.id == "ECONOMICS" })
        assertTrue("Humanities must include PSYCHOLOGY", humanitiesPersonas.any { it.id == "PSYCHOLOGY" })

        val allPersonas = TutorPersonaRegistry.getByCategory(TutorPersonaCategory.ALL)
        assertEquals(TutorPersonaRegistry.ALL_PERSONAS.size, allPersonas.size)
    }

    @Test
    fun testTopicAutoMatchingPersona() {
        assertEquals(TutorPersonaRegistry.BIOLOGY, TutorPersonaRegistry.detectMatchingPersona("Molecular Biology & Genetics"))
        assertEquals(TutorPersonaRegistry.BIOLOGY, TutorPersonaRegistry.detectMatchingPersona("Cellular Respiration"))
        assertEquals(TutorPersonaRegistry.HISTORY, TutorPersonaRegistry.detectMatchingPersona("History of the Roman Empire"))
        assertEquals(TutorPersonaRegistry.HISTORY, TutorPersonaRegistry.detectMatchingPersona("World War II Diplomatic Alliances"))
        assertEquals(TutorPersonaRegistry.POLITICS, TutorPersonaRegistry.detectMatchingPersona("Comparative Constitutional Governance"))
        assertEquals(TutorPersonaRegistry.POLITICS, TutorPersonaRegistry.detectMatchingPersona("International Diplomacy and Treaties"))
        assertEquals(TutorPersonaRegistry.CHEMISTRY, TutorPersonaRegistry.detectMatchingPersona("Organic Synthesis and Reaction Mechanisms"))
        assertEquals(TutorPersonaRegistry.PHYSICS, TutorPersonaRegistry.detectMatchingPersona("Quantum Mechanics and Astrophysics"))
        assertEquals(TutorPersonaRegistry.MATHEMATICS, TutorPersonaRegistry.detectMatchingPersona("Linear Algebra and Matrix Proofs"))
        assertEquals(TutorPersonaRegistry.COMPUTER_SCIENCE, TutorPersonaRegistry.detectMatchingPersona("Rust Concurrency and Linux eBPF"))
        assertEquals(TutorPersonaRegistry.ECONOMICS, TutorPersonaRegistry.detectMatchingPersona("Macroeconomics and Monetary Policy"))
        assertEquals(TutorPersonaRegistry.PSYCHOLOGY, TutorPersonaRegistry.detectMatchingPersona("Cognitive Neuroscience and Memory"))
        assertEquals(TutorPersonaRegistry.PHILOSOPHY, TutorPersonaRegistry.detectMatchingPersona("Ethics and Epistemology"))
        assertEquals(TutorPersonaRegistry.ADAPTIVE_POLYMATH, TutorPersonaRegistry.detectMatchingPersona("Something Completely Unknown 123"))
    }

    @Test
    fun testSubjectPedagogyInstructionsContent() {
        val bio = TutorPersonaRegistry.getById("BIOLOGY")
        assertTrue("Biology instruction must focus on biological pathways", bio.instruction.contains("cellular") || bio.instruction.contains("biological"))

        val hist = TutorPersonaRegistry.getById("HISTORY")
        assertTrue("History instruction must focus on historiography", hist.instruction.contains("historian") || hist.instruction.contains("historiograph"))

        val pol = TutorPersonaRegistry.getById("POLITICS")
        assertTrue("Politics instruction must focus on governance", pol.instruction.contains("governance") || pol.instruction.contains("political"))
    }

    @Test
    fun testPromptArchitectPromptGeneration() {
        val promptWithSubject = Prompts.generateSystemInstructionPrompt(
            userGoalOrNotes = "Focus on USMLE clinical correlations and 3 review questions per chapter.",
            targetSubject = "Cellular Biology"
        )
        assertTrue("Prompt should include user notes", promptWithSubject.contains("USMLE clinical correlations"))
        assertTrue("Prompt should include target subject header", promptWithSubject.contains("TARGET SUBJECT OR DOMAIN FOCUS:"))
        assertTrue("Prompt should include target subject", promptWithSubject.contains("Cellular Biology"))
        assertTrue("Prompt should contain system prompt guidelines", promptWithSubject.contains("REQUIREMENTS FOR THE GENERATED SYSTEM INSTRUCTION:"))

        val promptWithoutSubject = Prompts.generateSystemInstructionPrompt(
            userGoalOrNotes = "Simple step-by-step explanations.",
            targetSubject = null
        )
        assertTrue("Prompt should include notes", promptWithoutSubject.contains("Simple step-by-step explanations."))
        assertTrue("Prompt without subject should omit subject context header", !promptWithoutSubject.contains("TARGET SUBJECT OR DOMAIN FOCUS:"))
    }

    @Test
    fun testQuickTemplateToggleLogic() {
        val snippet1 = "Always conclude each lesson with 3 concise summary takeaways and bulleted key points."
        val snippet2 = "At the end of each chapter, provide 3 multiple-choice self-test questions with detailed answer explanations."

        var instructions = ""
        // Toggle on snippet 1
        instructions = if (instructions.contains(snippet1)) {
            instructions.replace(snippet1, "").lines().filter { it.isNotBlank() }.joinToString("\n\n").trim()
        } else {
            if (instructions.isBlank()) snippet1 else "$instructions\n\n$snippet1"
        }
        assertEquals(snippet1, instructions)

        // Toggle on snippet 2
        instructions = if (instructions.contains(snippet2)) {
            instructions.replace(snippet2, "").lines().filter { it.isNotBlank() }.joinToString("\n\n").trim()
        } else {
            if (instructions.isBlank()) snippet2 else "$instructions\n\n$snippet2"
        }
        assertTrue(instructions.contains(snippet1))
        assertTrue(instructions.contains(snippet2))

        // Toggle off snippet 1
        instructions = if (instructions.contains(snippet1)) {
            instructions.replace(snippet1, "").lines().filter { it.isNotBlank() }.joinToString("\n\n").trim()
        } else {
            if (instructions.isBlank()) snippet1 else "$instructions\n\n$snippet1"
        }
        assertEquals(snippet2, instructions)

        // Toggle off snippet 2
        instructions = if (instructions.contains(snippet2)) {
            instructions.replace(snippet2, "").lines().filter { it.isNotBlank() }.joinToString("\n\n").trim()
        } else {
            if (instructions.isBlank()) snippet2 else "$instructions\n\n$snippet2"
        }
        assertEquals("", instructions)
    }
}
