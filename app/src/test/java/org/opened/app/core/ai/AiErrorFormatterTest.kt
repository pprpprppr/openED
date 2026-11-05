package org.opened.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiErrorFormatterTest {

    @Test
    fun testQuotaErrorFormatted() {
        val err1 = "Gemini API error (429): RESOURCE_EXHAUSTED: Quota exceeded for quota metric 'Generate Content API requests' and limit 'GenerateContent request limit per minute for a region' of service 'generativelanguage.googleapis.com'"
        assertEquals(
            "⚠️ API quota limit reached for this model. Please switch to a Flash/Free model in Settings or try again in a minute.",
            formatUserFriendlyAiError(err1)
        )

        val err2 = "Rate limit reached: limit: 0 for gemini-3.1-pro"
        assertEquals(
            "⚠️ API quota limit reached for this model. Please switch to a Flash/Free model in Settings or try again in a minute.",
            formatUserFriendlyAiError(err2)
        )
    }

    @Test
    fun testCleartextErrorFormatted() {
        val err = "java.io.IOException: CLEARTEXT communication to openrouter.ai not permitted by network security policy"
        assertEquals(
            "⚠️ Unencrypted http:// URL blocked. Please use https:// (e.g. https://openrouter.ai/api/v1).",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testAuthErrorFormatted() {
        val err = "HTTP 401 Unauthorized: API_KEY_INVALID"
        assertEquals(
            "⚠️ Invalid API key. Please check your key in Settings.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testJsonEofAndSerializationErrorFormatted() {
        val err = "kotlinx.serialization.SerializationException: Unexpected JSON token at offset 4120: Expected end of the object or '}'"
        assertEquals(
            "⚠️ The AI returned an incomplete response. Please regenerate or try a different model.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testNetworkOfflineAndHostErrorFormatted() {
        val err = "java.net.UnknownHostException: Unable to resolve host \"openrouter.ai\": No address associated with hostname"
        assertEquals(
            "⚠️ Unable to connect. Please check your internet connection and try again.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testTimeoutErrorFormatted() {
        val err = "java.net.SocketTimeoutException: timeout after 30000ms"
        assertEquals(
            "⚠️ Request timed out. The AI model is taking too long to respond. Please try again.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testModelNotFound404Formatted() {
        val err = "Gemini API error (404): models/gemini-2.5-flash is no longer available"
        assertEquals(
            "⚠️ Model not found or deprecated. Please choose an active model in Settings.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testServerError503Formatted() {
        val err = "HTTP 503 Service Unavailable: The model is currently overloaded"
        assertEquals(
            "⚠️ AI service is temporarily unavailable or overloaded. Please try again in a few moments.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testMissingApiKeyFormatted() {
        val err = "MissingApiKeyException: Gemini API Key is missing. Please configure your key in Settings."
        assertEquals(
            "⚠️ API key missing. Please configure your API key in Settings.",
            formatUserFriendlyAiError(err)
        )
    }

    @Test
    fun testGenericErrorStrippingAndCapping() {
        val longError = "Failed to connect to https://some.service.com/v1/api/endpoint with payload {\"error\": \"internal_failure\"} at org.opened.app.core.ai.Client(Client.kt:120) because the server was busy processing other tasks for another user."
        val formatted = formatUserFriendlyAiError(longError)
        assertTrue(formatted.length <= 110)
        assertTrue(!formatted.contains("https://"))
        assertTrue(!formatted.contains("{\"error\""))
    }
}
