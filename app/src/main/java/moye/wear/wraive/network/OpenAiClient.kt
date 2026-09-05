package moye.wear.wraive.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.AttachmentKind
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import moye.wear.wraive.model.StreamEvent
import moye.wear.wraive.model.TokenUsage
import moye.wear.wraive.model.ToolCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderClient {
    override fun stream(request: CompletionRequest): Flow<StreamEvent> = when (
        request.provider.protocol
    ) {
        ProviderProtocol.OPENAI_RESPONSES -> streamResponses(request)
        else -> streamChatCompletions(request)
    }

    override suspend fun listModels(provider: ProviderConfig): List<ModelConfig> =
        withContext(Dispatchers.IO) {
            val call = httpClient.newCall(
                requestBuilder(provider, "${provider.baseUrl.trimEnd('/')}/models").get().build()
            )
            call.execute().requireSuccess().use { response ->
                val root = parseJson(response.body.string()).asJsonObject
                root.arrayOrNull("data").orEmpty().mapNotNull { element ->
                    val item = element.objectOrNull() ?: return@mapNotNull null
                    val id = item.string("id") ?: return@mapNotNull null
                    ModelConfig(
                        id = id,
                        providerId = provider.id,
                        capabilities = inferCapabilities(id)
                    )
                }.sortedBy { it.displayName.lowercase() }
            }
        }

    private fun streamChatCompletions(request: CompletionRequest): Flow<StreamEvent> = flow {
        val payload = JsonObject().apply {
            addProperty("model", request.model.id)
            addProperty("stream", true)
            addProperty("temperature", request.assistant.temperature)
            addProperty("top_p", request.assistant.topP)
            request.assistant.maxOutputTokens?.let { addProperty("max_tokens", it) }
            add("messages", chatMessages(request))
            if (request.toolDefinitions.isNotEmpty()) add("tools", JsonArray().apply {
                request.toolDefinitions.forEach(::add)
            })
            add("stream_options", JsonObject().apply { addProperty("include_usage", true) })
            merge(request.provider.customBody + request.assistant.customBody, gson)
        }
        val httpRequest = requestBuilder(
            request.provider,
            "${request.provider.baseUrl.trimEnd('/')}/chat/completions",
            request.assistant.customHeaders
        ).post(gson.toJson(payload).toRequestBody(JSON)).build()
        val call = httpClient.newCall(httpRequest)
        try {
            call.execute().requireSuccess().use { response ->
                val source = response.body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()?.trim().orEmpty()
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    parseChatChunk(data).forEach { emit(it) }
                }
            }
            emit(StreamEvent.Completed)
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun streamResponses(request: CompletionRequest): Flow<StreamEvent> = flow {
        val payload = JsonObject().apply {
            addProperty("model", request.model.id)
            addProperty("stream", true)
            addProperty("temperature", request.assistant.temperature)
            addProperty("top_p", request.assistant.topP)
            request.assistant.maxOutputTokens?.let { addProperty("max_output_tokens", it) }
            if (request.systemPrompt.isNotBlank()) addProperty("instructions", request.systemPrompt)
            add("input", responsesInput(request.messages))
            if (request.toolDefinitions.isNotEmpty()) add("tools", JsonArray().apply {
                request.toolDefinitions.forEach(::add)
            })
            merge(request.provider.customBody + request.assistant.customBody, gson)
        }
        val httpRequest = requestBuilder(
            request.provider,
            "${request.provider.baseUrl.trimEnd('/')}/responses",
            request.assistant.customHeaders
        ).post(gson.toJson(payload).toRequestBody(JSON)).build()
        val call = httpClient.newCall(httpRequest)
        try {
            call.execute().requireSuccess().use { response ->
                val source = response.body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()?.trim().orEmpty()
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    parseResponsesChunk(data)?.let { emit(it) }
                }
            }
            emit(StreamEvent.Completed)
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun chatMessages(request: CompletionRequest): JsonArray = JsonArray().apply {
        if (request.systemPrompt.isNotBlank()) add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", request.systemPrompt)
        })
        request.messages.forEach { message ->
            add(JsonObject().apply {
                addProperty("role", message.role.openAiName())
                add("content", openAiContent(message, responses = false))
                if (message.role == MessageRole.ASSISTANT && message.reasoning.isNotBlank()) {
                    addProperty("reasoning_content", message.reasoning)
                }
                if (message.role == MessageRole.ASSISTANT && message.toolCalls.isNotEmpty()) {
                    add("tool_calls", JsonArray().apply {
                        message.toolCalls.forEach { tool ->
                            add(JsonObject().apply {
                                addProperty("id", tool.providerCallId)
                                addProperty("type", "function")
                                add("function", JsonObject().apply {
                                    addProperty("name", tool.name)
                                    addProperty("arguments", tool.arguments)
                                })
                            })
                        }
                    })
                }
                if (message.role == MessageRole.TOOL && message.toolCalls.isNotEmpty()) {
                    addProperty("tool_call_id", message.toolCalls.first().providerCallId)
                }
            })
        }
    }

    private fun responsesInput(messages: List<ChatMessage>): JsonArray = JsonArray().apply {
        messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
            if (message.role == MessageRole.TOOL) {
                message.toolCalls.forEach { tool ->
                    add(JsonObject().apply {
                        addProperty("type", "function_call_output")
                        addProperty("call_id", tool.providerCallId)
                        addProperty("output", message.content)
                    })
                }
                return@forEach
            }
            if (message.content.isNotBlank() || message.attachments.isNotEmpty()) {
                add(JsonObject().apply {
                    addProperty("type", "message")
                    addProperty("role", message.role.openAiName())
                    add("content", responsesContent(message))
                })
            }
            if (message.role == MessageRole.ASSISTANT) {
                message.toolCalls.forEach { tool ->
                    add(JsonObject().apply {
                        addProperty("type", "function_call")
                        addProperty("call_id", tool.providerCallId)
                        addProperty("name", tool.name)
                        addProperty("arguments", tool.arguments)
                    })
                }
            }
        }
    }

    private fun responsesContent(message: ChatMessage): JsonArray = JsonArray().apply {
        if (message.content.isNotBlank()) add(JsonObject().apply {
            addProperty(
                "type",
                if (message.role == MessageRole.ASSISTANT) "output_text" else "input_text"
            )
            addProperty("text", message.content)
        })
        message.attachments.forEach { attachment ->
            if (
                attachment.kind == AttachmentKind.IMAGE &&
                (attachment.uri.startsWith("data:") || attachment.uri.startsWith("http"))
            ) {
                add(JsonObject().apply {
                    addProperty("type", "input_image")
                    addProperty("image_url", attachment.uri)
                })
            } else if (!attachment.extractedText.isNullOrBlank()) {
                add(JsonObject().apply {
                    addProperty("type", "input_text")
                    addProperty("text", "\n[${attachment.name}]\n${attachment.extractedText}")
                })
            }
        }
    }

    private fun openAiContent(message: ChatMessage, responses: Boolean): com.google.gson.JsonElement {
        if (message.attachments.isEmpty()) return gson.toJsonTree(message.content)
        return JsonArray().apply {
            if (message.content.isNotBlank()) add(JsonObject().apply {
                addProperty("type", if (responses) "input_text" else "text")
                addProperty("text", message.content)
            })
            message.attachments.forEach { attachment ->
                if (attachment.kind == AttachmentKind.IMAGE &&
                    (attachment.uri.startsWith("data:") || attachment.uri.startsWith("http"))
                ) {
                    add(JsonObject().apply {
                        addProperty("type", if (responses) "input_image" else "image_url")
                        if (responses) {
                            addProperty("image_url", attachment.uri)
                        } else {
                            add("image_url", JsonObject().apply {
                                addProperty("url", attachment.uri)
                            })
                        }
                    })
                } else if (!attachment.extractedText.isNullOrBlank()) {
                    add(JsonObject().apply {
                        addProperty("type", if (responses) "input_text" else "text")
                        addProperty(
                            "text",
                            "\n[${attachment.name}]\n${attachment.extractedText}"
                        )
                    })
                }
            }
        }
    }

    internal fun parseChatChunk(data: String): List<StreamEvent> {
        val root = parseJson(data).objectOrNull() ?: return emptyList()
        val usage = root.objectOrNull("usage")
        val events = mutableListOf<StreamEvent>()
        if (usage != null) {
            events += StreamEvent.Usage(
                TokenUsage(
                    inputTokens = usage.int("prompt_tokens"),
                    outputTokens = usage.int("completion_tokens"),
                    cachedTokens = usage.objectOrNull("prompt_tokens_details")
                        ?.int("cached_tokens") ?: 0
                )
            )
        }
        val delta = root.arrayOrNull("choices")?.firstOrNull()?.objectOrNull()
            ?.objectOrNull("delta") ?: return events
        delta.string("content")?.takeIf(String::isNotEmpty)?.let {
            events += StreamEvent.TextDelta(it)
        }
        (delta.string("reasoning_content") ?: delta.string("reasoning"))
            ?.takeIf(String::isNotEmpty)
            ?.let { events += StreamEvent.ReasoningDelta(it) }
        delta.arrayOrNull("tool_calls")?.forEach { element ->
            val call = element.objectOrNull() ?: return@forEach
            val function = call.objectOrNull("function")
            events += StreamEvent.ToolDelta(
                ToolCall(
                    id = call.int("index").toString(),
                    name = function?.string("name").orEmpty(),
                    arguments = function?.string("arguments").orEmpty(),
                    providerCallId = call.string("id").orEmpty()
                )
            )
        }
        return events
    }

    internal fun parseResponsesChunk(data: String): StreamEvent? {
        val root = parseJson(data).objectOrNull() ?: return null
        return when (root.string("type")) {
            "response.output_text.delta" -> root.string("delta")?.let(StreamEvent::TextDelta)
            "response.reasoning_summary_text.delta" ->
                root.string("delta")?.let(StreamEvent::ReasoningDelta)
            "response.function_call_arguments.delta" -> StreamEvent.ToolDelta(
                ToolCall(
                    id = root.string("item_id").orEmpty(),
                    name = root.string("name").orEmpty(),
                    arguments = root.string("delta").orEmpty(),
                    providerCallId = root.string("call_id") ?: root.string("item_id").orEmpty()
                )
            )
            "response.output_item.added" -> {
                val item = root.objectOrNull("item")
                if (item?.string("type") == "function_call") {
                    StreamEvent.ToolDelta(
                        ToolCall(
                            id = item.string("id") ?: item.string("call_id").orEmpty(),
                            name = item.string("name").orEmpty(),
                            arguments = item.string("arguments").orEmpty(),
                            providerCallId = item.string("call_id")
                                ?: item.string("id").orEmpty()
                        )
                    )
                } else {
                    null
                }
            }
            "response.completed" -> {
                val usage = root.objectOrNull("response")?.objectOrNull("usage")
                usage?.let {
                    StreamEvent.Usage(
                        TokenUsage(
                            inputTokens = it.int("input_tokens"),
                            outputTokens = it.int("output_tokens"),
                            cachedTokens = it.objectOrNull("input_tokens_details")
                                ?.int("cached_tokens") ?: 0
                        )
                    )
                }
            }
            else -> null
        }
    }

    private fun requestBuilder(
        provider: ProviderConfig,
        url: String,
        assistantHeaders: Map<String, String> = emptyMap()
    ): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("Accept", "text/event-stream")
            .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
            .apply {
                provider.customHeaders.forEach { (name, value) -> header(name, value) }
                assistantHeaders.forEach { (name, value) -> header(name, value) }
            }

    private fun MessageRole.openAiName(): String = when (this) {
        MessageRole.SYSTEM -> "system"
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.TOOL -> "tool"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonArray?.orEmpty(): Iterable<com.google.gson.JsonElement> = this ?: emptyList()
