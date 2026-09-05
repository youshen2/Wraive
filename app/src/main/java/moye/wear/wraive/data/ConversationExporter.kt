package moye.wear.wraive.data

import android.text.Html
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.MessageRole
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

class ConversationExporter(private val repository: AppRepository) {
    suspend fun markdown(conversation: Conversation, output: OutputStream) {
        val messages = repository.messages(conversation.id)
        output.writer(Charsets.UTF_8).use { writer ->
            writer.appendLine("# ${conversation.title}")
            writer.appendLine()
            messages.forEach { message ->
                writer.appendLine("## ${message.role.displayName()}")
                writer.appendLine()
                if (message.reasoning.isNotBlank()) {
                    writer.appendLine("<details><summary>Reasoning</summary>")
                    writer.appendLine()
                    writer.appendLine(message.reasoning)
                    writer.appendLine()
                    writer.appendLine("</details>")
                    writer.appendLine()
                }
                writer.appendLine(message.content)
                message.attachments.forEach { attachment ->
                    writer.appendLine()
                    writer.appendLine("[${attachment.name}](${attachment.uri})")
                }
                writer.appendLine()
                writer.appendLine(
                    "_${DateFormat.getDateTimeInstance().format(Date(message.createdAt))}_"
                )
                writer.appendLine()
            }
        }
    }

    suspend fun html(conversation: Conversation, output: OutputStream) {
        val messages = repository.messages(conversation.id)
        val body = buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<style>body{font:16px system-ui;max-width:760px;margin:auto;padding:20px;")
            append("background:#111;color:#eee}.message{padding:14px;border-radius:16px;")
            append("background:#242126;margin:12px 0}.user{background:#493575}pre{overflow:auto}")
            append("img{max-width:100%;border-radius:12px}.meta{opacity:.65;font-size:12px}</style>")
            append("</head><body><h1>${Html.escapeHtml(conversation.title)}</h1>")
            messages.forEach { message ->
                val roleClass = if (message.role == MessageRole.USER) " user" else ""
                append("<section class=\"message$roleClass\"><strong>")
                append(Html.escapeHtml(message.role.displayName()))
                append("</strong><p>")
                append(Html.escapeHtml(message.content).replace("\n", "<br>"))
                append("</p>")
                message.attachments.filter { it.mimeType.startsWith("image/") }.forEach {
                    append("<img src=\"${Html.escapeHtml(it.uri)}\" alt=\"${Html.escapeHtml(it.name)}\">")
                }
                append("<div class=\"meta\">")
                append(DateFormat.getDateTimeInstance().format(Date(message.createdAt)))
                append("</div></section>")
            }
            append("</body></html>")
        }
        output.writer(Charsets.UTF_8).use { it.write(body) }
    }

    private fun MessageRole.displayName(): String = when (this) {
        MessageRole.SYSTEM -> "System"
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "Assistant"
        MessageRole.TOOL -> "Tool"
    }
}
