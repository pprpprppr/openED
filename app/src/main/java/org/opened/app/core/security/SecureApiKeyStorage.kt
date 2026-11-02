package org.opened.app.core.security

import android.content.Context
import android.content.SharedPreferences
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureApiKeyStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var prefsInstance: SharedPreferences? = null

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

    private fun loadPrefs(): SharedPreferences {
        prefsInstance?.let { return it }
        return synchronized(this) {
            prefsInstance ?: run {
                val p = try {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    EncryptedSharedPreferences.create(
                        context,
                        "opened_secure_vault",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e: Exception) {
                    // Fallback for sandboxed or test environments where Keystore hardware provider might have issues
                    context.getSharedPreferences("opened_secure_vault_fallback", Context.MODE_PRIVATE)
                }

                // Populate in-memory cache to guarantee zero main-thread disk I/O on subsequent reads
                try {
                    p.all.forEach { (key, value) ->
                        if (value is String) {
                            memoryCache[key] = value
                        }
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

    // -------------------------------------------------------------------------
    // API Keys per provider
    // -------------------------------------------------------------------------

    fun getApiKeyForProvider(provider: AiProvider): String? {
        val prefKey = getPrefKeyForApiKey(provider)
        val cached = memoryCache[prefKey]
        if (cached != null) {
            return if (cached.isBlank()) null else cached.trim()
        }
        val diskVal = loadPrefs().getString(prefKey, null)
        if (diskVal != null) {
            memoryCache[prefKey] = diskVal
        }
        return if (diskVal.isNullOrBlank()) null else diskVal.trim()
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
            loadPrefs().edit().putString(prefKey, cleanKey).apply()
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
        val diskVal = loadPrefs().getString(prefKey, null)
        if (!diskVal.isNullOrBlank()) {
            memoryCache[prefKey] = diskVal
            return diskVal
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
            loadPrefs().edit().putString(prefKey, cleanModel).apply()
        }
    }

    // -------------------------------------------------------------------------
    // Custom Base URL
    // -------------------------------------------------------------------------

    fun getCustomBaseUrl(): String {
        val cached = memoryCache[KEY_CUSTOM_BASE_URL]
        if (!cached.isNullOrBlank()) return cached
        val diskVal = loadPrefs().getString(KEY_CUSTOM_BASE_URL, null)
        if (!diskVal.isNullOrBlank()) {
            memoryCache[KEY_CUSTOM_BASE_URL] = diskVal
            return diskVal
        }
        return "http://localhost:11434/v1"
    }

    fun saveCustomBaseUrl(url: String) {
        val cleanUrl = url.trim()
        memoryCache[KEY_CUSTOM_BASE_URL] = cleanUrl
        scope.launch(Dispatchers.IO) {
            loadPrefs().edit().putString(KEY_CUSTOM_BASE_URL, cleanUrl).apply()
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
