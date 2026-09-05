package moye.wear.wraive.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moye.wear.wraive.model.AppPreferences

class PreferencesStore(context: Context, private val gson: Gson) {
    private val store = context.getSharedPreferences("wraive_preferences", Context.MODE_PRIVATE)
    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<AppPreferences> = _preferences

    fun update(transform: (AppPreferences) -> AppPreferences) {
        val updated = normalized(transform(_preferences.value))
        store.edit().putString(KEY, gson.toJson(updated)).apply()
        _preferences.value = updated
    }

    fun replace(value: AppPreferences) {
        val safe = normalized(value)
        store.edit().putString(KEY, gson.toJson(safe)).apply()
        _preferences.value = safe
    }

    private fun load(): AppPreferences = store.getString(KEY, null)
        ?.let { json ->
            val incoming = JsonParser.parseString(json).asJsonObject
            gson.toJsonTree(AppPreferences()).asJsonObject.entrySet().forEach { (name, value) ->
                if (!incoming.has(name) || incoming.get(name).isJsonNull) incoming.add(name, value)
            }
            gson.fromJson(incoming, AppPreferences::class.java)
        }
        ?: AppPreferences()

    internal fun normalized(value: AppPreferences): AppPreferences = value.copy(
        localeTag = value.localeTag.orEmpty().ifBlank { "system" },
        customFontPath = value.customFontPath.orEmpty()
    )

    private companion object {
        const val KEY = "preferences"
    }
}
