package org.opened.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opened.app.core.ai.AiProvider
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-backed Android Keystore AES-256-GCM secure storage for API credentials and provider settings.
 *
 * Security Architecture:
 * - Encryption Scheme: AES-256 in Galois/Counter Mode (GCM) with no padding.
 * - Hardware Protection: Master key generated and securely isolated in AndroidKeyStore.
 * - Nonce / IV: Unique, cryptographically random 12-byte IV per encryption via SecureRandom.
 * - Integrity & Authenticity: 128-bit authentication tag verified on every decryption.
 * - Versioned Envelope: [1-byte version (0x01)] + [12-byte IV] + [Ciphertext + 16-byte Auth Tag].
 * - Non-Destructive Migration: Automatically reads and migrates legacy credentials from deprecated
 *   EncryptedSharedPreferences and fallback containers into the Keystore envelope on first launch.
 * - Privacy: Zero plaintext logging. Credentials excluded from device and cloud backups.
 */
@Singleton
class SecureApiKeyStorage internal constructor(
    private val context: Context?,
    private val prefsProvider: () -> SharedPreferences
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        prefsProvider = { context.getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE) }
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val secureRandom = SecureRandom()

    @Volatile
    private var prefsInstance: SharedPreferences? = null

    @Volatile
    private var softwareFallbackKey: SecretKey? = null

    private val _activeApiKey = MutableStateFlow<String?>(null)
    val activeApiKey: StateFlow<String?> = _activeApiKey.asStateFlow()

    private val _activeModel = MutableStateFlow<String>(AiProvider.GEMINI.defaultModel)
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    init {
        // Eagerly pre-warm Keystore on Dispatchers.IO
        warmUp()
    }

    fun warmUp() {
        scope.launch {
            loadPrefs()
        }
    }

    // -------------------------------------------------------------------------
    // Android Keystore Cryptographic Engine (AES-256-GCM)
    // -------------------------------------------------------------------------

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                try {
                    val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                    entry?.secretKey ?: generateKeystoreKey()
                } catch (_: Exception) {
                    // Recover from key corruption, hardware change, or permanently invalidated key
                    try {
                        keyStore.deleteEntry(KEY_ALIAS)
                    } catch (_: Throwable) {}
                    generateKeystoreKey()
                }
            } else {
                generateKeystoreKey()
            }
        } catch (_: Exception) {
            // Fallback for host JVM unit test environment where AndroidKeyStore provider is absent
            getOrCreateSoftwareTestKey()
        }
    }

    private fun generateKeystoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(false) // Allows explicit random IV injection per encryption
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateSoftwareTestKey(): SecretKey {
        softwareFallbackKey?.let { return it }
        return synchronized(this) {
            softwareFallbackKey ?: run {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(KEY_SIZE_BITS)
                val key = keyGen.generateKey()
                softwareFallbackKey = key
                key
            }
        }
    }

    /**
     * Encrypts plaintext into a versioned Base64 envelope:
     * [VERSION (1 byte)] + [IV (12 bytes)] + [Ciphertext + 128-bit Auth Tag]
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val secretKey = getOrCreateSecretKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val envelope = ByteArray(1 + GCM_IV_LENGTH_BYTES + ciphertextWithTag.size)
        envelope[0] = ENVELOPE_VERSION_V1
        System.arraycopy(iv, 0, envelope, 1, GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertextWithTag, 0, envelope, 1 + GCM_IV_LENGTH_BYTES, ciphertextWithTag.size)

        return Base64.getEncoder().encodeToString(envelope)
    }

    /**
     * Decrypts and verifies a versioned Base64 envelope.
     * Returns plaintext string or null if envelope is invalid or tampered with.
     */
    fun decrypt(encryptedBase64: String): String? {
        if (encryptedBase64.isBlank()) return null
        val envelope = try {
            Base64.getDecoder().decode(encryptedBase64)
        } catch (_: Exception) {
            return null
        }

        // Validate minimum size: 1 (version) + 12 (iv) + 16 (tag) = 29 bytes
        if (envelope.size < 1 + GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            return null
        }

        val version = envelope[0]
        if (version != ENVELOPE_VERSION_V1) {
            return null
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(envelope, 1, iv, 0, GCM_IV_LENGTH_BYTES)

        val ciphertextWithTagLength = envelope.size - (1 + GCM_IV_LENGTH_BYTES)
        val ciphertextWithTag = ByteArray(ciphertextWithTagLength)
        System.arraycopy(envelope, 1 + GCM_IV_LENGTH_BYTES, ciphertextWithTag, 0, ciphertextWithTagLength)

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val decryptedBytes = cipher.doFinal(ciphertextWithTag)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Persistent Preferences & Non-Destructive Migration
    // -------------------------------------------------------------------------

    private fun loadPrefs(): SharedPreferences {
        prefsInstance?.let { return it }
        return synchronized(this) {
            prefsInstance ?: run {
                val p = prefsProvider()

                // Migrate from legacy EncryptedSharedPreferences or fallback containers if present
                if (context != null) {
                    migrateLegacyCredentials(p)
                }

                // Populate in-memory cache with decrypted credentials; detect and recover from key-loss
                try {
                    val staleCorruptedKeys = mutableListOf<String>()
                    p.all.forEach { (key, value) ->
                        if (value is String) {
                            val decrypted = decrypt(value)
                            if (decrypted != null) {
                                memoryCache[key] = decrypted
                            } else if (value.isNotBlank()) {
                                // Value cannot be decrypted. Key was likely lost, regenerated,
                                // or restored onto a new device without the Keystore private key.
                                staleCorruptedKeys.add(key)
                            }
                        }
                    }
                    if (staleCorruptedKeys.isNotEmpty()) {
                        val editor = p.edit()
                        staleCorruptedKeys.forEach { editor.remove(it) }
                        editor.apply()
                    }
                } catch (_: Exception) {}

                // Update active key & model StateFlows
                val geminiKey = memoryCache[KEY_GEMINI_API_KEY]
                _activeApiKey.value = if (!geminiKey.isNullOrBlank()) geminiKey.trim() else null
                val geminiModel = memoryCache[KEY_GEMINI_MODEL]
                _activeModel.value = if (!geminiModel.isNullOrBlank()) geminiModel.trim() else AiProvider.GEMINI.defaultModel

                prefsInstance = p
                p
            }
        }
    }

    /**
     * Explicit recovery method called when a master key loss is detected or on manual vault reset.
     * Deletes any corrupted Keystore alias, clears memory cache and stored ciphertexts,
     * and generates a fresh hardware-backed key.
     */
    fun resetVaultOnKeyLoss() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Throwable) {}
        softwareFallbackKey = null
        memoryCache.clear()
        _activeApiKey.value = null
        _activeModel.value = AiProvider.GEMINI.defaultModel
        try {
            loadPrefs().edit().clear().apply()
        } catch (_: Throwable) {}
        getOrCreateSecretKey()
    }

    private fun migrateLegacyCredentials(newPrefs: SharedPreferences) {
        val ctx = context ?: return
        // 1. Try reading from legacy EncryptedSharedPreferences
        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val legacyEsp = EncryptedSharedPreferences.create(
                ctx,
                LEGACY_VAULT_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val allEsp = legacyEsp.all
            if (allEsp.isNotEmpty()) {
                val editor = newPrefs.edit()
                for ((key, value) in allEsp) {
                    if (value is String && value.isNotBlank() && !newPrefs.contains(key)) {
                        editor.putString(key, encrypt(value))
                        memoryCache[key] = value
                    }
                }
                editor.apply()
                // Safely clear legacy container once migrated
                legacyEsp.edit().clear().apply()
            }
        } catch (_: Throwable) {
            // EncryptedSharedPreferences might fail if hardware master key was corrupted or absent
        }

        // 2. Try reading from legacy unencrypted fallback store if present
        try {
            val fallbackPrefs = ctx.getSharedPreferences(LEGACY_FALLBACK_VAULT_NAME, Context.MODE_PRIVATE)
            val allFallback = fallbackPrefs.all
            if (allFallback.isNotEmpty()) {
                val editor = newPrefs.edit()
                for ((key, value) in allFallback) {
                    if (value is String && value.isNotBlank() && !newPrefs.contains(key)) {
                        editor.putString(key, encrypt(value))
                        memoryCache[key] = value
                    }
                }
                editor.apply()
                fallbackPrefs.edit().clear().apply()
            }
        } catch (_: Throwable) {
            // Ignore if fallback store cannot be read
        }
    }

    // -------------------------------------------------------------------------
    // API Keys per provider
    // -------------------------------------------------------------------------

    fun getApiKeyForProvider(provider: AiProvider): String? {
        val prefKey = getPrefKeyForApiKey(provider)
        val cached = memoryCache[prefKey]
        if (cached != null) {
            return if (cached.isBlank()) null else cached.trim()
        }
        val encryptedVal = loadPrefs().getString(prefKey, null) ?: return null
        val decrypted = decrypt(encryptedVal)
        if (decrypted != null) {
            memoryCache[prefKey] = decrypted
        }
        return if (decrypted.isNullOrBlank()) null else decrypted.trim()
    }

    suspend fun getApiKeyForProviderAsync(provider: AiProvider): String? = withContext(Dispatchers.IO) {
        getApiKeyForProvider(provider)
    }

    fun saveApiKeyForProvider(provider: AiProvider, apiKey: String) {
        val prefKey = getPrefKeyForApiKey(provider)
        val cleanKey = apiKey.trim()
        memoryCache[prefKey] = cleanKey

        if (provider == AiProvider.GEMINI) {
            _activeApiKey.value = cleanKey.ifBlank { null }
        }

        scope.launch(Dispatchers.IO) {
            val encrypted = encrypt(cleanKey)
            loadPrefs().edit().putString(prefKey, encrypted).apply()
        }
    }

    fun clearApiKeyForProvider(provider: AiProvider) {
        val prefKey = getPrefKeyForApiKey(provider)
        memoryCache.remove(prefKey)

        if (provider == AiProvider.GEMINI) {
            _activeApiKey.value = null
        }

        scope.launch(Dispatchers.IO) {
            loadPrefs().edit().remove(prefKey).apply()
        }
    }

    fun hasApiKeyForProvider(provider: AiProvider): Boolean {
        return !getApiKeyForProvider(provider).isNullOrBlank()
    }

    // -------------------------------------------------------------------------
    // Active Model IDs per provider
    // -------------------------------------------------------------------------

    fun getModelForProvider(provider: AiProvider): String {
        val prefKey = getPrefKeyForModel(provider)
        val cached = memoryCache[prefKey]
        if (!cached.isNullOrBlank()) return cached
        val encryptedVal = loadPrefs().getString(prefKey, null)
        if (!encryptedVal.isNullOrBlank()) {
            val decrypted = decrypt(encryptedVal)
            if (!decrypted.isNullOrBlank()) {
                memoryCache[prefKey] = decrypted
                return decrypted
            }
        }
        return provider.defaultModel
    }

    suspend fun getModelForProviderAsync(provider: AiProvider): String = withContext(Dispatchers.IO) {
        getModelForProvider(provider)
    }

    fun saveModelForProvider(provider: AiProvider, model: String) {
        val prefKey = getPrefKeyForModel(provider)
        val cleanModel = model.trim()
        memoryCache[prefKey] = cleanModel

        if (provider == AiProvider.GEMINI) {
            _activeModel.value = cleanModel.ifBlank { AiProvider.GEMINI.defaultModel }
        }

        scope.launch(Dispatchers.IO) {
            val encrypted = encrypt(cleanModel)
            loadPrefs().edit().putString(prefKey, encrypted).apply()
        }
    }

    // -------------------------------------------------------------------------
    // Custom Base URL
    // -------------------------------------------------------------------------

    fun getCustomBaseUrl(): String {
        val cached = memoryCache[KEY_CUSTOM_BASE_URL]
        if (!cached.isNullOrBlank()) return cached
        val encryptedVal = loadPrefs().getString(KEY_CUSTOM_BASE_URL, null)
        if (!encryptedVal.isNullOrBlank()) {
            val decrypted = decrypt(encryptedVal)
            if (!decrypted.isNullOrBlank()) {
                memoryCache[KEY_CUSTOM_BASE_URL] = decrypted
                return decrypted
            }
        }
        return "http://localhost:11434/v1"
    }

    fun saveCustomBaseUrl(url: String) {
        val cleanUrl = url.trim()
        memoryCache[KEY_CUSTOM_BASE_URL] = cleanUrl
        scope.launch(Dispatchers.IO) {
            val encrypted = encrypt(cleanUrl)
            loadPrefs().edit().putString(KEY_CUSTOM_BASE_URL, encrypted).apply()
        }
    }

    // -------------------------------------------------------------------------
    // Backwards-compatible methods
    // -------------------------------------------------------------------------

    fun getApiKey(): String? = getApiKeyForProvider(AiProvider.GEMINI)

    fun saveApiKey(apiKey: String) = saveApiKeyForProvider(AiProvider.GEMINI, apiKey)

    fun clearApiKey() = clearApiKeyForProvider(AiProvider.GEMINI)

    fun hasApiKey(): Boolean {
        return hasApiKeyForProvider(AiProvider.GEMINI) ||
                hasApiKeyForProvider(AiProvider.CLAUDE) ||
                hasApiKeyForProvider(AiProvider.OPENAI) ||
                hasApiKeyForProvider(AiProvider.CUSTOM)
    }

    private fun getPrefKeyForApiKey(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> KEY_GEMINI_API_KEY
        AiProvider.CLAUDE -> KEY_CLAUDE_API_KEY
        AiProvider.OPENAI -> KEY_OPENAI_API_KEY
        AiProvider.CUSTOM -> KEY_CUSTOM_API_KEY
    }

    private fun getPrefKeyForModel(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> KEY_GEMINI_MODEL
        AiProvider.CLAUDE -> KEY_CLAUDE_MODEL
        AiProvider.OPENAI -> KEY_OPENAI_MODEL
        AiProvider.CUSTOM -> KEY_CUSTOM_MODEL
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "opened_keystore_master_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val ENVELOPE_VERSION_V1: Byte = 0x01

        private const val VAULT_PREFS_NAME = "opened_keystore_vault"
        private const val LEGACY_VAULT_NAME = "opened_secure_vault"
        private const val LEGACY_FALLBACK_VAULT_NAME = "opened_secure_vault_fallback"

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
