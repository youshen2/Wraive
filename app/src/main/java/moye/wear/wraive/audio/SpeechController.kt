package moye.wear.wraive.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.SpeechConfig
import moye.wear.wraive.model.SpeechEngine
import moye.wear.wraive.network.requireSuccess
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale

class SpeechController(
    context: Context,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ready = false
    private var pendingSystemText: String? = null
    private var networkJob: Job? = null
    private var player: MediaPlayer? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            pendingSystemText?.let(::speakSystem)
            pendingSystemText = null
        }
    }

    fun speak(text: String, config: SpeechConfig, onError: (Throwable) -> Unit = {}) {
        stop()
        if (config.engine == SpeechEngine.SYSTEM) {
            tts.setSpeechRate(config.speed.coerceIn(0.5f, 2f))
            speakSystem(text)
            return
        }
        networkJob = scope.launch {
            try {
                require(config.apiKey.isNotBlank()) { "请先填写语音服务 API Key" }
                val audio = synthesize(text, config)
                val file = File.createTempFile("wraive-speech-", ".mp3", appContext.cacheDir)
                file.writeBytes(audio)
                withContext(Dispatchers.Main) {
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            it.release()
                            if (player === it) player = null
                            file.delete()
                        }
                        setOnErrorListener { mediaPlayer, _, _ ->
                            mediaPlayer.release()
                            if (player === mediaPlayer) player = null
                            file.delete()
                            onError(IllegalStateException("音频播放失败"))
                            true
                        }
                        prepare()
                        start()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) { onError(error) }
            }
        }
    }

    fun stop() {
        pendingSystemText = null
        tts.stop()
        networkJob?.cancel()
        networkJob = null
        player?.release()
        player = null
    }

    fun close() {
        stop()
        tts.shutdown()
    }

    private fun speakSystem(text: String) {
        if (!ready) {
            pendingSystemText = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wraive-message")
    }

    private fun synthesize(text: String, config: SpeechConfig): ByteArray = when (config.engine) {
        SpeechEngine.OPENAI -> openAi(text, config)
        SpeechEngine.ELEVENLABS -> elevenLabs(text, config)
        SpeechEngine.GOOGLE -> google(text, config)
        SpeechEngine.SYSTEM -> error("系统语音不需要网络合成")
    }

    private fun openAi(text: String, config: SpeechConfig): ByteArray {
        val endpoint = config.endpoint.ifBlank { "https://api.openai.com/v1/audio/speech" }
        val body = JsonObject().apply {
            addProperty("model", config.model.ifBlank { "gpt-4o-mini-tts" })
            addProperty("voice", config.voice.ifBlank { "alloy" })
            addProperty("input", text)
            addProperty("speed", config.speed.coerceIn(0.5f, 2f))
            addProperty("response_format", "mp3")
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { it.body.bytes() }
    }

    private fun elevenLabs(text: String, config: SpeechConfig): ByteArray {
        val endpoint = config.endpoint.ifBlank {
            "https://api.elevenlabs.io/v1/text-to-speech/${config.voice}"
        }
        val body = JsonObject().apply {
            addProperty("text", text)
            addProperty("model_id", config.model.ifBlank { "eleven_multilingual_v2" })
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("xi-api-key", config.apiKey)
            .header("Accept", "audio/mpeg")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { it.body.bytes() }
    }

    private fun google(text: String, config: SpeechConfig): ByteArray {
        val endpoint = config.endpoint.ifBlank {
            "https://texttospeech.googleapis.com/v1/text:synthesize"
        }.toHttpUrl().newBuilder().addQueryParameter("key", config.apiKey).build()
        val body = JsonObject().apply {
            add("input", JsonObject().apply { addProperty("text", text) })
            add("voice", JsonObject().apply {
                addProperty("languageCode", config.languageCode)
                config.voice.takeIf(String::isNotBlank)?.let { addProperty("name", it) }
            })
            add("audioConfig", JsonObject().apply {
                addProperty("audioEncoding", "MP3")
                addProperty("speakingRate", config.speed.coerceIn(0.5f, 2f))
            })
        }
        val request = Request.Builder().url(endpoint)
            .post(gson.toJson(body).toRequestBody(JSON)).build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            val encoded = gson.fromJson(response.body.string(), JsonObject::class.java)
                .get("audioContent")?.asString ?: error("语音服务没有返回音频")
            Base64.decode(encoded, Base64.DEFAULT)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
