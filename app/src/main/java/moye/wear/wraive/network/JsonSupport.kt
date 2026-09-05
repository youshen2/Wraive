package moye.wear.wraive.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Response

internal fun JsonObject.merge(values: Map<String, Any?>, gson: Gson) {
    values.forEach { (key, value) ->
        val incoming = gson.toJsonTree(value)
        val current = get(key)
        if (current is JsonObject && incoming is JsonObject) {
            current.merge(incoming)
        } else {
            add(key, incoming)
        }
    }
}

private fun JsonObject.merge(incoming: JsonObject) {
    incoming.entrySet().forEach { (key, value) ->
        val current = get(key)
        if (current is JsonObject && value is JsonObject) {
            current.merge(value)
        } else {
            add(key, value)
        }
    }
}

internal fun parseJson(text: String): JsonElement = JsonParser.parseString(text)

internal fun Response.requireSuccess(): Response {
    if (isSuccessful) return this
    val errorBody = body.string().take(4_096)
    close()
    throw ProviderHttpException(code, errorBody.ifBlank { "HTTP $code" })
}

internal fun JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

internal fun JsonObject.int(name: String): Int =
    get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

internal fun JsonElement.objectOrNull(): JsonObject? =
    takeIf(JsonElement::isJsonObject)?.asJsonObject

internal fun JsonObject.objectOrNull(name: String): JsonObject? =
    get(name)?.objectOrNull()

internal fun JsonObject.arrayOrNull(name: String): JsonArray? =
    get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
