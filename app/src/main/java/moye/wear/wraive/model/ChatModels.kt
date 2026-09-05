package moye.wear.wraive.model

import java.util.UUID

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

enum class MessageStatus { COMPLETE, STREAMING, FAILED, CANCELLED }

enum class AttachmentKind { IMAGE, TEXT, PDF, DOCUMENT, AUDIO }

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: AttachmentKind,
    val name: String,
    val uri: String,
    val mimeType: String,
    val extractedText: String? = null,
    val sizeBytes: Long = 0
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val providerCallId: String = id,
    val result: String? = null,
    val error: String? = null
)

data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String = "",
    val attachments: List<Attachment> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val error: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val usage: TokenUsage = TokenUsage(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val assistantId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val temporary: Boolean = false,
    val summary: String = "",
    val lastMessagePreview: String = ""
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ToolDelta(val call: ToolCall) : StreamEvent
    data class Usage(val usage: TokenUsage) : StreamEvent
    data object Completed : StreamEvent
}
