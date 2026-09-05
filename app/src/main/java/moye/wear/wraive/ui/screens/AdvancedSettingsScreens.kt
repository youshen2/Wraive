package moye.wear.wraive.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import moye.wear.wraive.model.MessageSearchHit
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.CloudBackupConfig
import moye.wear.wraive.model.PromptTransform
import moye.wear.wraive.model.NetworkProxyConfig
import moye.wear.wraive.model.ProxyType
import moye.wear.wraive.model.RequestLog
import moye.wear.wraive.model.RequestStatus
import moye.wear.wraive.model.SpeechConfig
import moye.wear.wraive.model.SpeechEngine
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.TranscriptionEngine
import moye.wear.wraive.model.UsageSummary
import moye.wear.wraive.ui.components.FullScreenEditor
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearSelectionField
import moye.wear.wraive.ui.components.WearSelectionButton
import moye.wear.wraive.ui.components.WearTextField
import moye.wear.wraive.ui.tr
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
fun TranslationScreen(
    assistants: List<AssistantProfile>,
    translate: suspend (String, String, String?) -> String
) {
    var source by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("English") }
    var assistantId by remember(assistants) {
        mutableStateOf(assistants.firstOrNull { it.model != null }?.id)
    }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WearListScreen("翻译工作台") {
        item { WearTextField(targetLanguage, { targetLanguage = it }, "目标语言") }
        item { WearTextField(source, { source = it }, "要翻译的内容", maxLines = 12) }
        assistants.filter { it.model != null }.forEach { assistant ->
            item(key = assistant.id) {
                WearSelectionButton(
                    assistant.name,
                    { assistantId = assistant.id },
                    selected = assistantId == assistant.id
                )
            }
        }
        item {
            WearActionButton(
                label = if (running) "正在翻译…" else "翻译",
                enabled = !running && source.isNotBlank() && targetLanguage.isNotBlank(),
                primary = true,
                icon = Icons.Default.Transform,
                onClick = {
                    running = true
                    error = null
                    scope.launch {
                        runCatching { translate(source, targetLanguage, assistantId) }
                            .onSuccess { result = it }
                            .onFailure { error = it.message }
                        running = false
                    }
                }
            )
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (result.isNotBlank()) {
            item { Text(tr("译文"), color = MaterialTheme.colorScheme.tertiary) }
            item { Text(result, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
fun VoiceSettingsScreen(
    config: SpeechConfig,
    onSave: (SpeechConfig) -> Unit,
    onTest: (SpeechConfig) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    WearListScreen("语音朗读") {
        item {
            WearActionButton(
                label = config.name,
                secondary = when (config.engine) {
                    SpeechEngine.SYSTEM -> "手表系统 TTS"
                    SpeechEngine.OPENAI -> "OpenAI 兼容语音"
                    SpeechEngine.ELEVENLABS -> "ElevenLabs"
                    SpeechEngine.GOOGLE -> "Google Cloud TTS"
                },
                icon = Icons.Default.GraphicEq,
                primary = true,
                onClick = { editing = true }
            )
        }
        item {
            WearActionButton("试听", { onTest(config) }, icon = Icons.Default.GraphicEq)
        }
        item {
            Text(
                tr("长按任意回复可使用当前语音朗读。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    SpeechEditor(
        visible = editing,
        original = config,
        onDismiss = { editing = false },
        onSave = {
            onSave(it)
            editing = false
        }
    )
}

@Composable
private fun SpeechEditor(
    visible: Boolean,
    original: SpeechConfig,
    onDismiss: () -> Unit,
    onSave: (SpeechConfig) -> Unit
) {
    var engine by remember(visible) { mutableStateOf(original.engine) }
    var name by remember(visible) { mutableStateOf(original.name) }
    var endpoint by remember(visible) { mutableStateOf(original.endpoint) }
    var apiKey by remember(visible) { mutableStateOf(original.apiKey) }
    var model by remember(visible) { mutableStateOf(original.model) }
    var voice by remember(visible) { mutableStateOf(original.voice) }
    var language by remember(visible) { mutableStateOf(original.languageCode) }
    var speed by remember(visible) { mutableStateOf(original.speed.toString()) }
    FullScreenEditor(
        visible = visible,
        title = "语音服务",
        onDismiss = onDismiss,
        onSave = {
            onSave(
                original.copy(
                    engine = engine,
                    name = name.trim().ifBlank { engine.displayName() },
                    endpoint = endpoint.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    voice = voice.trim(),
                    languageCode = language.trim().ifBlank { "zh-CN" },
                    speed = speed.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1f
                )
            )
        }
    ) {
        item {
            WearSelectionField("语音服务", engine, SpeechEngine.entries,
                onSelect = { engine = it }, optionLabel = { it.displayName() })
        }
        item { WearTextField(name, { name = it }, "名称") }
        if (engine != SpeechEngine.SYSTEM) {
            item { WearTextField(endpoint, { endpoint = it }, "API URL（留空用默认）") }
            item { WearTextField(apiKey, { apiKey = it }, "API Key", password = true) }
            item { WearTextField(model, { model = it }, "模型") }
            item { WearTextField(voice, { voice = it }, "音色 / Voice ID") }
            if (engine == SpeechEngine.GOOGLE) {
                item { WearTextField(language, { language = it }, "语言代码") }
            }
        }
        item { WearTextField(speed, { speed = it }, "语速 0.5–2.0") }
    }
}

@Composable
fun PromptTransformsScreen(
    transforms: List<PromptTransform>,
    onSave: (PromptTransform) -> Unit,
    onDelete: (PromptTransform) -> Unit
) {
    var editing by remember { mutableStateOf<PromptTransform?>(null) }
    var creating by remember { mutableStateOf(false) }
    WearListScreen("提示词转换") {
        item {
            WearActionButton(
                "添加转换规则",
                { creating = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        item {
            Text(
                tr("发送前按正则表达式改写用户或助手消息。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        transforms.forEach { transform ->
            item(key = transform.id) {
                WearActionButton(
                    label = transform.name,
                    secondary = if (transform.enabled) transform.pattern else "已停用",
                    icon = Icons.Default.Transform,
                    onClick = { editing = transform }
                )
            }
        }
    }
    PromptTransformEditor(
        visible = creating || editing != null,
        original = editing,
        onDismiss = { creating = false; editing = null },
        onSave = { onSave(it); creating = false; editing = null },
        onDelete = editing?.let { item -> { onDelete(item); editing = null } }
    )
}

@Composable
private fun PromptTransformEditor(
    visible: Boolean,
    original: PromptTransform?,
    onDismiss: () -> Unit,
    onSave: (PromptTransform) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(original?.id, visible) { mutableStateOf(original?.name.orEmpty()) }
    var pattern by remember(original?.id, visible) { mutableStateOf(original?.pattern.orEmpty()) }
    var replacement by remember(original?.id, visible) {
        mutableStateOf(original?.replacement.orEmpty())
    }
    var user by remember(original?.id, visible) {
        mutableStateOf(original?.applyToUser ?: true)
    }
    var assistant by remember(original?.id, visible) {
        mutableStateOf(original?.applyToAssistant ?: false)
    }
    var enabled by remember(original?.id, visible) {
        mutableStateOf(original?.enabled ?: true)
    }
    val patternValid = remember(pattern) { runCatching { Regex(pattern) }.isSuccess }
    FullScreenEditor(
        visible = visible,
        title = original?.name ?: "转换规则",
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank() && pattern.isNotBlank() && patternValid,
        onSave = {
            onSave(
                PromptTransform(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    pattern = pattern,
                    replacement = replacement,
                    applyToUser = user,
                    applyToAssistant = assistant,
                    enabled = enabled
                )
            )
        }
    ) {
        item { WearTextField(name, { name = it }, "名称") }
        item { WearTextField(pattern, { pattern = it }, "正则表达式", maxLines = 5) }
        if (!patternValid && pattern.isNotBlank()) {
            item { Text(tr("正则表达式无效"), color = MaterialTheme.colorScheme.error) }
        }
        item { WearTextField(replacement, { replacement = it }, "替换内容", maxLines = 6) }
        item {
            SwitchButton(
                checked = user,
                onCheckedChange = { user = it },
                label = { Text(tr("应用到用户消息")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SwitchButton(
                checked = assistant,
                onCheckedChange = { assistant = it },
                label = { Text(tr("应用到助手消息")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SwitchButton(
                checked = enabled,
                onCheckedChange = { enabled = it },
                label = { Text(tr("启用规则")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        onDelete?.let { action ->
            item {
                WearActionButton("删除规则", action, icon = Icons.Default.Delete, primary = true)
            }
        }
    }
}

@Composable
fun GlobalSearchScreen(
    search: suspend (String) -> List<MessageSearchHit>,
    onOpen: (MessageSearchHit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MessageSearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WearListScreen("全局搜索") {
        item { WearTextField(query, { query = it }, "搜索消息") }
        item {
            WearActionButton(
                "搜索",
                {
                    scope.launch {
                        results = search(query)
                        searched = true
                    }
                },
                icon = Icons.Default.Search,
                primary = true
            )
        }
        if (searched && results.isEmpty()) {
            item { Text(tr("没有匹配结果"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        results.forEach { hit ->
            item(key = hit.message.id) {
                WearActionButton(
                    label = hit.conversation.title,
                    secondary = hit.message.content.ifBlank { hit.message.reasoning }.take(100),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onClick = { onOpen(hit) }
                )
            }
        }
    }
}

@Composable
fun UsageStatsScreen(
    refreshKey: Int,
    loadSummary: suspend () -> UsageSummary,
    onLogs: () -> Unit
) {
    var summary by remember { mutableStateOf<UsageSummary?>(null) }
    LaunchedEffect(refreshKey) { summary = loadSummary() }
    WearListScreen("用量统计") {
        val value = summary
        if (value == null) {
            item { Text(tr("正在统计…"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                Text(
                    "${value.totalTokens} tokens",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Text(
                    tr("输入 ${value.inputTokens} · 输出 ${value.outputTokens} · 缓存 ${value.cachedTokens}"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Text(
                    tr("${value.requests} 次请求 · ${value.messages} 条消息 · ${value.conversations} 个会话"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Text(
                    tr("活跃 ${value.activeDays} 天 · 失败 ${value.failures} 次"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            value.daily.take(14).forEach { day ->
                item(key = day.dayStart) {
                    WearActionButton(
                        label = DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(day.dayStart)),
                        secondary = "${day.requests} 次 · ${day.tokens} tokens",
                        onClick = {}
                    )
                }
            }
        }
        item {
            WearActionButton("请求日志", onLogs, icon = Icons.Default.History)
        }
    }
}

@Composable
fun RequestLogsScreen(
    logs: List<RequestLog>,
    onClear: () -> Unit
) {
    WearListScreen("请求日志") {
        if (logs.isEmpty()) {
            item { Text(tr("还没有请求记录"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        logs.forEach { log ->
            item(key = log.id) {
                WearActionButton(
                    label = "${log.modelId} · ${tr(log.status.displayName())}",
                    secondary = buildString {
                        append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(log.startedAt)))
                        append(" · ${log.usage.totalTokens} tokens")
                        log.error?.let { append(" · $it") }
                    },
                    icon = Icons.Default.History,
                    onClick = {}
                )
            }
        }
        if (logs.isNotEmpty()) {
            item {
                WearActionButton("清空日志", onClear, icon = Icons.Default.Delete, primary = true)
            }
        }
    }
}

private fun SpeechEngine.displayName(): String = when (this) {
    SpeechEngine.SYSTEM -> "系统语音"
    SpeechEngine.OPENAI -> "OpenAI"
    SpeechEngine.ELEVENLABS -> "ElevenLabs"
    SpeechEngine.GOOGLE -> "Google"
}

@Composable
fun TranscriptionSettingsScreen(
    config: TranscriptionConfig,
    onSave: (TranscriptionConfig) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    WearListScreen("语音输入") {
        item {
            WearActionButton(
                label = when (config.engine) {
                    TranscriptionEngine.SYSTEM -> "系统语音识别"
                    TranscriptionEngine.OPENAI -> "OpenAI 兼容转写"
                    TranscriptionEngine.GOOGLE -> "Google Speech-to-Text"
                },
                secondary = if (config.engine == TranscriptionEngine.SYSTEM) {
                    "使用手表自带识别界面"
                } else {
                    config.model
                },
                icon = Icons.Default.Mic,
                primary = true,
                onClick = { editing = true }
            )
        }
    }
    TranscriptionEditor(
        visible = editing,
        original = config,
        onDismiss = { editing = false },
        onSave = {
            onSave(it)
            editing = false
        }
    )
}

@Composable
private fun TranscriptionEditor(
    visible: Boolean,
    original: TranscriptionConfig,
    onDismiss: () -> Unit,
    onSave: (TranscriptionConfig) -> Unit
) {
    var engine by remember(visible) { mutableStateOf(original.engine) }
    var endpoint by remember(visible) { mutableStateOf(original.endpoint) }
    var apiKey by remember(visible) { mutableStateOf(original.apiKey) }
    var model by remember(visible) { mutableStateOf(original.model) }
    var language by remember(visible) { mutableStateOf(original.languageCode) }
    FullScreenEditor(
        visible = visible,
        title = "语音输入",
        onDismiss = onDismiss,
        onSave = {
            onSave(
                original.copy(
                    engine = engine,
                    endpoint = endpoint.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    languageCode = language.trim().ifBlank { "zh-CN" }
                )
            )
        }
    ) {
        item {
            WearSelectionField("识别服务", engine, TranscriptionEngine.entries,
                onSelect = { engine = it }, optionLabel = { candidate -> when (candidate) {
                        TranscriptionEngine.SYSTEM -> "系统识别"
                        TranscriptionEngine.OPENAI -> "OpenAI 兼容"
                        TranscriptionEngine.GOOGLE -> "Google Cloud"
                    } })
        }
        if (engine != TranscriptionEngine.SYSTEM) {
            item { WearTextField(endpoint, { endpoint = it }, "API URL（留空用默认）") }
            item { WearTextField(apiKey, { apiKey = it }, "API Key", password = true) }
            item { WearTextField(model, { model = it }, "模型") }
            item { WearTextField(language, { language = it }, "语言代码") }
        }
    }
}

private fun RequestStatus.displayName(): String = when (this) {
    RequestStatus.RUNNING -> "进行中"
    RequestStatus.SUCCEEDED -> "成功"
    RequestStatus.FAILED -> "失败"
    RequestStatus.CANCELLED -> "已停止"
}

@Composable
fun CloudBackupScreen(
    config: CloudBackupConfig,
    onSave: (CloudBackupConfig) -> Unit,
    upload: suspend (CloudBackupConfig) -> Unit,
    restore: suspend (CloudBackupConfig) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    WearListScreen("S3 云备份") {
        item {
            WearActionButton(
                label = if (config.bucket.isBlank()) "配置 S3" else config.bucket,
                secondary = config.endpoint.ifBlank { "S3 兼容对象存储" },
                icon = Icons.Default.CloudUpload,
                primary = true,
                onClick = { editing = true }
            )
        }
        item {
            WearActionButton(
                "立即上传加密备份",
                {
                    scope.launch {
                        status = "正在上传…"
                        runCatching { upload(config) }
                            .onSuccess { status = "云备份已上传" }
                            .onFailure { status = it.message }
                    }
                },
                icon = Icons.Default.CloudUpload
            )
        }
        item {
            WearActionButton(
                "从云端恢复",
                {
                    scope.launch {
                        status = "正在恢复…"
                        runCatching { restore(config) }
                            .onSuccess { status = "云备份恢复完成" }
                            .onFailure { status = it.message }
                    }
                },
                icon = Icons.Default.CloudDownload
            )
        }
        config.lastSyncedAt?.let { time ->
            item {
                Text(
                    tr("上次同步 ") + DateFormat.getDateTimeInstance().format(Date(time)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        status?.let { message ->
            item {
                Text(
                    tr(message),
                    color = if (message.contains("失败") || message.contains("错误")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
        item {
            Text(
                tr("上传前使用 AES-GCM 加密；恢复必须使用相同密码。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    CloudBackupEditor(
        visible = editing,
        original = config,
        onDismiss = { editing = false },
        onSave = {
            onSave(it)
            editing = false
        }
    )
}

@Composable
private fun CloudBackupEditor(
    visible: Boolean,
    original: CloudBackupConfig,
    onDismiss: () -> Unit,
    onSave: (CloudBackupConfig) -> Unit
) {
    var endpoint by remember(visible) { mutableStateOf(original.endpoint) }
    var region by remember(visible) { mutableStateOf(original.region) }
    var bucket by remember(visible) { mutableStateOf(original.bucket) }
    var objectKey by remember(visible) { mutableStateOf(original.objectKey) }
    var accessKey by remember(visible) { mutableStateOf(original.accessKeyId) }
    var secretKey by remember(visible) { mutableStateOf(original.secretAccessKey) }
    var password by remember(visible) { mutableStateOf(original.encryptionPassword) }
    var pathStyle by remember(visible) { mutableStateOf(original.pathStyle) }
    FullScreenEditor(
        visible = visible,
        title = "S3 配置",
        onDismiss = onDismiss,
        saveEnabled = endpoint.isNotBlank() && bucket.isNotBlank(),
        onSave = {
            onSave(
                original.copy(
                    endpoint = endpoint.trim().trimEnd('/'),
                    region = region.trim(),
                    bucket = bucket.trim(),
                    objectKey = objectKey.trim().trimStart('/'),
                    accessKeyId = accessKey.trim(),
                    secretAccessKey = secretKey.trim(),
                    encryptionPassword = password,
                    pathStyle = pathStyle,
                    enabled = true
                )
            )
        }
    ) {
        item { WearTextField(endpoint, { endpoint = it }, "HTTPS Endpoint") }
        item { WearTextField(region, { region = it }, "Region") }
        item { WearTextField(bucket, { bucket = it }, "Bucket") }
        item { WearTextField(objectKey, { objectKey = it }, "对象路径") }
        item { WearTextField(accessKey, { accessKey = it }, "Access Key ID") }
        item { WearTextField(secretKey, { secretKey = it }, "Secret Access Key", password = true) }
        item { WearTextField(password, { password = it }, "备份加密密码", password = true) }
        item {
            SwitchButton(
                checked = pathStyle,
                onCheckedChange = { pathStyle = it },
                label = { Text(tr("Path-style 地址")) },
                secondaryLabel = { Text(tr("MinIO、R2 等兼容服务常用")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun NetworkProxyScreen(
    config: NetworkProxyConfig,
    onSave: (NetworkProxyConfig) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    WearListScreen("网络代理") {
        item {
            WearActionButton(
                label = if (config.enabled) "代理已启用" else "代理未启用",
                secondary = if (config.host.isBlank()) {
                    "HTTP / SOCKS"
                } else {
                    "${config.type.name} · ${config.host}:${config.port}"
                },
                icon = Icons.Default.SettingsEthernet,
                primary = config.enabled,
                onClick = { editing = true }
            )
        }
        item {
            Text(
                tr("模型、搜索、MCP、语音与云备份共用此代理。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    NetworkProxyEditor(
        visible = editing,
        original = config,
        onDismiss = { editing = false },
        onSave = {
            onSave(it)
            editing = false
        }
    )
}

@Composable
private fun NetworkProxyEditor(
    visible: Boolean,
    original: NetworkProxyConfig,
    onDismiss: () -> Unit,
    onSave: (NetworkProxyConfig) -> Unit
) {
    var enabled by remember(visible) { mutableStateOf(original.enabled) }
    var type by remember(visible) { mutableStateOf(original.type) }
    var host by remember(visible) { mutableStateOf(original.host) }
    var port by remember(visible) { mutableStateOf(original.port.toString()) }
    var username by remember(visible) { mutableStateOf(original.username) }
    var password by remember(visible) { mutableStateOf(original.password) }
    var bypass by remember(visible) {
        mutableStateOf(original.bypassHosts.orEmpty().joinToString(", "))
    }
    FullScreenEditor(
        visible = visible,
        title = "网络代理",
        onDismiss = onDismiss,
        saveEnabled = !enabled || host.isNotBlank(),
        onSave = {
            onSave(
                original.copy(
                    enabled = enabled,
                    type = type,
                    host = host.trim(),
                    port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 8080,
                    username = username.trim(),
                    password = password,
                    bypassHosts = bypass.split(',', '，').map(String::trim)
                        .filter(String::isNotBlank)
                )
            )
        }
    ) {
        item {
            SwitchButton(
                checked = enabled,
                onCheckedChange = { enabled = it },
                label = { Text(tr("启用代理")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            WearSelectionField("代理类型", type, ProxyType.entries,
                onSelect = { type = it }, optionLabel = { it.name })
        }
        item { WearTextField(host, { host = it }, "主机") }
        item { WearTextField(port, { port = it }, "端口") }
        item { WearTextField(username, { username = it }, "用户名") }
        item { WearTextField(password, { password = it }, "密码", password = true) }
        item { WearTextField(bypass, { bypass = it }, "不代理主机（逗号分隔）", maxLines = 4) }
    }
}
