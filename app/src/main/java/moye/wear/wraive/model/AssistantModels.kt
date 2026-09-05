package moye.wear.wraive.model

import java.util.UUID

data class AssistantProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val avatarUri: String? = null,
    val systemPrompt: String = "",
    val model: ModelRef? = null,
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val maxOutputTokens: Int? = null,
    val reasoningEnabled: Boolean = false,
    val reasoningBudget: Int? = null,
    val webSearchEnabled: Boolean = false,
    val searchProviderId: String? = null,
    val memoryEnabled: Boolean = true,
    val autoMemoryExtraction: Boolean = false,
    val worldBookIds: Set<String> = emptySet(),
    val mcpServerIds: Set<String> = emptySet(),
    val customHeaders: Map<String, String> = emptyMap(),
    val customBody: Map<String, Any?> = emptyMap(),
    val imageSize: String = "1024x1024",
    val imageQuality: String = "auto",
    val imageCount: Int = 1,
    val sortOrder: Int = 0
)

data class QuickPhrase(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val assistantId: String? = null,
    val sortOrder: Int = 0
)

data class WorldBookEntry(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val title: String,
    val keywords: List<String>,
    val content: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String? = null,
    val category: MemoryCategory,
    val content: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

enum class MemoryCategory { IDENTITY, WORKFLOW, VOICE, INSTRUCTION }

data class PromptTransform(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val pattern: String,
    val replacement: String,
    val applyToUser: Boolean = false,
    val applyToAssistant: Boolean = true,
    val enabled: Boolean = true
)
