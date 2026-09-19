package moye.wear.wraive.ui.screens

import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RevealDirection
import androidx.wear.compose.material3.RevealState
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberRevealState
import kotlinx.coroutines.launch
import java.io.OutputStream
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.ui.components.ConfirmActionDialog
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearInfoCard
import moye.wear.wraive.ui.tr

@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    generating: Set<String>,
    onOpen: (Conversation) -> Unit,
    onActions: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    archived: Boolean = false,
    onMenu: (() -> Unit)? = null,
    onNewConversation: (() -> Unit)? = null,
    newConversationHint: String? = null
) {
    val listState = rememberTransformingLazyColumnState()
    val scope = rememberCoroutineScope()
    val deleteLabel = tr("删除会话")
    val manageLabel = tr("管理会话")
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    var pendingRevealState by remember { mutableStateOf<RevealState?>(null) }
    WearListScreen(
        title = if (archived) "归档会话" else "会话",
        scrollState = listState,
        edgeButton = onMenu?.let { openMenu ->
            {
                EdgeButton(onClick = openMenu) {
                    Icon(Icons.Default.Menu, contentDescription = tr("打开菜单"))
                }
            }
        }
    ) {
        if (!archived && onNewConversation != null) {
            item(key = "new-conversation") {
                WearActionButton(
                    label = "新对话",
                    onClick = onNewConversation,
                    icon = Icons.Default.Add,
                    primary = true,
                    secondary = newConversationHint
                )
            }
        }
        if (conversations.isEmpty()) {
            item {
                if (archived) {
                    WearInfoCard("还没有归档会话", "归档后的会话会显示在这里。")
                } else {
                    WearInfoCard("还没有会话", "点击上方新对话，开始第一段对话。")
                }
            }
        }
        conversations.sortedWith(
            compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt }
        ).forEach { conversation ->
            item(key = conversation.id) {
                val revealState = rememberRevealState()
                val requestDelete = {
                    pendingDelete = conversation
                    pendingRevealState = revealState
                }
                LaunchedEffect(listState.isScrollInProgress) {
                    if (
                        listState.isScrollInProgress &&
                        revealState.currentValue != RevealValue.Covered
                    ) {
                        revealState.animateTo(RevealValue.Covered)
                    }
                }
                SwipeToReveal(
                    revealState = revealState,
                    revealDirection = RevealDirection.RightToLeft,
                    onSwipePrimaryAction = requestDelete,
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = requestDelete,
                            icon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                            text = { Text(deleteLabel) },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    },
                    secondaryAction = {
                        SecondaryActionButton(
                            onClick = {
                                scope.launch {
                                    revealState.snapTo(RevealValue.Covered)
                                    onActions(conversation)
                                }
                            },
                            icon = {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = manageLabel
                                )
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WearActionButton(
                        label = conversation.title,
                        secondary = if (conversation.id in generating) {
                            "正在生成…"
                        } else {
                            conversation.lastMessagePreview
                        },
                        icon = when {
                            conversation.pinned -> Icons.Default.PushPin
                            conversation.archived -> Icons.Default.Archive
                            else -> Icons.AutoMirrored.Filled.Chat
                        },
                        onClick = { onOpen(conversation) },
                        modifier = Modifier.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(deleteLabel) {
                                    requestDelete()
                                    true
                                },
                                CustomAccessibilityAction(manageLabel) {
                                    onActions(conversation)
                                    true
                                }
                            )
                        }
                    )
                }
            }
        }
    }
    ConfirmActionDialog(
        visible = pendingDelete != null,
        title = "删除会话？",
        message = "删除后，会话及其中的消息将无法恢复。",
        confirmLabel = "确认删除",
        onDismiss = {
            val revealState = pendingRevealState
            pendingDelete = null
            pendingRevealState = null
            scope.launch { revealState?.animateTo(RevealValue.Covered) }
        },
        onConfirm = {
            val conversation = pendingDelete
            pendingDelete = null
            pendingRevealState = null
            conversation?.let(onDelete)
        }
    )
}

@Composable
fun ConversationActionsScreen(
    conversation: Conversation,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    exportMarkdown: suspend (OutputStream) -> Unit,
    exportHtml: suspend (OutputStream) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember(conversation.id) { mutableStateOf(false) }
    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { exportMarkdown(it) }
                    ?: error("无法创建导出文件")
            }.onSuccess { exportStatus = "Markdown 已导出" }
                .onFailure { exportStatus = it.message }
        }
    }
    val htmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { exportHtml(it) }
                    ?: error("无法创建导出文件")
            }.onSuccess { exportStatus = "HTML 已导出" }
                .onFailure { exportStatus = it.message }
        }
    }
    WearListScreen(conversation.title) {
        item {
            WearActionButton(
                label = if (conversation.pinned) "取消置顶" else "置顶",
                icon = Icons.Default.PushPin,
                onClick = onPin
            )
        }
        item {
            WearActionButton(
                "导出 Markdown",
                { markdownLauncher.launch("${conversation.title}.md") },
                icon = Icons.Default.Download
            )
        }
        item {
            WearActionButton(
                "导出 HTML",
                { htmlLauncher.launch("${conversation.title}.html") },
                icon = Icons.Default.Code
            )
        }
        exportStatus?.let { status ->
            item { Text(tr(status), color = MaterialTheme.colorScheme.primary) }
        }
        item {
            WearActionButton(
                label = if (conversation.archived) "移出归档" else "归档",
                icon = Icons.Default.Archive,
                onClick = onArchive
            )
        }
        item {
            WearActionButton(
                label = "删除会话",
                icon = Icons.Default.Delete,
                danger = true,
                onClick = { confirmingDelete = true }
            )
        }
    }
    ConfirmActionDialog(
        visible = confirmingDelete,
        title = "删除会话？",
        message = "删除后，会话及其中的消息将无法恢复。",
        confirmLabel = "确认删除",
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            onDelete()
        }
    )
}
