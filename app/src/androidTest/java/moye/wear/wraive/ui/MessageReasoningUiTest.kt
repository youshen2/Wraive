package moye.wear.wraive.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.wear.compose.material3.AppScaffold
import androidx.compose.ui.unit.dp
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.MessageStatus
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.ui.screens.ChatScreen
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessageReasoningUiTest {
    @get:Rule val compose = createComposeRule()

    private val reply = mutableStateOf(ChatMessage(
        id = "reply", conversationId = "chat", role = MessageRole.ASSISTANT,
        content = "出门前看看天气。", reasoning = "先确认天气，再决定出行安排。"
    ))
    private val showReasoning = mutableStateOf(true)

    private fun showChat() {
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    ChatScreen(
                        conversation = Conversation(id = "chat", assistantId = "assistant", title = "出行建议"),
                        messages = listOf(reply.value), quickPhrases = emptyList(),
                        generating = reply.value.status == MessageStatus.STREAMING,
                        showReasoning = showReasoning.value, markdownEnabled = false,
                        readAttachment = { emptyList() }, transcriptionConfig = TranscriptionConfig(),
                        startRecording = {}, stopAndTranscribe = { "" }, cancelRecording = {},
                        onSend = { _, _ -> }, onStop = {}, onMessageActions = {}
                    )
                }
            }
        }
    }

    @Test
    fun reasoningHasItsOwnTouchTargetAndRespectsTheVisibilitySetting() {
        showChat()
        compose.onNodeWithText(reply.value.reasoning).assertDoesNotExist()
        compose.onNodeWithText(reply.value.content, useUnmergedTree = true).performClick()
        compose.onNodeWithText(reply.value.reasoning).assertDoesNotExist()
        compose.onNodeWithText("思考过程").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(reply.value.reasoning).assertIsDisplayed()
        compose.onNodeWithText("思考过程").performClick()
        compose.onNodeWithText(reply.value.reasoning).assertDoesNotExist()
        compose.runOnIdle { showReasoning.value = false }
        compose.onNodeWithText("思考过程").assertDoesNotExist()
    }

    @Test
    fun expandingStreamingReasoningKeepsTheReadingPosition() {
        reply.value = reply.value.copy(
            content = "", reasoning = "先确认目的地的天气。\n".repeat(30), status = MessageStatus.STREAMING
        )
        showChat()
        compose.onNodeWithText("思考中").performClick()
        compose.onNodeWithText("思考中").assertIsDisplayed()
        val readingPosition = compose.onNodeWithTag("message-reply").fetchSemanticsNode().positionInRoot.y
        compose.runOnIdle { reply.value = reply.value.copy(reasoning = reply.value.reasoning + "再安排路线。\n".repeat(20)) }
        compose.waitForIdle()
        assertEquals(readingPosition, compose.onNodeWithTag("message-reply").fetchSemanticsNode().positionInRoot.y, 1f)
        compose.onNodeWithContentDescription("回到最新消息").assertIsDisplayed()
        compose.onNodeWithText("思考中").performClick()
        compose.onNodeWithText(reply.value.reasoning).assertDoesNotExist()
    }

    @Test
    fun progressDistinguishesWaitingThinkingReplyingAndFailure() {
        reply.value = reply.value.copy(content = "", reasoning = "", status = MessageStatus.STREAMING)
        showChat()
        compose.onNodeWithText("等待回复").assertIsDisplayed()
        compose.runOnIdle { reply.value = reply.value.copy(reasoning = "先了解天气。") }
        compose.onNodeWithText("思考中").assertIsDisplayed()
        compose.onNodeWithText("等待回复").assertDoesNotExist()
        compose.runOnIdle { reply.value = reply.value.copy(content = "带一把伞。") }
        compose.onNodeWithText("回复中").assertIsDisplayed()
        compose.onNodeWithText("思考中").assertDoesNotExist()
        compose.runOnIdle { reply.value = reply.value.copy(status = MessageStatus.FAILED, error = "连接中断") }
        compose.onNodeWithText("回复失败").assertIsDisplayed()
        compose.onNodeWithText("连接中断").assertIsDisplayed()
        compose.runOnIdle { reply.value = reply.value.copy(status = MessageStatus.CANCELLED, error = null) }
        compose.onNodeWithText("已停止").assertIsDisplayed()
        compose.runOnIdle { reply.value = reply.value.copy(status = MessageStatus.COMPLETE) }
        compose.onNodeWithTag("chat-list").performScrollToIndex(1)
        compose.onNodeWithText("已停止").assertDoesNotExist()
        compose.onNodeWithText(reply.value.content).assertIsDisplayed()
    }
}
