package com.whaleup.gameshub.data

import android.util.Log
import com.whaleup.gameshub.R
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.network.APIBridge
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Global session holder for the Biome SDK.
 * Stores the BiomeSdkProps passed at launch time and provides
 * theme resolution for Activities.
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

    private val themeListeners = CopyOnWriteArraySet<ThemeChangeListener>()

    val theme: String
        get() = props?.currentTheme() ?: "light"

    interface ThemeChangeListener {
        fun onThemeChanged(theme: String)
    }

    fun addThemeChangeListener(listener: ThemeChangeListener) {
        themeListeners.add(listener)
    }

    fun removeThemeChangeListener(listener: ThemeChangeListener) {
        themeListeners.remove(listener)
    }

    fun updateTheme(theme: String) {
        props = props?.copy(theme = theme, updateTheme = null)
        notifyThemeChanged()
    }

    fun refreshTheme() {
        notifyThemeChanged()
    }

    fun getThemeResId(): Int {
        return if (theme.lowercase() == "dark") {
            R.style.Theme_GamesHub_Dark
        } else {
            R.style.Theme_GamesHub_Light
        }
    }

    /**
     * Initialize state managers that need context.
     * Call this from the launcher before starting any activity.
     */
    fun initialize(context: android.content.Context) {
        BiomeState.init(context)
        PlayerPrefsManager.init(context)
        APIBridge.init(context)
    }

    private fun notifyThemeChanged() {
        val currentTheme = theme
        themeListeners.forEach { it.onThemeChanged(currentTheme) }
    }
}
