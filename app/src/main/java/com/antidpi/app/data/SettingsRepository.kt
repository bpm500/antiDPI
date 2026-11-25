package com.antidpi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.antidpi.app.model.AppSettings
import com.antidpi.app.model.ConfigRating
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "antidpi_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PARALLEL_SLOTS = intPreferencesKey("parallel_slots")
        val BG_PROMPT_DISMISSED = booleanPreferencesKey("bg_prompt_dismissed")
        val SELECTED_CONFIG = stringPreferencesKey("selected_config")
        val RATINGS_JSON = stringPreferencesKey("ratings_json")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            parallelSlots = (prefs[Keys.PARALLEL_SLOTS] ?: 3).coerceIn(1, 6),
            bgPromptDismissed = prefs[Keys.BG_PROMPT_DISMISSED] ?: false,
            selectedConfigName = prefs[Keys.SELECTED_CONFIG]
        )
    }

    val ratingsFlow: Flow<Map<String, ConfigRating>> = context.dataStore.data.map { prefs ->
        parseRatings(prefs[Keys.RATINGS_JSON])
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun setParallelSlots(slots: Int) {
        context.dataStore.edit { it[Keys.PARALLEL_SLOTS] = slots.coerceIn(1, 6) }
    }

    suspend fun setBgPromptDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.BG_PROMPT_DISMISSED] = dismissed }
    }

    suspend fun setSelectedConfig(name: String?) {
        context.dataStore.edit {
            if (name == null) it.remove(Keys.SELECTED_CONFIG)
            else it[Keys.SELECTED_CONFIG] = name
        }
    }

    suspend fun updateRating(configName: String, ok: Int, total: Int, avgMs: Int) {
        context.dataStore.edit { prefs ->
            val current = parseRatings(prefs[Keys.RATINGS_JSON]).toMutableMap()
            current[configName] = ConfigRating(ok, total, avgMs)
            prefs[Keys.RATINGS_JSON] = encodeRatings(current)
        }
    }

    suspend fun setAllRatings(ratings: Map<String, ConfigRating>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RATINGS_JSON] = encodeRatings(ratings)
        }
    }

    suspend fun renameRatingKey(oldName: String, newName: String) {
        context.dataStore.edit { prefs ->
            val current = parseRatings(prefs[Keys.RATINGS_JSON]).toMutableMap()
            val value = current.remove(oldName)
            if (value != null) {
                current[newName] = value
                prefs[Keys.RATINGS_JSON] = encodeRatings(current)
            }
        }
    }

    suspend fun removeRatingKey(name: String) {
        context.dataStore.edit { prefs ->
            val current = parseRatings(prefs[Keys.RATINGS_JSON]).toMutableMap()
            if (current.remove(name) != null) {
                prefs[Keys.RATINGS_JSON] = encodeRatings(current)
            }
        }
    }

    private fun parseRatings(jsonStr: String?): Map<String, ConfigRating> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(jsonStr)
            val result = mutableMapOf<String, ConfigRating>()
            for (key in root.keys()) {
                val obj = root.optJSONObject(key) ?: continue
                result[key] = ConfigRating(
                    ok = obj.optInt("ok", 0),
                    total = obj.optInt("total", 0),
                    avgMs = obj.optInt("avgMs", 9999)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeRatings(map: Map<String, ConfigRating>): String {
        val root = JSONObject()
        for ((k, v) in map) {
            root.put(k, JSONObject().apply {
                put("ok", v.ok)
                put("total", v.total)
                put("avgMs", v.avgMs)
            })
        }
        return root.toString()
    }
}
