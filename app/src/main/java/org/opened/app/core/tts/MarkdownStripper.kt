package org.opened.app.core.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility for sanitizing Markdown documents and chunking them into spoken audio segments
 * compliant with Android TextToSpeech input length boundaries (under 4,000 chars).
 */
object MarkdownStripper {

    private val DIAGRAM_BLOCK_REGEX = Regex("""```(mermaid|diagram|chart|plot)[\s\S]*?```""", RegexOption.IGNORE_CASE)
    private val FENCED_CODE_REGEX = Regex("""```[a-zA-Z0-9_\-]*[\s\S]*?```""")
    private val DISPLAY_MATH_REGEX = Regex("""\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]""")
    private val INLINE_MATH_REGEX = Regex("""\$[^$\n]+\$|\\\([^\)\n]+\\\)""")
    private val INLINE_CODE_REGEX = Regex("""`([^`]+)`""")
    private val WIKI_LINK_REGEX = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?\]\]""")
    private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]+)\]\([^)]+\)""")
    private val CALLOUT_HEADER_REGEX = Regex("""^>\s*\[![A-Z]+\][^\n]*""", RegexOption.MULTILINE)
    private val BLOCKQUOTE_MARKER_REGEX = Regex("""^>\s*""", RegexOption.MULTILINE)
    private val HEADINGS_REGEX = Regex("""^#{1,6}\s+""", RegexOption.MULTILINE)
    private val BOLD_ITALIC_STARS_REGEX = Regex("""\*{1,3}([^*]+)\*{1,3}""")
    private val BOLD_ITALIC_UNDERSCORES_REGEX = Regex("""_{1,3}([^_]+)_{1,3}""")
    private val HR_REGEX = Regex("""^(?:---|\*\*\*|___)\s*${'$'}""", RegexOption.MULTILINE)
    private val TABLE_DELIMITER_REGEX = Regex("""^\|?\s*[-:]+[-| :]*\|?\s*${'$'}""", RegexOption.MULTILINE)
    private val EXCESS_SPACES_REGEX = Regex("""[ \t]+""")
    private val EXCESS_NEWLINES_REGEX = Regex("""\n{3,}""")

    /**
     * Cleans Markdown asynchronously on Dispatchers.Default for audio speech playback.
     */
    suspend fun stripForSpeech(rawMarkdown: String): String = withContext(Dispatchers.Default) {
        cleanMarkdownSync(rawMarkdown)
    }

    /**
     * Synchronous Markdown stripper replacing non-spoken blocks (code, diagrams, math)
     * with brief audio cues and converting math symbols into natural English words.
     */
    fun cleanMarkdownSync(rawMarkdown: String): String {
        if (rawMarkdown.isBlank()) return ""

        var text = rawMarkdown

        // 1. Completely remove diagram & chart blocks
        text = text.replace(DIAGRAM_BLOCK_REGEX, " Diagram omitted. ")

        // 2. Replace fenced code blocks with audio summary
        text = text.replace(FENCED_CODE_REGEX, " Code example omitted during audio playback. ")

        // 3. LaTeX and math formulas
        text = text.replace(DISPLAY_MATH_REGEX, " Mathematical formula omitted. ")
        text = text.replace(INLINE_MATH_REGEX, " formula ")

        // 4. Convert common mathematical and logic symbols to spoken phrases
        text = text.replace("→", " leads to ")
            .replace("⇒", " implies ")
            .replace("⇔", " if and only if ")
            .replace("≥", " greater than or equal to ")
            .replace("≤", " less than or equal to ")
            .replace("≠", " not equal to ")
            .replace("≈", " approximately equals ")
            .replace("±", " plus or minus ")
            .replace("×", " times ")
            .replace("÷", " divided by ")
            .replace("∞", " infinity ")
            .replace("∑", " sum of ")
            .replace("∏", " product of ")
            .replace("∫", " integral of ")
            .replace("∂", " partial derivative ")
            .replace("√", " square root of ")

        // 5. Table delimiters & pipe formatting
        text = text.replace(TABLE_DELIMITER_REGEX, "")
        text = text.replace("|", " ")

        // 6. Horizontal rules
        text = text.replace(HR_REGEX, "")

        // 7. Strip callout headers & blockquote markers
        text = text.replace(CALLOUT_HEADER_REGEX, "")
        text = text.replace(BLOCKQUOTE_MARKER_REGEX, "")

        // 8. Headers
        text = text.replace(HEADINGS_REGEX, "")

        // 9. Links: [[WikiLinks]] and [Markdown](url)
        text = text.replace(WIKI_LINK_REGEX) { match ->
            match.groupValues[2].ifBlank { match.groupValues[1] }
        }
        text = text.replace(MARKDOWN_LINK_REGEX) { match ->
            match.groupValues[1]
        }

        // 10. Inline code
        text = text.replace(INLINE_CODE_REGEX) { match ->
            match.groupValues[1]
        }

        // 11. Bold / Italic
        text = text.replace(BOLD_ITALIC_STARS_REGEX) { match -> match.groupValues[1] }
        text = text.replace(BOLD_ITALIC_UNDERSCORES_REGEX) { match -> match.groupValues[1] }

        // 12. List bullets & numbered lists
        text = text.replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "")
        text = text.replace(Regex("""^\s*\d+\.\s+""", RegexOption.MULTILINE), "")

        // 13. Normalize whitespace
        text = text.replace(EXCESS_SPACES_REGEX, " ")
        text = text.replace(EXCESS_NEWLINES_REGEX, "\n\n")

        return text.trim()
    }

    /**
     * Splits cleaned text into chunks strictly <= [maxChunkChars] (default 1800),
     * respecting paragraph, sentence, and word boundaries.
     */
    fun chunkForSpeech(cleanText: String, maxChunkChars: Int = 1800): List<String> {
        if (cleanText.isBlank()) return emptyList()
        if (cleanText.length <= maxChunkChars) return listOf(cleanText)

        val paragraphs = cleanText.split(Regex("""\n\s*\n"""))
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            val trimmedPara = paragraph.trim()
            if (trimmedPara.isEmpty()) continue

            if (trimmedPara.length <= maxChunkChars) {
                if (currentChunk.isNotEmpty() && currentChunk.length + trimmedPara.length + 2 > maxChunkChars) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk.clear()
                }
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(trimmedPara)
            } else {
                // Split large paragraph by sentence
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk.clear()
                }

                val sentences = trimmedPara.split(Regex("""(?<=[.!?])\s+"""))
                val sentenceBuilder = StringBuilder()
                for (sentence in sentences) {
                    val s = sentence.trim()
                    if (s.isEmpty()) continue
                    if (sentenceBuilder.isNotEmpty() && sentenceBuilder.length + s.length + 1 > maxChunkChars) {
                        chunks.add(sentenceBuilder.toString().trim())
                        sentenceBuilder.clear()
                    }
                    if (s.length > maxChunkChars) {
                        // Word-level fallback
                        val words = s.split(" ")
                        for (w in words) {
                            if (sentenceBuilder.length + w.length + 1 > maxChunkChars) {
                                chunks.add(sentenceBuilder.toString().trim())
                                sentenceBuilder.clear()
                            }
                            if (sentenceBuilder.isNotEmpty()) sentenceBuilder.append(" ")
                            sentenceBuilder.append(w)
                        }
                    } else {
                        if (sentenceBuilder.isNotEmpty()) sentenceBuilder.append(" ")
                        sentenceBuilder.append(s)
                    }
                }
                if (sentenceBuilder.isNotEmpty()) {
                    chunks.add(sentenceBuilder.toString().trim())
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.filter { it.isNotBlank() }
    }
}
