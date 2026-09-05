package moye.wear.wraive.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.CloudBackupConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CloudBackupService(
    private val repository: AppRepository,
    private val backupService: BackupService,
    private val httpClient: OkHttpClient
) {
    suspend fun upload(config: CloudBackupConfig) = withContext(Dispatchers.IO) {
        validate(config)
        val encrypted = encrypt(backupService.exportBytes(), config.encryptionPassword)
        execute(config, "PUT", encrypted).use { response ->
            if (!response.isSuccessful) {
                error("S3 上传失败 HTTP ${response.code}：${response.body.string().take(500)}")
            }
        }
        repository.upsertCloudBackupConfig(
            config.copy(enabled = true, lastSyncedAt = System.currentTimeMillis())
        )
    }

    suspend fun restore(config: CloudBackupConfig) = withContext(Dispatchers.IO) {
        validate(config)
        val encrypted = execute(config, "GET", null).use { response ->
            if (!response.isSuccessful) {
                error("S3 下载失败 HTTP ${response.code}：${response.body.string().take(500)}")
            }
            response.body.bytes()
        }
        backupService.importBytes(decrypt(encrypted, config.encryptionPassword))
        repository.upsertCloudBackupConfig(
            config.copy(enabled = true, lastSyncedAt = System.currentTimeMillis())
        )
    }

    private fun execute(
        config: CloudBackupConfig,
        method: String,
        body: ByteArray?
    ): okhttp3.Response {
        val url = objectUrl(config)
        val now = Date()
        val amzDate = format(now, "yyyyMMdd'T'HHmmss'Z'")
        val date = format(now, "yyyyMMdd")
        val payloadHash = if (body == null) EMPTY_SHA256 else sha256(body)
        val canonicalHost = if (
            (url.isHttps && url.port == 443) || (!url.isHttps && url.port == 80)
        ) url.host else "${url.host}:${url.port}"
        val canonicalHeaders = buildString {
            append("host:$canonicalHost\n")
            append("x-amz-content-sha256:$payloadHash\n")
            append("x-amz-date:$amzDate\n")
        }
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = listOf(
            method,
            url.encodedPath,
            url.encodedQuery.orEmpty(),
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")
        val scope = "$date/${config.region}/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")
        val signingKey = signatureKey(config.secretAccessKey, date, config.region)
        val signature = hmac(signingKey, stringToSign).toHex()
        val authorization = "AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
        val request = Request.Builder()
            .url(url)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("Host", canonicalHost)
            .header("Authorization", authorization)
            .apply {
                if (method == "PUT") {
                    put((body ?: ByteArray(0)).toRequestBody(OCTET_STREAM))
                } else {
                    get()
                }
            }
            .build()
        return httpClient.newCall(request).execute()
    }

    private fun objectUrl(config: CloudBackupConfig): HttpUrl {
        val base = config.endpoint.trimEnd('/').toHttpUrl()
        val builder = base.newBuilder()
        if (config.pathStyle) {
            builder.addPathSegment(config.bucket)
        } else {
            builder.host("${config.bucket}.${base.host}")
        }
        config.objectKey.trim('/').split('/').filter(String::isNotBlank)
            .forEach(builder::addPathSegment)
        return builder.build()
    }

    private fun validate(config: CloudBackupConfig) {
        require(config.endpoint.startsWith("https://")) { "S3 Endpoint 必须使用 HTTPS" }
        require(config.region.isNotBlank()) { "请填写 S3 Region" }
        require(config.bucket.isNotBlank()) { "请填写 S3 Bucket" }
        require(config.objectKey.isNotBlank()) { "请填写备份对象路径" }
        require(config.accessKeyId.isNotBlank()) { "请填写 Access Key ID" }
        require(config.secretAccessKey.isNotBlank()) { "请填写 Secret Access Key" }
        require(config.encryptionPassword.length >= 8) { "云备份加密密码至少 8 位" }
    }

    private fun encrypt(plain: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(password, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + salt.size + iv.size + encrypted.size)
            .put(MAGIC).put(salt).put(iv).put(encrypted).array()
    }

    private fun decrypt(payload: ByteArray, password: String): ByteArray {
        require(payload.size > MAGIC.size + 28) { "云备份文件无效" }
        val buffer = ByteBuffer.wrap(payload)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "不是 Wraive 加密云备份" }
        val salt = ByteArray(16).also(buffer::get)
        val iv = ByteArray(12).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun encryptionKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun signatureKey(secret: String, date: String, region: String): ByteArray {
        val dateKey = hmac(("AWS4$secret").toByteArray(Charsets.UTF_8), date)
        val regionKey = hmac(dateKey, region)
        val serviceKey = hmac(regionKey, "s3")
        return hmac(serviceKey, "aws4_request")
    }

    private fun hmac(key: ByteArray, value: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(value.toByteArray(Charsets.UTF_8))
        }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun format(date: Date, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)

    private companion object {
        val MAGIC = "WRAIVEENC1".toByteArray(Charsets.US_ASCII)
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
