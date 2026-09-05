package moye.wear.wraive.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.McpServerConfig
import moye.wear.wraive.model.McpTool
import moye.wear.wraive.model.McpToolResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McpClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private val sessions = ConcurrentHashMap<String, String>()

    suspend fun listTools(server: McpServerConfig): List<McpTool> = withContext(Dispatchers.IO) {
        ensureInitialized(server)
        val result = rpc(server, "tools/list", JsonObject())
        result.getAsJsonArray("tools")?.mapNotNull { element ->
            val tool = element.asJsonObject
            val name = tool.string("name") ?: return@mapNotNull null
            McpTool(
                serverId = server.id,
                name = name,
                description = tool.string("description").orEmpty(),
                inputSchema = tool.get("inputSchema")?.toString()
                    ?: "{\"type\":\"object\"}"
            )
        }.orEmpty()
    }

    suspend fun callTool(
        server: McpServerConfig,
        name: String,
        arguments: JsonObject
    ): McpToolResult = withContext(Dispatchers.IO) {
        ensureInitialized(server)
        val result = rpc(
            server,
            "tools/call",
            JsonObject().apply {
                addProperty("name", name)
                add("arguments", arguments)
            }
        )
        val text = result.getAsJsonArray("content")
            ?.mapNotNull { block ->
                block.asJsonObject.takeIf { it.string("type") == "text" }?.string("text")
            }
            ?.joinToString("\n")
            .orEmpty()
        McpToolResult(text = text, isError = result.get("isError")?.asBoolean == true)
    }

    private fun ensureInitialized(server: McpServerConfig) {
        if (sessions.containsKey(server.id)) return
        rpc(
            server,
            "initialize",
            JsonObject().apply {
                addProperty("protocolVersion", "2025-03-26")
                add("capabilities", JsonObject())
                add("clientInfo", JsonObject().apply {
                    addProperty("name", "Wraive")
                    addProperty("version", moye.wear.wraive.BuildConfig.VERSION_NAME)
                })
            }
        )
        notify(server, "notifications/initialized")
    }

    private fun rpc(server: McpServerConfig, method: String, params: JsonObject): JsonObject {
        val requestId = UUID.randomUUID().toString()
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", requestId)
            addProperty("method", method)
            add("params", params)
        }
        val response = execute(server, payload)
        response.getAsJsonObject("error")?.let { error ->
            throw IllegalStateException(error.string("message") ?: error.toString())
        }
        return response.getAsJsonObject("result") ?: JsonObject()
    }

    private fun notify(server: McpServerConfig, method: String) {
        execute(
            server,
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("method", method)
            }
        )
    }

    private fun execute(server: McpServerConfig, payload: JsonObject): JsonObject {
        val request = Request.Builder()
            .url(server.url)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .apply {
                sessions[server.id]?.let { header("Mcp-Session-Id", it) }
                server.headers.forEach { (name, value) -> header(name, value) }
            }
            .post(gson.toJson(payload).toRequestBody(JSON))
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            response.header("Mcp-Session-Id")?.let { sessions[server.id] = it }
            val body = response.body.string()
            val json = if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                body.lineSequence()
                    .firstOrNull { it.startsWith("data:") }
                    ?.removePrefix("data:")
                    ?.trim()
                    .orEmpty()
            } else {
                body
            }
            parseJson(json).asJsonObject
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
