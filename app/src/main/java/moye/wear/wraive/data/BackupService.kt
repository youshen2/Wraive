package moye.wear.wraive.data

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.AppPreferences
import moye.wear.wraive.model.CloudBackupConfig
import moye.wear.wraive.model.NetworkProxyConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.SpeechConfig
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.availableApiKeys
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupService(
    private val repository: AppRepository,
    private val preferencesStore: PreferencesStore,
    private val gson: Gson
) {
    suspend fun exportBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        export(output)
        return output.toByteArray()
    }

    suspend fun importBytes(bytes: ByteArray) {
        import(ByteArrayInputStream(bytes))
    }

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val bundle = BackupBundle(
            exportedAt = System.currentTimeMillis(),
            preferences = preferencesStore.preferences.value,
            data = repository.snapshot()
        )
        output.writer(Charsets.UTF_8).use { gson.toJson(bundle, it) }
    }

    suspend fun import(input: InputStream) = withContext(Dispatchers.IO) {
        val root = input.reader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        val version = root.get("formatVersion")?.asInt ?: 1
        require(version in 1..FORMAT_VERSION) {
            "不支持的备份版本 $version"
        }
        root.getAsJsonObject("preferences")?.fillMissing(
            gson.toJsonTree(AppPreferences()).asJsonObject
        )
        val data = root.getAsJsonObject("data") ?: error("备份缺少数据")
        listOf(
            "providers", "models", "assistants", "conversations", "messages", "memories",
            "worldBookEntries", "quickPhrases", "searchProviders", "mcpServers",
            "promptTransforms", "requestLogs"
        ).forEach { name ->
            if (!data.has(name) || data.get(name).isJsonNull) {
                data.add(name, com.google.gson.JsonArray())
            }
        }
        mapOf(
            "speechConfig" to SpeechConfig(),
            "cloudBackupConfig" to CloudBackupConfig(),
            "networkProxyConfig" to NetworkProxyConfig(),
            "transcriptionConfig" to TranscriptionConfig()
        ).forEach { (name, default) ->
            if (!data.has(name) || data.get(name).isJsonNull) data.add(name, gson.toJsonTree(default))
        }
        val bundle = gson.fromJson(root, BackupBundle::class.java)
        repository.restore(bundle.data)
        preferencesStore.replace(preferencesStore.normalized(bundle.preferences))
    }

    data class BackupBundle(
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAt: Long,
        val preferences: AppPreferences,
        val data: RepositorySnapshot
    )

    private companion object {
        const val FORMAT_VERSION = 2
    }
}

private fun JsonObject.fillMissing(defaults: JsonObject) {
    defaults.entrySet().forEach { (name, value) ->
        if (!has(name) || get(name).isJsonNull) add(name, value)
    }
}

class ProviderQrCodec(private val gson: Gson) {
    fun encode(provider: ProviderConfig): String {
        val data = Base64.encodeToString(
            gson.toJson(provider).toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "wraive://provider?data=$data"
    }

    fun decode(value: String): ProviderConfig {
        require(value.startsWith("wraive://provider?data=")) { "不是 Wraive 供应商二维码" }
        val data = value.substringAfter("data=")
        val json = Base64.decode(data, Base64.URL_SAFE).toString(Charsets.UTF_8)
        val provider = gson.fromJson(json, ProviderConfig::class.java)
        val rotation = runCatching { provider.keyRotation.name }
            .mapCatching(moye.wear.wraive.model.KeyRotationStrategy::valueOf)
            .getOrDefault(moye.wear.wraive.model.KeyRotationStrategy.ROUND_ROBIN)
        return provider.copy(
            name = provider.name.orEmpty().ifBlank { "Provider" },
            baseUrl = provider.baseUrl.orEmpty(),
            apiKey = provider.availableApiKeys().firstOrNull().orEmpty(),
            apiKeys = provider.availableApiKeys().drop(1),
            keyRotation = rotation,
            group = provider.group.orEmpty(),
            customHeaders = provider.customHeaders.orEmpty(),
            customBody = provider.customBody.orEmpty()
        )
    }

    fun bitmap(value: String, size: Int = 360): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    fun decode(bitmap: Bitmap): ProviderConfig {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val value = MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        ).text
        return decode(value)
    }
}
