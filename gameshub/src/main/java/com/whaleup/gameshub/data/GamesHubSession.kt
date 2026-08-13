package com.whaleup.gameshub.data

import android.util.Log
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.network.APIBridge
import com.whaleup.gameshub.ui.HubStrings

/**
 * Global session holder for the Biome SDK.
 * Stores the BiomeSdkProps passed at launch time.
 */
object GamesHubSession {
    private var _props: BiomeSdkProps? = null
    var props: BiomeSdkProps?
        get() = _props
        set(value) {
            val candidate = value?.let { props ->
                if (props.userConfig.userId.isBlank()) {
                    val fallbackConfig = BiomeState.getUserConfig()
                    if (fallbackConfig != null) {
                        Log.w("GamesHubSession", "Replacing blank userId config with persisted config")
                        props.copy(userConfig = fallbackConfig)
                    } else {
                        Log.w("GamesHubSession", "Ignoring props with blank userId and no persisted config")
                        _props
                    }
                } else {
                    props
                }
            }
            candidate?.userConfig?.let { config ->
                PlayerPrefsManager.setUserId(config.userId)
                val persistedSessionId = PlayerPrefsManager.get("sessionId") as? String
                val resolvedSessionId = candidate.sessionId.takeIf { it.isUsableSessionId() }
                    ?: config.sessionId.takeIf { it.isUsableSessionId() }
                    ?: persistedSessionId.takeIf { it.isUsableSessionId() }
                    ?: java.util.UUID.randomUUID().toString()
                val resolved = candidate.copy(
                    userConfig = config.copy(sessionId = resolvedSessionId)
                )
                _props = resolved
                BiomeState.setUserConfig(resolved.userConfig)
                BiomeState.hydrateUserProfile(config.userId)
                BiomeState.setSessionId(resolvedSessionId)
                APIBridge.setSessionId(resolvedSessionId)
                APIBridge.userAgent = resolved.userConfig.userAgent
                APIBridge.timezone = resolved.userConfig.timezone
            }
            if (candidate == null) _props = null
        }

    private fun String?.isUsableSessionId(): Boolean =
        !isNullOrBlank() && this != "sessionId"

    /**
     * Initialize state managers that need context.
     * Call this from the launcher before starting any activity.
     */
    fun initialize(context: android.content.Context) {
        BiomeState.init(context)
        PlayerPrefsManager.init(context)
        APIBridge.init(context)
    }

    /** Remove only the current user's persisted data and all SDK runtime state. */
    fun logout() {
        PlayerPrefsManager.deleteAll()
        BiomeState.reset()
        APIBridge.resetSession()
        CatalogCache.clear()
        HubStrings.reset()
        PlayerPrefsManager.clearUserId()
        _props = null
    }
}
