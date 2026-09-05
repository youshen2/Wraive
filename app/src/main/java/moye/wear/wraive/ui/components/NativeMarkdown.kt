package moye.wear.wraive.ui.components

import android.content.Context
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

@Composable
internal fun MarkdownMessageText(text: String, color: Color) {
    val segments = remember(text) { splitFencedSegments(text) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        segments.forEach { segment ->
            when (segment.kind) {
                MarkdownSegmentKind.MARKDOWN -> NativeMarkdownText(segment.content, color)
                MarkdownSegmentKind.CODE -> CodeCard(segment.content, segment.language)
                MarkdownSegmentKind.MERMAID -> MermaidCard(segment.content)
            }
        }
    }
}

@Composable
private fun NativeMarkdownText(markdown: String, color: Color) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSize = MaterialTheme.typography.bodyMedium.fontSize
    val textSizePx = with(density) { textSize.toPx() }
    val textColor = color.toArgb()
    val markwon = remember(context, textSizePx, textColor) {
        createNativeMarkwon(context, textSizePx, textColor)
    }
    val normalized = remember(markdown) { normalizeLatexDelimiters(markdown.trim()) }
    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(textColor)
                setLinkTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.value)
                includeFontPadding = false
                setLineSpacing(0f, 1.08f)
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(textColor)
            if (textView.tag != normalized) {
                textView.tag = normalized
                markwon.setMarkdown(textView, normalized)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun createNativeMarkwon(
    context: Context,
    textSizePx: Float,
    textColor: Int
): Markwon = Markwon.builder(context)
    .usePlugin(MarkwonInlineParserPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TaskListPlugin.create(context))
    .usePlugin(HtmlPlugin.create())
    .usePlugin(SoftBreakAddsNewLinePlugin.create())
    .usePlugin(JLatexMathPlugin.create(textSizePx) { builder ->
        builder.inlinesEnabled(true)
        builder.theme().textColor(textColor)
    })
    .build()

@Composable
private fun CodeCard(source: String, language: String) {
    val keywordColor = MaterialTheme.colorScheme.tertiary
    val literalColor = MaterialTheme.colorScheme.secondary
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
    val numberColor = MaterialTheme.colorScheme.primary
    val highlighted = remember(source, keywordColor, literalColor, commentColor, numberColor) {
        highlightCode(source, keywordColor, literalColor, commentColor, numberColor)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language.lowercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = highlighted,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun MermaidCard(source: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Mermaid",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = source,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

private data class MarkdownSegment(
    val content: String,
    val kind: MarkdownSegmentKind,
    val language: String = ""
)

private enum class MarkdownSegmentKind { MARKDOWN, CODE, MERMAID }

private fun splitFencedSegments(text: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)(?:```|$)")
    var cursor = 0
    fence.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            result += MarkdownSegment(text.substring(cursor, match.range.first), MarkdownSegmentKind.MARKDOWN)
        }
        val language = match.groupValues[1].trim()
        val content = match.groupValues[2].trimEnd()
        result += MarkdownSegment(
            content = content,
            kind = if (language.equals("mermaid", ignoreCase = true)) {
                MarkdownSegmentKind.MERMAID
            } else {
                MarkdownSegmentKind.CODE
            },
            language = language
        )
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        result += MarkdownSegment(text.substring(cursor), MarkdownSegmentKind.MARKDOWN)
    }
    return result.filter { it.content.isNotBlank() }
}

private fun normalizeLatexDelimiters(markdown: String): String {
    val blockDelimiter = "${'$'}${'$'}"
    return markdown
        .replace(Regex("\\\\\\[([\\s\\S]*?)\\\\]")) { match ->
            "\n$blockDelimiter\n${match.groupValues[1].trim()}\n$blockDelimiter\n"
        }
        .replace(Regex("\\\\\\((.+?)\\\\\\)")) { match ->
            "$blockDelimiter${match.groupValues[1]}$blockDelimiter"
        }
}

private fun highlightCode(
    source: String,
    keywordColor: Color,
    literalColor: Color,
    commentColor: Color,
    numberColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder(source)
    fun add(pattern: Regex, color: Color, weight: FontWeight? = null) {
        pattern.findAll(source).forEach { match ->
            builder.addStyle(
                SpanStyle(color = color, fontWeight = weight),
                match.range.first,
                match.range.last + 1
            )
        }
    }
    add(Regex("\\b(?:true|false|null|nil|None|[0-9]+(?:\\.[0-9]+)?)\\b"), numberColor)
    add(
        Regex("\\b(?:abstract|as|async|await|break|case|catch|class|const|continue|data|def|do|else|enum|extends|false|final|finally|for|fun|function|if|implements|import|in|interface|internal|is|let|new|null|object|override|package|private|protected|public|return|sealed|static|struct|super|suspend|switch|this|throw|true|try|typealias|typeof|val|var|void|when|while|with|yield)\\b"),
        keywordColor,
        FontWeight.SemiBold
    )
    add(Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"), literalColor)
    add(Regex("(?m)//.*$|/\\*[\\s\\S]*?\\*/|(?m)^\\s*#.*$"), commentColor)
    return builder.toAnnotatedString()
}
