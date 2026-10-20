package com.boardsprep.onboard.core.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject

data class CachedAiResponse(
    val content: String,
    val citations: List<AiCitation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class AiResponseCache private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryCache = LruCache<String, CachedAiResponse>(150)

    fun get(key: String): CachedAiResponse? {
        // 1. Check memory cache first
        memoryCache.get(key)?.let { return it }

        // 2. Check persistent SharedPreferences
        val jsonStr = prefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val content = json.getString("content")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val citationsList = mutableListOf<AiCitation>()
            val citationsArray = json.optJSONArray("citations")
            if (citationsArray != null) {
                for (i in 0 until citationsArray.length()) {
                    val citObj = citationsArray.getJSONObject(i)
                    citationsList.add(
                        AiCitation(
                            title = citObj.optString("title", "Source"),
                            url = citObj.optString("url", "")
                        )
                    )
                }
            }
            val cached = CachedAiResponse(content = content, citations = citationsList, timestamp = timestamp)
            memoryCache.put(key, cached)
            cached
        } catch (_: Exception) {
            null
        }
    }

    fun put(key: String, content: String, citations: List<AiCitation> = emptyList()) {
        if (content.isBlank()) return
        val cached = CachedAiResponse(content = content, citations = citations)
        memoryCache.put(key, cached)

        try {
            val json = JSONObject().apply {
                put("content", content)
                put("timestamp", cached.timestamp)
                val citationsArray = JSONArray()
                for (cit in citations) {
                    citationsArray.put(JSONObject().apply {
                        put("title", cit.title)
                        put("url", cit.url)
                    })
                }
                put("citations", citationsArray)
            }
            prefs.edit().putString(key, json.toString()).apply()
        } catch (_: Exception) {
            // Non-critical cache serialization failure
        }
    }

    fun clear() {
        memoryCache.evictAll()
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "onboard_ai_response_cache"

        @Volatile
        private var INSTANCE: AiResponseCache? = null

        fun getInstance(context: Context): AiResponseCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiResponseCache(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun generateKey(
            questionIdOrText: String,
            actionType: String,
            providerId: String,
            model: String
        ): String {
            val normalizedQ = questionIdOrText.trim().hashCode().toString()
            val normalizedModel = model.replace("/", "_").replace(":", "_")
            return "cache_${providerId}_${normalizedModel}_${actionType}_$normalizedQ"
        }
    }
}
