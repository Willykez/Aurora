package com.auroraai.chat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// ---------- OpenAI-compatible wire format (plain chat — no tool calling) ----------

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 4096
)

@Serializable
data class ChatResponseChunk(val id: String? = null, val choices: List<ChunkChoice> = emptyList())

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null
)

// ---------- UI models ----------

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    var text: String = "",
    var isStreaming: Boolean = false,
    var isError: Boolean = false
)

enum class AppTab { CHAT, SETTINGS }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini"
)
