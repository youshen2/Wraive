package moye.wear.wraive.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.QuickPhrase
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.TranscriptionEngine
import moye.wear.wraive.ui.components.MessageBubble
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearFisheyeScope
import moye.wear.wraive.ui.components.rememberCompatibleRotaryBehavior
import moye.wear.wraive.ui.tr

@Composable
fun ChatScreen(
    conversation: Conversation,
    messages: List<ChatMessage>,
    quickPhrases: List<QuickPhrase>,
    generating: Boolean,
    showReasoning: Boolean,
    markdownEnabled: Boolean,
    readAttachment: suspend (Uri) -> List<Attachment>,
    transcriptionConfig: TranscriptionConfig,
    startRecording: () -> Unit,
    stopAndTranscribe: suspend (TranscriptionConfig) -> String,
    cancelRecording: () -> Unit,
    onSend: (String, List<Attachment>) -> Unit,
    onStop: () -> Unit,
    onMessageActions: (ChatMessage) -> Unit
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var followLatest by remember(conversation.id) { mutableStateOf(true) }
    var lastMessageHeight by remember(conversation.id) { mutableIntStateOf(0) }
    var showInput by remember { mutableStateOf(false) }
    val scrollConnection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) followLatest = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (!scrolling && !canScrollForward) followLatest = true
            }
    }
    // Follow measured growth instead of restarting an animation for every token.
    LaunchedEffect(messages.size, lastMessageHeight, followLatest, state.isScrollInProgress) {
        if (followLatest && messages.isNotEmpty() && !state.isScrollInProgress) {
            state.requestScrollToItem(messages.size + 1)
        }
    }
    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        scrollState = state,
        timeText = { TimeText() },
        edgeButton = {
            val size = EdgeButtonSize.ExtraSmall
            EdgeButton(
                onClick = if (generating) onStop else ({ showInput = true }),
                modifier = Modifier.fillMaxWidth(),
                buttonSize = size,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (generating) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (generating) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            ) {
                Icon(
                    imageVector = if (generating) Icons.Default.Stop else Icons.Default.Edit,
                    contentDescription = tr(if (generating) "停止" else "输入"),
                    modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(size))
                )
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .testTag("chat-list")
                .nestedScroll(scrollConnection)
                .onPreRotaryScrollEvent {
                    if (it.verticalScrollPixels < 0) followLatest = false
                    false
                }
                .requestFocusOnHierarchyActive()
                .rotaryScrollable(rememberCompatibleRotaryBehavior(state), focusRequester),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                item(key = "chat-title") {
                    ListHeader(Modifier.fillMaxWidth()) {
                        Text(
                            conversation.title,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )
                    }
                }
                if (messages.isEmpty()) {
                    item(key = "chat-empty") {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("想聊点什么？"),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(messages, key = ChatMessage::id, contentType = { it.role }) { message ->
                    MessageBubble(
                        message = message,
                        showReasoning = showReasoning,
                        markdownEnabled = markdownEnabled,
                        onLongClick = { onMessageActions(message) },
                        onReasoningExpanded = { followLatest = false },
                        modifier = Modifier.testTag("message-${message.id}").onSizeChanged {
                            if (message.id == messages.lastOrNull()?.id) lastMessageHeight = it.height
                        }
                    )
                }
                item(key = "chat-bottom") { Spacer(Modifier.fillMaxWidth().height(8.dp).testTag("chat-bottom")) }
        }
        if (!followLatest) {
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        state.scrollToItem(messages.size + 1)
                        followLatest = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(48.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = tr("回到最新消息"))
            }
        }
    }
    ChatInputDialog(
        visible = showInput,
        quickPhrases = quickPhrases.filter {
            it.assistantId == null || it.assistantId == conversation.assistantId
        },
        readAttachment = readAttachment,
        transcriptionConfig = transcriptionConfig,
        startRecording = startRecording,
        stopAndTranscribe = stopAndTranscribe,
        cancelRecording = cancelRecording,
        onDismiss = {
            cancelRecording()
            showInput = false
        },
        onSend = { text, attachments ->
            cancelRecording()
            followLatest = true
            showInput = false
            onSend(text, attachments)
        }
    )
}

@Composable
private fun ChatInputDialog(
    visible: Boolean,
    quickPhrases: List<QuickPhrase>,
    readAttachment: suspend (Uri) -> List<Attachment>,
    transcriptionConfig: TranscriptionConfig,
    startRecording: () -> Unit,
    stopAndTranscribe: suspend (TranscriptionConfig) -> String,
    cancelRecording: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (String, List<Attachment>) -> Unit
) {
    if (!visible) return
    var text by remember(visible) { mutableStateOf("") }
    var attachments by remember(visible) { mutableStateOf<List<Attachment>>(emptyList()) }
    var attachmentError by remember(visible) { mutableStateOf<String?>(null) }
    var recording by remember(visible) { mutableStateOf(false) }
    var transcribing by remember(visible) { mutableStateOf(false) }
    var readingAttachments by remember(visible) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    val microphonePermissionError = tr("需要麦克风权限才能录音")
    val voicePrompt = tr("说点什么")
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!recognized.isNullOrBlank()) text = recognized
        }
    }
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching { startRecording() }
                .onSuccess { recording = true }
                .onFailure { attachmentError = it.message }
        } else {
            attachmentError = microphonePermissionError
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        scope.launch {
            attachmentError = null
            readingAttachments = true
            runCatching { uris.flatMap { readAttachment(it) } }
                .onSuccess { attachments = attachments + it }
                .onFailure { attachmentError = it.message }
            readingAttachments = false
        }
    }
    val inputBusy = recording || transcribing || readingAttachments
    val canSend = !inputBusy && (text.isNotBlank() || attachments.isNotEmpty())
    val submit = {
        if (canSend) {
            onSend(text.trim(), attachments)
        }
    }
    Dialog(
        visible = visible,
        onDismissRequest = {
            if (recording) cancelRecording()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            delay(200)
            focusRequester.requestFocus()
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    val size = EdgeButtonSize.ExtraSmall
                    EdgeButton(
                        onClick = submit,
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth(),
                        buttonSize = size
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = tr("发送"),
                            modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(size))
                        )
                    }
                }
            ) { contentPadding ->
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    rotaryScrollableBehavior = rememberCompatibleRotaryBehavior(listState)
                ) {
                    with(WearFisheyeScope(this, transformationSpec, isRound)) {
                        item {
                            ListHeader(Modifier.fillMaxWidth()) { Text(tr("输入消息")) }
                        }
                        item {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text(tr("输入内容")) },
                                minLines = 3,
                                maxLines = 8,
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submit() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                            )
                        }
                        item {
                            WearActionButton(
                                label = when {
                                    transcribing -> "正在转写…"
                                    recording -> "停止并转写"
                                    else -> "语音输入"
                                },
                                secondary = when (transcriptionConfig.engine) {
                                    TranscriptionEngine.SYSTEM -> "系统识别"
                                    TranscriptionEngine.OPENAI -> "OpenAI 兼容"
                                    TranscriptionEngine.GOOGLE -> "Google Cloud"
                                },
                                icon = Icons.Default.Mic,
                                onClick = {
                                    if (transcriptionConfig.engine == TranscriptionEngine.SYSTEM) {
                                        voiceLauncher.launch(
                                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                )
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE,
                                                    transcriptionConfig.languageCode
                                                )
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                            }
                                        )
                                    } else if (recording) {
                                        recording = false
                                        transcribing = true
                                        scope.launch {
                                            runCatching { stopAndTranscribe(transcriptionConfig) }
                                                .onSuccess { recognized ->
                                                    text = listOf(text, recognized)
                                                        .filter(String::isNotBlank)
                                                        .joinToString(" ")
                                                }
                                                .onFailure { attachmentError = it.message }
                                            transcribing = false
                                        }
                                    } else {
                                        recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                enabled = !transcribing && !readingAttachments
                            )
                        }
                        item {
                            WearActionButton(
                                label = if (readingAttachments) "正在读取附件…" else "添加附件",
                                secondary = if (attachments.isEmpty()) null else {
                                    attachments.joinToString { it.name }
                                },
                                icon = Icons.Default.AttachFile,
                                onClick = { attachmentLauncher.launch(arrayOf("*/*")) },
                                enabled = !inputBusy
                            )
                        }
                        attachmentError?.let { error ->
                            item { Text(error, color = MaterialTheme.colorScheme.error) }
                        }
                        if (quickPhrases.isNotEmpty()) {
                            item {
                                Text(
                                    tr("快捷短语"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            quickPhrases.forEach { phrase ->
                                item(key = phrase.id) {
                                    WearActionButton(
                                        label = phrase.title,
                                        secondary = phrase.content,
                                        onClick = { text = phrase.content }
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(36.dp)) }
                    }
                }
            }
        }
    }
}
