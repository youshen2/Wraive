package moye.wear.wraive.network

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import moye.wear.wraive.model.StreamEvent
import moye.wear.wraive.model.KeyRotationStrategy
import moye.wear.wraive.model.availableApiKeys
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class AiGateway(
    httpClient: OkHttpClient,
    gson: Gson,
    context: android.content.Context
) {
    private val openAi = OpenAiClient(httpClient, gson)
    private val anthropic = AnthropicClient(httpClient, gson)
    private val google = GoogleClient(httpClient, gson)
    private val imageGeneration = ImageGenerationClient(context, httpClient, gson)
    private val keyIndices = ConcurrentHashMap<String, AtomicInteger>()

    fun stream(request: CompletionRequest): Flow<StreamEvent> {
        return flow {
            var lastFailure: ProviderHttpException? = null
            for (provider in providersInAttemptOrder(request.provider)) {
                var emitted = false
                try {
                    client(provider).stream(request.copy(provider = provider)).collect { event ->
                        emitted = true
                        emit(event)
                    }
                    return@flow
                } catch (failure: ProviderHttpException) {
                    if (emitted || !failure.isRetryableKeyFailure()) throw failure
                    lastFailure = failure
                }
            }
            throw requireNotNull(lastFailure)
        }
    }

    suspend fun listModels(provider: ProviderConfig): List<ModelConfig> {
        var lastFailure: ProviderHttpException? = null
        for (selected in providersInAttemptOrder(provider)) {
            try {
                return client(selected).listModels(selected)
            } catch (failure: ProviderHttpException) {
                if (!failure.isRetryableKeyFailure()) throw failure
                lastFailure = failure
            }
        }
        throw requireNotNull(lastFailure)
    }

    suspend fun generateImages(request: ImageGenerationRequest): List<moye.wear.wraive.model.Attachment> {
        var lastFailure: ProviderHttpException? = null
        for (provider in providersInAttemptOrder(request.provider)) {
            try {
                return imageGeneration.generate(request.copy(provider = provider))
            } catch (failure: ProviderHttpException) {
                if (!failure.isRetryableKeyFailure()) throw failure
                lastFailure = failure
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun providersInAttemptOrder(provider: ProviderConfig): List<ProviderConfig> {
        val keys = provider.availableApiKeys()
        if (keys.isEmpty()) return listOf(provider.copy(apiKey = ""))
        if (keys.size == 1) return listOf(provider.copy(apiKey = keys.single()))
        val ordered = when (provider.keyRotation) {
            KeyRotationStrategy.RANDOM -> keys.shuffled(Random)
            KeyRotationStrategy.ROUND_ROBIN -> keyIndices
                .getOrPut(provider.id) { AtomicInteger() }
                .getAndIncrement()
                .mod(keys.size)
                .let { start -> List(keys.size) { offset -> keys[(start + offset) % keys.size] } }
        }
        return ordered.map { provider.copy(apiKey = it) }
    }

    private fun ProviderHttpException.isRetryableKeyFailure(): Boolean =
        statusCode == 401 || statusCode == 403 || statusCode == 408 || statusCode == 429 ||
            statusCode in 500..599

    private fun client(provider: ProviderConfig): ProviderClient = when (provider.protocol) {
        ProviderProtocol.OPENAI_CHAT,
        ProviderProtocol.OPENAI_RESPONSES -> openAi
        ProviderProtocol.ANTHROPIC -> anthropic
        ProviderProtocol.GOOGLE -> google
    }

    companion object {
        fun defaultHttpClient(proxyController: NetworkProxyController? = null): OkHttpClient =
            OkHttpClient.Builder()
            .apply {
                if (proxyController != null) {
                    proxySelector(proxyController)
                    proxyAuthenticator(proxyController.authenticator)
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
