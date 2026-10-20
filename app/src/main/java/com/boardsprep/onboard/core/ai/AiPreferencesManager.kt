package com.boardsprep.onboard.core.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiPreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedProviderState = MutableStateFlow(getSelectedProvider())
    val selectedProviderState: StateFlow<AiProvider> = _selectedProviderState.asStateFlow()

    private val _webSearchEnabledState = MutableStateFlow(isWebSearchEnabled())
    val webSearchEnabledState: StateFlow<Boolean> = _webSearchEnabledState.asStateFlow()

    fun getSelectedProvider(): AiProvider {
        val id = prefs.getString(KEY_SELECTED_PROVIDER, AiProvider.GROQ.id) ?: AiProvider.GROQ.id
        return AiProvider.fromId(id)
    }

    fun setSelectedProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.id).apply()
        _selectedProviderState.value = provider
    }

    fun getApiKey(provider: AiProvider = getSelectedProvider()): String {
        return prefs.getString(KEY_PREFIX_API_KEY + provider.id, "")?.trim().orEmpty()
    }

    fun setApiKey(provider: AiProvider, apiKey: String) {
        prefs.edit().putString(KEY_PREFIX_API_KEY + provider.id, apiKey.trim()).apply()
    }

    fun getSelectedModel(provider: AiProvider = getSelectedProvider()): String {
        // One-time auto-migration to fast low-token 20B default for Groq
        if (provider == AiProvider.GROQ && !prefs.getBoolean("ai_groq_20b_migrated_v2", false)) {
            prefs.edit()
                .putString(KEY_PREFIX_MODEL + provider.id, "openai/gpt-oss-20b")
                .putBoolean("ai_groq_20b_migrated_v2", true)
                .apply()
            return "openai/gpt-oss-20b"
        }

        val stored = prefs.getString(KEY_PREFIX_MODEL + provider.id, null)
        val validCatalog = AiModelCatalog.modelsByProvider[provider]?.map { it.id } ?: emptyList()
        if (!stored.isNullOrBlank() && (validCatalog.isEmpty() || validCatalog.contains(stored))) {
            return stored
        }
        return provider.defaultModel
    }

    fun setSelectedModel(provider: AiProvider, model: String) {
        prefs.edit().putString(KEY_PREFIX_MODEL + provider.id, model.trim()).apply()
    }

    fun getBaseUrl(provider: AiProvider = getSelectedProvider()): String {
        return when (provider) {
            AiProvider.CUSTOM -> prefs.getString(KEY_CUSTOM_BASE_URL, "")?.trim() ?: ""
            else -> provider.defaultBaseUrl
        }
    }

    fun setCustomBaseUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, url.trim()).apply()
    }

    fun isWebSearchEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_WEB_SEARCH, false)
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_WEB_SEARCH, enabled).apply()
        _webSearchEnabledState.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "onboard_ai_preferences"
        private const val KEY_SELECTED_PROVIDER = "ai_selected_provider"
        private const val KEY_PREFIX_API_KEY = "ai_api_key_"
        private const val KEY_PREFIX_MODEL = "ai_model_"
        private const val KEY_CUSTOM_BASE_URL = "ai_custom_base_url"
        private const val KEY_ENABLE_WEB_SEARCH = "ai_enable_web_search"

        @Volatile
        private var INSTANCE: AiPreferencesManager? = null

        fun getInstance(context: Context): AiPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
