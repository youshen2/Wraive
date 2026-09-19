package moye.wear.wraive.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelsTest {
    private val gson = Gson()

    @Test
    fun readsMessagesWrittenByThePublishedRelease() {
        // Field and enum aliases were verified against app/release/app-release.apk (0.1.0).
        val json = """
            {
              "a":"message", "b":"conversation", "c":"h", "d":"附件消息", "e":"",
              "f":[{"a":"attachment", "b":"g", "c":"photo.jpg", "d":"data:image/jpeg;base64,YQ==",
                    "e":"image/jpeg", "f":null, "g":1}],
              "g":[{"a":"call", "b":"search", "c":"{}", "d":"provider-call", "e":"result", "f":null}],
              "h":"g", "i":null, "j":"provider", "k":"model",
              "l":{"a":100,"b":20,"c":5}, "m":1000, "n":2000
            }
        """.trimIndent()
        val message = gson.fromJson(json, ChatMessage::class.java)
        assertEquals("message", message.id)
        assertEquals("conversation", message.conversationId)
        assertEquals(MessageRole.USER, message.role)
        assertEquals(MessageStatus.COMPLETE, message.status)
        assertEquals("附件消息", message.content)
        assertEquals(Attachment("attachment", AttachmentKind.IMAGE, "photo.jpg",
            "data:image/jpeg;base64,YQ==", "image/jpeg", sizeBytes = 1), message.attachments.single())
        assertEquals(ToolCall("call", "search", "{}", "provider-call", "result"), message.toolCalls.single())
        assertEquals(TokenUsage(100, 20, 5), message.usage)
        assertEquals(1000L, message.createdAt)
        assertEquals(2000L, message.updatedAt)
        val saved = gson.toJsonTree(message).asJsonObject
        assertTrue(saved.has("attachments"))
        assertEquals("USER", saved["role"].asString)
        assertEquals(message, gson.fromJson(saved, ChatMessage::class.java))
    }

    @Test
    fun readsConversationsWrittenByThePublishedRelease() {
        val json = """
            {"a":"conversation","b":"原会话","c":"assistant","d":1000,"e":2000,
             "f":true,"g":false,"h":false,"i":"摘要","j":"预览"}
        """.trimIndent()
        assertEquals(
            Conversation("conversation", "原会话", "assistant", 1000, 2000,
                pinned = true, summary = "摘要", lastMessagePreview = "预览"),
            gson.fromJson(json, Conversation::class.java)
        )
    }
}
