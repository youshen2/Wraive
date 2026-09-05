package moye.wear.wraive.network

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.AttachmentKind
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

data class ImageGenerationRequest(
    val provider: ProviderConfig,
    val model: ModelConfig,
    val assistant: AssistantProfile,
    val prompt: String,
    val referenceImages: List<Attachment> = emptyList(),
    val temporary: Boolean = false
)

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String = "image/png",
    val revisedPrompt: String? = null
)

class ImageGenerationClient(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun generate(request: ImageGenerationRequest): List<Attachment> =
        withContext(Dispatchers.IO) {
            val images = when (request.provider.protocol) {
                ProviderProtocol.GOOGLE -> generateGoogle(request)
                ProviderProtocol.OPENAI_CHAT,
                ProviderProtocol.OPENAI_RESPONSES -> generateOpenAi(request)
                ProviderProtocol.ANTHROPIC -> error("Anthropic 暂不提供图片生成接口")
            }
            require(images.isNotEmpty()) { "图片服务没有返回图片" }
            images.mapIndexed { index, image -> save(image, index, request.temporary) }
        }

    private fun generateOpenAi(request: ImageGenerationRequest): List<GeneratedImage> {
        val references = request.referenceImages.filter { it.kind == AttachmentKind.IMAGE }
        val endpoint = if (references.isEmpty()) "images/generations" else "images/edits"
        val body = if (references.isEmpty()) {
            JsonObject().apply {
                addProperty("model", request.model.id)
                addProperty("prompt", request.prompt)
                addProperty("n", request.assistant.imageCount.coerceIn(1, 4))
                addProperty("size", request.assistant.imageSize)
                if (request.assistant.imageQuality != "auto") {
                    addProperty("quality", request.assistant.imageQuality)
                }
                merge(request.provider.customBody + request.assistant.customBody, gson)
            }.let { gson.toJson(it).toRequestBody(JSON) }
        } else {
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", request.model.id)
                .addFormDataPart("prompt", request.prompt)
                .addFormDataPart("n", request.assistant.imageCount.coerceIn(1, 4).toString())
                .addFormDataPart("size", request.assistant.imageSize)
                .apply {
                    if (request.assistant.imageQuality != "auto") {
                        addFormDataPart("quality", request.assistant.imageQuality)
                    }
                    references.forEachIndexed { index, attachment ->
                        val bytes = attachment.imageBytes()
                        addFormDataPart(
                            "image[]",
                            attachment.name.ifBlank { "reference-$index.png" },
                            bytes.toRequestBody(attachment.mimeType.toMediaType())
                        )
                    }
                }
                .build()
        }
        val httpRequest = Request.Builder()
            .url("${request.provider.baseUrl.trimEnd('/')}/$endpoint")
            .header("Authorization", "Bearer ${request.provider.apiKey}")
            .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
            .apply {
                request.provider.customHeaders.forEach { (name, value) -> header(name, value) }
                request.assistant.customHeaders.forEach { (name, value) -> header(name, value) }
            }
            .post(body)
            .build()
        return httpClient.newCall(httpRequest).execute().requireSuccess().use { response ->
            val root = parseJson(response.body.string()).asJsonObject
            root.getAsJsonArray("data")?.mapNotNull { element ->
                val item = element.asJsonObject
                val bytes = item.string("b64_json")?.let {
                    Base64.decode(it, Base64.DEFAULT)
                } ?: item.string("url")?.let { download(it, request.provider) }
                bytes?.let {
                    GeneratedImage(
                        bytes = it,
                        mimeType = detectMimeType(it),
                        revisedPrompt = item.string("revised_prompt")
                    )
                }
            }.orEmpty()
        }
    }

    private fun generateGoogle(request: ImageGenerationRequest): List<GeneratedImage> {
        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", request.prompt) })
                        request.referenceImages.filter { it.kind == AttachmentKind.IMAGE }
                            .forEach { attachment ->
                                val bytes = attachment.imageBytes()
                                add(JsonObject().apply {
                                    add("inlineData", JsonObject().apply {
                                        addProperty("mimeType", attachment.mimeType)
                                        addProperty(
                                            "data",
                                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        )
                                    })
                                })
                            }
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                add("responseModalities", JsonArray().apply {
                    add("TEXT")
                    add("IMAGE")
                })
            })
            merge(request.provider.customBody + request.assistant.customBody, gson)
        }
        val url = "${request.provider.baseUrl.trimEnd('/')}/models/" +
            "${request.model.id.substringAfter("models/")}:generateContent"
        val httpUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("key", request.provider.apiKey)
            .build()
        val httpRequest = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Wraive/${moye.wear.wraive.BuildConfig.VERSION_NAME}")
            .apply {
                request.provider.customHeaders.forEach { (name, value) -> header(name, value) }
                request.assistant.customHeaders.forEach { (name, value) -> header(name, value) }
            }
            .post(gson.toJson(payload).toRequestBody(JSON))
            .build()
        return httpClient.newCall(httpRequest).execute().requireSuccess().use { response ->
            val root = parseJson(response.body.string()).asJsonObject
            root.getAsJsonArray("candidates")?.flatMap { candidate ->
                candidate.asJsonObject.getAsJsonObject("content")
                    ?.getAsJsonArray("parts")
                    ?.mapNotNull { partElement ->
                        val inline = partElement.asJsonObject.getAsJsonObject("inlineData")
                            ?: partElement.asJsonObject.getAsJsonObject("inline_data")
                        inline?.string("data")?.let { encoded ->
                            GeneratedImage(
                                bytes = Base64.decode(encoded, Base64.DEFAULT),
                                mimeType = inline.string("mimeType")
                                    ?: inline.string("mime_type")
                                    ?: "image/png"
                            )
                        }
                    }.orEmpty()
            }.orEmpty()
        }
    }

    private fun Attachment.imageBytes(): ByteArray = when {
        uri.startsWith("data:") -> Base64.decode(uri.substringAfter(','), Base64.DEFAULT)
        uri.startsWith("file:") -> File(Uri.parse(uri).path ?: error("图片路径无效")).readBytes()
        uri.startsWith("content:") -> context.contentResolver.openInputStream(Uri.parse(uri))
            ?.use { it.readBytes() } ?: error("无法读取 ${name}")
        uri.startsWith("http") -> download(uri, null)
        else -> error("不支持的图片来源：$uri")
    }

    private fun download(url: String, provider: ProviderConfig?): ByteArray =
        httpClient.newCall(
            Request.Builder().url(url).apply {
                provider?.customHeaders?.forEach { (name, value) -> header(name, value) }
            }.build()
        ).execute().requireSuccess().use { it.body.bytes() }

    private fun save(image: GeneratedImage, index: Int, temporary: Boolean): Attachment {
        val root = if (temporary) context.cacheDir else context.filesDir
        val directory = File(root, "generated_images").apply { mkdirs() }
        val extension = when (image.mimeType) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val file = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension")
        file.writeBytes(image.bytes)
        return Attachment(
            kind = AttachmentKind.IMAGE,
            name = "生成图片 ${index + 1}",
            uri = Uri.fromFile(file).toString(),
            mimeType = image.mimeType,
            extractedText = image.revisedPrompt,
            sizeBytes = image.bytes.size.toLong()
        )
    }

    private fun detectMimeType(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 12 && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" ->
            "image/webp"
        else -> "image/png"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
