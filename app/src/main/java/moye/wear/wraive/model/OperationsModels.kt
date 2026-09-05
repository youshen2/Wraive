package moye.wear.wraive.model

import java.util.UUID

enum class SpeechEngine { SYSTEM, OPENAI, ELEVENLABS, GOOGLE }

enum class TranscriptionEngine { SYSTEM, OPENAI, GOOGLE }

data class SpeechConfig(
    val id: String = DEFAULT_ID,
    val engine: SpeechEngine = SpeechEngine.SYSTEM,
    val name: String = "系统语音",
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini-tts",
    val voice: String = "alloy",
    val languageCode: String = "zh-CN",
    val speed: Float = 1f
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

data class TranscriptionConfig(
    val id: String = DEFAULT_ID,
    val engine: TranscriptionEngine = TranscriptionEngine.SYSTEM,
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini-transcribe",
    val languageCode: String = "zh-CN"
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

data class CloudBackupConfig(
    val id: String = DEFAULT_ID,
    val endpoint: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val objectKey: String = "wraive/backup.wraive",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val encryptionPassword: String = "",
    val pathStyle: Boolean = true,
    val enabled: Boolean = false,
    val lastSyncedAt: Long? = null
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

enum class ProxyType { HTTP, SOCKS }

data class NetworkProxyConfig(
    val id: String = DEFAULT_ID,
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
    val bypassHosts: List<String> = listOf("localhost", "127.0.0.1")
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

enum class RequestStatus { RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class RequestLog(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val providerId: String,
    val modelId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: RequestStatus = RequestStatus.RUNNING,
    val usage: TokenUsage = TokenUsage(),
    val error: String? = null,
    val responsePreview: String = ""
)

data class MessageSearchHit(
    val conversation: Conversation,
    val message: ChatMessage
)

data class UsageSummary(
    val conversations: Int,
    val messages: Int,
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long,
    val failures: Int,
    val activeDays: Int,
    val daily: List<DailyUsage>
) {
    val totalTokens: Long get() = inputTokens + outputTokens
}

data class DailyUsage(
    val dayStart: Long,
    val requests: Int,
    val tokens: Long
)
