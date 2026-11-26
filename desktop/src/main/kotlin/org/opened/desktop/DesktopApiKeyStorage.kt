package org.opened.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.platform.ApiKeyStorageHelper
import org.opened.app.core.platform.ApiKeyStore
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop AES-256-GCM local vault storing API keys and active models inside the user's OS data folder.
 */
class DesktopApiKeyStorage : ApiKeyStore {
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val secureRandom = SecureRandom()
    private val lock = Any()

    @Volatile
    private var secretKey: SecretKey? = null

    @Volatile
    private var loaded = false

    private val _activeApiKey = MutableStateFlow<String?>(null)
    override val activeApiKey: StateFlow<String?> = _activeApiKey.asStateFlow()

    private val _activeModel = MutableStateFlow(AiProvider.GEMINI.defaultModel)
    override val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    init {
        warmUp()
    }

    override fun warmUp() {
        synchronized(lock) {
            ensureLoaded()
            val currentGeminiModel = getModelForProvider(AiProvider.GEMINI)
            if (ApiKeyStorageHelper.isDeprecatedGeminiModel(currentGeminiModel)) {
                saveModelForProvider(AiProvider.GEMINI, "gemini-3.8-flash")
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        secretKey?.let { return it }
        synchronized(lock) {
            secretKey?.let { return it }
            val keyFile = DesktopPaths.vaultKeyFile
            val key = if (keyFile.exists()) {
                runCatching {
                    val raw = Base64.getDecoder().decode(keyFile.readText(Charsets.UTF_8).trim())
                    SecretKeySpec(raw, "AES")
                }.getOrNull()
            } else null

            val resolved = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { newKey ->
                keyFile.writeText(Base64.getEncoder().encodeToString(newKey.encoded), Charsets.UTF_8)
                restrictPosixPermissions(keyFile)
            }
            secretKey = resolved
            return resolved
        }
    }

    private fun restrictPosixPermissions(file: java.io.File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        }
    }

    private fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val envelope = ByteArray(1 + 12 + encrypted.size)
        envelope[0] = 0x01
        System.arraycopy(iv, 0, envelope, 1, 12)
        System.arraycopy(encrypted, 0, envelope, 13, encrypted.size)
        return Base64.getEncoder().encodeToString(envelope)
    }

    private fun decrypt(encoded: String): String? {
        if (encoded.isBlank()) return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.size < 29 || bytes[0] != 0x01.toByte()) return null
            val iv = bytes.copyOfRange(1, 13)
            val payload = bytes.copyOfRange(13, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun ensureLoaded() {
        if (loaded) return
        val props = Properties()
        val vaultFile = DesktopPaths.vaultFile
        if (vaultFile.exists()) {
            runCatching {
                vaultFile.inputStream().use { props.load(it) }
            }
        }
        for ((k, v) in props) {
            val key = k?.toString() ?: continue
            val encVal = v?.toString() ?: continue
            val plain = decrypt(encVal)
            if (plain != null) {
                memoryCache[key] = plain
            }
        }
        _activeApiKey.value = memoryCache[KEY_GEMINI_API_KEY]?.trim()?.ifBlank { null }
        _activeModel.value = memoryCache[KEY_GEMINI_MODEL]?.trim()?.ifBlank { null } ?: AiProvider.GEMINI.defaultModel
        loaded = true
    }

    private fun persistProps() {
        val props = Properties()
        for ((k, v) in memoryCache) {
            if (v.isNotEmpty()) {
                props.setProperty(k, encrypt(v))
            }
        }
        DesktopPaths.vaultFile.outputStream().use { props.store(it, "openED encrypted API vault") }
        restrictPosixPermissions(DesktopPaths.vaultFile)
    }

    override fun getApiKeyForProvider(provider: AiProvider): String? {
        synchronized(lock) {
            ensureLoaded()
            return memoryCache[apiKeyPref(provider)]?.trim()?.ifBlank { null }
        }
    }

    override fun saveApiKeyForProvider(provider: AiProvider, apiKey: String) {
        synchronized(lock) {
            ensureLoaded()
            val clean = apiKey.trim()
            memoryCache[apiKeyPref(provider)] = clean
            if (provider == AiProvider.GEMINI) {
                _activeApiKey.value = clean.ifBlank { null }
            }
            persistProps()
        }
    }

    override fun clearApiKeyForProvider(provider: AiProvider) {
        synchronized(lock) {
            ensureLoaded()
            memoryCache.remove(apiKeyPref(provider))
            if (provider == AiProvider.GEMINI) {
                _activeApiKey.value = null
            }
            persistProps()
        }
    }

    override fun hasApiKeyForProvider(provider: AiProvider): Boolean =
        !getApiKeyForProvider(provider).isNullOrBlank()

    override fun getModelForProvider(provider: AiProvider): String {
        synchronized(lock) {
            ensureLoaded()
            val cached = memoryCache[modelPref(provider)]
            if (!cached.isNullOrBlank()) {
                if (provider == AiProvider.GEMINI && ApiKeyStorageHelper.isDeprecatedGeminiModel(cached)) {
                    saveModelForProvider(provider, "gemini-3.8-flash")
                    return "gemini-3.8-flash"
                }
                return cached
            }
            return provider.defaultModel
        }
    }

    override fun saveModelForProvider(provider: AiProvider, model: String) {
        synchronized(lock) {
            ensureLoaded()
            val clean = if (provider == AiProvider.GEMINI && ApiKeyStorageHelper.isDeprecatedGeminiModel(model)) {
                "gemini-3.8-flash"
            } else {
                model.trim()
            }
            memoryCache[modelPref(provider)] = clean
            if (provider == AiProvider.GEMINI) {
                _activeModel.value = clean.ifBlank { AiProvider.GEMINI.defaultModel }
            }
            persistProps()
        }
    }

    override fun getCustomBaseUrl(): String {
        synchronized(lock) {
            ensureLoaded()
            val cached = memoryCache[KEY_CUSTOM_BASE_URL]
            return if (!cached.isNullOrBlank()) ApiKeyStorageHelper.sanitizeCustomBaseUrl(cached) else "http://localhost:11434/v1"
        }
    }

    override fun saveCustomBaseUrl(url: String) {
        synchronized(lock) {
            ensureLoaded()
            memoryCache[KEY_CUSTOM_BASE_URL] = ApiKeyStorageHelper.sanitizeCustomBaseUrl(url)
            persistProps()
        }
    }

    override fun getApiKey(): String? = getApiKeyForProvider(AiProvider.GEMINI)
    override fun saveApiKey(apiKey: String) = saveApiKeyForProvider(AiProvider.GEMINI, apiKey)
    override fun clearApiKey() = clearApiKeyForProvider(AiProvider.GEMINI)
    override fun hasApiKey(): Boolean = AiProvider.entries.any { hasApiKeyForProvider(it) }

    private fun apiKeyPref(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> KEY_GEMINI_API_KEY
        AiProvider.CLAUDE -> KEY_CLAUDE_API_KEY
        AiProvider.OPENAI -> KEY_OPENAI_API_KEY
        AiProvider.CUSTOM -> KEY_CUSTOM_API_KEY
    }

    private fun modelPref(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> KEY_GEMINI_MODEL
        AiProvider.CLAUDE -> KEY_CLAUDE_MODEL
        AiProvider.OPENAI -> KEY_OPENAI_MODEL
        AiProvider.CUSTOM -> KEY_CUSTOM_MODEL
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_GEMINI_MODEL = "gemini_active_model"
        private const val KEY_CLAUDE_MODEL = "claude_active_model"
        private const val KEY_OPENAI_MODEL = "openai_active_model"
        private const val KEY_CUSTOM_MODEL = "custom_active_model"
    }
}
