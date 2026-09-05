package moye.wear.wraive.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.McpServerConfig
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ModelCapability
import moye.wear.wraive.model.ModelRef
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.SearchProviderConfig
import moye.wear.wraive.model.WorldBookEntry
import moye.wear.wraive.ui.components.ConfirmActionDialog
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.FullScreenEditor
import moye.wear.wraive.ui.components.WearSelectionField
import moye.wear.wraive.ui.components.WearSelectionButton
import moye.wear.wraive.ui.components.WearCheckboxButton
import moye.wear.wraive.ui.components.WearTextField
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.tr
import java.util.UUID

@Composable
fun AssistantsScreen(
    assistants: List<AssistantProfile>,
    providers: List<ProviderConfig>,
    models: List<ModelConfig>,
    searchProviders: List<SearchProviderConfig>,
    mcpServers: List<McpServerConfig>,
    worldBookEntries: List<WorldBookEntry>,
    gson: Gson,
    onSave: (AssistantProfile) -> Unit,
    onDelete: (AssistantProfile) -> Unit,
    onOpenProviders: () -> Unit,
    autoOpenSetup: Boolean,
    onAutoOpenSetupHandled: () -> Unit
) {
    var editing by remember { mutableStateOf<AssistantProfile?>(null) }
    var showNew by remember { mutableStateOf(false) }
    LaunchedEffect(autoOpenSetup, assistants, providers, models) {
        if (!autoOpenSetup) return@LaunchedEffect
        val enabledProviderIds = providers
            .filter(ProviderConfig::enabled)
            .mapTo(mutableSetOf(), ProviderConfig::id)
        if (models.none { it.enabled && it.providerId in enabledProviderIds }) {
            return@LaunchedEffect
        }
        editing = assistants.firstOrNull { it.model == null }
        showNew = editing == null
        onAutoOpenSetupHandled()
    }
    WearListScreen("助手") {
        item {
            WearActionButton(
                "创建助手",
                { showNew = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        assistants.forEach { assistant ->
            item(key = assistant.id) {
                val model = assistant.model?.let { ref ->
                    models.firstOrNull { it.providerId == ref.providerId && it.id == ref.modelId }
                }
                WearActionButton(
                    label = assistant.name,
                    secondary = model?.displayName ?: "尚未选择模型",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { editing = assistant }
                )
            }
        }
    }
    AssistantEditorDialog(
        visible = showNew || editing != null,
        original = editing,
        providers = providers,
        models = models,
        searchProviders = searchProviders,
        mcpServers = mcpServers,
        worldBookEntries = worldBookEntries,
        gson = gson,
        onDismiss = {
            showNew = false
            editing = null
        },
        onSave = {
            onSave(it)
            showNew = false
            editing = null
        },
        onDelete = editing?.let { assistant ->
            {
                onDelete(assistant)
                editing = null
            }
        },
        onOpenProviders = {
            showNew = false
            editing = null
            onOpenProviders()
        }
    )
}

@Composable
private fun AssistantEditorDialog(
    visible: Boolean,
    original: AssistantProfile?,
    providers: List<ProviderConfig>,
    models: List<ModelConfig>,
    searchProviders: List<SearchProviderConfig>,
    mcpServers: List<McpServerConfig>,
    worldBookEntries: List<WorldBookEntry>,
    gson: Gson,
    onDismiss: () -> Unit,
    onSave: (AssistantProfile) -> Unit,
    onDelete: (() -> Unit)?,
    onOpenProviders: () -> Unit
) {
    if (!visible) return
    val readyProviders = providers.filter(ProviderConfig::enabled)
    val readyProviderIds = readyProviders.mapTo(mutableSetOf(), ProviderConfig::id)
    val readyModels = models.filter { it.enabled && it.providerId in readyProviderIds }
    val suggestedModel = readyModels.singleOrNull()?.let {
        ModelRef(providerId = it.providerId, modelId = it.id)
    }
    var name by remember(original?.id, visible) { mutableStateOf(original?.name.orEmpty()) }
    var description by remember(original?.id, visible) {
        mutableStateOf(original?.description.orEmpty())
    }
    var systemPrompt by remember(original?.id, visible) {
        mutableStateOf(original?.systemPrompt.orEmpty())
    }
    var model by remember(original?.id, visible, suggestedModel) {
        mutableStateOf(original?.model ?: suggestedModel)
    }
    var temperature by remember(original?.id, visible) {
        mutableStateOf((original?.temperature ?: 0.7).toString())
    }
    var reasoning by remember(original?.id, visible) {
        mutableStateOf(original?.reasoningEnabled ?: false)
    }
    var webSearch by remember(original?.id, visible) {
        mutableStateOf(original?.webSearchEnabled ?: false)
    }
    var memory by remember(original?.id, visible) {
        mutableStateOf(original?.memoryEnabled ?: true)
    }
    var autoMemory by remember(original?.id, visible) {
        mutableStateOf(original?.autoMemoryExtraction ?: false)
    }
    var searchProviderId by remember(original?.id, visible) {
        mutableStateOf(original?.searchProviderId)
    }
    var mcpIds by remember(original?.id, visible) {
        mutableStateOf(original?.mcpServerIds ?: emptySet())
    }
    var bookIds by remember(original?.id, visible) {
        mutableStateOf(original?.worldBookIds ?: emptySet())
    }
    var imageSize by remember(original?.id, visible) {
        mutableStateOf(original?.imageSize ?: "1024x1024")
    }
    var imageQuality by remember(original?.id, visible) {
        mutableStateOf(original?.imageQuality ?: "auto")
    }
    var imageCount by remember(original?.id, visible) {
        mutableStateOf((original?.imageCount ?: 1).toString())
    }
    var headers by remember(original?.id, visible) {
        mutableStateOf(gson.toJson(original?.customHeaders ?: emptyMap<String, String>()))
    }
    var customBody by remember(original?.id, visible) {
        mutableStateOf(gson.toJson(original?.customBody ?: emptyMap<String, Any?>()))
    }
    var showAdvanced by remember(original?.id, visible) { mutableStateOf(false) }
    var error by remember(original?.id, visible) { mutableStateOf<String?>(null) }
    var confirmingDelete by remember(original?.id, visible) { mutableStateOf(false) }
    val selectedModel = model?.let { selected ->
        readyModels.firstOrNull {
            it.providerId == selected.providerId && it.id == selected.modelId
        }
    }
    val modelIsReady = selectedModel != null
    FullScreenEditor(
        visible = visible,
        title = original?.name ?: "创建助手",
        onDismiss = onDismiss,
        saveEnabled = modelIsReady,
        onSave = {
            runCatching {
                val headerType = object : TypeToken<Map<String, String>>() {}.type
                val bodyType = object : TypeToken<Map<String, Any?>>() {}.type
                onSave(AssistantProfile(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { "新助手" },
                    description = description.trim(),
                    avatarUri = original?.avatarUri,
                    systemPrompt = systemPrompt.trim(),
                    model = model,
                    temperature = temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0)
                        ?: 0.7,
                    topP = original?.topP ?: 1.0,
                    maxOutputTokens = original?.maxOutputTokens,
                    reasoningEnabled = reasoning,
                    reasoningBudget = original?.reasoningBudget,
                    webSearchEnabled = webSearch,
                    searchProviderId = searchProviderId,
                    memoryEnabled = memory,
                    autoMemoryExtraction = autoMemory,
                    worldBookIds = bookIds,
                    mcpServerIds = mcpIds,
                    customHeaders = gson.fromJson(headers, headerType),
                    customBody = gson.fromJson(customBody, bodyType),
                    imageSize = imageSize,
                    imageQuality = imageQuality,
                    imageCount = imageCount.toIntOrNull()?.coerceIn(1, 4) ?: 1,
                    sortOrder = original?.sortOrder ?: 0
                ))
            }.onFailure { error = it.message }
        }
    ) {
        item {
            Text(
                tr("选择模型"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (readyModels.isEmpty()) {
            item {
                Text(
                    tr("还没有可用模型"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                WearActionButton(
                    label = "先添加模型",
                    secondary = "连接供应商后添加或拉取模型",
                    icon = Icons.Default.CloudOff,
                    primary = true,
                    onClick = onOpenProviders
                )
            }
        } else {
            item {
                WearSelectionField(
                    label = "选择模型",
                    value = model,
                    options = readyModels.map { ModelRef(it.providerId, it.id) },
                    onSelect = { model = it },
                    optionLabel = { ref ->
                        val candidate = readyModels.firstOrNull { it.providerId == ref?.providerId && it.id == ref.modelId }
                        val provider = readyProviders.firstOrNull { it.id == ref?.providerId }
                        candidate?.let { "${it.displayName} · ${provider?.name.orEmpty()}" } ?: "尚未选择模型"
                    }
                )
            }
            if (!modelIsReady) {
                item {
                    Text(
                        tr("选择一个模型后即可保存"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        item {
            WearActionButton(
                label = if (showAdvanced) {
                    "收起个性与高级设置"
                } else {
                    "个性与高级设置"
                },
                secondary = "名称、提示词、工具与生成参数",
                icon = Icons.Default.Tune,
                onClick = { showAdvanced = !showAdvanced }
            )
        }
        if (showAdvanced) {
            item { WearTextField(name, { name = it }, "名称") }
            item { WearTextField(description, { description = it }, "描述", 3) }
            item {
                WearTextField(
                    systemPrompt,
                    { systemPrompt = it },
                    "系统提示词",
                    8
                )
            }
            item {
                WearTextField(
                    temperature,
                    { temperature = it },
                    "Temperature"
                )
            }
            if (
                selectedModel?.capabilities
                    ?.contains(ModelCapability.IMAGE_GENERATION) == true
            ) {
                item {
                    Text(
                        tr("图片生成"),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    WearSelectionField("图片尺寸", imageSize,
                        listOf("1024x1024", "1024x1536", "1536x1024", "auto"),
                        onSelect = { imageSize = it })
                }
                item {
                    WearSelectionField("图片质量", imageQuality,
                        listOf("auto", "low", "medium", "high"),
                        onSelect = { imageQuality = it })
                }
                item {
                    WearTextField(imageCount, { imageCount = it }, "图片数量 1–4")
                }
            }
            item {
                WearTextField(headers, { headers = it }, "自定义请求头 JSON", 5)
            }
            item {
                WearTextField(
                    customBody,
                    { customBody = it },
                    "自定义请求体 JSON",
                    6
                )
            }
            error?.let {
                item { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                SwitchButton(
                    checked = reasoning,
                    onCheckedChange = { reasoning = it },
                    label = { Text(tr("推理 / 思考")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (memory) {
                item {
                    SwitchButton(
                        checked = autoMemory,
                        onCheckedChange = { autoMemory = it },
                        label = { Text(tr("自动提炼记忆")) },
                        secondaryLabel = {
                            Text(tr("从对话中保存稳定偏好与事实"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                SwitchButton(
                    checked = webSearch,
                    onCheckedChange = { webSearch = it },
                    label = { Text(tr("联网搜索")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (webSearch) {
                searchProviders.filter(SearchProviderConfig::enabled)
                    .forEach { search ->
                        item(key = search.id) {
                            WearSelectionButton(
                                label = search.name,
                                selected = searchProviderId == search.id,
                                onClick = { searchProviderId = search.id }
                            )
                        }
                    }
            }
            item {
                SwitchButton(
                    checked = memory,
                    onCheckedChange = { memory = it },
                    label = { Text(tr("长期记忆")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (mcpServers.isNotEmpty()) {
                item {
                    Text(
                        "MCP",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                mcpServers.forEach { server ->
                    item(key = server.id) {
                        WearCheckboxButton(
                            label = server.name,
                            checked = server.id in mcpIds,
                            onCheckedChange = {
                                mcpIds = if (server.id in mcpIds) {
                                    mcpIds - server.id
                                } else {
                                    mcpIds + server.id
                                }
                            }
                        )
                    }
                }
            }
            val distinctBooks = worldBookEntries.distinctBy(
                WorldBookEntry::bookId
            )
            if (distinctBooks.isNotEmpty()) {
                item {
                    Text(
                        tr("世界书"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                distinctBooks.forEach { entry ->
                    item(key = entry.bookId) {
                        WearCheckboxButton(
                            label = entry.bookId,
                            checked = entry.bookId in bookIds,
                            onCheckedChange = {
                                bookIds = if (entry.bookId in bookIds) {
                                    bookIds - entry.bookId
                                } else {
                                    bookIds + entry.bookId
                                }
                            }
                        )
                    }
                }
            }
            onDelete?.let {
                item {
                    WearActionButton(
                        "删除助手",
                        { confirmingDelete = true },
                        icon = Icons.Default.Delete,
                        danger = true
                    )
                }
            }
        }
    }
    ConfirmActionDialog(
        visible = confirmingDelete,
        title = "删除助手？",
        message = "删除后，这个助手将无法恢复。",
        confirmLabel = "确认删除",
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            onDelete?.invoke()
        }
    )
}
