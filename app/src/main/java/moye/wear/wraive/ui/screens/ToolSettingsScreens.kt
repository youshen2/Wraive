package moye.wear.wraive.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.McpServerConfig
import moye.wear.wraive.model.McpTransport
import moye.wear.wraive.model.MemoryCategory
import moye.wear.wraive.model.MemoryEntry
import moye.wear.wraive.model.QuickPhrase
import moye.wear.wraive.model.SearchProviderConfig
import moye.wear.wraive.model.SearchProviderType
import moye.wear.wraive.model.WorldBookEntry
import moye.wear.wraive.ui.components.FullScreenEditor
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearSelectionField
import moye.wear.wraive.ui.components.WearTextField
import moye.wear.wraive.ui.tr
import java.util.UUID

@Composable
fun SearchProvidersScreen(
    providers: List<SearchProviderConfig>,
    gson: Gson,
    onSave: (SearchProviderConfig) -> Unit,
    onDelete: (SearchProviderConfig) -> Unit
) {
    var editing by remember { mutableStateOf<SearchProviderConfig?>(null) }
    var showNew by remember { mutableStateOf(false) }
    WearListScreen("联网搜索") {
        item {
            WearActionButton(
                "添加搜索服务",
                { showNew = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        providers.forEach { provider ->
            item(key = provider.id) {
                WearActionButton(
                    provider.name,
                    { editing = provider },
                    icon = Icons.Default.Search,
                    secondary = provider.type.displayName()
                )
            }
        }
    }
    SearchEditor(
        visible = showNew || editing != null,
        original = editing,
        gson = gson,
        onDismiss = { showNew = false; editing = null },
        onSave = { onSave(it); showNew = false; editing = null },
        onDelete = editing?.let { provider ->
            { onDelete(provider); editing = null }
        }
    )
}

@Composable
private fun SearchEditor(
    visible: Boolean,
    original: SearchProviderConfig?,
    gson: Gson,
    onDismiss: () -> Unit,
    onSave: (SearchProviderConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(original?.id, visible) { mutableStateOf(original?.name.orEmpty()) }
    var type by remember(original?.id, visible) {
        mutableStateOf(original?.type ?: SearchProviderType.DUCKDUCKGO)
    }
    var baseUrl by remember(original?.id, visible) {
        mutableStateOf(original?.baseUrl.orEmpty())
    }
    var apiKey by remember(original?.id, visible) { mutableStateOf(original?.apiKey.orEmpty()) }
    var limit by remember(original?.id, visible) {
        mutableStateOf((original?.resultLimit ?: 5).toString())
    }
    var headers by remember(original?.id, visible) {
        mutableStateOf(gson.toJson(original?.customHeaders ?: emptyMap<String, String>()))
    }
    var error by remember(original?.id, visible) { mutableStateOf<String?>(null) }
    FullScreenEditor(
        visible = visible,
        title = original?.name ?: "添加搜索服务",
        onDismiss = onDismiss,
        onSave = {
            runCatching {
                val headerType = object : TypeToken<Map<String, String>>() {}.type
                onSave(
                    SearchProviderConfig(
                        id = original?.id ?: UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { type.displayName() },
                        type = type,
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        resultLimit = limit.toIntOrNull()?.coerceIn(1, 20) ?: 5,
                        customHeaders = gson.fromJson(headers, headerType)
                    )
                )
            }.onFailure { error = it.message }
        }
    ) {
        item {
            WearSelectionField("搜索服务", type, SearchProviderType.entries,
                onSelect = { type = it }, optionLabel = { it.displayName() })
        }
        item { WearTextField(name, { name = it }, "名称") }
        item { WearTextField(baseUrl, { baseUrl = it }, "API URL") }
        item { WearTextField(apiKey, { apiKey = it }, "API Key", password = true) }
        item { WearTextField(limit, { limit = it }, "结果数量") }
        item { WearTextField(headers, { headers = it }, "自定义请求头 JSON", maxLines = 5) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        onDelete?.let { action ->
            item {
                WearActionButton(
                    "删除搜索服务",
                    action,
                    icon = Icons.Default.Delete,
                    primary = true
                )
            }
        }
    }
}

@Composable
fun McpServersScreen(
    servers: List<McpServerConfig>,
    gson: Gson,
    onSave: (McpServerConfig) -> Unit,
    onDelete: (McpServerConfig) -> Unit
) {
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var showNew by remember { mutableStateOf(false) }
    WearListScreen("MCP 工具") {
        item {
            WearActionButton(
                "添加远程 MCP",
                { showNew = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        item {
            Text(
                tr("Wear OS 支持 Streamable HTTP 与 SSE 远程服务器"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        servers.forEach { server ->
            item(key = server.id) {
                WearActionButton(
                    server.name,
                    { editing = server },
                    icon = Icons.Default.Construction,
                    secondary = server.url
                )
            }
        }
    }
    McpEditor(
        visible = showNew || editing != null,
        original = editing,
        gson = gson,
        onDismiss = { showNew = false; editing = null },
        onSave = { onSave(it); showNew = false; editing = null },
        onDelete = editing?.let { server -> { onDelete(server); editing = null } }
    )
}

@Composable
private fun McpEditor(
    visible: Boolean,
    original: McpServerConfig?,
    gson: Gson,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(original?.id, visible) { mutableStateOf(original?.name.orEmpty()) }
    var url by remember(original?.id, visible) { mutableStateOf(original?.url.orEmpty()) }
    var transport by remember(original?.id, visible) {
        mutableStateOf(original?.transport ?: McpTransport.STREAMABLE_HTTP)
    }
    var timeout by remember(original?.id, visible) {
        mutableStateOf((original?.timeoutSeconds ?: 30).toString())
    }
    var headers by remember(original?.id, visible) {
        mutableStateOf(gson.toJson(original?.headers ?: emptyMap<String, String>()))
    }
    var error by remember(original?.id, visible) { mutableStateOf<String?>(null) }
    FullScreenEditor(
        visible = visible,
        title = original?.name ?: "添加 MCP",
        onDismiss = onDismiss,
        saveEnabled = url.isNotBlank(),
        onSave = {
            runCatching {
                val headerType = object : TypeToken<Map<String, String>>() {}.type
                onSave(
                    McpServerConfig(
                        id = original?.id ?: UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { "MCP" },
                        url = url.trim(),
                        transport = transport,
                        headers = gson.fromJson(headers, headerType),
                        timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 300) ?: 30
                    )
                )
            }.onFailure { error = it.message }
        }
    ) {
        item {
            WearSelectionField("传输方式", transport, McpTransport.entries,
                onSelect = { transport = it }, optionLabel = { it.name.replace('_', ' ') })
        }
        item { WearTextField(name, { name = it }, "名称") }
        item { WearTextField(url, { url = it }, "服务器 URL") }
        item { WearTextField(timeout, { timeout = it }, "超时秒数") }
        item { WearTextField(headers, { headers = it }, "自定义请求头 JSON", maxLines = 5) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        onDelete?.let { action ->
            item {
                WearActionButton("删除 MCP", action, icon = Icons.Default.Delete, primary = true)
            }
        }
    }
}

@Composable
fun MemoriesScreen(
    entries: List<MemoryEntry>,
    assistants: List<AssistantProfile>,
    onSave: (MemoryEntry) -> Unit,
    onDelete: (MemoryEntry) -> Unit
) {
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var showNew by remember { mutableStateOf(false) }
    WearListScreen("长期记忆") {
        item {
            WearActionButton(
                "添加记忆",
                { showNew = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        entries.forEach { entry ->
            item(key = entry.id) {
                val scope = assistants.firstOrNull { it.id == entry.assistantId }?.name ?: "全局"
                WearActionButton(
                    entry.category.name.lowercase().replaceFirstChar(Char::uppercase),
                    { editing = entry },
                    icon = Icons.Default.Memory,
                    secondary = "$scope · ${entry.content}"
                )
            }
        }
    }
    MemoryEditor(
        visible = showNew || editing != null,
        original = editing,
        assistants = assistants,
        onDismiss = { showNew = false; editing = null },
        onSave = { onSave(it); showNew = false; editing = null },
        onDelete = editing?.let { entry -> { onDelete(entry); editing = null } }
    )
}

@Composable
private fun MemoryEditor(
    visible: Boolean,
    original: MemoryEntry?,
    assistants: List<AssistantProfile>,
    onDismiss: () -> Unit,
    onSave: (MemoryEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    var content by remember(original?.id, visible) { mutableStateOf(original?.content.orEmpty()) }
    var category by remember(original?.id, visible) {
        mutableStateOf(original?.category ?: MemoryCategory.IDENTITY)
    }
    var assistantId by remember(original?.id, visible) { mutableStateOf(original?.assistantId) }
    FullScreenEditor(
        visible = visible,
        title = "记忆",
        onDismiss = onDismiss,
        saveEnabled = content.isNotBlank(),
        onSave = {
            onSave(
                MemoryEntry(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    assistantId = assistantId,
                    category = category,
                    content = content.trim(),
                    createdAt = original?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    ) {
        item {
            WearSelectionField("记忆分类", category, MemoryCategory.entries,
                onSelect = { category = it }, optionLabel = { it.name.lowercase() })
        }
        item {
            WearSelectionField("适用助手", assistantId, listOf(null) + assistants.map { it.id },
                onSelect = { assistantId = it },
                optionLabel = { id -> assistants.firstOrNull { it.id == id }?.name ?: "全局" })
        }
        item { WearTextField(content, { content = it }, "记忆内容", maxLines = 10) }
        onDelete?.let { action ->
            item { WearActionButton("删除记忆", action, icon = Icons.Default.Delete, primary = true) }
        }
    }
}

@Composable
fun WorldBookScreen(
    entries: List<WorldBookEntry>,
    onSave: (WorldBookEntry) -> Unit,
    onDelete: (WorldBookEntry) -> Unit
) {
    var editing by remember { mutableStateOf<WorldBookEntry?>(null) }
    var showNew by remember { mutableStateOf(false) }
    WearListScreen("世界书") {
        item {
            WearActionButton(
                "添加条目",
                { showNew = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        entries.forEach { entry ->
            item(key = entry.id) {
                WearActionButton(
                    entry.title,
                    { editing = entry },
                    icon = Icons.Default.Translate,
                    secondary = "${entry.bookId} · ${entry.keywords.joinToString()}"
                )
            }
        }
    }
    WorldBookEditor(
        visible = showNew || editing != null,
        original = editing,
        onDismiss = { showNew = false; editing = null },
        onSave = { onSave(it); showNew = false; editing = null },
        onDelete = editing?.let { entry -> { onDelete(entry); editing = null } }
    )
}

@Composable
private fun WorldBookEditor(
    visible: Boolean,
    original: WorldBookEntry?,
    onDismiss: () -> Unit,
    onSave: (WorldBookEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    var book by remember(original?.id, visible) { mutableStateOf(original?.bookId.orEmpty()) }
    var title by remember(original?.id, visible) { mutableStateOf(original?.title.orEmpty()) }
    var keywords by remember(original?.id, visible) {
        mutableStateOf(original?.keywords?.joinToString(", ").orEmpty())
    }
    var content by remember(original?.id, visible) { mutableStateOf(original?.content.orEmpty()) }
    var priority by remember(original?.id, visible) {
        mutableStateOf((original?.priority ?: 0).toString())
    }
    FullScreenEditor(
        visible,
        original?.title ?: "世界书条目",
        onDismiss,
        onSave = {
            onSave(
                WorldBookEntry(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    bookId = book.trim().ifBlank { "默认世界书" },
                    title = title.trim().ifBlank { "条目" },
                    keywords = keywords.split(',', '，').map(String::trim).filter(String::isNotBlank),
                    content = content.trim(),
                    priority = priority.toIntOrNull() ?: 0
                )
            )
        },
        saveEnabled = content.isNotBlank()
    ) {
        item { WearTextField(book, { book = it }, "世界书名称") }
        item { WearTextField(title, { title = it }, "条目标题") }
        item { WearTextField(keywords, { keywords = it }, "触发关键词，逗号分隔", maxLines = 4) }
        item { WearTextField(content, { content = it }, "注入内容", maxLines = 10) }
        item { WearTextField(priority, { priority = it }, "优先级") }
        onDelete?.let { action ->
            item { WearActionButton("删除条目", action, icon = Icons.Default.Delete, primary = true) }
        }
    }
}

@Composable
fun QuickPhrasesScreen(
    phrases: List<QuickPhrase>,
    assistants: List<AssistantProfile>,
    onSave: (QuickPhrase) -> Unit,
    onDelete: (QuickPhrase) -> Unit
) {
    var editing by remember { mutableStateOf<QuickPhrase?>(null) }
    var showNew by remember { mutableStateOf(false) }
    WearListScreen("快捷短语") {
        item {
            WearActionButton(
                "添加短语",
                { showNew = true },
                icon = Icons.Default.Add,
                primary = true
            )
        }
        phrases.forEach { phrase ->
            item(key = phrase.id) {
                WearActionButton(
                    phrase.title,
                    { editing = phrase },
                    icon = Icons.Default.FormatQuote,
                    secondary = phrase.content
                )
            }
        }
    }
    QuickPhraseEditor(
        visible = showNew || editing != null,
        original = editing,
        assistants = assistants,
        onDismiss = { showNew = false; editing = null },
        onSave = { onSave(it); showNew = false; editing = null },
        onDelete = editing?.let { phrase -> { onDelete(phrase); editing = null } }
    )
}

@Composable
private fun QuickPhraseEditor(
    visible: Boolean,
    original: QuickPhrase?,
    assistants: List<AssistantProfile>,
    onDismiss: () -> Unit,
    onSave: (QuickPhrase) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember(original?.id, visible) { mutableStateOf(original?.title.orEmpty()) }
    var content by remember(original?.id, visible) { mutableStateOf(original?.content.orEmpty()) }
    var assistantId by remember(original?.id, visible) { mutableStateOf(original?.assistantId) }
    FullScreenEditor(
        visible,
        original?.title ?: "快捷短语",
        onDismiss,
        onSave = {
            onSave(
                QuickPhrase(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    title = title.trim().ifBlank { content.take(12) },
                    content = content.trim(),
                    assistantId = assistantId,
                    sortOrder = original?.sortOrder ?: 0
                )
            )
        },
        saveEnabled = content.isNotBlank()
    ) {
        item { WearTextField(title, { title = it }, "标题") }
        item { WearTextField(content, { content = it }, "内容", maxLines = 8) }
        item {
            WearSelectionField("适用助手", assistantId, listOf(null) + assistants.map { it.id },
                onSelect = { assistantId = it },
                optionLabel = { id -> assistants.firstOrNull { it.id == id }?.name ?: "所有助手" })
        }
        onDelete?.let { action ->
            item { WearActionButton("删除短语", action, icon = Icons.Default.Delete, primary = true) }
        }
    }
}

private fun SearchProviderType.displayName(): String = name.lowercase()
    .replace('_', ' ')
    .replaceFirstChar(Char::uppercase)
