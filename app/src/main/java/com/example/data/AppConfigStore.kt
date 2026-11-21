package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.licenseAppsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "license_admin_apps_store"
)

class AppConfigStore(
    context: Context,
    private val defaultDatabaseId: String
) {
    private val dataStore = context.applicationContext.licenseAppsDataStore

    private val defaultApp = ManagedAppConfig(
        id = DEFAULT_APP_ID,
        name = "Catat Jualan",
        codePrefix = "JUALAN",
        databaseId = defaultDatabaseId,
        isDefault = true
    )

    val apps: Flow<List<ManagedAppConfig>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_APPS_JSON]
        if (raw.isNullOrBlank()) {
            listOf(defaultApp)
        } else {
            parseApps(raw).ifEmpty { listOf(defaultApp) }
        }
    }

    val selectedAppId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_APP_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_ID
    }

    val isDarkTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IS_DARK_THEME] ?: true
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_DARK_THEME] = enabled
        }
    }

    suspend fun selectApp(appId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_APP_ID] = appId
        }
    }

    suspend fun addApp(name: String, codePrefix: String, databaseId: String): ManagedAppConfig {
        val cleanName = name.trim()
        val cleanPrefix = sanitizePrefix(codePrefix)
        val cleanDbId = databaseId.trim().ifBlank { defaultDatabaseId }
        val newApp = ManagedAppConfig(
            id = "app_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            codePrefix = cleanPrefix,
            databaseId = cleanDbId,
            isDefault = false
        )
        dataStore.edit { prefs ->
            val current = parseApps(prefs[KEY_APPS_JSON]).ifEmpty { listOf(defaultApp) }
            val updated = current + newApp
            prefs[KEY_APPS_JSON] = serializeApps(updated)
            prefs[KEY_SELECTED_APP_ID] = newApp.id
        }
        return newApp
    }

    suspend fun updateApp(
        appId: String,
        name: String,
        codePrefix: String,
        databaseId: String
    ) {
        val cleanName = name.trim()
        val cleanPrefix = sanitizePrefix(codePrefix)
        val cleanDbId = databaseId.trim().ifBlank { defaultDatabaseId }
        dataStore.edit { prefs ->
            val current = parseApps(prefs[KEY_APPS_JSON]).ifEmpty { listOf(defaultApp) }
            val updated = current.map { existing ->
                if (existing.id == appId) {
                    existing.copy(
                        name = cleanName.ifBlank { existing.name },
                        codePrefix = cleanPrefix,
                        databaseId = cleanDbId
                    )
                } else {
                    existing
                }
            }
            prefs[KEY_APPS_JSON] = serializeApps(updated)
        }
    }

    suspend fun updateDatabaseId(appId: String, databaseId: String) {
        val cleanDbId = databaseId.trim().ifBlank { defaultDatabaseId }
        dataStore.edit { prefs ->
            val current = parseApps(prefs[KEY_APPS_JSON]).ifEmpty { listOf(defaultApp) }
            val updated = current.map { existing ->
                if (existing.id == appId) existing.copy(databaseId = cleanDbId) else existing
            }
            prefs[KEY_APPS_JSON] = serializeApps(updated)
        }
    }

    suspend fun deleteApp(appId: String): Boolean {
        var deleted = false
        dataStore.edit { prefs ->
            val current = parseApps(prefs[KEY_APPS_JSON]).ifEmpty { listOf(defaultApp) }
            val remaining = current.filterNot { it.id == appId }
            if (remaining.size < current.size) {
                deleted = true
                val finalApps = remaining.ifEmpty { listOf(defaultApp) }
                prefs[KEY_APPS_JSON] = serializeApps(finalApps)
                if (prefs[KEY_SELECTED_APP_ID] == appId || finalApps.none { it.id == prefs[KEY_SELECTED_APP_ID] }) {
                    prefs[KEY_SELECTED_APP_ID] = finalApps.first().id
                }
            }
        }
        return deleted
    }

    private fun parseApps(raw: String?): List<ManagedAppConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val prefix = obj.optString("codePrefix").ifBlank { "APP" }
                    val dbId = obj.optString("databaseId").ifBlank { defaultDatabaseId }
                    val isDef = obj.optBoolean("isDefault", id == DEFAULT_APP_ID)
                    add(
                        ManagedAppConfig(
                            id = id,
                            name = name,
                            codePrefix = prefix,
                            databaseId = dbId,
                            isDefault = isDef
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeApps(apps: List<ManagedAppConfig>): String {
        val array = JSONArray()
        apps.forEach { app ->
            val obj = JSONObject().apply {
                put("id", app.id)
                put("name", app.name)
                put("codePrefix", app.normalizedPrefix)
                put("databaseId", app.databaseId)
                put("isDefault", app.isDefault)
            }
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        const val DEFAULT_APP_ID = "catat_jualan_default"
        private val KEY_APPS_JSON = stringPreferencesKey("managed_apps_json")
        private val KEY_SELECTED_APP_ID = stringPreferencesKey("selected_app_id")
        private val KEY_IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")

        fun sanitizePrefix(raw: String): String {
            val cleaned = raw.trim().uppercase()
                .trimEnd('-')
                .replace(Regex("[^A-Z0-9_]"), "")
            return cleaned.take(20).ifBlank { "APP" }
        }
    }
}
