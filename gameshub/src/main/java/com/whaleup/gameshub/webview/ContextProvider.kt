package com.whaleup.gameshub.webview

import org.json.JSONObject

object ContextProvider {
    
    fun getContextJson(): String {
        val context = JSONObject()
        val props = com.whaleup.gameshub.data.GamesHubSession.props
        val config = props?.userConfig?.takeIf { it.userId.isNotBlank() }
            ?: com.whaleup.gameshub.data.BiomeState.getUserConfig()
        
        // User
        val user = JSONObject()
        user.put("id", config?.userId ?: "")
        user.put("name", config?.name ?: "")
        user.put("avatar", config?.avatar ?: "")
        context.put("user", user)
        
        // Auth
        val auth = JSONObject()
        auth.put("token", config?.authToken ?: "")
        context.put("auth", auth)
        
        // App Metadata
        val app = JSONObject()
        app.put("version", "2.0.0")
        app.put("platform", "android")
        app.put("theme", "light")
        context.put("app", app)

        return context.toString()
    }
}
