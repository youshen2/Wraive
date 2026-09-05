package moye.wear.wraive.network

import com.google.gson.Gson
import moye.wear.wraive.model.StreamEvent
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiClientTest {
    private val client = OpenAiClient(OkHttpClient(), Gson())

    @Test
    fun parseChatChunk_acceptsNullUsage() {
        val events = client.parseChatChunk(
            """{"choices":[{"delta":{"content":"你好"}}],"usage":null}"""
        )

        assertEquals(listOf(StreamEvent.TextDelta("你好")), events)
    }

    @Test
    fun parseResponsesChunk_acceptsNullUsage() {
        val event = client.parseResponsesChunk(
            """{"type":"response.completed","response":{"usage":null}}"""
        )

        assertNull(event)
    }
}
