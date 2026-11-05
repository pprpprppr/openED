package org.opened.app.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Bulletproof JSON cleaner and repairer that recovers from LLM output defects:
 * - Markdown fences (```json ... ```)
 * - Leading conversational text before the opening '{' or '['
 * - Trailing conversational text after the closing '}' or ']'
 * - Trailing commas before closing braces/brackets
 * - Truncation at EOF due to token limits or stream interruptions (unclosed quotes,
 *   unclosed arrays, unclosed objects, incomplete key/value pairs)
 */
object JsonRepairer {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val lenientJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    /**
     * Cleans and repairs raw JSON output from an LLM.
     * Guarantees a structurally balanced JSON string that can be decoded by kotlinx.serialization.
     */
    fun cleanAndRepairJson(raw: String): String {
        if (raw.isBlank()) return "{}"

        // 1. Extract JSON block from markdown fences if present
        var content = extractJsonFromMarkdown(raw.trim())

        // 2. Strip leading non-JSON characters until first '{' or '['
        val firstBrace = content.indexOf('{')
        val firstBracket = content.indexOf('[')
        val startIndex = when {
            firstBrace >= 0 && firstBracket >= 0 -> minOf(firstBrace, firstBracket)
            firstBrace >= 0 -> firstBrace
            firstBracket >= 0 -> firstBracket
            else -> -1
        }
        if (startIndex > 0) {
            content = content.substring(startIndex)
        } else if (startIndex == -1) {
            return "{}"
        }

        // 3. Remove trailing commas before closing braces/brackets
        content = removeTrailingCommas(content)

        // 4. Quick check: does it already parse?
        if (isValidJson(content)) {
            return content
        }

        // 5. Direct repair of EOF truncation
        val directRepaired = attemptRepair(content)
        if (isValidJson(directRepaired)) {
            return directRepaired
        }

        // 6. Backtrack past the last comma (removes partially generated trailing object)
        var backtracked = content
        for (attempt in 1..8) {
            val lastComma = backtracked.lastIndexOf(',')
            if (lastComma <= 0) break
            backtracked = backtracked.substring(0, lastComma).trim()
            val repaired = attemptRepair(backtracked)
            if (isValidJson(repaired)) {
                return repaired
            }
        }

        // 7. If still invalid, return direct repaired or raw content as fallback
        return if (directRepaired.isNotBlank()) directRepaired else content
    }

    fun isValidJson(jsonStr: String): Boolean {
        return runCatching {
            lenientJson.parseToJsonElement(jsonStr)
            true
        }.getOrDefault(false)
    }

    private fun extractJsonFromMarkdown(raw: String): String {
        val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        val match = fenceRegex.find(raw)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        // If there's an opening fence without a closing fence (truncated stream)
        val openFenceRegex = Regex("""^```(?:json)?\s*([\s\S]*)""", RegexOption.IGNORE_CASE)
        val openMatch = openFenceRegex.find(raw)
        if (openMatch != null) {
            return openMatch.groupValues[1].trim()
        }
        return raw
    }

    private fun removeTrailingCommas(input: String): String {
        return input.replace(Regex(""",\s*([\]\}])"""), "$1")
    }

    private fun attemptRepair(input: String): String {
        var str = input.trim()
        val stack = mutableListOf<Char>()
        var inString = false
        var escape = false
        var lastChar = ' '

        for (ch in str) {
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\') {
                if (inString) escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                lastChar = ch
                continue
            }
            if (!inString) {
                when (ch) {
                    '{' -> stack.add('}')
                    '[' -> stack.add(']')
                    '}', ']' -> {
                        if (stack.isNotEmpty() && stack.last() == ch) {
                            stack.removeAt(stack.size - 1)
                        }
                    }
                }
                if (!ch.isWhitespace()) {
                    lastChar = ch
                }
            }
        }

        val sb = java.lang.StringBuilder(str)

        // If EOF occurred inside an open string, close it
        if (inString) {
            sb.append('"')
            lastChar = '"'
        }

        var trimmedTail = sb.toString().trimEnd()

        // If tail ends with a colon (e.g. `"key":`), append empty string value
        if (trimmedTail.endsWith(":")) {
            sb.append(" \"\"")
            trimmedTail = sb.toString().trimEnd()
        }

        // If tail ends with a dangling comma, remove it
        if (trimmedTail.endsWith(",")) {
            val commaIdx = sb.lastIndexOf(',')
            if (commaIdx >= 0) {
                sb.deleteCharAt(commaIdx)
            }
        }

        // Close all unclosed brackets/braces in reverse order
        for (i in stack.indices.reversed()) {
            sb.append(stack[i])
        }

        val result = sb.toString()
        return removeTrailingCommas(result)
    }
}
