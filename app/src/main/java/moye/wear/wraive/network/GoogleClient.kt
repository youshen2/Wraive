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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GoogleClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderClient {
    override fun stream(request: CompletionRequest): Flow<StreamEvent> = flow {
        val payload = JsonObject().apply {
            if (request.systemPrompt.isNotBlank()) {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", request.systemPrompt) })
                    })
                })
            }
            add("contents", contents(request))
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", request.assistant.temperature)
                addProperty("topP", request.assistant.topP)
                request.assistant.maxOutputTokens?.let { addProperty("maxOutputTokens", it) }
                if (request.assistant.reasoningEnabled) {
                    add("thinkingConfig", JsonObject().apply {
                        addProperty("includeThoughts", true)
                        request.assistant.reasoningBudget?.let { addProperty("thinkingBudget", it) }
                    })
                }
            })
            if (request.toolDefinitions.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    add(JsonObject().apply {
                        add("functionDeclarations", googleTools(request.toolDefinitions))
                    })
                })
            }
            merge(request.provider.customBody + request.assistant.customBody, gson)
        }
        val url = "${request.provider.baseUrl.trimEnd('/')}/models/" +
            "${request.model.id.substringAfter("models/")}:streamGenerateContent"
        val httpRequest = requestBuilder(
            request.provider,
            url,
            includeSse = true,
            assistantHeaders = request.assistant.customHeaders
        )
            .post(gson.toJson(payload).toRequestBody(JSON))
            .build()
        val call = httpClient.newCall(httpRequest)
        try {
            call.execute().requireSuccess().use { response ->
                val source = response.body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()?.trim().orEmpty()
                    if (!line.startsWith("data:")) continue
                    parseChunk(line.removePrefix("data:").trim()).forEach { emit(it) }
                }
            }
            emit(StreamEvent.Completed)
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(provider: ProviderConfig): List<ModelConfig> =
        withContext(Dispatchers.IO) {
            val request = requestBuilder(
                provider,
                "${provider.baseUrl.trimEnd('/')}/models",
                includeSse = false
            ).get().build()
            httpClient.newCall(request).execute().requireSuccess().use { response ->
                val root = parseJson(response.body.string()).asJsonObject
                root.getAsJsonArray("models")?.mapNotNull { element ->
                    val item = element.asJsonObject
                    val id = item.string("name")?.substringAfter("models/")
                        ?: return@mapNotNull null
                    val methods = item.getAsJsonArray("supportedGenerationMethods")
                        ?.map { it.asString }.orEmpty()
                    if ("generateContent" !in methods) return@mapNotNull null
                    ModelConfig(
                        id = id,
                        providerId = provider.id,
                        displayName = item.string("displayName") ?: id,
                        capabilities = inferCapabilities(id),
                        maxContextTokens = item.int("inputTokenLimit").takeIf { it > 0 }
                            ?: 128_000,
                        maxOutputTokens = item.int("outputTokenLimit").takeIf { it > 0 }
                            ?: 8_192
                    )
                }.orEmpty()
            }
        }

    private fun contents(request: CompletionRequest): JsonArray = JsonArray().apply {
        request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
            add(JsonObject().apply {
                addProperty(
                    "role",
                    if (message.role == MessageRole.ASSISTANT) "model" else "user"
                )
                add("parts", JsonArray().apply {
                    if (message.content.isNotBlank()) add(JsonObject().apply {
                        addProperty("text", message.content)
                    })
                    message.attachments.forEach { attachment ->
                        val dataUrl = attachment.uri.takeIf { it.startsWith("data:") }
                        if (attachment.kind == AttachmentKind.IMAGE && dataUrl != null) {
                            val metadata = dataUrl.substringBefore(',').removePrefix("data:")
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", metadata.substringBefore(';'))
                                    addProperty("data", dataUrl.substringAfter(','))
                                })
                            })
                        } else if (!attachment.extractedText.isNullOrBlank()) {
                            add(JsonObject().apply {
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
                                add("functionResponse", JsonObject().apply {
                                    addProperty("name", tool.name)
                                    add("response", JsonObject().apply {
                                        addProperty("result", tool.result ?: tool.error.orEmpty())
                                    })
                                })
                            })
                        }
                    }
                    if (message.role == MessageRole.ASSISTANT) {
                        message.toolCalls.forEach { tool ->
                            add(JsonObject().apply {
                                add("functionCall", JsonObject().apply {
                                    addProperty("name", tool.name)
                                    add("args", parseJson(tool.arguments))
                                })
                            })
                        }
                    }
                })
            })
        }
    }

    private fun googleTools(definitions: List<JsonObject>): JsonArray = JsonArray().apply {
        definitions.forEach { definition ->
            val function = definition.getAsJsonObject("function") ?: definition
            add(JsonObject().apply {
                addProperty("name", function.string("name"))
                function.string("description")?.let { addProperty("description", it) }
                add(
                    "parameters",
                    function.getAsJsonObject("parameters") ?: JsonObject().apply {
                        addProperty("type", "OBJECT")
                    }
                )
            })
        }
    }

    private fun parseChunk(data: String): List<StreamEvent> {
        val root = parseJson(data).asJsonObject
        val events = mutableListOf<StreamEvent>()
        root.getAsJsonArray("candidates")?.forEach { candidateElement ->
            val parts = candidateElement.asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
            parts?.forEach { partElement ->
                val part = partElement.asJsonObject
                part.string("text")?.let { text ->
                    if (part.get("thought")?.asBoolean == true) {
                        events += StreamEvent.ReasoningDelta(text)
                    } else {
                        events += StreamEvent.TextDelta(text)
                    }
                }
                part.getAsJsonObject("functionCall")?.let { call ->
                    events += StreamEvent.ToolDelta(
                        ToolCall(
                            id = "google-${events.size}",
                            name = call.string("name").orEmpty(),
                            arguments = call.get("args")?.toString().orEmpty()
                        )
                    )
                }
            }
        }
        root.getAsJsonObject("usageMetadata")?.let { usage ->
            events += StreamEvent.Usage(
                TokenUsage(
                    inputTokens = usage.int("promptTokenCount"),
                    outputTokens = usage.int("candidatesTokenCount")
                )
            )
        }
        return events
    }

    private fun requestBuilder(
        provider: ProviderConfig,
        url: String,
        includeSse: Boolean,
        assistantHeaders: Map<String, String> = emptyMap()
    ): Request.Builder {
        val httpUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("key", provider.apiKey)
            .apply { if (includeSse) addQueryParameter("alt", "sse") }
            .build()
        return Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
            .apply {
                if (includeSse) header("Accept", "text/event-stream")
                provider.customHeaders.forEach { (name, value) -> header(name, value) }
                assistantHeaders.forEach { (name, value) -> header(name, value) }
            }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
