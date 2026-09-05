package moye.wear.wraive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import moye.wear.wraive.data.ProviderQrCodec
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ModelCapability
import moye.wear.wraive.model.KeyRotationStrategy
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import moye.wear.wraive.model.defaultBaseUrl
import moye.wear.wraive.model.availableApiKeys
import moye.wear.wraive.ui.components.ConfirmActionDialog
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.FullScreenEditor
import moye.wear.wraive.ui.components.WearSelectionField
import moye.wear.wraive.ui.components.WearCheckboxButton
import moye.wear.wraive.ui.components.WearTextField
import moye.wear.wraive.ui.components.WearInfoCard
import moye.wear.wraive.ui.components.WearSectionHeader
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.tr

@Composable
fun ProvidersScreen(
    providers: List<ProviderConfig>,
    models: List<ModelConfig>,
    gson: Gson,
    qrCodec: ProviderQrCodec,
    onCreate: (ProviderConfig) -> Unit,
    onOpen: (ProviderConfig) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    WearListScreen("模型供应商") {
        item {
            WearActionButton("添加供应商", { showNew = true },
                icon = Icons.Default.Add, primary = true)
        }
        item {
            WearActionButton("导入分享配置", { showImport = true }, icon = Icons.Default.Download)
        }
        providers.forEach { provider ->
            item(key = provider.id) {
                WearActionButton(
                    label = provider.name,
                    secondary = "${provider.protocol.displayName()} · " +
                        "${models.count { it.providerId == provider.id }} 个模型",
                    icon = Icons.Default.Cloud,
                    onClick = { onOpen(provider) }
                )
            }
        }
    }
    ProviderEditorDialog(
        visible = showNew,
        original = null,
        gson = gson,
        onDismiss = { showNew = false },
        onSave = {
            showNew = false
            onCreate(it)
        }
    )
    ImportProviderDialog(
        visible = showImport,
        qrCodec = qrCodec,
        onDismiss = { showImport = false },
        onImport = {
            showImport = false
            onCreate(it.copy(id = java.util.UUID.randomUUID().toString()))
        }
    )
}

@Composable
fun ProviderDetailsScreen(
    provider: ProviderConfig,
    models: List<ModelConfig>,
    fetchingModels: Boolean,
    gson: Gson,
    qrCodec: ProviderQrCodec,
    onSave: (ProviderConfig) -> Unit,
    onDelete: () -> Unit,
    onFetchModels: () -> Unit,
    onSaveModel: (ModelConfig) -> Unit,
    onDeleteModel: (ModelConfig) -> Unit
) {
    var editing by remember(provider.id) { mutableStateOf(false) }
    var sharing by remember(provider.id) { mutableStateOf(false) }
    var confirmingDelete by remember(provider.id) { mutableStateOf(false) }
    var addingModel by remember(provider.id) { mutableStateOf(false) }
    var editingModel by remember(provider.id) { mutableStateOf<ModelConfig?>(null) }
    WearListScreen(provider.name) {
        item(key = "configuration") {
            WearActionButton("供应商配置", { editing = true }, icon = Icons.Default.Tune)
        }
        item(key = "models-title") { WearSectionHeader("模型列表") }
        item(key = "fetch-models") {
            WearActionButton(
                label = if (fetchingModels) "正在获取模型…" else "拉取模型列表",
                onClick = onFetchModels,
                icon = Icons.Default.Download,
                enabled = !fetchingModels
            )
        }
        item(key = "add-model") {
            WearActionButton("添加模型", { addingModel = true }, icon = Icons.Default.Add)
        }
        if (models.isEmpty()) {
            item(key = "empty-models") {
                WearInfoCard("还没有模型", "拉取模型列表或手动添加模型。")
            }
        }
        models.forEach { model ->
            item(key = "model:${model.id}") {
                WearActionButton(model.displayName, { editingModel = model }, secondary = model.id)
            }
        }
        item(key = "management-title") { WearSectionHeader("管理") }
        item(key = "share") {
            WearActionButton("二维码分享", { sharing = true }, icon = Icons.Default.QrCode)
        }
        item(key = "delete") {
            WearActionButton("删除供应商", { confirmingDelete = true },
                icon = Icons.Default.Delete, danger = true)
        }
    }
    ProviderEditorDialog(
        visible = editing,
        original = provider,
        gson = gson,
        onDismiss = { editing = false },
        onSave = {
            onSave(it)
            editing = false
        }
    )
    if (sharing) ProviderQrDialog(provider, qrCodec) { sharing = false }
    ConfirmActionDialog(
        visible = confirmingDelete,
        title = "删除供应商？",
        message = "删除后，这个供应商将无法恢复。",
        confirmLabel = "确认删除",
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            onDelete()
        }
    )
    ModelEditorDialog(
        provider = provider.takeIf { addingModel || editingModel != null },
        original = editingModel,
        onDismiss = {
            addingModel = false
            editingModel = null
        },
        onSave = {
            onSaveModel(it)
            addingModel = false
            editingModel = null
        },
        onDelete = editingModel?.let { model ->
            {
                onDeleteModel(model)
                editingModel = null
            }
        }
    )
}

@Composable
private fun ProviderEditorDialog(
    visible: Boolean,
    original: ProviderConfig?,
    gson: Gson,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    if (!visible) return
    var protocol by remember(original?.id, visible) {
        mutableStateOf(original?.protocol ?: ProviderProtocol.OPENAI_CHAT)
    }
    var name by remember(original?.id, visible) { mutableStateOf(original?.name.orEmpty()) }
    var baseUrl by remember(original?.id, visible) {
        mutableStateOf(original?.baseUrl ?: protocol.defaultBaseUrl())
    }
    var apiKeys by remember(original?.id, visible) {
        mutableStateOf(original?.availableApiKeys()?.joinToString("\n").orEmpty())
    }
    var group by remember(original?.id, visible) { mutableStateOf(original?.group.orEmpty()) }
    var keyRotation by remember(original?.id, visible) {
        mutableStateOf(original?.keyRotation ?: KeyRotationStrategy.ROUND_ROBIN)
    }
    var headers by remember(original?.id, visible) {
        mutableStateOf(gson.toJson(original?.customHeaders ?: emptyMap<String, String>()))
    }
    var body by remember(original?.id, visible) {
        mutableStateOf(gson.toJson(original?.customBody ?: emptyMap<String, Any?>()))
    }
    var showAdvanced by remember(original?.id, visible) { mutableStateOf(false) }
    var error by remember(original?.id, visible) { mutableStateOf<String?>(null) }
    FullScreenEditor(
        visible = visible,
        title = original?.name ?: "添加供应商",
        onDismiss = onDismiss,
        saveEnabled = baseUrl.isNotBlank(),
        onSave = {
            runCatching {
                val parsedKeys = apiKeys.lineSequence().map(String::trim)
                    .filter(String::isNotBlank).distinct().toList()
                val headerType = object : TypeToken<Map<String, String>>() {}.type
                val bodyType = object : TypeToken<Map<String, Any?>>() {}.type
                onSave(
                    ProviderConfig(
                        id = original?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { protocol.displayName() },
                        protocol = protocol,
                        baseUrl = baseUrl.trim().trimEnd('/'),
                        apiKey = parsedKeys.firstOrNull().orEmpty(),
                        apiKeys = parsedKeys.drop(1),
                        keyRotation = keyRotation,
                        group = group.trim(),
                        enabled = original?.enabled ?: true,
                        customHeaders = gson.fromJson(headers, headerType),
                        customBody = gson.fromJson(body, bodyType),
                        usePromptCaching = original?.usePromptCaching ?: false,
                        sortOrder = original?.sortOrder ?: 0
                    )
                )
            }.onFailure { error = it.message }
        }
    ) {
        item {
            Text(
                tr("基础设置"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            WearSelectionField("接口类型", protocol, ProviderProtocol.entries,
                onSelect = { selected ->
                    val oldDefault = protocol.defaultBaseUrl()
                    protocol = selected
                    if (baseUrl == oldDefault) baseUrl = selected.defaultBaseUrl()
                }, optionLabel = { it.displayName() })
        }
        item {
            WearTextField(
                apiKeys,
                { apiKeys = it },
                "API Keys（每行一个）",
                maxLines = 6,
                password = true
            )
        }
        item { WearTextField(baseUrl, { baseUrl = it }, "API Base URL") }
        item {
            WearActionButton(
                label = if (showAdvanced) "收起高级设置" else "高级设置",
                secondary = if (showAdvanced) {
                    "名称、分组、密钥策略与 JSON"
                } else {
                    "名称、分组与自定义参数"
                },
                icon = Icons.Default.Tune,
                onClick = { showAdvanced = !showAdvanced }
            )
        }
        if (showAdvanced) {
            item { WearTextField(name, { name = it }, "名称") }
            item { WearTextField(group, { group = it }, "分组") }
            item {
                WearSelectionField("密钥策略", keyRotation, KeyRotationStrategy.entries,
                    onSelect = { keyRotation = it }, optionLabel = {
                        if (it == KeyRotationStrategy.ROUND_ROBIN) "密钥轮询" else "随机密钥"
                    })
            }
            item {
                WearTextField(headers, { headers = it }, "自定义请求头 JSON", 4)
            }
            item {
                WearTextField(body, { body = it }, "自定义请求体 JSON", 5)
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}


@Composable
private fun ProviderQrDialog(
    provider: ProviderConfig,
    codec: ProviderQrCodec,
    onDismiss: () -> Unit
) {
    val bitmap = remember(provider) { codec.bitmap(codec.encode(provider)) }
    Dialog(visible = true, onDismissRequest = onDismiss) {
        WearListScreen(provider.name, edgeButton = {
            EdgeButton(onClick = onDismiss) { Text(tr("完成")) }
        }) {
            item { Image(bitmap.asImageBitmap(), contentDescription = tr("供应商配置二维码"),
                modifier = Modifier.fillMaxWidth().padding(8.dp)) }
        }
    }
}

@Composable
private fun ImportProviderDialog(
    visible: Boolean,
    qrCodec: ProviderQrCodec,
    onDismiss: () -> Unit,
    onImport: (ProviderConfig) -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) runCatching { qrCodec.decode(bitmap) }
            .onSuccess(onImport)
            .onFailure { error = it.message }
    }
    val image = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: error("无法读取二维码图片")
            qrCodec.decode(bitmap)
        }.onSuccess(onImport).onFailure { error = it.message }
    }
    Dialog(visible = visible, onDismissRequest = onDismiss) {
        WearListScreen("导入分享配置", edgeButton = {
            EdgeButton(onClick = {
                runCatching { qrCodec.decode(text.trim()) }
                    .onSuccess(onImport)
                    .onFailure { error = it.message }
            }, enabled = text.isNotBlank()) { Text(tr("导入")) }
        }) {
            item { WearTextField(text, { text = it }, "粘贴 Wraive 分享文本", 6) }
            item { WearActionButton("相机扫码", { camera.launch(null) }, icon = Icons.Default.QrCode) }
            item { WearActionButton("从图片读取", { image.launch("image/*") }, icon = Icons.Default.Download) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

private fun ProviderProtocol.displayName(): String = when (this) {
    ProviderProtocol.OPENAI_CHAT -> "OpenAI Chat Completions"
    ProviderProtocol.OPENAI_RESPONSES -> "OpenAI Responses"
    ProviderProtocol.ANTHROPIC -> "Anthropic Messages"
    ProviderProtocol.GOOGLE -> "Google Gemini"
}

@Composable
private fun ModelEditorDialog(
    provider: ProviderConfig?,
    original: ModelConfig?,
    onDismiss: () -> Unit,
    onSave: (ModelConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    if (provider == null) return
    var id by remember(original?.id, provider.id) { mutableStateOf(original?.id.orEmpty()) }
    var displayName by remember(original?.id, provider.id) {
        mutableStateOf(original?.displayName.orEmpty())
    }
    var contextTokens by remember(original?.id, provider.id) {
        mutableStateOf((original?.maxContextTokens ?: 128_000).toString())
    }
    var outputTokens by remember(original?.id, provider.id) {
        mutableStateOf((original?.maxOutputTokens ?: 8_192).toString())
    }
    var capabilities by remember(original?.id, provider.id) {
        mutableStateOf(original?.capabilities ?: setOf(ModelCapability.TEXT))
    }
    var showAdvanced by remember(original?.id, provider.id) { mutableStateOf(false) }
    var confirmingDelete by remember(original?.id, provider.id) { mutableStateOf(false) }
    FullScreenEditor(
        visible = true,
        title = original?.displayName ?: "添加模型",
        onDismiss = onDismiss,
        saveEnabled = id.isNotBlank(),
        onSave = {
            onSave(
                ModelConfig(
                    id = id.trim(),
                    providerId = provider.id,
                    displayName = displayName.trim().ifBlank { id.trim() },
                    capabilities = capabilities + ModelCapability.TEXT,
                    maxContextTokens = contextTokens.toIntOrNull() ?: 128_000,
                    maxOutputTokens = outputTokens.toIntOrNull() ?: 8_192,
                    sortOrder = original?.sortOrder ?: 0
                )
            )
        }
    ) {
        item {
            WearTextField(
                id,
                { id = it },
                "模型 ID"
            )
        }
        item {
            WearTextField(
                displayName,
                { displayName = it },
                "显示名称"
            )
        }
        item {
            WearActionButton(
                label = if (showAdvanced) "收起高级设置" else "高级设置",
                secondary = "Token 限制与模型能力",
                icon = Icons.Default.Tune,
                onClick = { showAdvanced = !showAdvanced }
            )
        }
        if (showAdvanced) {
            item {
                WearTextField(
                    contextTokens,
                    { contextTokens = it },
                    "上下文 Token"
                )
            }
            item {
                WearTextField(
                    outputTokens,
                    { outputTokens = it },
                    "最大输出 Token"
                )
            }
            ModelCapability.entries.filterNot { it == ModelCapability.TEXT }.forEach { capability ->
                item(key = capability.name) {
                    WearCheckboxButton(
                        label = capability.name.lowercase().replace('_', ' '),
                        checked = capability in capabilities,
                        onCheckedChange = {
                            capabilities = if (capability in capabilities) {
                                capabilities - capability
                            } else {
                                capabilities + capability
                            }
                        }
                    )
                }
            }
        }
        onDelete?.let {
            item {
                WearActionButton(
                    "删除模型",
                    { confirmingDelete = true },
                    icon = Icons.Default.Delete,
                    danger = true
                )
            }
        }
    }
    ConfirmActionDialog(
        visible = confirmingDelete,
        title = "删除模型？",
        message = "删除后，这个模型配置将无法恢复。",
        confirmLabel = "确认删除",
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            onDelete?.invoke()
        }
    )
}
