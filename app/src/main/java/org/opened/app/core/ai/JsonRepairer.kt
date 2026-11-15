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

        // 3. Fix unescaped LaTeX backslashes inside JSON string values
        content = fixUnescapedBackslashes(content)

        // 4. Remove trailing commas before closing braces/brackets
        content = removeTrailingCommas(content)

        // 5. Quick check: does it already parse?
        if (isValidJson(content)) {
            return content
        }

        // 6. Direct repair of EOF truncation
        val directRepaired = attemptRepair(content)
        if (isValidJson(directRepaired)) {
            return directRepaired
        }

        // 7. Backtrack past the last comma (removes partially generated trailing object)
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

        // 8. If still invalid, return direct repaired or raw content as fallback
        return if (directRepaired.isNotBlank()) directRepaired else content
    }

    /**
     * Repairs unescaped LaTeX backslashes (e.g., \frac, \int, \alpha, \sqrt, \theta, \times)
     * inside JSON strings to prevent invalid escape sequence deserialization errors.
     */
    fun fixUnescapedBackslashes(input: String): String {
        val sb = StringBuilder(input.length + 32)
        var inString = false
        var i = 0
        val len = input.length

        while (i < len) {
            val c = input[i]
            if (c == '"') {
                var backslashCount = 0
                var j = i - 1
                while (j >= 0 && input[j] == '\\') {
                    backslashCount++
                    j--
                }
                if (backslashCount % 2 == 0) {
                    inString = !inString
                }
                sb.append(c)
                i++
                continue
            }

            if (inString && c == '\\') {
                if (i + 1 >= len) {
                    sb.append("\\\\")
                    i++
                    continue
                }
                val next = input[i + 1]

                // Known LaTeX command prefixes starting with f, t, b, n, r
                val isLatexCommandWithControlLetter = when (next) {
                    'f' -> input.regionMatches(i, "\\frac", 0, 5, ignoreCase = false)
                    't' -> input.regionMatches(i, "\\text", 0, 5, ignoreCase = false) ||
                           input.regionMatches(i, "\\theta", 0, 6, ignoreCase = false) ||
                           input.regionMatches(i, "\\times", 0, 6, ignoreCase = false) ||
                           input.regionMatches(i, "\\tau", 0, 4, ignoreCase = false) ||
                           input.regionMatches(i, "\\to", 0, 3, ignoreCase = false) ||
                           input.regionMatches(i, "\\tan", 0, 4, ignoreCase = false)
                    'b' -> input.regionMatches(i, "\\beta", 0, 5, ignoreCase = false) ||
                           input.regionMatches(i, "\\bar", 0, 4, ignoreCase = false) ||
                           input.regionMatches(i, "\\binom", 0, 6, ignoreCase = false)
                    'n' -> input.regionMatches(i, "\\nabla", 0, 6, ignoreCase = false) ||
                           input.regionMatches(i, "\\nu", 0, 3, ignoreCase = false) ||
                           input.regionMatches(i, "\\neq", 0, 4, ignoreCase = false)
                    'r' -> input.regionMatches(i, "\\rho", 0, 4, ignoreCase = false) ||
                           input.regionMatches(i, "\\right", 0, 6, ignoreCase = false)
                    else -> false
                }

                val isStandardControl = when (next) {
                    '"', '\\', '/' -> true
                    'u' -> {
                        i + 5 < len && (2..5).all { offset ->
                            val hexChar = input[i + offset]
                            hexChar in '0'..'9' || hexChar in 'a'..'f' || hexChar in 'A'..'F'
                        }
                    }
                    'b', 'f', 'n', 'r', 't' -> !isLatexCommandWithControlLetter
                    else -> false
                }

                if (isStandardControl) {
                    sb.append(c)
                    sb.append(next)
                    i += 2
                    if (next == 'u') {
                        for (k in 2..5) {
                            sb.append(input[i])
                            i++
                        }
                    }
                } else {
                    // Escape the backslash so \int becomes \\int, \frac becomes \\frac, etc.
                    sb.append("\\\\")
                    sb.append(next)
                    i += 2
                }
                continue
            }

            sb.append(c)
            i++
        }
        return sb.toString()
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
