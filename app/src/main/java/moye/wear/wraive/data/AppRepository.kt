package moye.wear.wraive.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.CloudBackupConfig
import moye.wear.wraive.model.McpServerConfig
import moye.wear.wraive.model.MemoryEntry
import moye.wear.wraive.model.MessageSearchHit
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.normalizeModelCapabilities
import moye.wear.wraive.model.NetworkProxyConfig
import moye.wear.wraive.model.PromptTransform
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.QuickPhrase
import moye.wear.wraive.model.RequestLog
import moye.wear.wraive.model.RequestStatus
import moye.wear.wraive.model.SearchProviderConfig
import moye.wear.wraive.model.SearchProviderType
import moye.wear.wraive.model.SpeechConfig
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.UsageSummary
import moye.wear.wraive.model.DailyUsage
import moye.wear.wraive.model.WorldBookEntry
import moye.wear.wraive.model.availableApiKeys
import java.time.Instant
import java.time.ZoneId
import java.io.File

class AppRepository(
    context: Context,
    private val gson: Gson,
    private val database: WraiveDatabase = WraiveDatabase(context),
    private val secrets: SecretStore = SecretStore(context)
) {
    private val generatedImagesDir = File(context.filesDir, "generated_images")
    private val temporaryImagesDir = File(context.cacheDir, "generated_images")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageRevision = MutableStateFlow(0L)

    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers: StateFlow<List<ProviderConfig>> = _providers

    private val _models = MutableStateFlow<List<ModelConfig>>(emptyList())
    val models: StateFlow<List<ModelConfig>> = _models

    private val _assistants = MutableStateFlow<List<AssistantProfile>>(emptyList())
    val assistants: StateFlow<List<AssistantProfile>> = _assistants

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memories: StateFlow<List<MemoryEntry>> = _memories

    private val _worldBookEntries = MutableStateFlow<List<WorldBookEntry>>(emptyList())
    val worldBookEntries: StateFlow<List<WorldBookEntry>> = _worldBookEntries

    private val _quickPhrases = MutableStateFlow<List<QuickPhrase>>(emptyList())
    val quickPhrases: StateFlow<List<QuickPhrase>> = _quickPhrases

    private val _searchProviders = MutableStateFlow<List<SearchProviderConfig>>(emptyList())
    val searchProviders: StateFlow<List<SearchProviderConfig>> = _searchProviders

    private val _mcpServers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val mcpServers: StateFlow<List<McpServerConfig>> = _mcpServers

    private val _promptTransforms = MutableStateFlow<List<PromptTransform>>(emptyList())
    val promptTransforms: StateFlow<List<PromptTransform>> = _promptTransforms

    private val _speechConfig = MutableStateFlow(SpeechConfig())
    val speechConfig: StateFlow<SpeechConfig> = _speechConfig

    private val _requestLogs = MutableStateFlow<List<RequestLog>>(emptyList())
    val requestLogs: StateFlow<List<RequestLog>> = _requestLogs

    private val _cloudBackupConfig = MutableStateFlow(CloudBackupConfig())
    val cloudBackupConfig: StateFlow<CloudBackupConfig> = _cloudBackupConfig

    private val _networkProxyConfig = MutableStateFlow(NetworkProxyConfig())
    val networkProxyConfig: StateFlow<NetworkProxyConfig> = _networkProxyConfig

    private val _transcriptionConfig = MutableStateFlow(TranscriptionConfig())
    val transcriptionConfig: StateFlow<TranscriptionConfig> = _transcriptionConfig

    init {
        scope.launch {
            refreshAll()
            seedDefaults()
        }
    }

    fun observeMessages(conversationId: String) = messageRevision.map {
        withContext(Dispatchers.IO) {
            load(
                WraiveDatabase.KIND_MESSAGE,
                ChatMessage::class.java,
                conversationId
            )
        }
    }

    suspend fun upsertProvider(provider: ProviderConfig) = withContext(Dispatchers.IO) {
        secrets.put(SECRET_PROVIDER, provider.id, provider.availableApiKeys().joinToString("\n"))
        put(
            WraiveDatabase.KIND_PROVIDER,
            provider.id,
            provider.copy(apiKey = "", apiKeys = emptyList()),
            sortOrder = provider.sortOrder
        )
        refreshProviders()
    }

    suspend fun deleteProvider(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_PROVIDER, id)
        secrets.remove(SECRET_PROVIDER, id)
        refreshProviders()
    }

    suspend fun upsertModel(model: ModelConfig) = withContext(Dispatchers.IO) {
        put(
            WraiveDatabase.KIND_MODEL,
            model.storageId(),
            model,
            parentId = model.providerId,
            sortOrder = model.sortOrder
        )
        refreshModels()
    }

    suspend fun replaceProviderModels(providerId: String, models: List<ModelConfig>) =
        withContext(Dispatchers.IO) {
            database.deleteChildren(WraiveDatabase.KIND_MODEL, providerId)
            models.forEach { model ->
                put(
                    WraiveDatabase.KIND_MODEL,
                    model.storageId(),
                    model,
                    parentId = providerId,
                    sortOrder = model.sortOrder
                )
            }
            refreshModels()
        }

    suspend fun deleteModel(model: ModelConfig) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_MODEL, model.storageId())
        refreshModels()
    }

    suspend fun upsertAssistant(assistant: AssistantProfile) = withContext(Dispatchers.IO) {
        put(
            WraiveDatabase.KIND_ASSISTANT,
            assistant.id,
            assistant,
            sortOrder = assistant.sortOrder
        )
        refreshAssistants()
    }

    suspend fun deleteAssistant(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_ASSISTANT, id)
        refreshAssistants()
    }

    suspend fun upsertConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        if (!conversation.temporary) {
            put(
                WraiveDatabase.KIND_CONVERSATION,
                conversation.id,
                conversation,
                parentId = conversation.assistantId,
                updatedAt = conversation.updatedAt
            )
            refreshConversations()
        }
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        deleteOwnedAttachments(messages(id))
        database.deleteChildren(WraiveDatabase.KIND_MESSAGE, id)
        database.delete(WraiveDatabase.KIND_CONVERSATION, id)
        messageRevision.value++
        refreshConversations()
    }

    suspend fun upsertMessage(message: ChatMessage, temporary: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (!temporary) {
                put(
                    WraiveDatabase.KIND_MESSAGE,
                    message.id,
                    message,
                    parentId = message.conversationId,
                    updatedAt = message.createdAt
                )
                messageRevision.value++
            }
        }

    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) {
        load(WraiveDatabase.KIND_MESSAGE, ChatMessage::class.java)
            .firstOrNull { it.id == id }?.let { deleteOwnedAttachments(listOf(it)) }
        database.delete(WraiveDatabase.KIND_MESSAGE, id)
        messageRevision.value++
    }

    suspend fun deleteMessagesFrom(
        conversationId: String,
        timestamp: Long,
        inclusive: Boolean
    ) = withContext(Dispatchers.IO) {
        val removed = messages(conversationId).filter {
            if (inclusive) it.createdAt >= timestamp else it.createdAt > timestamp
        }
        deleteOwnedAttachments(removed)
        database.deleteChildrenFrom(
            WraiveDatabase.KIND_MESSAGE,
            conversationId,
            timestamp,
            inclusive
        )
        messageRevision.value++
    }

    suspend fun messages(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        load(WraiveDatabase.KIND_MESSAGE, ChatMessage::class.java, conversationId)
    }

    suspend fun upsertMemory(entry: MemoryEntry) = withContext(Dispatchers.IO) {
        put(WraiveDatabase.KIND_MEMORY, entry.id, entry, parentId = entry.assistantId)
        refreshMemories()
    }

    suspend fun deleteMemory(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_MEMORY, id)
        refreshMemories()
    }

    suspend fun upsertWorldBookEntry(entry: WorldBookEntry) = withContext(Dispatchers.IO) {
        put(
            WraiveDatabase.KIND_WORLD_BOOK,
            entry.id,
            entry,
            parentId = entry.bookId,
            sortOrder = -entry.priority
        )
        refreshWorldBooks()
    }

    suspend fun deleteWorldBookEntry(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_WORLD_BOOK, id)
        refreshWorldBooks()
    }

    suspend fun upsertQuickPhrase(phrase: QuickPhrase) = withContext(Dispatchers.IO) {
        put(
            WraiveDatabase.KIND_QUICK_PHRASE,
            phrase.id,
            phrase,
            parentId = phrase.assistantId,
            sortOrder = phrase.sortOrder
        )
        refreshQuickPhrases()
    }

    suspend fun deleteQuickPhrase(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_QUICK_PHRASE, id)
        refreshQuickPhrases()
    }

    suspend fun upsertSearchProvider(provider: SearchProviderConfig) = withContext(Dispatchers.IO) {
        secrets.put(SECRET_SEARCH, provider.id, provider.apiKey)
        put(
            WraiveDatabase.KIND_SEARCH_PROVIDER,
            provider.id,
            provider.copy(apiKey = "")
        )
        refreshSearchProviders()
    }

    suspend fun deleteSearchProvider(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_SEARCH_PROVIDER, id)
        secrets.remove(SECRET_SEARCH, id)
        refreshSearchProviders()
    }

    suspend fun upsertMcpServer(server: McpServerConfig) = withContext(Dispatchers.IO) {
        put(WraiveDatabase.KIND_MCP_SERVER, server.id, server)
        refreshMcpServers()
    }

    suspend fun deleteMcpServer(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_MCP_SERVER, id)
        refreshMcpServers()
    }

    suspend fun upsertPromptTransform(transform: PromptTransform) = withContext(Dispatchers.IO) {
        put(WraiveDatabase.KIND_PROMPT_TRANSFORM, transform.id, transform)
        refreshPromptTransforms()
    }

    suspend fun deletePromptTransform(id: String) = withContext(Dispatchers.IO) {
        database.delete(WraiveDatabase.KIND_PROMPT_TRANSFORM, id)
        refreshPromptTransforms()
    }

    suspend fun upsertSpeechConfig(config: SpeechConfig) = withContext(Dispatchers.IO) {
        secrets.put(SECRET_SPEECH, config.id, config.apiKey)
        put(
            WraiveDatabase.KIND_SPEECH_CONFIG,
            config.id,
            config.copy(apiKey = "")
        )
        refreshSpeechConfig()
    }

    suspend fun upsertRequestLog(log: RequestLog) = withContext(Dispatchers.IO) {
        put(
            WraiveDatabase.KIND_REQUEST_LOG,
            log.id,
            log,
            parentId = log.conversationId,
            updatedAt = log.finishedAt ?: log.startedAt
        )
        refreshRequestLogs()
    }

    suspend fun clearRequestLogs() = withContext(Dispatchers.IO) {
        database.deleteKind(WraiveDatabase.KIND_REQUEST_LOG)
        refreshRequestLogs()
    }

    suspend fun upsertCloudBackupConfig(config: CloudBackupConfig) = withContext(Dispatchers.IO) {
        secrets.put(
            SECRET_CLOUD,
            config.id,
            gson.toJson(
                CloudSecrets(
                    secretAccessKey = config.secretAccessKey,
                    encryptionPassword = config.encryptionPassword
                )
            )
        )
        put(
            WraiveDatabase.KIND_CLOUD_BACKUP,
            config.id,
            config.copy(secretAccessKey = "", encryptionPassword = "")
        )
        refreshCloudBackupConfig()
    }

    suspend fun upsertNetworkProxyConfig(config: NetworkProxyConfig) = withContext(Dispatchers.IO) {
        secrets.put(SECRET_PROXY, config.id, config.password)
        put(
            WraiveDatabase.KIND_NETWORK_PROXY,
            config.id,
            config.copy(password = "")
        )
        refreshNetworkProxyConfig()
    }

    suspend fun upsertTranscriptionConfig(config: TranscriptionConfig) =
        withContext(Dispatchers.IO) {
            secrets.put(SECRET_TRANSCRIPTION, config.id, config.apiKey)
            put(
                WraiveDatabase.KIND_TRANSCRIPTION_CONFIG,
                config.id,
                config.copy(apiKey = "")
            )
            refreshTranscriptionConfig()
        }

    suspend fun searchMessages(query: String): List<MessageSearchHit> = withContext(Dispatchers.IO) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return@withContext emptyList()
        val conversationsById = _conversations.value.associateBy(Conversation::id)
        load(WraiveDatabase.KIND_MESSAGE, ChatMessage::class.java)
            .asReversed()
            .filter { message ->
                message.content.lowercase().contains(needle) ||
                    message.reasoning.lowercase().contains(needle) ||
                    message.attachments.any { it.name.lowercase().contains(needle) }
            }
            .mapNotNull { message ->
                conversationsById[message.conversationId]?.let { MessageSearchHit(it, message) }
            }
            .take(100)
    }

    suspend fun usageSummary(): UsageSummary = withContext(Dispatchers.IO) {
        val logs = _requestLogs.value
        val zone = ZoneId.systemDefault()
        val daily = logs
            .filter { it.status != RequestStatus.RUNNING }
            .groupBy { log ->
                Instant.ofEpochMilli(log.startedAt).atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .map { (day, entries) ->
                DailyUsage(
                    dayStart = day,
                    requests = entries.size,
                    tokens = entries.sumOf { it.usage.totalTokens.toLong() }
                )
            }
            .sortedByDescending(DailyUsage::dayStart)
        UsageSummary(
            conversations = _conversations.value.size,
            messages = load(WraiveDatabase.KIND_MESSAGE, ChatMessage::class.java).size,
            requests = logs.count { it.status != RequestStatus.RUNNING },
            inputTokens = logs.sumOf { it.usage.inputTokens.toLong() },
            outputTokens = logs.sumOf { it.usage.outputTokens.toLong() },
            cachedTokens = logs.sumOf { it.usage.cachedTokens.toLong() },
            failures = logs.count { it.status == RequestStatus.FAILED },
            activeDays = daily.size,
            daily = daily.take(30)
        )
    }

    suspend fun snapshot(): RepositorySnapshot = withContext(Dispatchers.IO) {
        RepositorySnapshot(
            providers = _providers.value,
            models = _models.value,
            assistants = _assistants.value,
            conversations = _conversations.value,
            messages = _conversations.value.flatMap { messages(it.id) },
            memories = _memories.value,
            worldBookEntries = _worldBookEntries.value,
            quickPhrases = _quickPhrases.value,
            searchProviders = _searchProviders.value,
            mcpServers = _mcpServers.value,
            promptTransforms = _promptTransforms.value,
            speechConfig = _speechConfig.value,
            requestLogs = _requestLogs.value,
            cloudBackupConfig = _cloudBackupConfig.value,
            networkProxyConfig = _networkProxyConfig.value,
            transcriptionConfig = _transcriptionConfig.value
        )
    }

    suspend fun restore(snapshot: RepositorySnapshot) = withContext(Dispatchers.IO) {
        deleteOwnedAttachments(load(WraiveDatabase.KIND_MESSAGE, ChatMessage::class.java))
        database.clearAll()
        snapshot.providers.forEach { provider ->
            secrets.put(
                SECRET_PROVIDER,
                provider.id,
                provider.availableApiKeys().joinToString("\n")
            )
            put(
                WraiveDatabase.KIND_PROVIDER,
                provider.id,
                provider.copy(apiKey = "", apiKeys = emptyList()),
                sortOrder = provider.sortOrder
            )
        }
        snapshot.models.forEach { model ->
            put(
                WraiveDatabase.KIND_MODEL,
                model.storageId(),
                model,
                parentId = model.providerId,
                sortOrder = model.sortOrder
            )
        }
        snapshot.assistants.forEach { assistant ->
            put(
                WraiveDatabase.KIND_ASSISTANT,
                assistant.id,
                assistant,
                sortOrder = assistant.sortOrder
            )
        }
        snapshot.conversations.forEach { conversation ->
            put(
                WraiveDatabase.KIND_CONVERSATION,
                conversation.id,
                conversation,
                parentId = conversation.assistantId,
                updatedAt = conversation.updatedAt
            )
        }
        snapshot.messages.forEach { message ->
            put(
                WraiveDatabase.KIND_MESSAGE,
                message.id,
                message,
                parentId = message.conversationId,
                updatedAt = message.createdAt
            )
        }
        snapshot.memories.forEach { entry ->
            put(WraiveDatabase.KIND_MEMORY, entry.id, entry, parentId = entry.assistantId)
        }
        snapshot.worldBookEntries.forEach { entry ->
            put(
                WraiveDatabase.KIND_WORLD_BOOK,
                entry.id,
                entry,
                parentId = entry.bookId,
                sortOrder = -entry.priority
            )
        }
        snapshot.quickPhrases.forEach { phrase ->
            put(
                WraiveDatabase.KIND_QUICK_PHRASE,
                phrase.id,
                phrase,
                parentId = phrase.assistantId,
                sortOrder = phrase.sortOrder
            )
        }
        snapshot.searchProviders.forEach { provider ->
            secrets.put(SECRET_SEARCH, provider.id, provider.apiKey)
            put(
                WraiveDatabase.KIND_SEARCH_PROVIDER,
                provider.id,
                provider.copy(apiKey = "")
            )
        }
        snapshot.mcpServers.forEach { server ->
            put(WraiveDatabase.KIND_MCP_SERVER, server.id, server)
        }
        snapshot.promptTransforms.forEach { transform ->
            put(WraiveDatabase.KIND_PROMPT_TRANSFORM, transform.id, transform)
        }
        secrets.put(SECRET_SPEECH, snapshot.speechConfig.id, snapshot.speechConfig.apiKey)
        put(
            WraiveDatabase.KIND_SPEECH_CONFIG,
            snapshot.speechConfig.id,
            snapshot.speechConfig.copy(apiKey = "")
        )
        snapshot.requestLogs.forEach { log ->
            put(
                WraiveDatabase.KIND_REQUEST_LOG,
                log.id,
                log,
                parentId = log.conversationId,
                updatedAt = log.finishedAt ?: log.startedAt
            )
        }
        upsertCloudBackupConfig(snapshot.cloudBackupConfig)
        upsertNetworkProxyConfig(snapshot.networkProxyConfig)
        upsertTranscriptionConfig(snapshot.transcriptionConfig)
        messageRevision.value++
        refreshAll()
        seedDefaults()
    }

    private suspend fun seedDefaults() {
        if (_assistants.value.isEmpty()) {
            upsertAssistant(
                AssistantProfile(
                    name = "Wraive",
                    description = "通用 AI 助手",
                    systemPrompt = "你是一个可靠、清晰且乐于助人的 AI 助手。"
                )
            )
        }
        if (_searchProviders.value.isEmpty()) {
            upsertSearchProvider(
                SearchProviderConfig(
                    name = "DuckDuckGo",
                    type = SearchProviderType.DUCKDUCKGO,
                    baseUrl = "https://api.duckduckgo.com"
                )
            )
        }
        if (load(WraiveDatabase.KIND_SPEECH_CONFIG, SpeechConfig::class.java).isEmpty()) {
            upsertSpeechConfig(SpeechConfig())
        }
        if (load(WraiveDatabase.KIND_CLOUD_BACKUP, CloudBackupConfig::class.java).isEmpty()) {
            upsertCloudBackupConfig(CloudBackupConfig())
        }
        if (load(WraiveDatabase.KIND_NETWORK_PROXY, NetworkProxyConfig::class.java).isEmpty()) {
            upsertNetworkProxyConfig(NetworkProxyConfig())
        }
        if (load(WraiveDatabase.KIND_TRANSCRIPTION_CONFIG, TranscriptionConfig::class.java).isEmpty()) {
            upsertTranscriptionConfig(TranscriptionConfig())
        }
    }

    private suspend fun refreshAll() {
        refreshProviders()
        refreshModels()
        refreshAssistants()
        refreshConversations()
        refreshMemories()
        refreshWorldBooks()
        refreshQuickPhrases()
        refreshSearchProviders()
        refreshMcpServers()
        refreshPromptTransforms()
        refreshSpeechConfig()
        refreshRequestLogs()
        refreshCloudBackupConfig()
        refreshNetworkProxyConfig()
        refreshTranscriptionConfig()
    }

    private fun refreshProviders() {
        _providers.value = load(WraiveDatabase.KIND_PROVIDER, ProviderConfig::class.java)
            .map { provider ->
                val keys = secrets.get(SECRET_PROVIDER, provider.id)
                    .lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
                val rotation = runCatching { provider.keyRotation.name }
                    .mapCatching(moye.wear.wraive.model.KeyRotationStrategy::valueOf)
                    .getOrDefault(moye.wear.wraive.model.KeyRotationStrategy.ROUND_ROBIN)
                provider.copy(
                    apiKey = keys.firstOrNull().orEmpty(),
                    apiKeys = keys.drop(1),
                    group = provider.group.orEmpty(),
                    keyRotation = rotation,
                    name = provider.name.orEmpty().ifBlank { "Provider" },
                    baseUrl = provider.baseUrl.orEmpty(),
                    customHeaders = provider.customHeaders.orEmpty(),
                    customBody = provider.customBody.orEmpty()
                )
            }
    }

    private fun refreshModels() {
        _models.value = load(WraiveDatabase.KIND_MODEL, ModelConfig::class.java).map { model ->
            model.copy(
                displayName = model.displayName.orEmpty().ifBlank { model.id },
                capabilities = normalizeModelCapabilities(model.capabilities),
                maxContextTokens = model.maxContextTokens.takeIf { it > 0 } ?: 128_000,
                maxOutputTokens = model.maxOutputTokens.takeIf { it > 0 } ?: 8_192
            )
        }
    }

    private fun refreshAssistants() {
        _assistants.value = load(WraiveDatabase.KIND_ASSISTANT, AssistantProfile::class.java)
            .map { assistant ->
                assistant.copy(
                    name = assistant.name.orEmpty().ifBlank { "Assistant" },
                    description = assistant.description.orEmpty(),
                    systemPrompt = assistant.systemPrompt.orEmpty(),
                    worldBookIds = assistant.worldBookIds.orEmpty(),
                    mcpServerIds = assistant.mcpServerIds.orEmpty(),
                    customHeaders = assistant.customHeaders.orEmpty(),
                    customBody = assistant.customBody.orEmpty(),
                    imageSize = assistant.imageSize.orEmpty().ifBlank { "1024x1024" },
                    imageQuality = assistant.imageQuality.orEmpty().ifBlank { "auto" },
                    imageCount = assistant.imageCount.coerceIn(1, 4)
                )
            }
    }

    private fun refreshConversations() {
        _conversations.value = load(
            WraiveDatabase.KIND_CONVERSATION,
            Conversation::class.java
        )
    }

    private fun refreshMemories() {
        _memories.value = load(WraiveDatabase.KIND_MEMORY, MemoryEntry::class.java)
    }

    private fun refreshWorldBooks() {
        _worldBookEntries.value = load(
            WraiveDatabase.KIND_WORLD_BOOK,
            WorldBookEntry::class.java
        )
    }

    private fun refreshQuickPhrases() {
        _quickPhrases.value = load(WraiveDatabase.KIND_QUICK_PHRASE, QuickPhrase::class.java)
    }

    private fun refreshSearchProviders() {
        _searchProviders.value = load(
            WraiveDatabase.KIND_SEARCH_PROVIDER,
            SearchProviderConfig::class.java
        ).map {
            it.copy(
                apiKey = secrets.get(SECRET_SEARCH, it.id),
                name = it.name.orEmpty().ifBlank { it.type.name },
                baseUrl = it.baseUrl.orEmpty(),
                customHeaders = it.customHeaders.orEmpty(),
                resultLimit = it.resultLimit.takeIf { limit -> limit > 0 } ?: 5
            )
        }
    }

    private fun refreshMcpServers() {
        _mcpServers.value = load(WraiveDatabase.KIND_MCP_SERVER, McpServerConfig::class.java)
            .map {
                it.copy(
                    name = it.name.orEmpty().ifBlank { "MCP" },
                    url = it.url.orEmpty(),
                    headers = it.headers.orEmpty(),
                    timeoutSeconds = it.timeoutSeconds.takeIf { seconds -> seconds > 0 } ?: 30
                )
            }
    }

    private fun refreshPromptTransforms() {
        _promptTransforms.value = load(
            WraiveDatabase.KIND_PROMPT_TRANSFORM,
            PromptTransform::class.java
        )
    }

    private fun refreshSpeechConfig() {
        _speechConfig.value = load(
            WraiveDatabase.KIND_SPEECH_CONFIG,
            SpeechConfig::class.java
        ).firstOrNull()?.let { config ->
            config.copy(
                apiKey = secrets.get(SECRET_SPEECH, config.id),
                name = config.name.orEmpty().ifBlank { "系统语音" },
                endpoint = config.endpoint.orEmpty(),
                model = config.model.orEmpty().ifBlank { "gpt-4o-mini-tts" },
                voice = config.voice.orEmpty().ifBlank { "alloy" },
                languageCode = config.languageCode.orEmpty().ifBlank { "zh-CN" },
                speed = config.speed.takeIf { it > 0f } ?: 1f
            )
        } ?: SpeechConfig()
    }

    private fun refreshRequestLogs() {
        _requestLogs.value = load(WraiveDatabase.KIND_REQUEST_LOG, RequestLog::class.java)
    }

    private fun refreshCloudBackupConfig() {
        _cloudBackupConfig.value = load(
            WraiveDatabase.KIND_CLOUD_BACKUP,
            CloudBackupConfig::class.java
        ).firstOrNull()?.let { config ->
            val secret = runCatching {
                gson.fromJson(
                    secrets.get(SECRET_CLOUD, config.id),
                    CloudSecrets::class.java
                )
            }.getOrNull()
            config.copy(
                secretAccessKey = secret?.secretAccessKey.orEmpty(),
                encryptionPassword = secret?.encryptionPassword.orEmpty(),
                endpoint = config.endpoint.orEmpty(),
                region = config.region.orEmpty().ifBlank { "us-east-1" },
                bucket = config.bucket.orEmpty(),
                objectKey = config.objectKey.orEmpty().ifBlank { "wraive/backup.wraive" },
                accessKeyId = config.accessKeyId.orEmpty()
            )
        } ?: CloudBackupConfig()
    }

    private fun refreshNetworkProxyConfig() {
        _networkProxyConfig.value = load(
            WraiveDatabase.KIND_NETWORK_PROXY,
            NetworkProxyConfig::class.java
        ).firstOrNull()?.let { config ->
            config.copy(
                password = secrets.get(SECRET_PROXY, config.id),
                host = config.host.orEmpty(),
                username = config.username.orEmpty(),
                bypassHosts = config.bypassHosts.orEmpty(),
                port = config.port.takeIf { it in 1..65535 } ?: 8080
            )
        } ?: NetworkProxyConfig()
    }

    private fun refreshTranscriptionConfig() {
        _transcriptionConfig.value = load(
            WraiveDatabase.KIND_TRANSCRIPTION_CONFIG,
            TranscriptionConfig::class.java
        ).firstOrNull()?.let { config ->
            config.copy(
                apiKey = secrets.get(SECRET_TRANSCRIPTION, config.id),
                endpoint = config.endpoint.orEmpty(),
                model = config.model.orEmpty().ifBlank { "gpt-4o-mini-transcribe" },
                languageCode = config.languageCode.orEmpty().ifBlank { "zh-CN" }
            )
        } ?: TranscriptionConfig()
    }

    private fun <T : Any> put(
        kind: String,
        id: String,
        value: T,
        parentId: String? = null,
        sortOrder: Int = 0,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        database.put(kind, id, gson.toJson(value), parentId, sortOrder, updatedAt)
    }

    private fun <T> load(kind: String, type: Class<T>, parentId: String? = null): List<T> =
        database.query(kind, parentId).map { gson.fromJson(it, type) }

    private fun deleteOwnedAttachments(messages: List<ChatMessage>) {
        messages.flatMap(ChatMessage::attachments).forEach { attachment ->
            if (!attachment.uri.startsWith("file:")) return@forEach
            val file = Uri.parse(attachment.uri).path?.let(::File) ?: return@forEach
            val parent = file.canonicalFile.parentFile
            if (
                parent == generatedImagesDir.canonicalFile ||
                parent == temporaryImagesDir.canonicalFile
            ) {
                file.delete()
            }
        }
    }

    private companion object {
        const val SECRET_PROVIDER = "provider"
        const val SECRET_SEARCH = "search"
        const val SECRET_SPEECH = "speech"
        const val SECRET_CLOUD = "cloud"
        const val SECRET_PROXY = "proxy"
        const val SECRET_TRANSCRIPTION = "transcription"
    }
}

private fun ModelConfig.storageId(): String = "$providerId:$id"

data class RepositorySnapshot(
    val providers: List<ProviderConfig>,
    val models: List<ModelConfig>,
    val assistants: List<AssistantProfile>,
    val conversations: List<Conversation>,
    val messages: List<ChatMessage>,
    val memories: List<MemoryEntry>,
    val worldBookEntries: List<WorldBookEntry>,
    val quickPhrases: List<QuickPhrase>,
    val searchProviders: List<SearchProviderConfig>,
    val mcpServers: List<McpServerConfig>,
    val promptTransforms: List<PromptTransform>,
    val speechConfig: SpeechConfig = SpeechConfig(),
    val requestLogs: List<RequestLog> = emptyList(),
    val cloudBackupConfig: CloudBackupConfig = CloudBackupConfig(),
    val networkProxyConfig: NetworkProxyConfig = NetworkProxyConfig(),
    val transcriptionConfig: TranscriptionConfig = TranscriptionConfig()
)

private data class CloudSecrets(
    val secretAccessKey: String = "",
    val encryptionPassword: String = ""
)
