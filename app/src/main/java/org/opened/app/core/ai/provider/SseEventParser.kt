package org.opened.app.core.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object SseEventParser {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    /**
     * Parses an incoming SSE line and extracts the raw payload if it starts with "data:".
     * Returns null if the line is empty, comment, event type, or [DONE].
     */
    fun extractDataPayload(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return payload
    }

    /**
     * Checks if a line is the SSE termination token "[DONE]".
     */
    fun isDone(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "data: [DONE]" || trimmed == "data:[DONE]" || trimmed == "[DONE]"
    }

    /**
     * Extracts text delta from an OpenAI or OpenAI-compatible streaming chunk:
     * {"choices": [{"delta": {"content": "text"}}]}
     */
    fun extractOpenAiDelta(payload: String): String? {
        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            val choices = root["choices"]?.jsonArray
            val delta = choices?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
            delta?.get("content")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts text delta from an Anthropic Claude streaming chunk:
     * {"type": "content_block_delta", "delta": {"type": "text_delta", "text": "text"}}
     */
    fun extractAnthropicDelta(payload: String): String? {
        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            val type = root["type"]?.jsonPrimitive?.content
            if (type == "content_block_delta") {
                val delta = root["delta"]?.jsonObject
                delta?.get("text")?.jsonPrimitive?.content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Processes an InputStream line by line, extracting text deltas using the provided extractor.
     */
    inline fun processStream(
        inputStream: InputStream,
        extractDelta: (String) -> String?,
        onChunk: (String) -> Unit
    ) {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (isDone(currentLine)) {
                    break
                }
                val payload = extractDataPayload(currentLine) ?: continue
                val text = extractDelta(payload)
                if (!text.isNullOrEmpty()) {
                    onChunk(text)
                }
            }
        }
    }
}
