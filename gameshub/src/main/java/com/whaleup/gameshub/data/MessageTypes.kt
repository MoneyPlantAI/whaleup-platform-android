package com.whaleup.gameshub.data

/**
 * All message types matching the Whaleup RN SDK's WhaleupMessageType.
 * Used for routing messages between WebView and native SDK.
 */
object BiomeMessageType {
    const val NAVIGATION = "navigation"
    const val GAMEPLAY = "gameplay"

    const val STATE_SYNC = "stateSync"
    const val LOAD_FAILURE = "loadFailure"
    const val NETWORK_INTERRUPTION = "networkInterruption"
    const val ACTIVITY_RECREATION = "activityRecreation"
    const val OUT_OF_MEMORY = "outOfMemory"
    const val JS_ERROR = "jsError"
    const val NAVIGATION_ERROR = "navigationError"
    const val CRITICAL_FAILURE = "criticalFailure"
    const val ANALYTICS_EVENT = "analyticsEvent"
    const val SHARE = "share"
    const val PLAYER_PREFS = "playerPrefs"
    const val HUB_EVENT = "hub_event"
    const val MULTIPLAYER = "multiplayer"
}

/**
 * All message actions matching the Whaleup RN SDK's WhaleupMessageAction.
 * Actions are paired with message types to form complete message intents.
 */
object BiomeMessageAction {
    // ─── Multiplayer — Client → Engine (WebView sends, SDK emits to socket) ─────────
    const val MP_CONNECT                 = "mp_connect"
    const val MP_DISCONNECT              = "mp_disconnect"
    const val MP_QUEUE_JOIN              = "queue_join"
    const val MP_QUEUE_LEAVE             = "queue_leave"           // bidirectional, no ACK
    const val MP_QUEUE_RECONNECT         = "queue_reconnected"
    const val MP_QUEUE_DISCONNECT        = "queue_disconnect"      // bidirectional, no ACK
    const val MP_LOBBY_LEAVE             = "lobby_leave"           // bidirectional, no ACK
    const val MP_LOBBY_DISCONNECT        = "lobby_disconnect"      // bidirectional, no ACK
    const val MP_PREFLIGHT_PONG          = "lobby_preflight_pong"
    const val MP_GAMEROOM_PLAYER_READY   = "gameRoom_player_ready" // Client → Engine, no ACK
    const val MP_GAMEROOM_COMMAND        = "gameRoom_command"
    const val MP_GAMEROOM_LEAVE          = "gameRoom_leave"        // bidirectional, no ACK
    const val MP_GAMEROOM_RECONNECT      = "gameRoom_reconnected"  // bidirectional, no ACK
    const val MP_GAMEROOM_WHISPER        = "gameRoom_whisper"
    const val MP_GAMEROOM_DISCONNECT     = "gameRoom_disconnect"   // bidirectional, no ACK

    // ─── Multiplayer — SDK → WebView (ACK responses — only 3 events have ACK) ───────
    const val MP_CONNECTED               = "mp_connected"
    const val MP_ERROR                   = "mp_error"
    const val MP_QUEUE_JOIN_RESP         = "queue_join_response"
    const val MP_GAMEROOM_COMMAND_RESP   = "gameRoom_command_response"
    const val MP_GAMEROOM_WHISPER_RESP   = "gameRoom_whisper_response"

    // ─── Multiplayer — Engine → Client (Engine pushes, SDK relays to WebView) ───────
    const val MP_QUEUE_TIMER             = "queue_timer"
    const val MP_QUEUE_EXIT              = "queue_exit"
    const val MP_LOBBY_JOINED            = "lobby_joined"           // Engine pushes, client does NOT emit back
    const val MP_LOBBY_PLAYER_JOINED     = "lobby_player_joined"
    const val MP_LOBBY_PLAYER_LEFT       = "lobby_player_left"
    const val MP_LOBBY_WAIT_TIMER        = "lobby_wait_timer"
    const val MP_LOBBY_COUNTDOWN         = "lobby_countdown_started"
    const val MP_LOBBY_ABORTED           = "lobby_countdown_aborted"
    const val MP_LOBBY_PREFLIGHT         = "lobby_preflight_ping"
    const val MP_LOBBY_SEALED            = "lobby_sealed"
    const val MP_LOBBY_EXIT              = "lobby_exit"
    const val MP_LOBBY_DISBANDED         = "lobby_disbanded"
    const val MP_LOBBY_RECONNECTED       = "lobby_reconnected"      // Engine pushes automatically
    const val MP_GAMEROOM_JOINED         = "gameRoom_joined"        // Engine pushes, client does NOT emit back
    const val MP_GAMEROOM_PLAYER_LOADED  = "gameRoom_player_loaded"
    const val MP_GAMEROOM_START_CDOWN    = "gameRoom_start_countdown"
    const val MP_GAMEROOM_PLAYER_LEFT    = "gameRoom_player_left"
    const val MP_GAMEROOM_RECONNECTED    = "gameRoom_reconnected"   // Engine pushes automatically
    const val MP_GAMEROOM_COMPLETE       = "gameRoom_complete"
    const val MP_GAMEROOM_BROADCAST      = "gameRoom_broadcast"
    const val MP_ENGINE_ERROR            = "engine_error"
    const val MP_SESSION_STATUS          = "session_status"
    const val MP_EVICTED                 = "evicted"
    const val MP_TIMER_TICK              = "timer_tick"

    // Navigation actions
    const val LAUNCH_GAME = "launchGame"
    const val HUB_LOADED = "hubLoaded"
    const val HUB_UNLOADED = "hubUnloaded"
    const val START_GAME = "startGame"
    const val EXIT_GAME = "exitGame"
    const val GAME_LOADED = "gameLoaded"
    const val GAME_UNLOADED = "gameUnloaded"
    const val BEGIN_GAME_EXIT = "beginGameExit"
    const val REFRESH_WEBVIEW_FOR_HUB = "refreshWebViewForHub"
    const val HUB_RELOADED = "hubReloaded"
    const val CLOSE_SDK = "closeSdk"
    const val VIEW_PROFILE = "viewProfile"
    const val CLOSE = "close" // Keep for backward compatibility

    // Gameplay actions
    const val ROUND_STARTED = "roundStarted"
    const val GAME_COMPLETED = "gameCompleted"
    const val GAME_STARTED_ACK = "gameStartedAck"
    const val GAME_COMPLETED_ACK = "gameCompletedAck"
    const val COINS_EARNED = "coinsEarned" // Keep legacy
    const val GEMS_EARNED = "gemsEarned"   // Keep legacy

    // Gullak actions
    const val CLAIM_GULLAK = "claimGullak"
    const val GULLAK_CLAIMED_ACK = "gullakClaimedAck"

    // State sync actions
    const val REQUEST_PROFILE = "requestProfile"
    const val UPDATE_PROFILE = "updateProfile"
    const val REQUEST_GAME_CONFIG = "requestGameConfig"
    const val UPDATE_GAME_CONFIG = "updateGameConfig"
    const val GAME_API_REQUEST = "gameApiRequest"
    const val GAME_API_RESPONSE = "gameApiResponse"
    const val CUSTOM_REQUEST = "customRequest"
    const val RESTORE_STATE = "restoreState"

    // Load failure actions
    const val HUB_LOAD_ERROR = "hubLoadError"
    const val GAME_LOAD_ERROR = "gameLoadError"
    const val WEBVIEW_TERMINATED = "webViewTerminated"
    const val INTERNAL_ERROR = "internalError"
    const val RETRY_LOAD = "retryLoad"
    const val ROUND_START_ERROR = "roundStartError"
    const val ROUND_COMPLETE_ERROR = "roundCompleteError"

    // Network interruption actions
    const val NETWORK_INTERRUPTED = "networkInterrupted"
    const val NETWORK_RESTORED = "networkRestored"
    const val NETWORK_LOAD_ERROR = "networkLoadError"
    const val RETRY_AFTER_NETWORK = "retryAfterNetwork"

    // Activity recreation actions
    const val RELOAD_WEBVIEW = "reloadWebView"
    const val STATE_RESTORED = "stateRestored"
    const val ACTIVITY_RECREATED = "activityRecreated"

    // Out of memory actions
    const val LOW_MEMORY_WARNING = "lowMemoryWarning"
    const val RECOVER_AFTER_OOM = "recoverAfterOOM"
    const val RECOVERY_COMPLETE = "recoveryComplete"
    const val OOM_WEBVIEW_TERMINATED = "oomWebViewTerminated"
    const val RECOVERY_IN_PROGRESS = "recoveryInProgress"
    const val RECOVERY_OUTCOME = "recoveryOutcome"

    // JS error actions
    const val JS_ERROR = "jsError"
    const val FATAL_JS_ERROR = "fatalJsError"
    const val RECOVER_FROM_JS_ERROR = "recoverFromJsError"
    const val JS_ERROR_RECOVER_COMPLETE = "jsErrorRecoverComplete"

    // Navigation error actions
    const val INVALID_NAVIGATION = "invalidNavigation"
    const val NAVIGATION_BLOCKED = "navigationBlocked"
    const val BACK_NAVIGATION = "backNavigation" // Keep legacy
    const val URL_BLOCKED = "urlBlocked"         // Keep legacy

    // Critical failure actions
    const val CRITICAL_FAILURE = "criticalFailure"
    const val HOST_DECISION = "hostDecision"

    // Analytics actions
    const val ANALYTICS_EVENT = "analyticsEvent"

    // Share actions
    const val SHARE_CONTENT = "shareContent"
    const val SHARE_SUCCESS = "shareSuccess"
    const val SHARE_FAILED = "shareFailed"
    const val COPY_TO_CLIPBOARD = "copyToClipboard"

    // Player prefs actions
    const val GET_PLAYER_PREF = "getPlayerPref"
    const val SET_PLAYER_PREF = "setPlayerPref"
    const val DELETE_PLAYER_PREF = "deletePlayerPref"
    const val PLAYER_PREF_VALUE = "playerPrefValue"
    const val MIGRATE_PLAYER_PREFS = "migratePlayerPrefs"
    const val USER_LOGGED_OUT = "userLoggedOut"

    // Mixpanel Analytics Events (from event design v1)
    const val HUB_VIEWED = "hub_viewed"
    const val HUB_GAME_CARD_TAPPED = "hub_game_card_tapped"
}
