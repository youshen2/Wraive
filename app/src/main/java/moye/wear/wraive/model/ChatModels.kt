package moye.wear.wraive.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

// Alternate names match the persisted JSON from the 0.1.0 release APK.
// SerializedName also keeps these fields and their generic types available to Gson under R8.
enum class MessageRole {
    @SerializedName(value = "SYSTEM", alternate = ["g"]) SYSTEM,
    @SerializedName(value = "USER", alternate = ["h"]) USER,
    @SerializedName(value = "ASSISTANT", alternate = ["i"]) ASSISTANT,
    @SerializedName(value = "TOOL", alternate = ["j"]) TOOL
}

enum class MessageStatus {
    @SerializedName(value = "COMPLETE", alternate = ["g"]) COMPLETE,
    @SerializedName(value = "STREAMING", alternate = ["h"]) STREAMING,
    @SerializedName(value = "FAILED", alternate = ["i"]) FAILED,
    @SerializedName(value = "CANCELLED", alternate = ["j"]) CANCELLED
}

enum class AttachmentKind {
    @SerializedName(value = "IMAGE", alternate = ["g"]) IMAGE,
    @SerializedName(value = "TEXT", alternate = ["h"]) TEXT,
    @SerializedName("PDF") PDF,
    @SerializedName(value = "DOCUMENT", alternate = ["i"]) DOCUMENT,
    @SerializedName("AUDIO") AUDIO
}

data class Attachment(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String = UUID.randomUUID().toString(),
    @SerializedName(value = "kind", alternate = ["b"])
    val kind: AttachmentKind,
    @SerializedName(value = "name", alternate = ["c"])
    val name: String,
    @SerializedName(value = "uri", alternate = ["d"])
    val uri: String,
    @SerializedName(value = "mimeType", alternate = ["e"])
    val mimeType: String,
    @SerializedName(value = "extractedText", alternate = ["f"])
    val extractedText: String? = null,
    @SerializedName(value = "sizeBytes", alternate = ["g"])
    val sizeBytes: Long = 0
)

data class ToolCall(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String,
    @SerializedName(value = "name", alternate = ["b"])
    val name: String,
    @SerializedName(value = "arguments", alternate = ["c"])
    val arguments: String,
    @SerializedName(value = "providerCallId", alternate = ["d"])
    val providerCallId: String = id,
    @SerializedName(value = "result", alternate = ["e"])
    val result: String? = null,
    @SerializedName(value = "error", alternate = ["f"])
    val error: String? = null
)

data class TokenUsage(
    @SerializedName(value = "inputTokens", alternate = ["a"])
    val inputTokens: Int = 0,
    @SerializedName(value = "outputTokens", alternate = ["b"])
    val outputTokens: Int = 0,
    @SerializedName(value = "cachedTokens", alternate = ["c"])
    val cachedTokens: Int = 0
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

data class ChatMessage(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String = UUID.randomUUID().toString(),
    @SerializedName(value = "conversationId", alternate = ["b"])
    val conversationId: String,
    @SerializedName(value = "role", alternate = ["c"])
    val role: MessageRole,
    @SerializedName(value = "content", alternate = ["d"])
    val content: String,
    @SerializedName(value = "reasoning", alternate = ["e"])
    val reasoning: String = "",
    @SerializedName(value = "attachments", alternate = ["f"])
    val attachments: List<Attachment> = emptyList(),
    @SerializedName(value = "toolCalls", alternate = ["g"])
    val toolCalls: List<ToolCall> = emptyList(),
    @SerializedName(value = "status", alternate = ["h"])
    val status: MessageStatus = MessageStatus.COMPLETE,
    @SerializedName(value = "error", alternate = ["i"])
    val error: String? = null,
    @SerializedName(value = "providerId", alternate = ["j"])
    val providerId: String? = null,
    @SerializedName(value = "modelId", alternate = ["k"])
    val modelId: String? = null,
    @SerializedName(value = "usage", alternate = ["l"])
    val usage: TokenUsage = TokenUsage(),
    @SerializedName(value = "createdAt", alternate = ["m"])
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName(value = "updatedAt", alternate = ["n"])
    val updatedAt: Long = createdAt
)

data class Conversation(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String = UUID.randomUUID().toString(),
    @SerializedName(value = "title", alternate = ["b"])
    val title: String = "新对话",
    @SerializedName(value = "assistantId", alternate = ["c"])
    val assistantId: String,
    @SerializedName(value = "createdAt", alternate = ["d"])
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName(value = "updatedAt", alternate = ["e"])
    val updatedAt: Long = createdAt,
    @SerializedName(value = "pinned", alternate = ["f"])
    val pinned: Boolean = false,
    @SerializedName(value = "archived", alternate = ["g"])
    val archived: Boolean = false,
    @SerializedName(value = "temporary", alternate = ["h"])
    val temporary: Boolean = false,
    @SerializedName(value = "summary", alternate = ["i"])
    val summary: String = "",
    @SerializedName(value = "lastMessagePreview", alternate = ["j"])
    val lastMessagePreview: String = ""
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ToolDelta(val call: ToolCall) : StreamEvent
    data class Usage(val usage: TokenUsage) : StreamEvent
    data object Completed : StreamEvent
}
