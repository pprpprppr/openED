package org.opened.app.core.ai

object Prompts {

    fun diagnosticInterviewPrompt(topic: String): String {
        return """
You are openED's Diagnostic Interviewer. A user wants to learn about:
"$topic"

Generate 3 to 5 targeted diagnostic questions to assess:
1. Current skill level and foundational background in or related to this topic.
2. Primary learning goals (e.g. practical career usage, academic mastery, hobby project).
3. Preferred learning depth (conceptual high-level vs. deep technical/mathematical/code-level).
4. Practical hands-on vs. theoretical focus.

Respond ONLY with a valid JSON object matching this schema:
{
  "questions": [
    {
      "id": "q1",
      "question": "Clear question text?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "isMultiSelect": false,
      "rationale": "Why this question matters for course design"
    }
  ]
}
Do not wrap in markdown quotes or code fences. Return raw JSON only.
""".trimIndent()
    }

    fun syllabusGenerationPrompt(
        topic: String,
        diagnosticSummary: String
    ): String {
        return """
You are openED's Master Curriculum Architect.
The user wants to study: "$topic"

Diagnostic Assessment Profile:
$diagnosticSummary

Design a comprehensive, highly structured self-study curriculum.
Divide the curriculum into 3 to 6 logical Modules.
Each Module must contain 2 to 5 ordered Chapters.
For each chapter, provide:
- Exact chapter title
- Estimated reading & practice time (in minutes)
- Concise summary of what will be learned
- Key concepts covered

Respond ONLY with a valid JSON object matching this schema:
{
  "title": "Comprehensive Course Title",
  "topic": "$topic",
  "userLevel": "BEGINNER | INTERMEDIATE | ADVANCED",
  "targetAudience": "Summary of target learner profile",
  "estimatedTotalHours": 8.5,
  "prerequisites": ["Prereq 1", "Prereq 2"],
  "modules": [
    {
      "title": "Module 1: Title",
      "summary": "Module summary",
      "chapters": [
        {
          "title": "Chapter 1: Title",
          "estimatedMinutes": 20,
          "summary": "Chapter summary",
          "keyConcepts": ["Concept A", "Concept B"]
        }
      ]
    }
  ]
}
Do not wrap in markdown quotes or code fences. Return raw JSON only.
""".trimIndent()
    }

    fun chapterContentPrompt(
        courseTitle: String,
        topic: String,
        userLevel: String,
        moduleTitle: String,
        chapterTitle: String,
        chapterSummary: String,
        keyConcepts: List<String>,
        otherChapterTitles: List<String>
    ): String {
        val crossRefInstruction = if (otherChapterTitles.isNotEmpty()) {
            """
KNOWLEDGE GRAPH WIKI-LINKING RULE:
Whenever you mention or build upon concepts covered in other chapters of this curriculum or library, enclose the exact chapter title in double brackets like `[[Exact Chapter Title]]`.
Here are available chapter titles in the user's workspace:
${otherChapterTitles.take(25).joinToString("\n") { "- $it" }}
Always inject `[[Chapter Title]]` links naturally into the text when discussing those concepts.
"""
        } else ""

        return """
You are openED, a world-class polymath instructor and mentor.
You are generating a complete, in-depth, self-contained lesson for:

Course: $courseTitle ($topic)
Learner Level: $userLevel
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts to Master: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

WRITING & FORMATTING GUIDELINES:
1. Provide a comprehensive, rigorous lesson (1500 - 3000 words).
2. Begin with an intuitive conceptual hook and real-world analogy.
3. Break content into clear Markdown sections using `#`, `##`, and `###` headers.
4. Include rich LaTeX mathematical blocks using `${'$'}${'$'}...${'$'}${'$'}` for display formulas or `${'$'}...${'$'}` for inline math expressions where relevant.
5. Provide practical, complete, syntax-highlighted code blocks with language tags (e.g. ```python, ```c, ```rust, ```kotlin, ```bash) whenever applicable.
6. Use GitHub-style callouts for key insights:
   > [!NOTE] Background context or intuition
   > [!TIP] Practical optimization or pro-tip
   > [!IMPORTANT] Critical rule, pitfall, or theorem
   > [!WARNING] Common bugs or misconceptions
7. Conclude with a "Mental Check & Self-Quiz" section with 2-3 interactive reflection prompts.

Stream the full lesson content directly in Markdown.
""".trimIndent()
    }

    fun inlineRefinePrompt(
        chapterContext: String,
        selectedText: String,
        action: RefineAction,
        customInstruction: String?
    ): String {
        val instructionText = when (action) {
            RefineAction.SIMPLIFY -> "Simplify this passage. Make it accessible and clear with an intuitive analogy while retaining core technical accuracy."
            RefineAction.ADD_CODE_MATH -> "Enhance this passage by providing concrete code examples or LaTeX math derivations (${'$'}${'$'}...${'$'}${'$'}) to make it actionable and rigorous."
            RefineAction.FACT_CHECK_CORRECT -> "Fact-check this passage against state-of-the-art standards, verify technical claims, fix inaccuracies, and update nuances."
            RefineAction.CUSTOM -> customInstruction ?: "Refine and improve this passage."
        }

        return """
You are openED's In-Chapter AI Editor.
You are asked to refine a specific passage within a chapter.

CHAPTER CONTEXT:
${chapterContext.take(1500)}

SELECTED PASSAGE TO REFINE:
\"\"\"
$selectedText
\"\"\"

INSTRUCTION:
$instructionText

Return ONLY the refined replacement text for the selected passage in Markdown. Do not include introductory conversational filler or quotes around the whole text.
""".trimIndent()
    }

    fun socraticQaPrompt(
        chapterTitle: String,
        chapterContent: String,
        userQuestion: String
    ): String {
        return """
You are openED's Socratic Teaching Assistant.
The learner is reading the chapter "$chapterTitle" and has asked a question:

LEARNER QUESTION:
"$userQuestion"

CURRENT CHAPTER CONTENT (GROUND TRUTH CONTEXT):
${chapterContent.take(4000)}

TASK:
Provide a clear, engaging, and pedagogically sound Socratic answer.
1. Answer the specific question directly.
2. Anchor your explanation to the concepts introduced in this chapter.
3. If relevant, include a brief code snippet, formula (${'$'}${'$'}...${'$'}${'$'}), or thought-provoking follow-up question to test their understanding.
4. Keep the answer structured and readable in Markdown.
""".trimIndent()
    }

    fun generateFlashcardsPrompt(chapterTitle: String, chapterContent: String): String {
        return """
You are openED's Spaced-Repetition Flashcard Engine.
Generate 5 to 10 high-yield active-recall flashcards from the following chapter.

CHAPTER: "$chapterTitle"
CONTENT:
${chapterContent.take(4000)}

RULES:
- Each flashcard front must be a concise question or cloze-deletion prompt.
- Each flashcard back must be a complete, self-contained answer (1-4 sentences). Include key formulas or code snippets where critical.
- Prioritize conceptual understanding and edge cases over trivial definitions.
- Vary difficulty: include foundational, intermediate, and application-level cards.

Return ONLY a valid JSON object:
{
  "flashcards": [
    { "front": "Question text?", "back": "Answer text." }
  ]
}
Do not wrap in markdown code fences. Return raw JSON only.
""".trimIndent()
    }

    fun adaptiveQuizPrompt(chapterTitle: String, chapterContent: String): String {
        return """
You are openED's Adaptive Mastery Examiner.
Create 5 scenario-based multiple-choice questions for the chapter below.

CHAPTER: "$chapterTitle"
CONTENT:
${chapterContent.take(4000)}

REQUIREMENTS:
- Questions must require understanding, not mere recall.
- Each question has exactly 4 options (A–D).
- Include real-world scenario framing where possible.
- Provide a clear explanation for why the correct answer is right and others are wrong.

Return ONLY a valid JSON object:
{
  "questions": [
    {
      "question": "Scenario-based question text?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "correctIndex": 0,
      "explanation": "Why A is correct and B/C/D are wrong."
    }
  ]
}
Do not wrap in markdown code fences. Return raw JSON only.
""".trimIndent()
    }

    fun feynmanEvaluationPrompt(chapterTitle: String, chapterContent: String, userExplanation: String): String {
        return """
You are openED's Feynman Technique Evaluator.
A learner has explained the core concept of the chapter below in their own words.
Your job is to evaluate the quality and completeness of their explanation.

CHAPTER: "$chapterTitle"
CHAPTER CONTENT (Ground Truth):
${chapterContent.take(3000)}

LEARNER'S EXPLANATION:
"$userExplanation"

EVALUATION CRITERIA:
1. Conceptual accuracy — did they get the fundamental mechanism right?
2. Completeness — did they cover the key ideas?
3. Clarity — could a beginner understand their explanation?
4. Missing concepts — what important ideas did they omit?

Return ONLY a valid JSON object:
{
  "score": 78,
  "whatYouGotRight": ["Correctly identified X", "Good analogy for Y"],
  "missingConcepts": ["Did not mention Z", "No discussion of edge case W"],
  "refinedExplanation": "A clear, complete, beginner-friendly explanation of the chapter's core concept."
}
Do not wrap in markdown code fences. Return raw JSON only.
""".trimIndent()
    }
}
