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
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.StreamEvent
import moye.wear.wraive.model.TokenUsage
import moye.wear.wraive.model.ToolCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderClient {
    override fun stream(request: CompletionRequest): Flow<StreamEvent> = flow {
        val payload = JsonObject().apply {
            addProperty("model", request.model.id)
            addProperty("stream", true)
            addProperty(
                "max_tokens",
                request.assistant.maxOutputTokens ?: request.model.maxOutputTokens
            )
            addProperty("temperature", request.assistant.temperature)
            addProperty("top_p", request.assistant.topP)
            add("system", systemBlocks(request))
            add("messages", messageBlocks(request))
            if (request.assistant.reasoningEnabled) {
                add("thinking", JsonObject().apply {
                    addProperty("type", "enabled")
                    addProperty("budget_tokens", request.assistant.reasoningBudget ?: 4_096)
                })
            }
            if (request.toolDefinitions.isNotEmpty()) {
                add("tools", anthropicTools(request.toolDefinitions))
            }
            merge(request.provider.customBody + request.assistant.customBody, gson)
        }
        val httpRequest = requestBuilder(
            request.provider,
            "${request.provider.baseUrl.trimEnd('/')}/messages",
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
                    parseChunk(data)?.let { emit(it) }
                }
            }
            emit(StreamEvent.Completed)
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(provider: ProviderConfig): List<ModelConfig> =
        withContext(Dispatchers.IO) {
            val call = httpClient.newCall(
                requestBuilder(provider, "${provider.baseUrl.trimEnd('/')}/models").get().build()
            )
            call.execute().requireSuccess().use { response ->
                val root = parseJson(response.body.string()).asJsonObject
                root.getAsJsonArray("data")?.mapNotNull { element ->
                    val model = element.asJsonObject
                    val id = model.string("id") ?: return@mapNotNull null
                    ModelConfig(
                        id = id,
                        providerId = provider.id,
                        displayName = model.string("display_name") ?: id,
                        capabilities = inferCapabilities(id)
                    )
                }.orEmpty()
            }
        }

    private fun systemBlocks(request: CompletionRequest): JsonArray = JsonArray().apply {
        if (request.systemPrompt.isBlank()) return@apply
        add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", request.systemPrompt)
            if (request.provider.usePromptCaching) {
                add("cache_control", JsonObject().apply { addProperty("type", "ephemeral") })
            }
        })
    }

    private fun messageBlocks(request: CompletionRequest): JsonArray = JsonArray().apply {
        request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
            add(JsonObject().apply {
                addProperty(
                    "role",
                    if (message.role == MessageRole.ASSISTANT) "assistant" else "user"
                )
                add("content", JsonArray().apply {
                    if (message.content.isNotBlank()) add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", message.content)
                    })
                    message.attachments.forEach { attachment ->
                        val dataUrl = attachment.uri.takeIf { it.startsWith("data:") }
                        if (attachment.kind == AttachmentKind.IMAGE && dataUrl != null) {
                            val metadata = dataUrl.substringBefore(',').removePrefix("data:")
                            val data = dataUrl.substringAfter(',')
                            add(JsonObject().apply {
                                addProperty("type", "image")
                                add("source", JsonObject().apply {
                                    addProperty("type", "base64")
                                    addProperty("media_type", metadata.substringBefore(';'))
                                    addProperty("data", data)
                                })
                            })
                        } else if (!attachment.extractedText.isNullOrBlank()) {
                            add(JsonObject().apply {
                                addProperty("type", "text")
                                addProperty(
                                    "text",
                                    "\n[${attachment.name}]\n${attachment.extractedText}"
                                )
                            })
                        }
                    }
                    if (message.role == MessageRole.TOOL) {
                        message.toolCalls.forEach { tool ->
                            add(JsonObject().apply {
                                addProperty("type", "tool_result")
                                addProperty("tool_use_id", tool.id)
                                addProperty("content", tool.result ?: tool.error.orEmpty())
                                addProperty("is_error", tool.error != null)
                            })
                        }
                    }
                    if (message.role == MessageRole.ASSISTANT) {
                        message.toolCalls.forEach { tool ->
                            add(JsonObject().apply {
                                addProperty("type", "tool_use")
                                addProperty("id", tool.providerCallId)
                                addProperty("name", tool.name)
                                add("input", parseJson(tool.arguments))
                            })
                        }
                    }
                })
            })
        }
    }

    private fun anthropicTools(definitions: List<JsonObject>): JsonArray = JsonArray().apply {
        definitions.forEach { definition ->
            val function = definition.getAsJsonObject("function") ?: definition
            add(JsonObject().apply {
                addProperty("name", function.string("name"))
                function.string("description")?.let { addProperty("description", it) }
                add(
                    "input_schema",
                    function.getAsJsonObject("parameters") ?: JsonObject().apply {
                        addProperty("type", "object")
                    }
                )
            })
        }
    }

    private fun parseChunk(data: String): StreamEvent? {
        val root = parseJson(data).asJsonObject
        return when (root.string("type")) {
            "content_block_start" -> {
                val block = root.getAsJsonObject("content_block")
                if (block?.string("type") == "tool_use") {
                    StreamEvent.ToolDelta(
                        ToolCall(
                            id = root.int("index").toString(),
                            name = block.string("name").orEmpty(),
                            arguments = "",
                            providerCallId = block.string("id").orEmpty()
                        )
                    )
                } else null
            }
            "content_block_delta" -> {
                val delta = root.getAsJsonObject("delta") ?: return null
                when (delta.string("type")) {
                    "text_delta" -> delta.string("text")?.let(StreamEvent::TextDelta)
                    "thinking_delta" -> delta.string("thinking")
                        ?.let(StreamEvent::ReasoningDelta)
                    "input_json_delta" -> StreamEvent.ToolDelta(
                        ToolCall(
                            id = root.int("index").toString(),
                            name = "",
                            arguments = delta.string("partial_json").orEmpty()
                        )
                    )
                    else -> null
                }
            }
            "message_start" -> root.getAsJsonObject("message")
                ?.getAsJsonObject("usage")
                ?.let { usage ->
                    StreamEvent.Usage(TokenUsage(inputTokens = usage.int("input_tokens")))
                }
            "message_delta" -> root.getAsJsonObject("usage")?.let { usage ->
                StreamEvent.Usage(TokenUsage(outputTokens = usage.int("output_tokens")))
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
            .header("x-api-key", provider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
            .apply {
                provider.customHeaders.forEach { (name, value) -> header(name, value) }
                assistantHeaders.forEach { (name, value) -> header(name, value) }
            }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
