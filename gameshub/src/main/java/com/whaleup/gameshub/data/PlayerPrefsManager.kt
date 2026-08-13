package com.whaleup.gameshub.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

private const val TAG = "PlayerPrefsManager"
private const val PREFS_PREFIX = "biome_playerprefs_"
private const val MIGRATION_FLAG_PREFIX = "biome_migration_"

/**
 * Per-user key-value preference storage.
 * Kotlin port of Whaleup's PlayerPrefsManager.ts.
 *
 * Stores typed values with type metadata for accurate retrieval.
 * Supports migration from WebView localStorage data.
 */
object PlayerPrefsManager {

    private var prefs: SharedPreferences? = null
    private var userId: String? = null
    private var isReady = false
    private val cache = mutableMapOf<String, Any?>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("biome_player_prefs", Context.MODE_PRIVATE)
    }

    fun setUserId(userId: String) {
        this.userId = userId
        this.isReady = true
        cache.clear()
    }

    fun clearUserId() {
        userId = null
        isReady = false
        cache.clear()
    }

    private fun ensureReady(action: String): Boolean {
        if (!isReady || userId == null) {
            Log.e(TAG, "$action called before userId set")
            return false
        }
        return true
    }

    private fun storageKey(key: String): String {
        val resolvedUser = userId ?: "default"
        return "${PREFS_PREFIX}${resolvedUser}_$key"
    }

    private fun migrationFlagKey(): String {
        val resolvedUser = userId ?: "default"
        return "${MIGRATION_FLAG_PREFIX}$resolvedUser"
    }

    /**
     * Get a preference value, returning defaultValue if not found.
     */
    fun get(key: String, defaultValue: Any? = null): Any? {
        if (!ensureReady("Get")) return defaultValue

        // Check cache first
        if (cache.containsKey(key)) {
            Log.d(TAG, "Get (Cache): $key=${cache[key]}")
            return cache[key]
        }

        return try {
            val raw = prefs?.getString(storageKey(key), null) ?: run {
                cache[key] = defaultValue
                return defaultValue
            }

            val stored = JSONObject(raw)
            val type = stored.optString("type", "string")
            val value: Any? = when (type) {
                "string" -> stored.optString("value")
                "number" -> stored.optDouble("value")
                "boolean" -> stored.optBoolean("value")
                "null" -> null
                "object" -> stored.opt("value") // returns as-is (JSONObject/JSONArray)
                else -> stored.optString("value")
            }
            cache[key] = value
            Log.d(TAG, "Get (Disk): $key=$value")
            value
        } catch (e: Exception) {
            // Legacy plain string value
            val raw = prefs?.getString(storageKey(key), null)
            cache[key] = raw ?: defaultValue
            raw ?: defaultValue
        }
    }

    /**
     * Set a preference value with type preservation.
     */
    fun set(key: String, value: Any?) {
        if (!ensureReady("Set")) return

        try {
            val type = when {
                value == null -> "null"
                value is String -> "string"
                value is Number -> "number"
                value is Boolean -> "boolean"
                else -> "object"
            }

            val stored = JSONObject().apply {
                put("value", value)
                put("type", type)
            }

            cache[key] = value
            Log.d(TAG, "Set: $key=$value")
            prefs?.edit()?.putString(storageKey(key), stored.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Set failed for key: $key", e)
        }
    }

    /**
     * Delete a preference.
     */
    fun delete(key: String) {
        if (!ensureReady("Delete")) return
        prefs?.edit()?.remove(storageKey(key))?.apply()
        cache.remove(key)
    }

    /**
     * Delete all preferences for the current user.
     */
    fun deleteAll() {
        if (!ensureReady("DeleteAll")) return

        val prefix = "${PREFS_PREFIX}${userId}_"
        val editor = prefs?.edit() ?: return
        prefs?.all?.keys?.filter { it.startsWith(prefix) }?.forEach { editor.remove(it) }
        editor.remove(migrationFlagKey())
        editor.apply()
        cache.clear()
    }

    /**
     * Migrate from WebView localStorage data.
     * Only runs once per user (tracked via migration flag).
     */
    fun migrateFromLocalStorage(data: Map<String, String>) {
        if (!ensureReady("migrateFromLocalStorage")) return

        // Check if already migrated
        if (prefs?.getBoolean(migrationFlagKey(), false) == true) return

        try {
            for ((rawKey, rawValue) in data) {
                val cleanKey = rawKey.replace(Regex("^(Hike|Whaleup)_[^_]+_"), "")
                val parsedValue: Any? = try {
                    JSONObject(rawValue).let { it } // try as JSON object
                } catch (_: Exception) {
                    try {
                        rawValue.toDouble() // try as number
                    } catch (_: Exception) {
                        rawValue // keep as string
                    }
                }
                set(cleanKey, parsedValue)
            }
            prefs?.edit()?.putBoolean(migrationFlagKey(), true)?.apply()
            Log.d(TAG, "Migration completed for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
    }
}
