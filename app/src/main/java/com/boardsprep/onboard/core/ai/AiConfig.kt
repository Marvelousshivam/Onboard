package com.boardsprep.onboard.core.ai

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val isSearchSupported: Boolean,
    val helpUrl: String
) {
    GOOGLE_STUDIO(
        id = "google_studio",
        displayName = "Google AI Studio (Gemini)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        defaultModel = "gemini-2.5-flash",
        isSearchSupported = true,
        helpUrl = "https://aistudio.google.com/app/apikey"
    ),
    OPEN_ROUTER(
        id = "open_router",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "openrouter/free",
        isSearchSupported = true,
        helpUrl = "https://openrouter.ai/keys"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq LPU",
        defaultBaseUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "openai/gpt-oss-20b",
        isSearchSupported = false,
        helpUrl = "https://console.groq.com/keys"
    ),
    PERPLEXITY(
        id = "perplexity",
        displayName = "Perplexity AI",
        defaultBaseUrl = "https://api.perplexity.ai/chat/completions",
        defaultModel = "sonar",
        isSearchSupported = true,
        helpUrl = "https://www.perplexity.ai/settings/api"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        isSearchSupported = false,
        helpUrl = "https://platform.openai.com/api-keys"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom Endpoint",
        defaultBaseUrl = "",
        defaultModel = "",
        isSearchSupported = false,
        helpUrl = ""
    );

    companion object {
        fun fromId(id: String): AiProvider = entries.find { it.id.equals(id, ignoreCase = true) } ?: GROQ
        fun fromDisplayName(name: String): AiProvider = entries.find { it.displayName.equals(name, ignoreCase = true) } ?: GROQ
    }
}

data class AiModelInfo(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val tag: String = "",
    val isFree: Boolean = false,
    val supportsReasoning: Boolean = false,
    val description: String = ""
)

object AiModelCatalog {
    val modelsByProvider: Map<AiProvider, List<AiModelInfo>> = mapOf(
        AiProvider.GROQ to listOf(
            AiModelInfo(
                id = "openai/gpt-oss-20b",
                name = "GPT-OSS 20B",
                provider = AiProvider.GROQ,
                tag = "FASTEST · LOW TOKENS",
                isFree = true,
                description = "Ultra-fast response on Groq LPU · Minimal token burn"
            ),
            AiModelInfo(
                id = "qwen/qwen3.8-27b",
                name = "Qwen 3.8 27B",
                provider = AiProvider.GROQ,
                tag = "BALANCED",
                isFree = true,
                description = "Alibaba Qwen high-accuracy model on Groq LPU"
            ),
            AiModelInfo(
                id = "allam-2-7b",
                name = "Allam 2 7B",
                provider = AiProvider.GROQ,
                tag = "COMPACT",
                isFree = true,
                description = "Compact 7B parameter model · Ultra-low latency"
            ),
            AiModelInfo(
                id = "openai/gpt-oss-120b",
                name = "GPT-OSS 120B",
                provider = AiProvider.GROQ,
                tag = "DEEP REASONING",
                isFree = true,
                supportsReasoning = true,
                description = "Heavy chain-of-thought · High token cost (use for deep derivations)"
            )
        ),
        AiProvider.GOOGLE_STUDIO to listOf(
            AiModelInfo(
                id = "gemini-2.5-flash",
                name = "Gemini 2.5 Flash",
                provider = AiProvider.GOOGLE_STUDIO,
                tag = "RECOMMENDED · FAST",
                isFree = true,
                description = "Fast, multimodal, highly accurate for CBSE STEM & English"
            ),
            AiModelInfo(
                id = "gemini-2.5-pro",
                name = "Gemini 2.5 Pro",
                provider = AiProvider.GOOGLE_STUDIO,
                tag = "DEEP REASONING",
                isFree = true,
                supportsReasoning = true,
                description = "Maximum reasoning depth for complex Physics derivations & Maths proofs"
            ),
            AiModelInfo(
                id = "gemini-2.0-flash",
                name = "Gemini 2.0 Flash",
                provider = AiProvider.GOOGLE_STUDIO,
                tag = "LOW LATENCY",
                isFree = true,
                description = "Ultra-low latency with native search grounding"
            ),
            AiModelInfo(
                id = "gemini-1.5-flash",
                name = "Gemini 1.5 Flash",
                provider = AiProvider.GOOGLE_STUDIO,
                tag = "LONG CONTEXT",
                isFree = true,
                description = "Long context window for entire chapters"
            )
        ),
        AiProvider.OPEN_ROUTER to listOf(
            AiModelInfo(
                id = "openrouter/free",
                name = "OpenRouter Free Auto",
                provider = AiProvider.OPEN_ROUTER,
                tag = "SMART ROUTE · FREE",
                isFree = true,
                description = "Automatically routes to currently available top free community models"
            ),
            AiModelInfo(
                id = "google/gemini-2.5-flash",
                name = "Gemini 2.5 Flash",
                provider = AiProvider.OPEN_ROUTER,
                tag = "FLAGSHIP FAST",
                description = "Google's flagship fast model via OpenRouter"
            ),
            AiModelInfo(
                id = "deepseek/deepseek-r1",
                name = "DeepSeek R1",
                provider = AiProvider.OPEN_ROUTER,
                tag = "DEEP REASONING",
                supportsReasoning = true,
                description = "Chain-of-thought verification for multi-step numericals"
            ),
            AiModelInfo(
                id = "deepseek/deepseek-v3",
                name = "DeepSeek V3",
                provider = AiProvider.OPEN_ROUTER,
                tag = "HIGH ACCURACY",
                description = "Exceptional reasoning at ultra-low cost"
            ),
            AiModelInfo(
                id = "meta-llama/llama-3.3-70b-instruct",
                name = "Llama 3.3 70B Instruct",
                provider = AiProvider.OPEN_ROUTER,
                tag = "OPEN WEIGHTS",
                description = "Meta's premier open weights model"
            ),
            AiModelInfo(
                id = "anthropic/claude-3.5-haiku",
                name = "Claude 3.5 Haiku",
                provider = AiProvider.OPEN_ROUTER,
                tag = "BEST FOR ENGLISH",
                description = "Fast, nuanced, superb tone and grammar for English Core"
            )
        ),
        AiProvider.PERPLEXITY to listOf(
            AiModelInfo("sonar", "Sonar (Live Search)", AiProvider.PERPLEXITY, tag = "LIVE SEARCH", description = "Real-time web search with automatic verified citations"),
            AiModelInfo("sonar-pro", "Sonar Pro (Advanced)", AiProvider.PERPLEXITY, tag = "ADVANCED SEARCH", description = "Deeper search synthesis for syllabus & circular queries"),
            AiModelInfo("sonar-reasoning", "Sonar Reasoning", AiProvider.PERPLEXITY, tag = "DEEP REASONING", supportsReasoning = true, description = "Combines live internet crawling with multi-step reasoning")
        ),
        AiProvider.OPENAI to listOf(
            AiModelInfo("gpt-4o-mini", "GPT-4o Mini", AiProvider.OPENAI, tag = "FAST & EFFICIENT", description = "Affordable, versatile model for everyday questions"),
            AiModelInfo("gpt-4o", "GPT-4o Flagship", AiProvider.OPENAI, tag = "FLAGSHIP", description = "High-accuracy flagship model")
        ),
        AiProvider.CUSTOM to emptyList()
    )
}

data class DppQuestionContext(
    val subject: String,
    val chapter: String,
    val questionText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val selectedOptionIndex: Int?,
    val officialExplanation: String,
    val isPracticeMode: Boolean = true
) {
    val isUserCorrect: Boolean
        get() = selectedOptionIndex != null && selectedOptionIndex == correctOptionIndex
    
    val selectedOptionLetter: String?
        get() = selectedOptionIndex?.let { listOf("A", "B", "C", "D").getOrNull(it) }

    val correctOptionLetter: String
        get() = listOf("A", "B", "C", "D").getOrElse(correctOptionIndex) { "A" }
}

data class QuestionBankAiContext(
    val questionId: String,
    val subject: String,
    val chapterName: String,
    val questionType: String,
    val marks: Int,
    val questionText: String,
    val modelAnswer: String,
    val stepMarkingSummary: String,
    val keyTerms: List<String>
)

data class AiMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class AiCitation(
    val title: String,
    val url: String
)

data class AiStreamChunk(
    val deltaText: String = "",
    val isComplete: Boolean = false,
    val citations: List<AiCitation> = emptyList(),
    val errorMessage: String? = null
)
