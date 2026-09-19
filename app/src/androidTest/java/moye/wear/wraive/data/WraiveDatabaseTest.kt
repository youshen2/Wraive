package moye.wear.wraive.data

import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.AttachmentKind
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID

class WraiveDatabaseTest {
    @Test
    fun largeMessagesAndAttachmentsSurviveReopening() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "database-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?
            ) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        }
        val gson = Gson()
        val message = ChatMessage(
            conversationId = "conversation", role = MessageRole.USER, content = "图片附件",
            attachments = listOf(Attachment(
                kind = AttachmentKind.IMAGE, name = "photo.jpg", mimeType = "image/jpeg",
                uri = "data:image/jpeg;base64," + "a".repeat(3 * 1024 * 1024)
            )), createdAt = 2L
        )
        val before = message.copy(id = "before", content = "之前", attachments = emptyList(), createdAt = 1L)
        val after = message.copy(
            id = "after", role = MessageRole.ASSISTANT, content = "长回复喵🐈".repeat(300_000),
            attachments = emptyList(), createdAt = 3L
        )
        val expected = listOf(before, message, after)
        try {
            WraiveDatabase(context).use { database ->
                expected.forEach {
                    database.put(WraiveDatabase.KIND_MESSAGE, it.id, gson.toJson(it), it.conversationId, updatedAt = it.createdAt)
                }
            }
            WraiveDatabase(context).use { database ->
                val restored = database.query(WraiveDatabase.KIND_MESSAGE, message.conversationId)
                    .map { gson.fromJson(it, ChatMessage::class.java) }
                assertEquals(expected, restored)
                database.deleteChildren(WraiveDatabase.KIND_MESSAGE, message.conversationId)
                assertEquals(emptyList<String>(), database.query(WraiveDatabase.KIND_MESSAGE))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
