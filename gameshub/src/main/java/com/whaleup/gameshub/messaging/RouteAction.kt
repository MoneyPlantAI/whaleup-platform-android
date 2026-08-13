package com.whaleup.gameshub.messaging

/**
 * Sealed class representing the actions that the message router can dispatch.
 * Each variant maps to a handler in the ActionProcessor.
 * 
 * This is the Kotlin equivalent of Whaleup's RouteAction type union.
 */
sealed class RouteAction {

    /** Bubble the raw message up to the host app's onMessage callback */
    data class Bubble(val message: BiomeMessage) : RouteAction()

    /** Report an error to the host app's onWhaleupSDKError callback */
    data class SdkError(val error: BiomeSdkError) : RouteAction()

    /** Report an event to the host app's onWhaleupSDKEvent callback */
    data class SdkEvent(val event: BiomeSdkEvent) : RouteAction()

    /** Navigate back to the hub */
    object LoadHub : RouteAction()

    /** Launch a specific game by ID */
    data class LoadGame(val gameId: String?) : RouteAction()

    /** Close the current WebView */
    object Close : RouteAction()

    /** Close the entire SDK experience */
    object CloseSdk : RouteAction()

    /** Send the current user profile to the WebView */
    object SendProfile : RouteAction()

    /** Send a specific game's config to the WebView */
    data class SendGameConfig(val gameId: String) : RouteAction()

    /** Invoke an API call and optionally send the response back to the WebView */
    data class ApiCall(
        val endpoint: String,
        val data: Map<String, Any?>?,
        val respondWith: String? = null
    ) : RouteAction()

    /** Request the host app to decide on a recovery strategy */
    data class HostDecision(
        val strategy: String,
        val data: Map<String, Any?>? = null
    ) : RouteAction()

    /** Get a player preference value and send it back to WebView */
    data class GetPlayerPref(
        val key: String,
        val defaultValue: Any? = null
    ) : RouteAction()

    /** Set a player preference value */
    data class SetPlayerPref(
        val key: String,
        val value: Any? = null
    ) : RouteAction()

    /** Delete a player preference */
    data class DeletePlayerPref(val key: String) : RouteAction()

    /** Migrate player preferences from localStorage data */
    data class MigratePlayerPrefs(val prefs: Map<String, String>) : RouteAction()

    object UserLogout : RouteAction()

    /** Trigger native share sheet */
    data class ShareIntent(
        val text: String,
        val payload: SharePayload
    ) : RouteAction()

    /** Copy text to clipboard */
    data class CopyToClipboard(val text: String) : RouteAction()

    /** Ignore this message — no action needed */
    object Ignore : RouteAction()

    /** Forward multiplayer command to the MultiplayerModule */
    data class MultiplayerCommand(val action: String, val data: Map<String, Any?>?) : RouteAction()
}

/**
 * Data class for messages flowing between WebView and native layer.
 * Equivalent to Whaleup's JsMessage / NativeToJsMessage.
 */
data class BiomeMessage(
    val type: String,
    val action: String,
    val data: Map<String, Any?>? = null
)

/**
 * Data class for SDK errors reported to the host app.
 */
data class BiomeSdkError(
    val type: String,
    val action: String,
    val data: Map<String, Any?>? = null
)

/**
 * Data class for SDK events reported to the host app.
 */
data class BiomeSdkEvent(
    val type: String,
    val action: String,
    val message: String? = null,
    val data: Map<String, Any?>? = null
)

/**
 * Payload for share intents from the WebView.
 */
data class SharePayload(
    val image: String? = null,
    val title: String? = null,
    val url: String? = null
)
