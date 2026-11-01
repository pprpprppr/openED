package org.opened.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureApiKeyStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
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
    }

    fun getApiKey(): String? {
        val key = prefs.getString(KEY_GEMINI_API_KEY, null)
        return if (key.isNullOrBlank()) null else key.trim()
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
