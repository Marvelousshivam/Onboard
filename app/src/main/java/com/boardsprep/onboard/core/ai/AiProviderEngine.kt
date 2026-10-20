package com.boardsprep.onboard.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AiProviderEngine(
    private val client: OkHttpClient = defaultOkHttpClient
) {
    companion object {
        private const val TAG = "AiProviderEngine"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Streams completions via Server-Sent Events (SSE) Flow.
     */
    fun streamChat(
        messages: List<AiMessage>,
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        enableWebSearch: Boolean = false,
        temperature: Double = 0.4,
        maxTokens: Int = 280
    ): Flow<AiStreamChunk> = callbackFlow {
        if (apiKey.isBlank() && provider != AiProvider.CUSTOM) {
            trySend(AiStreamChunk(errorMessage = "API Key is missing for ${provider.displayName}. Please configure it in AI Settings."))
            close()
            return@callbackFlow
        }

        val effectiveUrl = baseUrl.ifBlank { provider.defaultBaseUrl }
        val effectiveModel = model.ifBlank { provider.defaultModel }

        val requestBodyJson = JSONObject().apply {
            put("model", effectiveModel)
            put("stream", true)
            put("temperature", temperature)
            put("max_tokens", maxTokens)

            // Optimize reasoning tokens on Groq & OpenAI reasoning models to prevent fast token burn
            if (provider == AiProvider.GROQ && (effectiveModel.contains("gpt-oss") || effectiveModel.contains("qwen"))) {
                put("reasoning_effort", "low")
            }

            val messagesArray = JSONArray()
            for (msg in messages) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", messagesArray)

            // Web Grounding extensions
            if (enableWebSearch) {
                when (provider) {
                    AiProvider.OPEN_ROUTER -> {
                        val plugins = JSONArray().apply {
                            put(JSONObject().apply { put("id", "web") })
                        }
                        put("plugins", plugins)
                    }
                    else -> {}
                }
            }
        }

        val requestBuilder = Request.Builder()
            .url(effectiveUrl)
            .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        // OpenRouter attribution headers
        if (provider == AiProvider.OPEN_ROUTER) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/Marvelousshivam/Onboard")
            requestBuilder.addHeader("X-Title", "OnBOARD CBSE 2027")
        }

        val call = client.newCall(requestBuilder.build())

        val accumulatedCitations = mutableListOf<AiCitation>()
        val accumulatedContent = StringBuilder()
        val accumulatedReasoning = StringBuilder()

        try {
            val response = call.execute()

            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val parsedError = parseErrorMessage(response.code, errBody)
                trySend(AiStreamChunk(errorMessage = parsedError))
                close()
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                trySend(AiStreamChunk(errorMessage = "Empty response body from provider"))
                close()
                return@callbackFlow
            }

            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty() || currentLine.startsWith(":")) continue // Ping / comment

                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        if (accumulatedContent.isEmpty() && accumulatedReasoning.isNotEmpty()) {
                            trySend(AiStreamChunk(deltaText = accumulatedReasoning.toString()))
                        }
                        trySend(AiStreamChunk(isComplete = true, citations = accumulatedCitations))
                        break
                    }

                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val delta = firstChoice.optJSONObject("delta")
                            val textDelta = delta?.optString("content") ?: ""
                            val reasoningDelta = delta?.optString("reasoning") ?: ""

                            if (textDelta.isNotEmpty()) {
                                accumulatedContent.append(textDelta)
                                trySend(AiStreamChunk(deltaText = textDelta))
                            } else if (reasoningDelta.isNotEmpty()) {
                                accumulatedReasoning.append(reasoningDelta)
                            }
                        }

                        // Parse Perplexity / Gemini Citations if present
                        val citationsArray = json.optJSONArray("citations")
                        if (citationsArray != null) {
                            for (i in 0 until citationsArray.length()) {
                                val url = citationsArray.optString(i)
                                if (url.isNotBlank() && accumulatedCitations.none { it.url == url }) {
                                    accumulatedCitations.add(AiCitation(title = "Source ${accumulatedCitations.size + 1}", url = url))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Non-critical SSE JSON parse: ${e.message}")
                    }
                }
            }

            if (accumulatedContent.isEmpty() && accumulatedReasoning.isNotEmpty()) {
                trySend(AiStreamChunk(deltaText = accumulatedReasoning.toString()))
            }
            trySend(AiStreamChunk(isComplete = true, citations = accumulatedCitations))
            close()
        } catch (e: Exception) {
            trySend(AiStreamChunk(errorMessage = e.localizedMessage ?: "Network connection failed"))
            close(e)
        }

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Quick latency and connectivity benchmark.
     */
    suspend fun testConnection(
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val effectiveUrl = baseUrl.ifBlank { provider.defaultBaseUrl }
        val effectiveModel = model.ifBlank { provider.defaultModel }

        val requestBodyJson = JSONObject().apply {
            put("model", effectiveModel)
            put("max_tokens", 90)
            if (provider == AiProvider.GROQ && (effectiveModel.contains("gpt-oss") || effectiveModel.contains("qwen"))) {
                put("reasoning_effort", "low")
            }
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
        }

        val requestBuilder = Request.Builder()
            .url(effectiveUrl)
            .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        if (provider == AiProvider.OPEN_ROUTER) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/Marvelousshivam/Onboard")
            requestBuilder.addHeader("X-Title", "OnBOARD CBSE 2027")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val err = parseErrorMessage(response.code, responseBody)
                Result.failure(Exception(err))
            } else {
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                val msgObj = choices?.optJSONObject(0)?.optJSONObject("message")
                val content = msgObj?.optString("content")?.trim().orEmpty()
                val reasoning = msgObj?.optString("reasoning")?.trim().orEmpty()
                val reply = when {
                    content.isNotBlank() -> content
                    reasoning.isNotBlank() -> reasoning.take(40) + "..."
                    else -> "OK"
                }
                Result.success("Connected successfully ($latency ms) • Response: $reply")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val errObj = json.optJSONObject("error")
            val msg = errObj?.optString("message") ?: json.optString("message")
            if (msg.isNotBlank()) "HTTP $code: $msg" else "HTTP $code Error"
        } catch (_: Exception) {
            when (code) {
                401 -> "HTTP 401: Invalid API Key. Please verify your key."
                429 -> "HTTP 429: Rate limit exceeded. Try a different model or provider."
                500, 502, 503 -> "HTTP $code: Provider service is temporarily unavailable."
                else -> "HTTP $code error occurred."
            }
        }
    }
}
