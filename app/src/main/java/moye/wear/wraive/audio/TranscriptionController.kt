package moye.wear.wraive.audio

import android.content.Context
import android.media.MediaRecorder
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.TranscriptionEngine
import moye.wear.wraive.network.parseJson
import moye.wear.wraive.network.requireSuccess
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class TranscriptionController(
    context: Context,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private val cacheDir = context.applicationContext.cacheDir
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    @Suppress("DEPRECATION")
    fun startRecording() {
        cancel()
        val file = File.createTempFile("wraive-recording-", ".awb", cacheDir)
        val mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(23_850)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recordingFile = file
        recorder = mediaRecorder
    }

    suspend fun stopAndTranscribe(config: TranscriptionConfig): String =
        withContext(Dispatchers.IO) {
            val active = recorder ?: error("没有正在进行的录音")
            val file = recordingFile ?: error("录音文件不存在")
            recorder = null
            recordingFile = null
            try {
                active.stop()
                active.release()
                require(file.length() > 0) { "录音内容为空" }
                require(config.apiKey.isNotBlank()) { "请先填写转写服务 API Key" }
                when (config.engine) {
                    TranscriptionEngine.OPENAI -> transcribeOpenAi(file, config)
                    TranscriptionEngine.GOOGLE -> transcribeGoogle(file, config)
                    TranscriptionEngine.SYSTEM -> error("系统语音输入不需要录音上传")
                }
            } finally {
                runCatching { active.release() }
                file.delete()
            }
        }

    fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    private fun transcribeOpenAi(file: File, config: TranscriptionConfig): String {
        val endpoint = config.endpoint.ifBlank {
            "https://api.openai.com/v1/audio/transcriptions"
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model.ifBlank { "gpt-4o-mini-transcribe" })
            .addFormDataPart("language", config.languageCode.substringBefore('-'))
            .addFormDataPart(
                "file",
                file.name,
                file.readBytes().toRequestBody("audio/amr-wb".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            parseJson(response.body.string()).asJsonObject.get("text")?.asString
                ?: error("转写服务没有返回文本")
        }
    }

    private fun transcribeGoogle(file: File, config: TranscriptionConfig): String {
        val endpoint = config.endpoint.ifBlank {
            "https://speech.googleapis.com/v1/speech:recognize"
        }.toHttpUrl().newBuilder().addQueryParameter("key", config.apiKey).build()
        val body = JsonObject().apply {
            add("config", JsonObject().apply {
                addProperty("encoding", "AMR_WB")
                addProperty("sampleRateHertz", 16_000)
                addProperty("languageCode", config.languageCode)
                config.model.takeIf(String::isNotBlank)?.let { addProperty("model", it) }
            })
            add("audio", JsonObject().apply {
                addProperty(
                    "content",
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                )
            })
        }
        val request = Request.Builder().url(endpoint)
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            val root = parseJson(response.body.string()).asJsonObject
            root.getAsJsonArray("results").orEmpty().joinToString(" ") { result ->
                result.asJsonObject.getAsJsonArray("alternatives")
                    ?.firstOrNull()?.asJsonObject?.get("transcript")?.asString.orEmpty()
            }.trim().ifBlank { error("转写服务没有返回文本") }
        }
    }
}

private fun JsonArray?.orEmpty(): Iterable<com.google.gson.JsonElement> = this ?: emptyList()
