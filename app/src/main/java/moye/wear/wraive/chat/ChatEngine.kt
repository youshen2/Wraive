package moye.wear.wraive.chat

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import moye.wear.wraive.data.AppRepository
import moye.wear.wraive.data.PreferencesStore
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.MessageStatus
import moye.wear.wraive.model.MemoryCategory
import moye.wear.wraive.model.MemoryEntry
import moye.wear.wraive.model.ModelCapability
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.RequestLog
import moye.wear.wraive.model.RequestStatus
import moye.wear.wraive.model.StreamEvent
import moye.wear.wraive.model.TokenUsage
import moye.wear.wraive.model.ToolCall
import moye.wear.wraive.network.AiGateway
import moye.wear.wraive.network.CompletionRequest
import moye.wear.wraive.network.ImageGenerationRequest
import moye.wear.wraive.service.GenerationService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.JsonParser
import android.net.Uri
import java.io.File

class ChatEngine(
    private val context: Context,
    private val repository: AppRepository,
    private val preferencesStore: PreferencesStore,
    private val gateway: AiGateway,
    private val toolRuntime: ToolRuntime,
    private val promptComposer: PromptComposer = PromptComposer()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _activeMessages = MutableStateFlow<Map<String, ChatMessage>>(emptyMap())
    private val _temporaryMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val _generatingConversationIds = MutableStateFlow<Set<String>>(emptySet())
    val generatingConversationIds: StateFlow<Set<String>> = _generatingConversationIds

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = combine(
        repository.observeMessages(conversationId),
        _temporaryMessages,
        _activeMessages
    ) { stored, temporary, active ->
        val base = temporary[conversationId] ?: stored
        val current = active[conversationId] ?: return@combine base
        base.filterNot { it.id == current.id } + current
    }

    suspend fun createConversation(
        assistantId: String,
        temporary: Boolean = false
    ): Conversation {
        val conversation = Conversation(assistantId = assistantId, temporary = temporary)
        repository.upsertConversation(conversation)
        if (temporary) _temporaryMessages.value += conversation.id to emptyList()
        return conversation
    }

    fun send(
        conversation: Conversation,
        text: String,
        attachments: List<Attachment> = emptyList()
    ): Job {
        jobs[conversation.id]?.cancel()
        val job = scope.launch {
            val userMessage = ChatMessage(
                conversationId = conversation.id,
                role = MessageRole.USER,
                content = text.trim(),
                attachments = attachments
            )
            repository.upsertMessage(userMessage, conversation.temporary)
            val history = if (conversation.temporary) {
                (_temporaryMessages.value[conversation.id].orEmpty() + userMessage).also {
                    _temporaryMessages.value += conversation.id to it
                }
            } else {
                repository.messages(conversation.id)
            }
            generate(conversation, history)
        }
        jobs[conversation.id] = job
        return job
    }

    fun regenerate(conversation: Conversation, fromMessage: ChatMessage? = null): Job {
        jobs[conversation.id]?.cancel()
        val job = scope.launch {
            val messages = if (conversation.temporary) {
                _temporaryMessages.value[conversation.id].orEmpty()
            } else {
                repository.messages(conversation.id)
            }
            val lastAssistant = fromMessage?.takeIf { it.role == MessageRole.ASSISTANT }
                ?: messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                ?: return@launch
            val retained = messages.filter { it.createdAt < lastAssistant.createdAt }
            if (conversation.temporary) {
                _temporaryMessages.value += conversation.id to retained
            } else {
                repository.deleteMessagesFrom(
                    conversation.id,
                    lastAssistant.createdAt,
                    inclusive = true
                )
            }
            generate(conversation, retained)
        }
        jobs[conversation.id] = job
        return job
    }

    suspend fun editUserMessage(
        conversation: Conversation,
        message: ChatMessage,
        content: String
    ) {
        require(message.role == MessageRole.USER)
        jobs[conversation.id]?.cancel()
        val edited = message.copy(content = content.trim(), updatedAt = System.currentTimeMillis())
        if (conversation.temporary) {
            val retained = _temporaryMessages.value[conversation.id].orEmpty()
                .filter { it.createdAt <= message.createdAt }
                .map { if (it.id == message.id) edited else it }
            _temporaryMessages.value += conversation.id to retained
        } else {
            repository.deleteMessagesFrom(conversation.id, message.createdAt, inclusive = false)
            repository.upsertMessage(edited)
        }
    }

    suspend fun branchConversation(
        conversation: Conversation,
        throughMessageId: String
    ): Conversation {
        val branched = conversation.copy(
            id = UUID.randomUUID().toString(),
            title = "${conversation.title} · 分支",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pinned = false
        )
        repository.upsertConversation(branched)
        val sourceMessages = if (conversation.temporary) {
            _temporaryMessages.value[conversation.id].orEmpty()
        } else {
            repository.messages(conversation.id)
        }
        for (message in sourceMessages) {
            repository.upsertMessage(
                message.copy(
                    id = UUID.randomUUID().toString(),
                    conversationId = branched.id
                )
            )
            if (message.id == throughMessageId) break
        }
        return branched
    }

    fun cancel(conversationId: String) {
        jobs.remove(conversationId)?.cancel()
    }

    fun discardTemporary(conversationId: String) {
        cancel(conversationId)
        val messages = _temporaryMessages.value[conversationId].orEmpty()
        messages.flatMap(ChatMessage::attachments)
            .filter { it.uri.startsWith("file:") }
            .forEach { attachment ->
                Uri.parse(attachment.uri).path?.let(::File)?.let { file ->
                    if (file.parentFile == File(context.cacheDir, "generated_images")) {
                        file.delete()
                    }
                }
            }
        _temporaryMessages.value -= conversationId
        _activeMessages.value -= conversationId
    }

    private suspend fun generate(
        originalConversation: Conversation,
        initialHistory: List<ChatMessage>
    ) {
        _generatingConversationIds.value += originalConversation.id
        var background = false
        var requestLog: RequestLog? = null
        var completedUsage = TokenUsage()
        try {
            val assistant = repository.assistants.value.firstOrNull {
                it.id == originalConversation.assistantId
            } ?: error("助手不存在")
            val modelRef = assistant.model ?: error("请先为助手选择模型")
            val provider = repository.providers.value.firstOrNull {
                it.id == modelRef.providerId && it.enabled
            } ?: error("模型供应商不可用")
            require(provider.apiKey.isNotBlank()) { "请先填写 ${provider.name} 的 API Key" }
            val model = repository.models.value.firstOrNull {
                it.providerId == modelRef.providerId && it.id == modelRef.modelId
            } ?: ModelConfig(id = modelRef.modelId, providerId = modelRef.providerId)
            background = preferencesStore.preferences.value.backgroundGeneration
            if (background) GenerationService.begin(context, originalConversation.title)
            requestLog = RequestLog(
                conversationId = originalConversation.id,
                providerId = provider.id,
                modelId = model.id
            ).also {
                if (!originalConversation.temporary) repository.upsertRequestLog(it)
            }

            if (ModelCapability.IMAGE_GENERATION in model.capabilities) {
                generateImages(
                    conversation = originalConversation,
                    assistant = assistant,
                    provider = provider,
                    model = model,
                    history = initialHistory
                )
                finishConversation(originalConversation, initialHistory, "图片已生成")
                val completedLog = requireNotNull(requestLog).copy(
                    finishedAt = System.currentTimeMillis(),
                    status = RequestStatus.SUCCEEDED,
                    responsePreview = "图片已生成"
                )
                requestLog = completedLog
                if (!originalConversation.temporary) repository.upsertRequestLog(completedLog)
                return
            }

            val compressed = compressIfNeeded(originalConversation, assistant, model, initialHistory)
            val systemPrompt = promptComposer.systemPrompt(
                assistant = assistant,
                model = model,
                memories = repository.memories.value,
                worldBookEntries = repository.worldBookEntries.value,
                history = compressed.history,
                conversationSummary = compressed.summary
            )
            val history = promptComposer.transformHistory(
                compressed.history,
                repository.promptTransforms.value
            ).toMutableList()
            val tools = toolRuntime.prepare(assistant)
            var finalPreview = ""
            var round = 0
            while (round < MAX_TOOL_ROUNDS) {
                val messageId = UUID.randomUUID().toString()
                val createdAt = System.currentTimeMillis() + round
                var content = ""
                var reasoning = ""
                var usage = TokenUsage()
                val calls = linkedMapOf<String, ToolCall>()
                fun active(status: MessageStatus = MessageStatus.STREAMING): ChatMessage = ChatMessage(
                    id = messageId,
                    conversationId = originalConversation.id,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    reasoning = reasoning,
                    toolCalls = calls.values.toList(),
                    status = status,
                    providerId = provider.id,
                    modelId = model.id,
                    usage = usage,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                _activeMessages.value += originalConversation.id to active()
                gateway.stream(
                    CompletionRequest(
                        provider = provider,
                        model = model,
                        assistant = assistant,
                        systemPrompt = systemPrompt,
                        messages = history,
                        toolDefinitions = tools.definitions
                    )
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> content += event.text
                        is StreamEvent.ReasoningDelta -> reasoning += event.text
                        is StreamEvent.ToolDelta -> mergeToolDelta(calls, event.call)
                        is StreamEvent.Usage -> usage = usage.merge(event.usage)
                        StreamEvent.Completed -> Unit
                    }
                    _activeMessages.value += originalConversation.id to active()
                }
                val assistantMessage = active(MessageStatus.COMPLETE)
                repository.upsertMessage(assistantMessage, originalConversation.temporary)
                completedUsage += usage
                history += assistantMessage
                if (originalConversation.temporary) {
                    _temporaryMessages.value += originalConversation.id to history.toList()
                }
                finalPreview = content.ifBlank { reasoning }
                if (calls.isEmpty()) break

                calls.values.forEach { call ->
                    val completed = toolRuntime.execute(call, tools)
                    val toolMessage = ChatMessage(
                        conversationId = originalConversation.id,
                        role = MessageRole.TOOL,
                        content = completed.result ?: completed.error.orEmpty(),
                        toolCalls = listOf(completed),
                        status = if (completed.error == null) {
                            MessageStatus.COMPLETE
                        } else {
                            MessageStatus.FAILED
                        },
                        providerId = provider.id,
                        modelId = model.id
                    )
                    repository.upsertMessage(toolMessage, originalConversation.temporary)
                    history += toolMessage
                    if (originalConversation.temporary) {
                        _temporaryMessages.value += originalConversation.id to history.toList()
                    }
                }
                _activeMessages.value -= originalConversation.id
                if (round == MAX_TOOL_ROUNDS - 1) error("工具调用轮次过多")
                round++
            }
            finishConversation(originalConversation, initialHistory, finalPreview)
            if (
                !originalConversation.temporary &&
                assistant.memoryEnabled &&
                assistant.autoMemoryExtraction
            ) {
                completedUsage += runCatching {
                    extractMemories(
                        conversation = originalConversation,
                        assistant = assistant,
                        provider = provider,
                        model = model,
                        history = history
                    )
                }.getOrDefault(TokenUsage())
            }
            val completedLog = requireNotNull(requestLog).copy(
                finishedAt = System.currentTimeMillis(),
                status = RequestStatus.SUCCEEDED,
                usage = completedUsage,
                responsePreview = finalPreview.take(160)
            )
            requestLog = completedLog
            if (!originalConversation.temporary) repository.upsertRequestLog(completedLog)
        } catch (cancelled: CancellationException) {
            persistInterrupted(originalConversation, MessageStatus.CANCELLED, "已停止")
            requestLog?.copy(
                finishedAt = System.currentTimeMillis(),
                status = RequestStatus.CANCELLED,
                usage = completedUsage,
                error = "已停止"
            )?.let {
                if (!originalConversation.temporary) repository.upsertRequestLog(it)
            }
            throw cancelled
        } catch (error: Exception) {
            val message = error.message ?: error::class.java.simpleName
            persistInterrupted(
                originalConversation,
                MessageStatus.FAILED,
                message
            )
            requestLog?.copy(
                finishedAt = System.currentTimeMillis(),
                status = RequestStatus.FAILED,
                usage = completedUsage,
                error = message
            )?.let {
                if (!originalConversation.temporary) repository.upsertRequestLog(it)
            }
        } finally {
            _activeMessages.value -= originalConversation.id
            _generatingConversationIds.value -= originalConversation.id
            jobs.remove(originalConversation.id)
            if (background) GenerationService.end(context)
        }
    }

    private suspend fun compressIfNeeded(
        conversation: Conversation,
        assistant: AssistantProfile,
        model: ModelConfig,
        history: List<ChatMessage>
    ): CompressedContext {
        if (!preferencesStore.preferences.value.contextCompressionEnabled) {
            return CompressedContext(history, conversation.summary)
        }
        if (estimateTokens(history) < model.maxContextTokens * 3 / 4 || history.size <= 10) {
            return CompressedContext(history, conversation.summary)
        }
        val keep = history.takeLast(8)
        val older = history.dropLast(8)
        val provider = repository.providers.value.first { it.id == model.providerId }
        val summaryRequest = ChatMessage(
            conversationId = conversation.id,
            role = MessageRole.USER,
            content = buildString {
                append("Summarize the following conversation accurately and compactly. ")
                append("Preserve decisions, facts, user preferences, unresolved tasks, and tool results.\n\n")
                older.forEach { append("${it.role}: ${it.content}\n") }
            }
        )
        var summary = ""
        gateway.stream(
            CompletionRequest(
                provider = provider,
                model = model,
                assistant = assistant.copy(
                    reasoningEnabled = false,
                    webSearchEnabled = false,
                    mcpServerIds = emptySet()
                ),
                systemPrompt = "Write only the compact conversation summary.",
                messages = listOf(summaryRequest)
            )
        ).collect { event ->
            if (event is StreamEvent.TextDelta) summary += event.text
        }
        if (summary.isNotBlank()) {
            repository.upsertConversation(
                conversation.copy(summary = summary, updatedAt = System.currentTimeMillis())
            )
        }
        return CompressedContext(keep, summary.ifBlank { conversation.summary })
    }

    private suspend fun persistInterrupted(
        conversation: Conversation,
        status: MessageStatus,
        error: String
    ) {
        val interrupted = _activeMessages.value[conversation.id]?.copy(
            status = status,
            error = error,
            updatedAt = System.currentTimeMillis()
        ) ?: ChatMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = "",
            status = status,
            error = error
        )
        repository.upsertMessage(interrupted, conversation.temporary)
        if (conversation.temporary) {
            _temporaryMessages.value += conversation.id to (
                _temporaryMessages.value[conversation.id].orEmpty() +
                    interrupted
                )
        }
    }

    private suspend fun generateImages(
        conversation: Conversation,
        assistant: AssistantProfile,
        provider: moye.wear.wraive.model.ProviderConfig,
        model: ModelConfig,
        history: List<ChatMessage>
    ) {
        val promptMessage = history.lastOrNull { it.role == MessageRole.USER }
            ?: error("请先输入图片描述")
        require(promptMessage.content.isNotBlank()) { "图片生成需要文字描述" }
        val pending = ChatMessage(
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = "正在生成图片…",
            status = MessageStatus.STREAMING,
            providerId = provider.id,
            modelId = model.id
        )
        _activeMessages.value += conversation.id to pending
        val images = gateway.generateImages(
            ImageGenerationRequest(
                provider = provider,
                model = model,
                assistant = assistant,
                prompt = promptMessage.content,
                referenceImages = promptMessage.attachments,
                temporary = conversation.temporary
            )
        )
        val completed = pending.copy(
            content = if (images.size == 1) "图片已生成" else "已生成 ${images.size} 张图片",
            attachments = images,
            status = MessageStatus.COMPLETE,
            updatedAt = System.currentTimeMillis()
        )
        repository.upsertMessage(completed, conversation.temporary)
        if (conversation.temporary) {
            _temporaryMessages.value += conversation.id to (
                _temporaryMessages.value[conversation.id].orEmpty() + completed
                )
        }
    }

    private suspend fun finishConversation(
        conversation: Conversation,
        history: List<ChatMessage>,
        preview: String
    ) {
        val firstUser = history.firstOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val title = if (
            preferencesStore.preferences.value.autoTitle &&
            conversation.title == "新对话" &&
            firstUser.isNotBlank()
        ) {
            firstUser.lineSequence().first().take(24)
        } else {
            conversation.title
        }
        repository.upsertConversation(
            conversation.copy(
                title = title,
                updatedAt = System.currentTimeMillis(),
                lastMessagePreview = preview.take(80)
            )
        )
    }

    private suspend fun extractMemories(
        conversation: Conversation,
        assistant: AssistantProfile,
        provider: moye.wear.wraive.model.ProviderConfig,
        model: ModelConfig,
        history: List<ChatMessage>
    ): TokenUsage {
        val userMessages = history.filter { it.role == MessageRole.USER }
        if (userMessages.size < 3) return TokenUsage()
        val lastUserTime = userMessages.last().createdAt
        val latestMemoryTime = repository.memories.value
            .filter { it.assistantId == assistant.id }
            .maxOfOrNull(MemoryEntry::updatedAt) ?: 0L
        if (latestMemoryTime >= lastUserTime) return TokenUsage()
        val extractionMessage = ChatMessage(
            conversationId = conversation.id,
            role = MessageRole.USER,
            content = buildString {
                append("Extract only durable user facts, preferences, workflows, voice/style, ")
                append("and standing instructions from this conversation. ")
                append("Return a JSON array of objects with category and content. ")
                append("Allowed categories: IDENTITY, WORKFLOW, VOICE, INSTRUCTION. ")
                append("Return [] when nothing is durable.\n\n")
                history.takeLast(12).forEach { append("${it.role}: ${it.content}\n") }
            }
        )
        var output = ""
        var usage = TokenUsage()
        gateway.stream(
            CompletionRequest(
                provider = provider,
                model = model,
                assistant = assistant.copy(
                    reasoningEnabled = false,
                    webSearchEnabled = false,
                    mcpServerIds = emptySet()
                ),
                systemPrompt = "Return valid JSON only.",
                messages = listOf(extractionMessage)
            )
        ).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> output += event.text
                is StreamEvent.Usage -> usage = usage.merge(event.usage)
                else -> Unit
            }
        }
        val json = output.substringAfter('[', "").substringBeforeLast(']', "")
        if (json.isBlank()) return usage
        val existing = repository.memories.value.map { it.content.trim().lowercase() }.toSet()
        JsonParser.parseString("[$json]").asJsonArray.take(6).forEach { element ->
            val item = element.asJsonObject
            val content = item.get("content")?.asString?.trim().orEmpty()
            val category = runCatching {
                MemoryCategory.valueOf(item.get("category")?.asString.orEmpty().uppercase())
            }.getOrDefault(MemoryCategory.IDENTITY)
            if (content.isNotBlank() && content.lowercase() !in existing) {
                repository.upsertMemory(
                    MemoryEntry(
                        assistantId = assistant.id,
                        category = category,
                        content = content
                    )
                )
            }
        }
        return usage
    }

    private fun mergeToolDelta(
        calls: MutableMap<String, ToolCall>,
        delta: ToolCall
    ) {
        val previous = calls[delta.id]
        calls[delta.id] = if (previous == null) {
            delta
        } else {
            previous.copy(
                name = previous.name.ifBlank { delta.name },
                arguments = previous.arguments + delta.arguments,
                providerCallId = previous.providerCallId.ifBlank { delta.providerCallId }
            )
        }
    }

    private fun TokenUsage.merge(next: TokenUsage): TokenUsage = TokenUsage(
        inputTokens = next.inputTokens.takeIf { it > 0 } ?: inputTokens,
        outputTokens = next.outputTokens.takeIf { it > 0 } ?: outputTokens,
        cachedTokens = next.cachedTokens.takeIf { it > 0 } ?: cachedTokens
    )

    private operator fun TokenUsage.plus(next: TokenUsage): TokenUsage = TokenUsage(
        inputTokens = inputTokens + next.inputTokens,
        outputTokens = outputTokens + next.outputTokens,
        cachedTokens = cachedTokens + next.cachedTokens
    )

    private fun estimateTokens(messages: List<ChatMessage>): Int = messages.sumOf { message ->
        (message.content.length + message.reasoning.length +
            message.attachments.sumOf { it.extractedText?.length ?: 0 }) / 4 + 16
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 4
    }

    private data class CompressedContext(
        val history: List<ChatMessage>,
        val summary: String
    )
}
