package moye.wear.wraive.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.launch
import moye.wear.wraive.AppGraph
import moye.wear.wraive.audio.SpeechController
import moye.wear.wraive.compat.DeviceCompatibility
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.AttachmentKind
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.MessageStatus
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.LocalWearBackAction
import moye.wear.wraive.ui.components.WearSelectionDialog
import moye.wear.wraive.ui.screens.AppearanceScreen
import moye.wear.wraive.ui.screens.AboutScreen
import moye.wear.wraive.ui.screens.SettingsSection
import moye.wear.wraive.ui.screens.AssistantsScreen
import moye.wear.wraive.ui.screens.BackupScreen
import moye.wear.wraive.ui.screens.ChatScreen
import moye.wear.wraive.ui.screens.CloudBackupScreen
import moye.wear.wraive.ui.screens.ConversationActionsScreen
import moye.wear.wraive.ui.screens.ConversationsScreen
import moye.wear.wraive.ui.screens.GlobalSearchScreen
import moye.wear.wraive.ui.screens.MainMenuScreen
import moye.wear.wraive.ui.screens.McpServersScreen
import moye.wear.wraive.ui.screens.MemoriesScreen
import moye.wear.wraive.ui.screens.NetworkProxyScreen
import moye.wear.wraive.ui.screens.MessageActionsScreen
import moye.wear.wraive.ui.screens.ProvidersScreen
import moye.wear.wraive.ui.screens.ProviderDetailsScreen
import moye.wear.wraive.ui.screens.PromptTransformsScreen
import moye.wear.wraive.ui.screens.QuickPhrasesScreen
import moye.wear.wraive.ui.screens.SearchProvidersScreen
import moye.wear.wraive.ui.screens.SettingsScreen
import moye.wear.wraive.ui.screens.RequestLogsScreen
import moye.wear.wraive.ui.screens.UsageStatsScreen
import moye.wear.wraive.ui.screens.TranscriptionSettingsScreen
import moye.wear.wraive.ui.screens.TranslationScreen
import moye.wear.wraive.ui.screens.VoiceSettingsScreen
import moye.wear.wraive.ui.screens.WorldBookScreen
import java.io.File

@Composable
fun AppNavigation(graph: AppGraph) {
    val navController = rememberSwipeDismissableNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val providers by graph.repository.providers.collectAsStateWithLifecycle()
    val models by graph.repository.models.collectAsStateWithLifecycle()
    val assistants by graph.repository.assistants.collectAsStateWithLifecycle()
    val conversations by graph.repository.conversations.collectAsStateWithLifecycle()
    val memories by graph.repository.memories.collectAsStateWithLifecycle()
    val worldBooks by graph.repository.worldBookEntries.collectAsStateWithLifecycle()
    val quickPhrases by graph.repository.quickPhrases.collectAsStateWithLifecycle()
    val searchProviders by graph.repository.searchProviders.collectAsStateWithLifecycle()
    val mcpServers by graph.repository.mcpServers.collectAsStateWithLifecycle()
    val promptTransforms by graph.repository.promptTransforms.collectAsStateWithLifecycle()
    val speechConfig by graph.repository.speechConfig.collectAsStateWithLifecycle()
    val requestLogs by graph.repository.requestLogs.collectAsStateWithLifecycle()
    val cloudBackup by graph.repository.cloudBackupConfig.collectAsStateWithLifecycle()
    val networkProxy by graph.repository.networkProxyConfig.collectAsStateWithLifecycle()
    val transcriptionConfig by graph.repository.transcriptionConfig.collectAsStateWithLifecycle()
    val preferences by graph.preferences.preferences.collectAsStateWithLifecycle()
    val generating by graph.chatEngine.generatingConversationIds.collectAsStateWithLifecycle()
    val temporaryConversations = remember { mutableStateMapOf<String, Conversation>() }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var autoOpenAssistantSetup by remember { mutableStateOf(false) }
    var fetchingModelsFor by remember { mutableStateOf<String?>(null) }
    val speech = remember { SpeechController(context, graph.httpClient, graph.gson) }
    DisposableEffect(Unit) { onDispose { speech.close() } }

    val readyProviderIds = providers
        .filter { it.enabled && it.baseUrl.isNotBlank() }
        .mapTo(mutableSetOf(), ProviderConfig::id)
    val readyModels = models.filter { it.enabled && it.providerId in readyProviderIds }
    val readyAssistants = assistants.filter { assistant ->
        assistant.model?.let { selected ->
            readyModels.any { it.providerId == selected.providerId && it.id == selected.modelId }
        } == true
    }
    val newConversationHint = when {
        readyModels.isEmpty() -> "先连接模型服务"
        readyAssistants.isEmpty() -> "先为助手选择模型"
        else -> null
    }
    var choosingAssistant by remember { mutableStateOf(false) }
    var newConversationTemporary by remember { mutableStateOf(false) }

    fun openNewConversation(assistantId: String, temporary: Boolean) {
        val assistant = assistants.firstOrNull { it.id == assistantId } ?: return
        if (assistant.model == null) {
            Toast.makeText(context, "请先为助手选择模型", Toast.LENGTH_SHORT).show()
            navController.navigate(Routes.ASSISTANTS)
            return
        }
        scope.launch {
            val conversation = graph.chatEngine.createConversation(assistant.id, temporary)
            if (temporary) temporaryConversations[conversation.id] = conversation
            navController.navigate("${Routes.CHAT}/${conversation.id}") {
                popUpTo(Routes.HOME)
            }
        }
    }

    fun startConversation(temporary: Boolean) {
        when {
            readyModels.isEmpty() -> navController.navigate(Routes.PROVIDERS)
            readyAssistants.isEmpty() -> navController.navigate(Routes.ASSISTANTS)
            readyAssistants.size == 1 -> openNewConversation(readyAssistants.first().id, temporary)
            else -> {
                newConversationTemporary = temporary
                choosingAssistant = true
            }
        }
    }

    fun deleteConversation(conversation: Conversation) {
        scope.launch {
            graph.repository.deleteConversation(conversation.id)
            if (navController.currentDestination?.route == "${Routes.CONVERSATION_ACTIONS}/{conversationId}") {
                navController.popBackStack()
            }
        }
    }

    CompositionLocalProvider(LocalWearBackAction provides { navController.popBackStack() }) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.HOME,
            userSwipeEnabled = DeviceCompatibility.supportsSwipeToDismiss
        ) {
            composable(Routes.HOME) {
                ConversationsScreen(
                    conversations = conversations.filterNot(Conversation::archived),
                    generating = generating,
                    onOpen = { navController.navigate("${Routes.CHAT}/${it.id}") },
                    onActions = { navController.navigate("${Routes.CONVERSATION_ACTIONS}/${it.id}") },
                    onDelete = ::deleteConversation,
                    onMenu = { navController.navigate(Routes.MENU) },
                    onNewConversation = { startConversation(false) },
                    newConversationHint = newConversationHint
                )
            }
            composable(Routes.MENU) {
                MainMenuScreen(
                    onNewConversation = ::startConversation,
                    newConversationHint = newConversationHint,
                    onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                    onOpenAssistants = { navController.navigate(Routes.ASSISTANTS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.ARCHIVE) {
                ConversationsScreen(
                    conversations = conversations.filter(Conversation::archived),
                    archived = true,
                    generating = generating,
                    onOpen = { navController.navigate("${Routes.CHAT}/${it.id}") },
                    onActions = {
                        navController.navigate("${Routes.CONVERSATION_ACTIONS}/${it.id}")
                    },
                    onDelete = ::deleteConversation
                )
            }
            composable("${Routes.CONVERSATION_ACTIONS}/{conversationId}") { entry ->
                val id = entry.arguments?.getString("conversationId")
                val conversation = conversations.firstOrNull { it.id == id }
                if (conversation == null) {
                    WearListScreen("会话不存在") {}
                } else {
                    ConversationActionsScreen(
                        conversation = conversation,
                        onPin = {
                            scope.launch {
                                graph.repository.upsertConversation(
                                    conversation.copy(pinned = !conversation.pinned)
                                )
                                navController.popBackStack()
                            }
                        },
                        onArchive = {
                            scope.launch {
                                graph.repository.upsertConversation(
                                    conversation.copy(archived = !conversation.archived)
                                )
                                navController.popBackStack()
                            }
                        },
                        exportMarkdown = { output ->
                            graph.conversationExporter.markdown(conversation, output)
                        },
                        exportHtml = { output ->
                            graph.conversationExporter.html(conversation, output)
                        },
                        onDelete = { deleteConversation(conversation) }
                    )
                }
            }
            composable("${Routes.CHAT}/{conversationId}") { entry ->
                val id = entry.arguments?.getString("conversationId")
                val conversation = conversations.firstOrNull { it.id == id }
                    ?: id?.let(temporaryConversations::get)
                if (conversation == null) {
                    WearListScreen("会话不存在") {}
                } else {
                    if (conversation.temporary) {
                        DisposableEffect(conversation.id) {
                            onDispose {
                                graph.chatEngine.discardTemporary(conversation.id)
                                temporaryConversations.remove(conversation.id)
                            }
                        }
                    }
                    val messageFlow = remember(graph.chatEngine, conversation.id) {
                        graph.chatEngine.observeMessages(conversation.id)
                    }
                    val messages by messageFlow
                        .collectAsState(initial = emptyList())
                    val openedAt = remember(conversation.id) { System.currentTimeMillis() }
                    var autoSpokenId by remember(conversation.id) { mutableStateOf<String?>(null) }
                    LaunchedEffect(messages.lastOrNull()?.id, preferences.autoSpeakReplies) {
                        val latest = messages.lastOrNull()
                        if (
                            preferences.autoSpeakReplies &&
                            latest?.role == MessageRole.ASSISTANT &&
                            latest.status == MessageStatus.COMPLETE &&
                            latest.updatedAt >= openedAt &&
                            latest.id != autoSpokenId &&
                            latest.content.isNotBlank()
                        ) {
                            autoSpokenId = latest.id
                            speech.speak(latest.content, speechConfig) { context.showError(it) }
                        }
                    }
                    ChatScreen(
                        conversation = conversation,
                        messages = messages,
                        quickPhrases = quickPhrases,
                        generating = conversation.id in generating,
                        showReasoning = preferences.showReasoning,
                        markdownEnabled = preferences.markdownEnabled,
                        readAttachment = graph.attachmentReader::read,
                        transcriptionConfig = transcriptionConfig,
                        startRecording = graph.transcriptionController::startRecording,
                        stopAndTranscribe = graph.transcriptionController::stopAndTranscribe,
                        cancelRecording = graph.transcriptionController::cancel,
                        onSend = { text, attachments ->
                            graph.chatEngine.send(conversation, text, attachments)
                        },
                        onStop = { graph.chatEngine.cancel(conversation.id) },
                        onMessageActions = { message ->
                            selectedMessage = message
                            navController.navigate("${Routes.MESSAGE_ACTIONS}/${conversation.id}")
                        }
                    )
                }
            }
            composable("${Routes.MESSAGE_ACTIONS}/{conversationId}") { entry ->
                val id = entry.arguments?.getString("conversationId")
                val conversation = conversations.firstOrNull { it.id == id }
                    ?: id?.let(temporaryConversations::get)
                val message = selectedMessage
                if (conversation == null || message == null) {
                    WearListScreen("消息不存在") {}
                } else {
                    MessageActionsScreen(
                        message = message,
                        showProvider = preferences.showProvider,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Wraive", message.content))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        onSpeak = {
                            speech.speak(message.content, speechConfig) { context.showError(it) }
                        },
                        onEdit = { content ->
                            scope.launch {
                                graph.chatEngine.editUserMessage(conversation, message, content)
                                navController.popBackStack()
                            }
                        },
                        onBranch = {
                            scope.launch {
                                val branched = graph.chatEngine.branchConversation(
                                    conversation,
                                    message.id
                                )
                                navController.navigate("${Routes.CHAT}/${branched.id}")
                            }
                        },
                        onRegenerate = {
                            graph.chatEngine.regenerate(conversation, message)
                            navController.popBackStack()
                        },
                        onShareImages = message.attachments
                            .filter { it.kind == AttachmentKind.IMAGE && it.uri.startsWith("file:") }
                            .takeIf(List<*>::isNotEmpty)
                            ?.let { attachments ->
                                {
                                    val uris = ArrayList(attachments.map { attachment ->
                                        val path = Uri.parse(attachment.uri).path
                                            ?: error("图片路径无效")
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.files",
                                            File(path)
                                        )
                                    })
                                    val intent = Intent(
                                        if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                                    ).apply {
                                        type = "image/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        if (uris.size == 1) {
                                            putExtra(Intent.EXTRA_STREAM, uris.first())
                                        } else {
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                        }
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享图片"))
                                }
                            },
                        onDelete = {
                            scope.launch {
                                graph.repository.deleteMessage(message.id)
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
            composable(Routes.PROVIDERS) {
                ProvidersScreen(
                    providers = providers,
                    models = models,
                    gson = graph.gson,
                    qrCodec = graph.providerQrCodec,
                    onCreate = { provider ->
                        scope.launch {
                            graph.repository.upsertProvider(provider)
                            navController.navigate("${Routes.PROVIDER_DETAILS}/${provider.id}")
                        }
                    },
                    onOpen = { provider ->
                        navController.navigate("${Routes.PROVIDER_DETAILS}/${provider.id}")
                    }
                )
            }
            composable("${Routes.PROVIDER_DETAILS}/{providerId}") { entry ->
                val provider = providers.firstOrNull { it.id == entry.arguments?.getString("providerId") }
                if (provider == null) {
                    WearListScreen("供应商不存在") {}
                } else {
                    ProviderDetailsScreen(
                        provider = provider,
                        models = models.filter { it.providerId == provider.id },
                        fetchingModels = fetchingModelsFor == provider.id,
                        gson = graph.gson,
                        qrCodec = graph.providerQrCodec,
                        onSave = { provider ->
                            scope.launch { graph.repository.upsertProvider(provider) }
                        },
                        onDelete = {
                            scope.launch {
                                graph.repository.deleteProvider(provider.id)
                                navController.popBackStack()
                            }
                        },
                        onFetchModels = {
                            fetchingModelsFor = provider.id
                            scope.launch {
                                try {
                                    runCatching { graph.gateway.listModels(provider) }
                                        .onSuccess {
                                            graph.repository.replaceProviderModels(provider.id, it)
                                            Toast.makeText(
                                                context,
                                                "已获取 ${it.size} 个模型",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .onFailure { context.showError(it) }
                                } finally {
                                    fetchingModelsFor = null
                                }
                            }
                        },
                        onSaveModel = { model ->
                            val completesSetup = models.none { it.enabled }
                            scope.launch {
                                graph.repository.upsertModel(model)
                                if (completesSetup) {
                                    autoOpenAssistantSetup = true
                                    navController.navigate(Routes.ASSISTANTS)
                                }
                            }
                        },
                        onDeleteModel = { model ->
                            scope.launch { graph.repository.deleteModel(model) }
                        }
                    )
                }
            }
            composable(Routes.ASSISTANTS) {
                AssistantsScreen(
                    assistants = assistants,
                    providers = providers,
                    models = models,
                    searchProviders = searchProviders,
                    mcpServers = mcpServers,
                    worldBookEntries = worldBooks,
                    gson = graph.gson,
                    onSave = { assistant ->
                        val completesSetup = assistants.none { it.model != null }
                        scope.launch {
                            graph.repository.upsertAssistant(assistant)
                            if (completesSetup && assistant.model != null) {
                                navController.popBackStack(Routes.HOME, false)
                            }
                        }
                    },
                    onDelete = { scope.launch { graph.repository.deleteAssistant(it.id) } },
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                    autoOpenSetup = autoOpenAssistantSetup,
                    onAutoOpenSetupHandled = { autoOpenAssistantSetup = false }
                )
            }
            (listOf<SettingsSection?>(null) + SettingsSection.entries).forEach { section ->
                val route = if (section == null) Routes.SETTINGS else "${Routes.SETTINGS}/${section.name}"
                composable(route) {
                    SettingsScreen(
                        section = section,
                        onSection = { navController.navigate("${Routes.SETTINGS}/${it.name}") },
                        onAbout = { navController.navigate(Routes.ABOUT) },
                        onProviders = { navController.navigate(Routes.PROVIDERS) },
                        onAssistants = { navController.navigate(Routes.ASSISTANTS) },
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onMcp = { navController.navigate(Routes.MCP) },
                        onMemory = { navController.navigate(Routes.MEMORY) },
                        onWorldBook = { navController.navigate(Routes.WORLD_BOOK) },
                        onQuickPhrases = { navController.navigate(Routes.QUICK_PHRASES) },
                        onPromptTransforms = { navController.navigate(Routes.PROMPT_TRANSFORMS) },
                        onTranslate = { navController.navigate(Routes.TRANSLATE) },
                        onVoice = { navController.navigate(Routes.VOICE) },
                        onTranscription = { navController.navigate(Routes.TRANSCRIPTION) },
                        onGlobalSearch = { navController.navigate(Routes.GLOBAL_SEARCH) },
                        onStats = { navController.navigate(Routes.STATS) },
                        onNetworkProxy = { navController.navigate(Routes.NETWORK_PROXY) },
                        onBackup = { navController.navigate(Routes.BACKUP) },
                        onCloudBackup = { navController.navigate(Routes.CLOUD_BACKUP) },
                        onAppearance = { navController.navigate(Routes.APPEARANCE) }
                    )
                }
            }
            composable(Routes.ABOUT) { AboutScreen() }
            composable(Routes.SEARCH) {
                SearchProvidersScreen(
                    providers = searchProviders,
                    gson = graph.gson,
                    onSave = { scope.launch { graph.repository.upsertSearchProvider(it) } },
                    onDelete = { scope.launch { graph.repository.deleteSearchProvider(it.id) } }
                )
            }
            composable(Routes.MCP) {
                McpServersScreen(
                    servers = mcpServers,
                    gson = graph.gson,
                    onSave = { scope.launch { graph.repository.upsertMcpServer(it) } },
                    onDelete = { scope.launch { graph.repository.deleteMcpServer(it.id) } }
                )
            }
            composable(Routes.MEMORY) {
                MemoriesScreen(
                    entries = memories,
                    assistants = assistants,
                    onSave = { scope.launch { graph.repository.upsertMemory(it) } },
                    onDelete = { scope.launch { graph.repository.deleteMemory(it.id) } }
                )
            }
            composable(Routes.WORLD_BOOK) {
                WorldBookScreen(
                    entries = worldBooks,
                    onSave = { scope.launch { graph.repository.upsertWorldBookEntry(it) } },
                    onDelete = { scope.launch { graph.repository.deleteWorldBookEntry(it.id) } }
                )
            }
            composable(Routes.QUICK_PHRASES) {
                QuickPhrasesScreen(
                    phrases = quickPhrases,
                    assistants = assistants,
                    onSave = { scope.launch { graph.repository.upsertQuickPhrase(it) } },
                    onDelete = { scope.launch { graph.repository.deleteQuickPhrase(it.id) } }
                )
            }
            composable(Routes.PROMPT_TRANSFORMS) {
                PromptTransformsScreen(
                    transforms = promptTransforms,
                    onSave = { scope.launch { graph.repository.upsertPromptTransform(it) } },
                    onDelete = { scope.launch { graph.repository.deletePromptTransform(it.id) } }
                )
            }
            composable(Routes.TRANSLATE) {
                TranslationScreen(
                    assistants = assistants,
                    translate = graph.translationService::translate
                )
            }
            composable(Routes.VOICE) {
                VoiceSettingsScreen(
                    config = speechConfig,
                    onSave = { scope.launch { graph.repository.upsertSpeechConfig(it) } },
                    onTest = {
                        speech.speak("你好，我是 Wraive。", it) { error -> context.showError(error) }
                    }
                )
            }
            composable(Routes.TRANSCRIPTION) {
                TranscriptionSettingsScreen(
                    config = transcriptionConfig,
                    onSave = { scope.launch { graph.repository.upsertTranscriptionConfig(it) } }
                )
            }
            composable(Routes.GLOBAL_SEARCH) {
                GlobalSearchScreen(
                    search = graph.repository::searchMessages,
                    onOpen = { hit ->
                        navController.navigate("${Routes.CHAT}/${hit.conversation.id}")
                    }
                )
            }
            composable(Routes.STATS) {
                UsageStatsScreen(
                    refreshKey = requestLogs.size,
                    loadSummary = graph.repository::usageSummary,
                    onLogs = { navController.navigate(Routes.REQUEST_LOGS) }
                )
            }
            composable(Routes.REQUEST_LOGS) {
                RequestLogsScreen(
                    logs = requestLogs,
                    onClear = { scope.launch { graph.repository.clearRequestLogs() } }
                )
            }
            composable(Routes.NETWORK_PROXY) {
                NetworkProxyScreen(
                    config = networkProxy,
                    onSave = { scope.launch { graph.repository.upsertNetworkProxyConfig(it) } }
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(graph.backupService, graph.foreignImportService)
            }
            composable(Routes.CLOUD_BACKUP) {
                CloudBackupScreen(
                    config = cloudBackup,
                    onSave = { scope.launch { graph.repository.upsertCloudBackupConfig(it) } },
                    upload = graph.cloudBackupService::upload,
                    restore = graph.cloudBackupService::restore
                )
            }
            composable(Routes.APPEARANCE) {
                AppearanceScreen(preferences, graph.preferences::update)
            }
        }
        readyAssistants.firstOrNull()?.let { firstAssistant ->
            WearSelectionDialog(
                visible = choosingAssistant,
                title = "选择助手",
                selected = firstAssistant,
                options = readyAssistants,
                onDismiss = { choosingAssistant = false },
                onSelect = { openNewConversation(it.id, newConversationTemporary) },
                optionLabel = AssistantProfile::name
            )
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val MENU = "menu"
    const val CHAT = "chat"
    const val ARCHIVE = "archive"
    const val CONVERSATION_ACTIONS = "conversation-actions"
    const val MESSAGE_ACTIONS = "message-actions"
    const val PROVIDERS = "providers"
    const val PROVIDER_DETAILS = "provider-details"
    const val ASSISTANTS = "assistants"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val SEARCH = "search"
    const val MCP = "mcp"
    const val MEMORY = "memory"
    const val WORLD_BOOK = "world-book"
    const val QUICK_PHRASES = "quick-phrases"
    const val PROMPT_TRANSFORMS = "prompt-transforms"
    const val TRANSLATE = "translate"
    const val VOICE = "voice"
    const val TRANSCRIPTION = "transcription"
    const val GLOBAL_SEARCH = "global-search"
    const val STATS = "stats"
    const val REQUEST_LOGS = "request-logs"
    const val NETWORK_PROXY = "network-proxy"
    const val BACKUP = "backup"
    const val CLOUD_BACKUP = "cloud-backup"
    const val APPEARANCE = "appearance"
}

private fun Context.showError(error: Throwable) {
    Toast.makeText(this, error.message ?: error::class.java.simpleName, Toast.LENGTH_LONG).show()
}
