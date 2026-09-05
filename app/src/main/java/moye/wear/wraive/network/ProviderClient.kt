package moye.wear.wraive.network

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.StreamEvent

data class CompletionRequest(
    val provider: ProviderConfig,
    val model: ModelConfig,
    val assistant: AssistantProfile,
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val toolDefinitions: List<JsonObject> = emptyList()
)

interface ProviderClient {
    fun stream(request: CompletionRequest): Flow<StreamEvent>
    suspend fun listModels(provider: ProviderConfig): List<ModelConfig>
}

class ProviderHttpException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)
