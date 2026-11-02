package org.opened.app.core.security

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.Base64

class SecureApiKeyStorageTest {

    private lateinit var storage: SecureApiKeyStorage

    @Before
    fun setUp() {
        val inMemoryPrefs = createInMemorySharedPreferences()
        storage = SecureApiKeyStorage(context = null) { inMemoryPrefs }
    }

    @Test
    fun testEncryptDecryptRoundTrip() {
        val originalKey = "AIzaSyD-dummyGeminiApiKey1234567890_test"
        val encrypted = storage.encrypt(originalKey)

        assertNotNull("Encrypted string should not be null", encrypted)
        assertTrue("Encrypted string should be non-empty", encrypted.isNotEmpty())
        assertNotEquals("Ciphertext must not match plaintext", originalKey, encrypted)

        val decrypted = storage.decrypt(encrypted)
        assertEquals("Decrypted plaintext must match original exactly", originalKey, decrypted)
    }

    @Test
    fun testRandomizedIvProducesDistinctCiphertextsForIdenticalPlaintext() {
        val plaintext = "identical-secret-token"
        val cipher1 = storage.encrypt(plaintext)
        val cipher2 = storage.encrypt(plaintext)

        assertNotEquals("Each encryption must generate a unique random IV", cipher1, cipher2)

        // Both must decrypt to the exact same plaintext
        assertEquals(plaintext, storage.decrypt(cipher1))
        assertEquals(plaintext, storage.decrypt(cipher2))
    }

    @Test
    fun testEnvelopeStructureAndVersionByte() {
        val plaintext = "test-token"
        val encryptedBase64 = storage.encrypt(plaintext)

        val envelope = Base64.getDecoder().decode(encryptedBase64)
        // [1 byte version] + [12 bytes IV] + [ciphertext] + [16 bytes GCM tag]
        assertTrue("Envelope must be at least 29 bytes", envelope.size >= 29)
        assertEquals("Version byte must be 0x01", 0x01.toByte(), envelope[0])
    }

    @Test
    fun testTamperDetectionWithAuthenticationTag() {
        val plaintext = "sensitive-data-to-protect"
        val encryptedBase64 = storage.encrypt(plaintext)
        val envelope = Base64.getDecoder().decode(encryptedBase64)

        // Tamper with the last byte of the authentication tag
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0xFF).toByte()
        val tamperedBase64 = Base64.getEncoder().encodeToString(envelope)

        val decrypted = storage.decrypt(tamperedBase64)
        assertNull("Tampered ciphertext or tag must fail decryption and return null", decrypted)
    }

    @Test
    fun testUnsupportedVersionRejection() {
        val plaintext = "sensitive-data"
        val encryptedBase64 = storage.encrypt(plaintext)
        val envelope = Base64.getDecoder().decode(encryptedBase64)

        // Alter version byte to 0x02
        envelope[0] = 0x02.toByte()
        val alteredVersionBase64 = Base64.getEncoder().encodeToString(envelope)

        val decrypted = storage.decrypt(alteredVersionBase64)
        assertNull("Unsupported envelope version must return null", decrypted)
    }

    @Test
    fun testEmptyAndInvalidInputs() {
        assertEquals("", storage.encrypt(""))
        assertNull(storage.decrypt(""))
        assertNull(storage.decrypt("invalid-base-64!!@@"))
        assertNull(storage.decrypt(Base64.getEncoder().encodeToString(byteArrayOf(0x01, 0x02))))
    }

    private fun createInMemorySharedPreferences(): android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        val editorHandler = java.lang.reflect.InvocationHandler { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    map[args[0] as String] = args[1]
                    proxy
                }
                "remove" -> {
                    map.remove(args[0] as String)
                    proxy
                }
                "clear" -> {
                    map.clear()
                    proxy
                }
                "apply", "commit" -> {
                    if (method.returnType == Boolean::class.javaPrimitiveType) true else null
                }
                else -> proxy
            }
        }
        val editor = Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java),
            editorHandler
        ) as android.content.SharedPreferences.Editor

        val prefsHandler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "getString" -> {
                    val key = args[0] as String
                    val def = args[1] as? String
                    (map[key] as? String) ?: def
                }
                "getAll" -> map
                "contains" -> map.containsKey(args[0] as String)
                "edit" -> editor
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java),
            prefsHandler
        ) as android.content.SharedPreferences
    }
}
