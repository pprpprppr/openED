package org.opened.app.core.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class SseEventParserTest {

    @Test
    fun testExtractDataPayload() {
        assertEquals("{\"test\": 123}", SseEventParser.extractDataPayload("data: {\"test\": 123}"))
        assertEquals("hello world", SseEventParser.extractDataPayload("data:hello world"))
        assertNull(SseEventParser.extractDataPayload(": ping"))
        assertNull(SseEventParser.extractDataPayload(""))
        assertNull(SseEventParser.extractDataPayload("data: [DONE]"))
    }

    @Test
    fun testIsDone() {
        assertTrue(SseEventParser.isDone("data: [DONE]"))
        assertTrue(SseEventParser.isDone("data:[DONE]"))
        assertTrue(SseEventParser.isDone("[DONE]"))
        assertFalse(SseEventParser.isDone("data: {\"choices\":[]}"))
    }

    @Test
    fun testExtractOpenAiDelta() {
        val payload = """{"choices":[{"index":0,"delta":{"content":"Hello, openED!"},"finish_reason":null}]}"""
        val chunk = SseEventParser.extractOpenAiDelta(payload)
        assertEquals("Hello, openED!", chunk)

        val emptyDelta = """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
        assertNull(SseEventParser.extractOpenAiDelta(emptyDelta))

        val malformed = """{"choices": [ invalid json """
        assertNull(SseEventParser.extractOpenAiDelta(malformed))
    }

    @Test
    fun testExtractAnthropicDelta() {
        val payload = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Scholarly lesson"}}"""
        val chunk = SseEventParser.extractAnthropicDelta(payload)
        assertEquals("Scholarly lesson", chunk)

        val ping = """{"type":"ping"}"""
        assertNull(SseEventParser.extractAnthropicDelta(ping))
    }

    @Test
    fun testProcessStreamWithMultiLineSse() {
        val sseStreamContent = """
            event: message
            data: {"choices":[{"delta":{"content":"Part 1 "}}]}

            data: {"choices":[{"delta":{"content":"Part 2"}}]}
            data: [DONE]
            data: {"choices":[{"delta":{"content":"Should not read this"}}]}
        """.trimIndent()

        val collected = mutableListOf<String>()
        val inputStream = ByteArrayInputStream(sseStreamContent.toByteArray(Charsets.UTF_8))

        SseEventParser.processStream(
            inputStream = inputStream,
            extractDelta = { payload -> SseEventParser.extractOpenAiDelta(payload) },
            onChunk = { chunk -> collected.add(chunk) }
        )

        assertEquals(2, collected.size)
        assertEquals("Part 1 ", collected[0])
        assertEquals("Part 2", collected[1])
    }
}
