package moye.wear.wraive.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.webkit.WebView
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.ui.components.ConfirmActionDialog
import moye.wear.wraive.ui.components.FullScreenEditor
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearTextField
import moye.wear.wraive.ui.tr

@Composable
fun MessageActionsScreen(
    message: ChatMessage,
    showProvider: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onEdit: (String) -> Unit,
    onBranch: () -> Unit,
    onRegenerate: () -> Unit,
    onShareImages: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editedText by remember(message.id) { mutableStateOf(message.content) }
    var showHtml by remember(message.id) { mutableStateOf(false) }
    var confirmingDelete by remember(message.id) { mutableStateOf(false) }
    WearListScreen("消息操作") {
        item {
            Text(
                message.content.take(180),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6
            )
        }
        if (showProvider && message.providerId != null) {
            item {
                Text(
                    "${message.providerId} · ${message.modelId.orEmpty()}",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        if (message.usage.totalTokens > 0) {
            item {
                Text(
                    tr("输入 ${message.usage.inputTokens} · 输出 ${message.usage.outputTokens} tokens"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            WearActionButton("复制", onCopy, icon = Icons.Default.ContentCopy)
        }
        if (Regex("<([a-zA-Z][^>]*)>").containsMatchIn(message.content)) {
            item {
                WearActionButton("预览 HTML", { showHtml = true }, icon = Icons.Default.Code)
            }
        }
        if (message.role == MessageRole.ASSISTANT) {
            item {
                WearActionButton("朗读", onSpeak, icon = Icons.AutoMirrored.Filled.VolumeUp)
            }
            item {
                WearActionButton("重新生成", onRegenerate, icon = Icons.Default.Refresh)
            }
        }
        onShareImages?.let { share ->
            item {
                WearActionButton("分享图片", share, icon = Icons.Default.Share)
            }
        }
        if (message.role == MessageRole.USER) {
            item {
                WearActionButton("编辑", { editing = true }, icon = Icons.Default.Edit)
            }
        }
        item {
            WearActionButton(
                "从此处创建分支",
                onBranch,
                icon = Icons.AutoMirrored.Filled.CallSplit
            )
        }
        item {
            WearActionButton(
                "删除消息",
                { confirmingDelete = true },
                icon = Icons.Default.Delete,
                danger = true
            )
        }
    }
    FullScreenEditor(
        visible = editing,
        title = "编辑消息",
        onDismiss = { editing = false },
        saveEnabled = editedText.isNotBlank(),
        onSave = {
            onEdit(editedText)
            editing = false
        }
    ) {
        item { WearTextField(editedText, { editedText = it }, "内容", maxLines = 12) }
    }
    ConfirmActionDialog(
        visible = confirmingDelete,
        title = "删除消息？",
        message = "删除后，这条消息将无法恢复。",
        confirmLabel = "确认删除",
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            onDelete()
        }
    )
    if (showHtml) {
        Dialog(
            onDismissRequest = { showHtml = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        null,
                        message.content,
                        "text/html",
                        "UTF-8",
                        null
                    )
                },
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
        }
    }
}
