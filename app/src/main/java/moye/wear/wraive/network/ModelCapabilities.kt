package moye.wear.wraive.network

import moye.wear.wraive.model.ModelCapability

internal fun inferCapabilities(modelId: String): Set<ModelCapability> {
    val id = modelId.lowercase()
    return buildSet {
        add(ModelCapability.TEXT)
        if (listOf("gpt-4", "gpt-5", "vision", "gemini", "claude-3", "claude-4", "qwen-vl", "omni").any(id::contains)) {
            add(ModelCapability.VISION)
        }
        if (listOf("reason", "thinking", "deepseek-r", "o1", "o3", "o4", "gpt-5", "gemini-2.5", "gemini-3", "claude-4").any(id::contains)) {
            add(ModelCapability.REASONING)
        }
        if (!id.contains("embedding") && !id.contains("tts")) {
            add(ModelCapability.TOOLS)
        }
        if (listOf("image", "dall-e", "imagen", "flux").any(id::contains)) {
            add(ModelCapability.IMAGE_GENERATION)
        }
    }
}
