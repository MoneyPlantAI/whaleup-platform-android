package com.whaleup.gameshub.data

/**
 * Configuration provided by the host app to initialize the SDK.
 * Matches Whaleup's UserConfig interface.
 */
data class UserConfig(
    val userId: String,
    val sessionId: String,
    val apiBaseUrl: String,
    val userAgent: String,
    val timezone: String? = null,
    val authToken: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val allowedDomains: List<String>
)

/**
 * Raw message from the WebView (parsed from JSON).
 * The data field is kept as Map for flexibility (matching Whaleup's JsMessage).
 */
data class JsMessage(
    val type: String,
    val action: String,
    val data: Map<String, Any?>? = null
)

/**
 * SDK error reported to the host app via onWhaleupSDKError callback.
 */
data class SDKError(
    val type: String,
    val action: String,
    val data: Map<String, Any?>? = null
)

/**
 * SDK event reported to the host app via onWhaleupSDKEvent callback.
 */
data class SDKEvent(
    val type: String,
    val action: String,
    val message: String? = null,
    val data: Map<String, Any?>? = null
)
