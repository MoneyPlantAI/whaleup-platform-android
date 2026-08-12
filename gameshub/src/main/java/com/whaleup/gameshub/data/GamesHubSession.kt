package com.whaleup.gameshub.data

import android.util.Log
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.network.APIBridge

/**
 * Global session holder for the Biome SDK.
 * Stores the BiomeSdkProps passed at launch time.
 */
object GamesHubSession {
    private var _props: BiomeSdkProps? = null
    var props: BiomeSdkProps?
        get() = _props
        set(value) {
            _props = value?.let { props ->
                if (props.userConfig.userId.isBlank()) {
                    val fallbackConfig = BiomeState.getUserConfig()
                    if (fallbackConfig != null) {
                        Log.w("GamesHubSession", "Replacing blank userId config with persisted config")
                        resolveSessionId(props.copy(userConfig = fallbackConfig))
                    } else {
                        Log.w("GamesHubSession", "Ignoring props with blank userId and no persisted config")
                        _props
                    }
                } else {
                    resolveSessionId(props)
                }
            }
        }

    fun resolveSessionId(props: BiomeSdkProps): BiomeSdkProps {
        val config = props.userConfig
        val resolvedSessionId = if (!config.sessionId.isUsableSessionId()) {
            java.util.UUID.randomUUID().toString()
        } else {
            config.sessionId
        }
        val resolvedConfig = config.copy(sessionId = resolvedSessionId)
        return props.copy(userConfig = resolvedConfig)
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
}
