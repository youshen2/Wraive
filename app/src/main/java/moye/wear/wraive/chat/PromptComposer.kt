package moye.wear.wraive.chat

import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MemoryEntry
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.PromptTransform
import moye.wear.wraive.model.WorldBookEntry
import java.text.DateFormat
import java.util.Date

class PromptComposer {
    fun systemPrompt(
        assistant: AssistantProfile,
        model: ModelConfig,
        memories: List<MemoryEntry>,
        worldBookEntries: List<WorldBookEntry>,
        history: List<ChatMessage>,
        conversationSummary: String
    ): String {
        val recentText = history.takeLast(12).joinToString("\n") { it.content }.lowercase()
        val triggeredWorldBook = worldBookEntries
            .filter { entry ->
                entry.enabled &&
                    entry.bookId in assistant.worldBookIds &&
                    entry.keywords.any { keyword -> keyword.lowercase() in recentText }
            }
            .sortedByDescending(WorldBookEntry::priority)
        return buildString {
            append(resolveVariables(assistant.systemPrompt, assistant, model))
            if (assistant.memoryEnabled) {
                val selectedMemories = memories.filter {
                    it.enabled && (it.assistantId == null || it.assistantId == assistant.id)
                }
                if (selectedMemories.isNotEmpty()) {
                    append("\n\n# Long-term memory\n")
                    selectedMemories.forEach { append("- ${it.category.name.lowercase()}: ${it.content}\n") }
                }
            }
            if (triggeredWorldBook.isNotEmpty()) {
                append("\n\n# Relevant world book entries\n")
                triggeredWorldBook.forEach { append("## ${it.title}\n${it.content}\n") }
            }
            if (conversationSummary.isNotBlank()) {
                append("\n\n# Earlier conversation summary\n$conversationSummary")
            }
        }.trim()
    }

    fun transformHistory(
        history: List<ChatMessage>,
        transforms: List<PromptTransform>
    ): List<ChatMessage> = history.map { message ->
        val applicable = transforms.filter { transform ->
            transform.enabled && when (message.role) {
                MessageRole.USER -> transform.applyToUser
                MessageRole.ASSISTANT -> transform.applyToAssistant
                else -> false
            }
        }
        message.copy(
            content = applicable.fold(message.content) { content, transform ->
                runCatching {
                    Regex(transform.pattern).replace(content, transform.replacement)
                }.getOrElse { content }
            }
        )
    }

    fun resolveVariables(
        value: String,
        assistant: AssistantProfile,
        model: ModelConfig
    ): String {
        val now = Date()
        return value
            .replace("{{model}}", model.displayName)
            .replace("{{model_id}}", model.id)
            .replace("{{assistant}}", assistant.name)
            .replace("{{date}}", DateFormat.getDateInstance().format(now))
            .replace("{{time}}", DateFormat.getTimeInstance().format(now))
            .replace("{{datetime}}", DateFormat.getDateTimeInstance().format(now))
    }
}
