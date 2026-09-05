package moye.wear.wraive.chat

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import moye.wear.wraive.data.AppRepository
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.McpServerConfig
import moye.wear.wraive.model.McpTool
import moye.wear.wraive.model.SearchProviderConfig
import moye.wear.wraive.model.ToolCall
import moye.wear.wraive.network.McpClient
import moye.wear.wraive.network.SearchClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.DateFormat
import java.util.Date
import java.net.InetAddress

class ToolRuntime(
    private val repository: AppRepository,
    private val searchClient: SearchClient,
    private val mcpClient: McpClient,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private val fetchClient = httpClient.newBuilder().followRedirects(false).build()
    suspend fun prepare(assistant: AssistantProfile): PreparedTools {
        val routes = mutableMapOf<String, ToolRoute>()
        val definitions = mutableListOf<JsonObject>()
        definitions += functionDefinition(
            name = FETCH_TOOL,
            description = "Fetch readable text from a public URL.",
            properties = JsonObject().apply {
                add("url", JsonObject().apply { addProperty("type", "string") })
            },
            required = listOf("url")
        )
        routes[FETCH_TOOL] = ToolRoute.Fetch
        definitions += functionDefinition(
            name = TIME_TOOL,
            description = "Get the current local date and time on the user's watch.",
            properties = JsonObject()
        )
        routes[TIME_TOOL] = ToolRoute.CurrentTime
        definitions += functionDefinition(
            name = CALCULATOR_TOOL,
            description = "Evaluate a mathematical expression locally and return the numeric result.",
            properties = JsonObject().apply {
                add("expression", JsonObject().apply { addProperty("type", "string") })
            },
            required = listOf("expression")
        )
        routes[CALCULATOR_TOOL] = ToolRoute.Calculator

        val search = repository.searchProviders.value.firstOrNull {
            it.id == assistant.searchProviderId && it.enabled
        } ?: repository.searchProviders.value.firstOrNull { it.enabled }
        if (assistant.webSearchEnabled && search != null) {
            definitions += functionDefinition(
                name = SEARCH_TOOL,
                description = "Search the web for current information and return cited results.",
                properties = JsonObject().apply {
                    add("query", JsonObject().apply { addProperty("type", "string") })
                },
                required = listOf("query")
            )
            routes[SEARCH_TOOL] = ToolRoute.Search(search)
        }

        repository.mcpServers.value
            .filter { it.enabled && it.id in assistant.mcpServerIds }
            .forEach { server ->
                mcpClient.listTools(server).forEach { tool ->
                    val exposedName = mcpName(server, tool)
                    definitions += functionDefinition(
                        name = exposedName,
                        description = tool.description,
                        parameters = JsonParser.parseString(tool.inputSchema).asJsonObject
                    )
                    routes[exposedName] = ToolRoute.Mcp(server, tool)
                }
            }
        return PreparedTools(definitions, routes)
    }

    suspend fun execute(call: ToolCall, tools: PreparedTools): ToolCall {
        val arguments = call.arguments.takeIf { it.isNotBlank() }
            ?.let { JsonParser.parseString(it).asJsonObject }
            ?: JsonObject()
        return try {
            val result = when (val route = tools.routes[call.name]) {
                ToolRoute.Fetch -> fetch(arguments.get("url")?.asString.orEmpty())
                ToolRoute.CurrentTime -> DateFormat.getDateTimeInstance().format(Date())
                ToolRoute.Calculator -> ExpressionEvaluator.evaluate(
                    arguments.get("expression")?.asString.orEmpty()
                )
                is ToolRoute.Search -> {
                    val query = arguments.get("query")?.asString.orEmpty()
                    gson.toJson(searchClient.search(route.provider, query))
                }
                is ToolRoute.Mcp -> mcpClient.callTool(
                    route.server,
                    route.tool.name,
                    arguments
                ).text
                null -> error("Unknown tool ${call.name}")
            }
            call.copy(result = result)
        } catch (error: Exception) {
            call.copy(error = error.message ?: error::class.java.simpleName)
        }
    }

    private fun fetch(url: String): String {
        var current = url.toHttpUrl()
        repeat(6) { redirectCount ->
            validatePublicUrl(current)
            val response = fetchClient.newCall(
                Request.Builder()
                    .url(current)
                    .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
                    .get()
                    .build()
            ).execute()
            response.use {
                if (it.code in 300..399) {
                    require(redirectCount < 5) { "Too many redirects" }
                    val location = it.header("Location") ?: error("Redirect has no Location")
                    current = current.resolve(location) ?: error("Invalid redirect URL")
                    return@repeat
                }
                if (!it.isSuccessful) error("HTTP ${it.code}")
                val body = it.body.string().take(64_000)
                return if (it.header("Content-Type").orEmpty().contains("text/html")) {
                    body.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                } else {
                    body
                }
            }
        }
        error("Too many redirects")
    }

    private fun validatePublicUrl(url: HttpUrl) {
        require(url.scheme == "https" || url.scheme == "http") { "Only HTTP(S) URLs are allowed" }
        require(url.host != "localhost" && !url.host.endsWith(".local")) {
            "Local network URLs are not allowed"
        }
        val addresses = InetAddress.getAllByName(url.host)
        require(addresses.isNotEmpty() && addresses.none { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
        }) { "Local or private network URLs are not allowed" }
    }

    private fun functionDefinition(
        name: String,
        description: String,
        properties: JsonObject? = null,
        required: List<String> = emptyList(),
        parameters: JsonObject? = null
    ): JsonObject = JsonObject().apply {
        addProperty("type", "function")
        add("function", JsonObject().apply {
            addProperty("name", name)
            addProperty("description", description)
            add("parameters", parameters ?: JsonObject().apply {
                addProperty("type", "object")
                add("properties", properties ?: JsonObject())
                if (required.isNotEmpty()) add("required", JsonArray().apply {
                    required.forEach { add(it) }
                })
            })
        })
    }

    private fun mcpName(server: McpServerConfig, tool: McpTool): String {
        val prefix = server.name.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(16)
        val name = tool.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "mcp_${prefix}_$name".take(64)
    }

    private companion object {
        const val SEARCH_TOOL = "web_search"
        const val FETCH_TOOL = "fetch_url"
        const val TIME_TOOL = "current_time"
        const val CALCULATOR_TOOL = "calculator"
    }
}

data class PreparedTools(
    val definitions: List<JsonObject>,
    internal val routes: Map<String, ToolRoute>
)

sealed interface ToolRoute {
    data object Fetch : ToolRoute
    data object CurrentTime : ToolRoute
    data object Calculator : ToolRoute
    data class Search(val provider: SearchProviderConfig) : ToolRoute
    data class Mcp(val server: McpServerConfig, val tool: McpTool) : ToolRoute
}
