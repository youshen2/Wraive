package moye.wear.wraive.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.SearchProviderConfig
import moye.wear.wraive.model.SearchProviderType
import moye.wear.wraive.model.SearchResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SearchClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun search(config: SearchProviderConfig, query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            when (config.type) {
                SearchProviderType.DUCKDUCKGO -> duckDuckGo(config, query)
                SearchProviderType.BING -> bing(config, query)
                SearchProviderType.TAVILY -> tavily(config, query)
                SearchProviderType.BRAVE -> brave(config, query)
                SearchProviderType.SERPER -> serper(config, query)
                SearchProviderType.EXA -> exa(config, query)
                SearchProviderType.SEARXNG -> searxng(config, query)
                SearchProviderType.JINA -> jina(config, query)
                SearchProviderType.ZHIPU -> zhipu(config, query)
                SearchProviderType.LINKUP -> linkup(config, query)
                SearchProviderType.METASO -> metaso(config, query)
                SearchProviderType.OLLAMA -> ollama(config, query)
                SearchProviderType.PERPLEXITY -> perplexity(config, query)
                SearchProviderType.BOCHA -> bocha(config, query)
                SearchProviderType.GROK -> grok(config, query)
                SearchProviderType.CUSTOM -> generic(config, query)
            }.take(config.resultLimit)
        }

    private fun duckDuckGo(config: SearchProviderConfig, query: String): List<SearchResult> {
        val base = config.baseUrl.ifBlank { "https://api.duckduckgo.com" }
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("no_html", "1")
            .build()
        val root = execute(Request.Builder().url(url).get(), config)
        return buildList {
            val abstract = root.string("AbstractText").orEmpty()
            if (abstract.isNotBlank()) {
                add(
                    SearchResult(
                        title = root.string("Heading").orEmpty().ifBlank { query },
                        url = root.string("AbstractURL").orEmpty(),
                        snippet = abstract
                    )
                )
            }
            root.getAsJsonArray("RelatedTopics")?.forEach { element ->
                val item = element.asJsonObject
                val text = item.string("Text") ?: return@forEach
                add(
                    SearchResult(
                        title = text.substringBefore(" - ").take(120),
                        url = item.string("FirstURL").orEmpty(),
                        snippet = text
                    )
                )
            }
        }
    }

    private fun bing(config: SearchProviderConfig, query: String): List<SearchResult> {
        val base = config.baseUrl.ifBlank { "https://api.bing.microsoft.com/v7.0/search" }
        val url = base.toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        val root = execute(
            Request.Builder().url(url).header("Ocp-Apim-Subscription-Key", config.apiKey).get(),
            config
        )
        return commonResults(root.getAsJsonObject("webPages")?.getAsJsonArray("value"))
    }

    private fun tavily(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("api_key", config.apiKey)
            addProperty("query", query)
            addProperty("max_results", config.resultLimit)
            addProperty("include_answer", true)
        }
        val base = config.baseUrl.ifBlank { "https://api.tavily.com/search" }
        val root = execute(jsonPost(base, body), config)
        return commonResults(root.getAsJsonArray("results"), snippetKeys = listOf("content"))
    }

    private fun brave(config: SearchProviderConfig, query: String): List<SearchResult> {
        val base = config.baseUrl.ifBlank { "https://api.search.brave.com/res/v1/web/search" }
        val url = base.toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        val root = execute(
            Request.Builder().url(url).header("X-Subscription-Token", config.apiKey).get(),
            config
        )
        return commonResults(root.getAsJsonObject("web")?.getAsJsonArray("results"))
    }

    private fun serper(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("q", query)
            addProperty("num", config.resultLimit)
        }
        val base = config.baseUrl.ifBlank { "https://google.serper.dev/search" }
        val root = execute(
            jsonPost(base, body).header("X-API-KEY", config.apiKey),
            config
        )
        return commonResults(
            root.getAsJsonArray("organic"),
            urlKeys = listOf("link", "url")
        )
    }

    private fun exa(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("numResults", config.resultLimit)
            add("contents", JsonObject().apply { addProperty("text", true) })
        }
        val base = config.baseUrl.ifBlank { "https://api.exa.ai/search" }
        val root = execute(jsonPost(base, body).header("x-api-key", config.apiKey), config)
        return commonResults(
            root.getAsJsonArray("results"),
            snippetKeys = listOf("text", "summary")
        )
    }

    private fun searxng(config: SearchProviderConfig, query: String): List<SearchResult> {
        val url = config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .build()
        val root = execute(Request.Builder().url(url).get(), config)
        return commonResults(root.getAsJsonArray("results"))
    }

    private fun jina(config: SearchProviderConfig, query: String): List<SearchResult> {
        val base = config.baseUrl.ifBlank { "https://s.jina.ai" }.trimEnd('/')
        val url = base.toHttpUrl().newBuilder().addPathSegment(query).build()
        val request = Request.Builder().url(url).apply {
            if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
        }.get()
        val response = httpClient.newCall(withHeaders(request, config).build())
            .execute().requireSuccess()
        return response.use {
            listOf(SearchResult(title = query, url = "", snippet = it.body.string()))
        }
    }

    private fun zhipu(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("search_query", query.take(70))
            addProperty("search_engine", "search_std")
            addProperty("search_intent", false)
            addProperty("count", config.resultLimit.coerceIn(1, 50))
            addProperty("content_size", "medium")
        }
        val base = config.baseUrl.ifBlank {
            "https://open.bigmodel.cn/api/paas/v4/web_search"
        }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        return commonResults(
            root.getAsJsonArray("search_result"),
            urlKeys = listOf("link", "url"),
            snippetKeys = listOf("content", "snippet")
        )
    }

    private fun linkup(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("q", query)
            addProperty("depth", "standard")
            addProperty("outputType", "searchResults")
        }
        val base = config.baseUrl.ifBlank { "https://api.linkup.so/v1/search" }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        return commonResults(
            root.getAsJsonArray("results"),
            titleKeys = listOf("name", "title"),
            snippetKeys = listOf("content", "snippet")
        )
    }

    private fun metaso(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("question", query)
            addProperty("stream", false)
        }
        val base = config.baseUrl.ifBlank { "https://metaso.cn/api/open/search/v2" }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        val data = root.getAsJsonObject("data") ?: root
        val answer = data.string("text").orEmpty()
        val references = commonResults(
            data.getAsJsonArray("references"),
            urlKeys = listOf("url", "link"),
            snippetKeys = listOf("snippet", "summary", "text")
        )
        return references.ifEmpty {
            listOf(SearchResult(title = query, url = "", snippet = answer))
        }.map { result ->
            if (result.snippet.isBlank()) result.copy(snippet = answer.take(1_200)) else result
        }
    }

    private fun ollama(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("max_results", config.resultLimit.coerceIn(1, 10))
        }
        val base = config.baseUrl.ifBlank { "https://ollama.com/api/web_search" }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        return commonResults(
            root.getAsJsonArray("results"),
            snippetKeys = listOf("content", "snippet")
        )
    }

    private fun perplexity(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("max_results", config.resultLimit.coerceIn(1, 20))
            addProperty("max_tokens_per_page", 1_024)
        }
        val base = config.baseUrl.ifBlank { "https://api.perplexity.ai/search" }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        return commonResults(root.getAsJsonArray("results"))
    }

    private fun bocha(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("summary", true)
            addProperty("freshness", "noLimit")
            addProperty("count", config.resultLimit.coerceIn(1, 50))
        }
        val base = config.baseUrl.ifBlank { "https://api.bocha.cn/v1/web-search" }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        return commonResults(
            root.getAsJsonObject("data")?.getAsJsonObject("webPages")
                ?.getAsJsonArray("value"),
            titleKeys = listOf("name", "title"),
            snippetKeys = listOf("summary", "snippet")
        )
    }

    private fun grok(config: SearchProviderConfig, query: String): List<SearchResult> {
        val body = JsonObject().apply {
            addProperty("model", "grok-4.6")
            add("input", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", query)
                })
            })
            add("tools", JsonArray().apply {
                add(JsonObject().apply { addProperty("type", "web_search") })
            })
        }
        val base = config.baseUrl.ifBlank { "https://api.x.ai/v1/responses" }
        val root = execute(
            jsonPost(base, body).header("Authorization", "Bearer ${config.apiKey}"),
            config
        )
        var answer = ""
        val results = mutableListOf<SearchResult>()
        root.getAsJsonArray("output")?.forEach { outputElement ->
            val output = outputElement.asJsonObject
            output.getAsJsonArray("content")?.forEach { contentElement ->
                val content = contentElement.asJsonObject
                content.string("text")?.let { answer += it }
                content.getAsJsonArray("annotations")?.forEach { annotationElement ->
                    val annotation = annotationElement.asJsonObject
                    val url = annotation.string("url")
                        ?: annotation.getAsJsonObject("url_citation")?.string("url")
                    if (!url.isNullOrBlank()) {
                        results += SearchResult(
                            title = annotation.string("title") ?: url,
                            url = url,
                            snippet = answer.takeLast(1_000)
                        )
                    }
                }
            }
        }
        return results.distinctBy(SearchResult::url).ifEmpty {
            listOf(SearchResult(title = query, url = "", snippet = answer))
        }
    }

    private fun generic(config: SearchProviderConfig, query: String): List<SearchResult> {
        require(config.baseUrl.isNotBlank()) { "自定义搜索服务必须填写 API URL" }
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("q", query)
            addProperty("count", config.resultLimit)
            if (config.apiKey.isNotBlank()) addProperty("api_key", config.apiKey)
        }
        val root = execute(jsonPost(config.baseUrl, body), config)
        val candidates = listOf("results", "data", "items", "organic")
            .firstNotNullOfOrNull(root::getAsJsonArray)
        if (candidates != null) return commonResults(candidates)
        val answer = root.string("answer") ?: root.string("content") ?: root.toString()
        return listOf(SearchResult(title = config.name, url = "", snippet = answer))
    }

    private fun commonResults(
        items: JsonArray?,
        titleKeys: List<String> = listOf("title", "name"),
        urlKeys: List<String> = listOf("url", "link"),
        snippetKeys: List<String> = listOf("snippet", "description", "content")
    ): List<SearchResult> = items?.mapNotNull { element ->
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val title = titleKeys.firstNotNullOfOrNull(item::string).orEmpty()
        val url = urlKeys.firstNotNullOfOrNull(item::string).orEmpty()
        val snippet = snippetKeys.firstNotNullOfOrNull(item::string).orEmpty()
        if (title.isBlank() && snippet.isBlank()) null else SearchResult(title, url, snippet)
    }.orEmpty()

    private fun jsonPost(url: String, body: JsonObject): Request.Builder = Request.Builder()
        .url(url)
        .post(gson.toJson(body).toRequestBody(JSON))

    private fun execute(builder: Request.Builder, config: SearchProviderConfig): JsonObject =
        httpClient.newCall(withHeaders(builder, config).build()).execute().requireSuccess().use {
            parseJson(it.body.string()).asJsonObject
        }

    private fun withHeaders(
        builder: Request.Builder,
        config: SearchProviderConfig
    ): Request.Builder = builder
        .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
        .apply { config.customHeaders.forEach { (name, value) -> header(name, value) } }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
