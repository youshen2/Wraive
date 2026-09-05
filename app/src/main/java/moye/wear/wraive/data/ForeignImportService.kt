package moye.wear.wraive.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ModelRef
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import moye.wear.wraive.network.inferCapabilities
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

data class ForeignImportSummary(
    val source: String,
    val providers: Int,
    val models: Int,
    val assistants: Int,
    val conversations: Int,
    val messages: Int
) {
    fun displayText(): String =
        "$source：供应商 $providers，模型 $models，助手 $assistants，会话 $conversations，消息 $messages"
}

/** Merges Chatbox and Cherry Studio backups into Wraive without deleting local data. */
class ForeignImportService(
    private val repository: AppRepository,
    @Suppress("unused") private val gson: Gson
) {
    suspend fun import(input: InputStream, fileName: String = ""): ForeignImportSummary =
        withContext(Dispatchers.IO) {
            val bytes = input.readBytes()
            require(bytes.isNotEmpty()) { "导入文件为空" }
            val roots = if (bytes.isZip()) readZipJson(bytes) else listOf(parseJson(bytes))
            require(roots.isNotEmpty()) { "压缩包中没有可识别的 JSON 数据" }

            val source = detectSource(roots, fileName)
            val aggregate = mergeRoots(roots)
            importAggregate(source, aggregate)
        }

    private suspend fun importAggregate(source: String, root: JsonObject): ForeignImportSummary {
        val providerMap = linkedMapOf<String, ProviderConfig>()
        val importedProviders = linkedMapOf<String, ProviderConfig>()
        val importedModels = linkedMapOf<String, ModelConfig>()

        providerObjects(root).forEachIndexed { index, item ->
            val sourceId = item.text("id", "providerId", "key", "type", "name")
                ?: "provider-$index"
            val name = item.text("name", "displayName", "label") ?: sourceId
            val protocol = inferProtocol(
                item.text("protocol", "type", "provider", "apiType") ?: name
            )
            val targetId = stableId(source, "provider", sourceId)
            val keys = buildList {
                item.text("apiKey", "key", "secretKey", "accessToken")?.let(::add)
                item.array("apiKeys", "keys")?.forEach { element ->
                    element.asText()?.takeIf(String::isNotBlank)?.let(::add)
                }
            }.map(String::trim).filter(String::isNotBlank).distinct()
            val provider = ProviderConfig(
                id = targetId,
                name = name,
                protocol = protocol,
                baseUrl = item.text("baseUrl", "apiHost", "apiUrl", "host", "endpoint")
                    ?.trimEnd('/') ?: protocol.defaultUrl(),
                apiKey = keys.firstOrNull().orEmpty(),
                apiKeys = keys.drop(1),
                enabled = item.boolean("enabled", "isEnabled") ?: true,
                group = item.text("group", "groupName").orEmpty(),
                customHeaders = item.stringMap("headers", "customHeaders")
            )
            providerMap[sourceId] = provider
            providerMap[name] = provider
            importedProviders[targetId] = provider

            modelObjects(item).forEachIndexed { modelIndex, modelItem ->
                val model = parseModel(
                    source = source,
                    item = modelItem,
                    fallbackId = "model-$modelIndex",
                    provider = provider
                )
                importedModels["${model.providerId}:${model.id}"] = model
            }
        }

        val rootSettings = root.objectValue("settings", "config")
        if (importedProviders.isEmpty()) {
            parseLegacyProvider(source, rootSettings ?: root)?.let { provider ->
                importedProviders[provider.id] = provider
                providerMap["default"] = provider
                providerMap[provider.name] = provider
            }
        }

        modelObjects(root).forEachIndexed { index, item ->
            val sourceProvider = item.asObjectOrNull()
                ?.text("providerId", "provider", "providerKey")
            val provider = sourceProvider?.let(providerMap::get)
                ?: importedProviders.values.firstOrNull()
                ?: return@forEachIndexed
            val model = parseModel(source, item, "model-$index", provider)
            importedModels["${model.providerId}:${model.id}"] = model
        }

        val settingsModel = rootSettings?.text("model", "modelId", "apiModel")
        val firstProvider = importedProviders.values.firstOrNull()
        if (settingsModel != null && firstProvider != null) {
            val model = ModelConfig(
                id = settingsModel,
                providerId = firstProvider.id,
                displayName = settingsModel,
                capabilities = inferCapabilities(settingsModel)
            )
            importedModels.putIfAbsent("${model.providerId}:${model.id}", model)
        }

        importedProviders.values.forEach { repository.upsertProvider(it) }
        importedModels.values.forEach { repository.upsertModel(it) }

        val assistants = linkedMapOf<String, AssistantProfile>()
        assistantObjects(root).forEachIndexed { index, item ->
            val sourceId = item.text("id", "assistantId", "key") ?: "assistant-$index"
            val provider = item.text("providerId", "provider", "providerKey")
                ?.let(providerMap::get) ?: firstProvider
            val modelId = item.text("modelId", "model", "defaultModel")
            val assistant = AssistantProfile(
                id = stableId(source, "assistant", sourceId),
                name = item.text("name", "title") ?: "导入助手 ${index + 1}",
                description = item.text("description", "desc").orEmpty(),
                systemPrompt = item.text("systemPrompt", "prompt", "systemMessage").orEmpty(),
                model = if (provider != null && modelId != null) ModelRef(provider.id, modelId) else null,
                temperature = item.number("temperature") ?: 0.7,
                topP = item.number("topP", "top_p") ?: 1.0,
                maxOutputTokens = item.number("maxTokens", "maxOutputTokens")?.toInt(),
                webSearchEnabled = item.boolean("webSearch", "enableWebSearch") ?: false,
                memoryEnabled = item.boolean("memoryEnabled", "enableMemory") ?: true
            )
            assistants[sourceId] = assistant
            assistants[assistant.id] = assistant
            repository.upsertAssistant(assistant)
        }

        val defaultAssistant = assistants.values.firstOrNull() ?: AssistantProfile(
            id = stableId(source, "assistant", "default"),
            name = "$source 导入",
            description = "由 $source 备份导入",
            model = firstProvider?.let { provider ->
                importedModels.values.firstOrNull { it.providerId == provider.id }
                    ?.let { ModelRef(provider.id, it.id) }
            }
        ).also { repository.upsertAssistant(it) }

        val blocksByMessage = messageBlockObjects(root)
            .groupBy { it.text("messageId", "message_id") }
        val globalMessages = messageObjects(root)
            .filter { it.text("topicId", "sessionId", "conversationId", "chatId") != null }
            .groupBy { it.text("topicId", "sessionId", "conversationId", "chatId") }

        var conversationCount = 0
        var messageCount = 0
        val conversations = conversationObjects(root)
        if (conversations.isNotEmpty()) {
            conversations.forEachIndexed { index, item ->
                val sourceId = item.text("id", "topicId", "sessionId", "key") ?: "chat-$index"
                val assistant = item.text("assistantId", "assistant", "botId")
                    ?.let(assistants::get) ?: defaultAssistant
                val targetConversationId = stableId(source, "conversation", sourceId)
                val nestedMessages = item.array("messages", "items")
                    ?.objects().orEmpty()
                val messages = (nestedMessages + globalMessages[sourceId].orEmpty())
                    .distinctBy { it.text("id", "messageId") ?: it.toString() }
                val createdAt = item.timestamp("createdAt", "created_at", "createdTime", "date")
                val updatedAt = item.timestamp("updatedAt", "updated_at", "updatedTime")
                    ?: messages.maxOfOrNull { it.timestamp("createdAt", "created_at", "timestamp") ?: 0L }
                    ?: createdAt
                val conversation = Conversation(
                    id = targetConversationId,
                    title = item.text("title", "name", "topicName") ?: "导入会话 ${index + 1}",
                    assistantId = assistant.id,
                    createdAt = createdAt ?: System.currentTimeMillis(),
                    updatedAt = updatedAt ?: System.currentTimeMillis(),
                    pinned = item.boolean("pinned", "isPinned") ?: false,
                    archived = item.boolean("archived", "isArchived") ?: false,
                    summary = item.text("summary").orEmpty(),
                    lastMessagePreview = messages.lastOrNull()?.contentText(blocksByMessage).orEmpty().take(120)
                )
                repository.upsertConversation(conversation)
                messages.forEachIndexed { messageIndex, message ->
                    repository.upsertMessage(
                        parseMessage(
                            source,
                            targetConversationId,
                            message,
                            messageIndex,
                            blocksByMessage
                        )
                    )
                }
                conversationCount++
                messageCount += messages.size
            }
        } else {
            val messages = messageObjects(root)
            if (messages.isNotEmpty()) {
                val targetConversationId = stableId(source, "conversation", "default")
                repository.upsertConversation(
                    Conversation(
                        id = targetConversationId,
                        title = "$source 导入会话",
                        assistantId = defaultAssistant.id,
                        lastMessagePreview = messages.last().contentText(blocksByMessage).take(120)
                    )
                )
                messages.forEachIndexed { index, message ->
                    repository.upsertMessage(
                        parseMessage(source, targetConversationId, message, index, blocksByMessage)
                    )
                }
                conversationCount = 1
                messageCount = messages.size
            }
        }

        require(
            importedProviders.isNotEmpty() || assistants.isNotEmpty() || conversationCount > 0
        ) { "没有识别到可导入的 Chatbox 或 Cherry Studio 数据" }
        return ForeignImportSummary(
            source = source,
            providers = importedProviders.size,
            models = importedModels.size,
            assistants = assistants.values.distinctBy(AssistantProfile::id).size.coerceAtLeast(1),
            conversations = conversationCount,
            messages = messageCount
        )
    }

    private fun parseLegacyProvider(source: String, settings: JsonObject): ProviderConfig? {
        val key = settings.text(
            "apiKey", "openaiApiKey", "claudeApiKey", "geminiApiKey", "accessToken"
        ).orEmpty()
        val model = settings.text("model", "modelId", "apiModel").orEmpty()
        val type = settings.text("provider", "providerId", "apiType", "modelProvider")
            ?: when {
                model.contains("claude", true) -> "anthropic"
                model.contains("gemini", true) -> "google"
                else -> "openai"
            }
        val baseUrl = settings.text("baseUrl", "apiHost", "apiUrl", "host", "endpoint")
        if (key.isBlank() && model.isBlank() && baseUrl.isNullOrBlank()) return null
        val protocol = inferProtocol(type)
        return ProviderConfig(
            id = stableId(source, "provider", type),
            name = "$source ${type.replaceFirstChar(Char::uppercase)}",
            protocol = protocol,
            baseUrl = baseUrl?.trimEnd('/') ?: protocol.defaultUrl(),
            apiKey = key
        )
    }

    private fun parseModel(
        source: String,
        item: JsonElement,
        fallbackId: String,
        provider: ProviderConfig
    ): ModelConfig {
        val objectValue = item.asObjectOrNull()
        val id = item.asText()
            ?: objectValue?.text("id", "modelId", "model", "name", "key")
            ?: fallbackId
        return ModelConfig(
            id = id,
            providerId = provider.id,
            displayName = objectValue?.text("displayName", "name", "label") ?: id,
            capabilities = inferCapabilities(id),
            maxContextTokens = objectValue?.number("contextWindow", "maxContextTokens")?.toInt()
                ?: 128_000,
            maxOutputTokens = objectValue?.number("maxTokens", "maxOutputTokens")?.toInt()
                ?: 8_192,
            enabled = objectValue?.boolean("enabled", "isEnabled") ?: true
        )
    }

    private fun parseMessage(
        source: String,
        conversationId: String,
        item: JsonObject,
        index: Int,
        blocksByMessage: Map<String?, List<JsonObject>>
    ): ChatMessage {
        val sourceId = item.text("id", "messageId", "key") ?: "$conversationId-$index"
        val createdAt = item.timestamp("createdAt", "created_at", "timestamp", "date")
            ?: System.currentTimeMillis() + index
        val blocks = blocksByMessage[sourceId].orEmpty()
        val reasoning = item.text("reasoning", "thinking", "reasoningContent")
            ?: blocks.filter { block ->
                block.text("type", "blockType")?.contains(Regex("think|reason", RegexOption.IGNORE_CASE)) == true
            }.joinToString("\n") { it.text("content", "text").orEmpty() }
        return ChatMessage(
            id = stableId(source, "message", sourceId),
            conversationId = conversationId,
            role = when (item.text("role", "type", "sender")?.lowercase()) {
                "assistant", "ai", "bot" -> MessageRole.ASSISTANT
                "system" -> MessageRole.SYSTEM
                "tool", "function" -> MessageRole.TOOL
                else -> MessageRole.USER
            },
            content = item.contentText(blocksByMessage),
            reasoning = reasoning.orEmpty(),
            providerId = item.text("providerId", "provider"),
            modelId = item.text("modelId", "model"),
            createdAt = createdAt,
            updatedAt = item.timestamp("updatedAt", "updated_at") ?: createdAt
        )
    }

    private fun detectSource(roots: List<JsonObject>, fileName: String): String {
        val content = roots.joinToString().lowercase()
        return when {
            fileName.contains("cherry", true) ||
                "indexeddb" in content || "message_blocks" in content -> "Cherry Studio"
            fileName.contains("chatbox", true) || "chatsessions" in content -> "Chatbox"
            else -> error("无法判断备份来源，请选择 Chatbox 或 Cherry Studio 的 JSON/ZIP 备份")
        }
    }

    private fun mergeRoots(roots: List<JsonObject>): JsonObject {
        if (roots.size == 1) return roots.single().unwrappedObject()
        return JsonObject().apply {
            add("documents", JsonArray().also { array -> roots.forEach(array::add) })
        }
    }

    private fun providerObjects(root: JsonObject) =
        root.findArrays("providers", "providerConfigs", "llmProviders").flatMap { it.objects() }

    private fun modelObjects(root: JsonObject) =
        root.findArrays("models", "modelConfigs", "modelList").flatMap { it.toList() }

    private fun assistantObjects(root: JsonObject) =
        root.findArrays("assistants", "assistantList", "bots").flatMap { it.objects() }

    private fun conversationObjects(root: JsonObject) =
        root.findArrays("chatSessions", "sessions", "topics", "conversations", "chats")
            .flatMap { it.objects() }

    private fun messageObjects(root: JsonObject) =
        root.findArrays("messages", "chatMessages").flatMap { it.objects() }

    private fun messageBlockObjects(root: JsonObject) =
        root.findArrays("message_blocks", "messageBlocks", "blocks").flatMap { it.objects() }

    private fun JsonObject.findArrays(vararg names: String): List<JsonArray> {
        val results = mutableListOf<JsonArray>()
        fun visit(element: JsonElement, depth: Int) {
            if (depth > 10) return
            val unwrapped = element.unwrapJsonString()
            when {
                unwrapped.isJsonObject -> unwrapped.asJsonObject.entrySet().forEach { (key, value) ->
                    if (names.any { it.equals(key, true) }) {
                        value.unwrapJsonString().asArrayOrNull()?.let(results::add)
                    }
                    visit(value, depth + 1)
                }
                unwrapped.isJsonArray -> unwrapped.asJsonArray.forEach { visit(it, depth + 1) }
            }
        }
        visit(this, 0)
        return results.distinctBy { it.toString() }
    }

    private fun JsonObject.unwrappedObject(): JsonObject {
        val nested = objectValue("data", "backup", "payload")
        return nested ?: this
    }

    private fun JsonObject.objectValue(vararg names: String): JsonObject? = names.firstNotNullOfOrNull {
        get(it)?.unwrapJsonString()?.asObjectOrNull()
    }

    private fun JsonObject.array(vararg names: String): JsonArray? = names.firstNotNullOfOrNull {
        get(it)?.unwrapJsonString()?.asArrayOrNull()
    }

    private fun JsonObject.text(vararg names: String): String? = names.firstNotNullOfOrNull {
        get(it)?.asText()?.takeIf(String::isNotBlank)
    }

    private fun JsonObject.number(vararg names: String): Double? = names.firstNotNullOfOrNull {
        get(it)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive?.runCatching { asDouble }
            ?.getOrNull()
    }

    private fun JsonObject.boolean(vararg names: String): Boolean? = names.firstNotNullOfOrNull {
        get(it)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive?.let { value ->
            runCatching { value.asBoolean }.getOrNull()
        }
    }

    private fun JsonObject.stringMap(vararg names: String): Map<String, String> =
        objectValue(*names)?.entrySet()?.mapNotNull { (key, value) ->
            value.asText()?.let { key to it }
        }?.toMap().orEmpty()

    private fun JsonObject.timestamp(vararg names: String): Long? {
        val value = names.firstNotNullOfOrNull(::get) ?: return null
        val number = runCatching { value.asLong }.getOrNull()
        if (number != null) return if (number in 1..9_999_999_999L) number * 1000 else number
        return runCatching { Instant.parse(value.asString).toEpochMilli() }.getOrNull()
    }

    private fun JsonObject.contentText(
        blocksByMessage: Map<String?, List<JsonObject>> = emptyMap()
    ): String {
        val direct = get("content") ?: get("text") ?: get("message")
        direct?.contentValue()?.takeIf(String::isNotBlank)?.let { return it }
        val id = text("id", "messageId", "key")
        return blocksByMessage[id].orEmpty()
            .filterNot { block ->
                block.text("type", "blockType")?.contains(Regex("think|reason", RegexOption.IGNORE_CASE)) == true
            }
            .joinToString("\n") { it.get("content")?.contentValue().orEmpty() }
            .trim()
    }

    private fun JsonElement.contentValue(): String = when {
        isJsonPrimitive -> asText().orEmpty()
        isJsonArray -> asJsonArray.mapNotNull { element ->
            element.asText() ?: element.asObjectOrNull()?.let { objectValue ->
                objectValue.text("text", "content", "value")
            }
        }.joinToString("\n")
        isJsonObject -> asJsonObject.text("text", "content", "value").orEmpty()
        else -> ""
    }

    private fun JsonElement.asText(): String? = takeIf { it.isJsonPrimitive }
        ?.asJsonPrimitive?.takeIf { it.isString || it.isNumber || it.isBoolean }?.asString

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonElement.asArrayOrNull(): JsonArray? = takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonArray.objects(): List<JsonObject> = mapNotNull { element ->
        element.unwrapJsonString().asObjectOrNull()
    }

    private fun JsonElement.unwrapJsonString(): JsonElement {
        if (!isJsonPrimitive || !asJsonPrimitive.isString) return this
        val text = asString.trim()
        if (!text.startsWith('{') && !text.startsWith('[')) return this
        return runCatching { JsonParser.parseString(text) }.getOrDefault(this)
    }

    private fun parseJson(bytes: ByteArray): JsonObject {
        val root = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).unwrapJsonString()
        return root.asObjectOrNull() ?: error("备份 JSON 根节点不是对象")
    }

    private fun readZipJson(bytes: ByteArray): List<JsonObject> {
        val roots = mutableListOf<JsonObject>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var total = 0L
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json", true)) {
                    val jsonBytes = zip.readBytesLimited(MAX_JSON_ENTRY_BYTES)
                    total += jsonBytes.size
                    require(total <= MAX_TOTAL_JSON_BYTES) { "备份中的 JSON 数据过大" }
                    runCatching { parseJson(jsonBytes) }.getOrNull()?.let(roots::add)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return roots
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "备份中的单个 JSON 文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun ByteArray.isZip(): Boolean = size >= 4 &&
        this[0] == 0x50.toByte() && this[1] == 0x4b.toByte() &&
        this[2] == 0x03.toByte() && this[3] == 0x04.toByte()

    private fun inferProtocol(raw: String): ProviderProtocol {
        val type = raw.lowercase()
        return when {
            "anthropic" in type || "claude" in type -> ProviderProtocol.ANTHROPIC
            "google" in type || "gemini" in type -> ProviderProtocol.GOOGLE
            "responses" in type -> ProviderProtocol.OPENAI_RESPONSES
            else -> ProviderProtocol.OPENAI_CHAT
        }
    }

    private fun ProviderProtocol.defaultUrl(): String = when (this) {
        ProviderProtocol.OPENAI_CHAT,
        ProviderProtocol.OPENAI_RESPONSES -> "https://api.openai.com/v1"
        ProviderProtocol.ANTHROPIC -> "https://api.anthropic.com/v1"
        ProviderProtocol.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
    }

    private fun stableId(source: String, kind: String, id: String): String =
        UUID.nameUUIDFromBytes("$source:$kind:$id".toByteArray()).toString()

    private companion object {
        const val MAX_JSON_ENTRY_BYTES = 48 * 1024 * 1024
        const val MAX_TOTAL_JSON_BYTES = 96L * 1024 * 1024
    }
}
