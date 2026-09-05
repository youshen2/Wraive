package moye.wear.wraive.chat

import kotlinx.coroutines.flow.collect
import moye.wear.wraive.data.AppRepository
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.StreamEvent
import moye.wear.wraive.network.AiGateway
import moye.wear.wraive.network.CompletionRequest

class TranslationService(
    private val repository: AppRepository,
    private val gateway: AiGateway
) {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        assistantId: String? = null
    ): String {
        require(text.isNotBlank()) { "请输入要翻译的内容" }
        require(targetLanguage.isNotBlank()) { "请输入目标语言" }
        val assistant = repository.assistants.value.firstOrNull { it.id == assistantId }
            ?: repository.assistants.value.firstOrNull { it.model != null }
            ?: error("请先配置带模型的助手")
        val ref = assistant.model ?: error("请先为助手选择模型")
        val provider = repository.providers.value.firstOrNull {
            it.id == ref.providerId && it.enabled
        } ?: error("模型供应商不可用")
        val model = repository.models.value.firstOrNull {
            it.providerId == ref.providerId && it.id == ref.modelId
        } ?: ModelConfig(ref.modelId, ref.providerId)
        var translated = ""
        var reasoning = ""
        gateway.stream(
            CompletionRequest(
                provider = provider,
                model = model,
                assistant = assistant.copy(
                    reasoningEnabled = false,
                    webSearchEnabled = false,
                    mcpServerIds = emptySet()
                ),
                systemPrompt = "Translate the user's text into $targetLanguage. " +
                    "Preserve meaning, tone, Markdown, code blocks, links, and paragraph structure. " +
                    "Return only the translation.",
                messages = listOf(
                    ChatMessage(
                        conversationId = "translation",
                        role = MessageRole.USER,
                        content = text
                    )
                )
            )
        ).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> translated += event.text
                is StreamEvent.ReasoningDelta -> reasoning += event.text
                else -> Unit
            }
        }
        return translated.ifBlank { reasoning }.trim().ifBlank { error("模型未返回译文") }
    }
}
