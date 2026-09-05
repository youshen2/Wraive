package moye.wear.wraive.model

import java.util.UUID

enum class ProviderProtocol {
    OPENAI_CHAT,
    OPENAI_RESPONSES,
    ANTHROPIC,
    GOOGLE
}

enum class KeyRotationStrategy { ROUND_ROBIN, RANDOM }

enum class ModelCapability {
    TEXT,
    VISION,
    REASONING,
    TOOLS,
    WEB_SEARCH,
    IMAGE_GENERATION
}

internal fun normalizeModelCapabilities(
    capabilities: Set<*>?
): Set<ModelCapability> {
    val normalized = capabilities.orEmpty().mapNotNull { capability ->
        when (capability) {
            is ModelCapability -> capability
            is String -> ModelCapability.entries.firstOrNull { it.name == capability }
            else -> null
        }
    }.toSet()
    return normalized.ifEmpty { setOf(ModelCapability.TEXT) }
}

data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProviderProtocol,
    val baseUrl: String,
    val apiKey: String = "",
    val apiKeys: List<String> = emptyList(),
    val keyRotation: KeyRotationStrategy = KeyRotationStrategy.ROUND_ROBIN,
    val group: String = "",
    val enabled: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap(),
    val customBody: Map<String, Any?> = emptyMap(),
    val usePromptCaching: Boolean = false,
    val sortOrder: Int = 0
)

@Suppress("UNCHECKED_CAST")
fun ProviderConfig.availableApiKeys(): List<String> =
    (listOf(apiKey as String?) + (apiKeys as? List<String?>).orEmpty())
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()

data class ModelConfig(
    val id: String,
    val providerId: String,
    val displayName: String = id,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT),
    val maxContextTokens: Int = 128_000,
    val maxOutputTokens: Int = 8_192,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

data class ModelRef(
    val providerId: String,
    val modelId: String
)

fun ProviderProtocol.defaultBaseUrl(): String = when (this) {
    ProviderProtocol.OPENAI_CHAT,
    ProviderProtocol.OPENAI_RESPONSES -> "https://api.openai.com/v1"
    ProviderProtocol.ANTHROPIC -> "https://api.anthropic.com/v1"
    ProviderProtocol.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
}
