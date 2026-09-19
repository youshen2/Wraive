package moye.wear.wraive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import moye.wear.wraive.model.AttachmentKind
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.MessageStatus
import moye.wear.wraive.ui.tr
import java.text.DateFormat
import java.util.Date

@Composable
fun MessageBubble(
    message: ChatMessage,
    showReasoning: Boolean,
    markdownEnabled: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReasoningExpanded: () -> Unit = {}
) {
    if (message.role == MessageRole.TOOL) {
        ToolMessage(message, modifier)
        return
    }
    val mine = message.role == MessageRole.USER
    val assistant = message.role == MessageRole.ASSISTANT
    val container = when {
        mine -> MaterialTheme.colorScheme.primaryContainer
        assistant -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = if (assistant) CardDefaults.shape else RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (mine) 24.dp else 8.dp,
        bottomEnd = if (mine) 8.dp else 24.dp
    )
    val timestamp = remember(message.createdAt) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt))
    }
    var reasoningExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var previewImage by remember(message.id) { mutableStateOf<Attachment?>(null) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 260.dp)
                .clip(shape)
                .background(container)
                .combinedClickable(onClick = {
                    if (!assistant && showReasoning && message.reasoning.isNotBlank()) {
                        reasoningExpanded = !reasoningExpanded
                    }
                }, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (assistant) 10.dp else 6.dp)
        ) {
            if (assistant) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tr("AI 回复"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = tr(if (mine) "你" else "助手"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) contentColor else MaterialTheme.colorScheme.primary
                )
            }
            if (assistant && showReasoning && message.reasoning.isNotBlank()) {
                ReasoningSection(
                    reasoning = message.reasoning,
                    thinking = message.status == MessageStatus.STREAMING && message.content.isBlank(),
                    expanded = reasoningExpanded,
                    onToggle = {
                        reasoningExpanded = !reasoningExpanded
                        if (reasoningExpanded) onReasoningExpanded()
                    }
                )
            } else if (!assistant && showReasoning && message.reasoning.isNotBlank()) {
                Text(
                    text = tr(if (reasoningExpanded) "思考过程" else "思考过程 · 点击展开"),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.68f)
                )
                if (reasoningExpanded) {
                    SelectionContainer {
                        Text(
                            text = message.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.78f)
                        )
                    }
                }
            }
            if (message.content.isNotBlank()) {
                SelectionContainer {
                    if (markdownEnabled) {
                        MarkdownMessageText(message.content, contentColor)
                    } else {
                        Text(message.content, color = contentColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            message.attachments.forEach { attachment ->
                if (attachment.kind == AttachmentKind.IMAGE) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { previewImage = attachment },
                        contentScale = ContentScale.Crop
                    )
                    attachment.extractedText?.takeIf(String::isNotBlank)?.let { revisedPrompt ->
                        Text(
                            text = revisedPrompt,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 3
                        )
                    }
                } else {
                    Text(
                        text = tr("附件 · ${attachment.name}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }
            }
            if (assistant) {
                AssistantMessageStatus(message, showReasoning)
            } else {
                val status = when (message.status) {
                    MessageStatus.STREAMING -> " · 生成中"
                    MessageStatus.FAILED -> " · 失败"
                    MessageStatus.CANCELLED -> " · 已停止"
                    MessageStatus.COMPLETE -> ""
                }
                Text(
                    text = timestamp + tr(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.58f)
                )
            }
            message.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    previewImage?.let { attachment ->
        var scale by remember(attachment.uri) { mutableStateOf(1f) }
        var offset by remember(attachment.uri) { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Dialog(
            onDismissRequest = { previewImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { previewImage = null },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformState)
                        .clickable { }
                )
            }
        }
    }
}

@Composable
private fun ReasoningSection(
    reasoning: String,
    thinking: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val expansionState = tr(if (expanded) "已展开" else "已收起")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = expansionState },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (thinking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = tr(if (thinking) "思考中" else "思考过程"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    text = reasoning,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageStatus(message: ChatMessage, showReasoning: Boolean) {
    val streaming = message.status == MessageStatus.STREAMING
    // The reasoning button already communicates progress while thinking.
    if (message.status == MessageStatus.COMPLETE ||
        (streaming && showReasoning && message.reasoning.isNotBlank() && message.content.isBlank())
    ) return
    val label = when (message.status) {
        MessageStatus.STREAMING -> when {
            message.content.isNotBlank() -> "回复中"
            message.reasoning.isNotBlank() -> "思考中"
            else -> "等待回复"
        }
        MessageStatus.FAILED -> "回复失败"
        MessageStatus.CANCELLED -> "已停止"
        MessageStatus.COMPLETE -> return
    }
    val color = if (message.status == MessageStatus.FAILED) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (streaming) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (message.status == MessageStatus.FAILED) Icons.Default.ErrorOutline
                    else Icons.Default.StopCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
        }
        Text(text = tr(label), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ToolMessage(message: ChatMessage, modifier: Modifier) {
    val call = message.toolCalls.firstOrNull()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = call?.name?.ifBlank { tr("工具") } ?: tr("工具"),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message.content.take(800),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8
            )
        }
    }
}
