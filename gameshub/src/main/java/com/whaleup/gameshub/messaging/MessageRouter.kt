package com.whaleup.gameshub.messaging

import android.util.Log
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.network.HubEndpoint
import org.json.JSONObject

private const val TAG = "MessageRouter"

/**
 * Routes incoming WebView messages to the appropriate RouteAction(s).
 * 
 * This is the Kotlin port of Whaleup's MessageRouter.ts. It parses raw JSON
 * from the bridge, validates the structure, and dispatches to type-specific
 * routing methods that produce a list of RouteActions.
 */
object MessageRouter {

    /**
     * Parse a raw JSON string into a BiomeMessage.
     * Returns null if the message is malformed.
     */
    fun parseMessage(rawJson: String): BiomeMessage? {
        return try {
            val json = JSONObject(rawJson)
            val type = json.optString("type", "")
            val action = json.optString("action", "")

            if (type.isEmpty() || action.isEmpty()) {
                Log.w(TAG, "Message missing type or action: $rawJson")
                return null
            }

            val data = json.optJSONObject("data")?.toMap()
            BiomeMessage(type = type, action = action, data = data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $rawJson", e)
            null
        }
    }

    /**
     * Route a parsed message to a list of RouteActions.
     */
    fun route(message: BiomeMessage): List<RouteAction> {
        Log.d(TAG, "Routing message: type=${message.type}, action=${message.action}")
        val actions = when (message.type) {
            BiomeMessageType.NAVIGATION -> routeNavigation(message)
            BiomeMessageType.GAMEPLAY -> routeGameplay(message)
            BiomeMessageType.STATE_SYNC -> routeStateSync(message)
            BiomeMessageType.LOAD_FAILURE -> routeLoadFailure(message)
            BiomeMessageType.NETWORK_INTERRUPTION -> routeNetworkInterruption(message)
            BiomeMessageType.ACTIVITY_RECREATION -> routeActivityRecreation(message)
            BiomeMessageType.OUT_OF_MEMORY -> routeOutOfMemory(message)
            BiomeMessageType.JS_ERROR -> routeJsError(message)
            BiomeMessageType.NAVIGATION_ERROR -> routeNavigationError(message)
            BiomeMessageType.CRITICAL_FAILURE -> routeCriticalFailure(message)
            BiomeMessageType.ANALYTICS_EVENT -> routeAnalytics(message)
            BiomeMessageType.SHARE -> routeShare(message)
            BiomeMessageType.PLAYER_PREFS -> routePlayerPrefs(message)
            BiomeMessageType.HUB_EVENT -> routeHubEvent(message)
            BiomeMessageType.MULTIPLAYER -> routeMultiplayer(message)
            else -> {
                Log.w(TAG, "Unknown message type: ${message.type}")
                listOf(RouteAction.Bubble(message))
            }
        }
        Log.d(TAG, "Generated actions: ${actions.joinToString { it.javaClass.simpleName }}")
        return actions
    }

    private fun routeMultiplayer(msg: BiomeMessage): List<RouteAction> {
        return listOf(RouteAction.MultiplayerCommand(action = msg.action, data = msg.data))
    }

    // region Navigation

    private fun routeNavigation(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.LAUNCH_GAME -> {

                val gameId = msg.data?.get("gameId") as? String

                if (!gameId.isNullOrEmpty()) {
                    BiomeState.setCurrentGameId(gameId)
                    Log.i(TAG, "[Navigation] Game selected for launch: $gameId")
                } else {
                    Log.w(TAG, "launchGame missing gameId")
                }

                listOf(
                    RouteAction.Bubble(msg),
                    RouteAction.LoadGame(gameId) // allow null if your API supports it
                )
            }

            BiomeMessageAction.HUB_LOADED -> {
                Log.i(TAG, "Hub loaded")
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.HUB_UNLOADED -> {
                Log.i(TAG, "[Navigation] Hub unloaded (UI cleared)")
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.HUB_RELOADED -> {
                Log.i(TAG, "Hub reloaded and ready")
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.VIEW_PROFILE -> {
                Log.i(TAG, "View profile requested via navigation")
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.START_GAME -> {
                Log.i(TAG, "Game started (playable)")
                val startGameId = (msg.data?.get("gameId") as? String)
                    ?: BiomeState.getCurrentGameId()
                if (!startGameId.isNullOrEmpty()) {
                    BiomeState.setCurrentGameId(startGameId)
                }
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.GAME_LOADED -> {
                Log.i(TAG, "Game loaded")
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.GAME_UNLOADED -> {
                Log.i(TAG, "Game unloaded")
                listOf(RouteAction.Bubble(msg))
            }

            BiomeMessageAction.EXIT_GAME -> {
                Log.i(TAG, "Exit game requested")
                val exitGameId = BiomeState.getCurrentGameId()
                val exitUserId = BiomeState.getUserConfig()?.userId
                listOf(
                    RouteAction.SdkEvent(
                        BiomeSdkEvent(
                            type = "exitGame",
                            action = "beginGameExit",
                            message = "Game session ending",
                            data = mapOf(
                                "gameId" to exitGameId,
                                "userId" to exitUserId
                            )
                        )
                    ),
                    RouteAction.Bubble(msg),
                    RouteAction.LoadHub
                )
            }

            BiomeMessageAction.CLOSE -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.Close
            )

            BiomeMessageAction.CLOSE_SDK -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.CloseSdk
            )

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Gameplay

    private fun routeGameplay(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.ROUND_STARTED -> {
                val gameId = (msg.data?.get("gameId") as? String)
                    ?: BiomeState.getCurrentGameId()

                val userId = (msg.data?.get("userId") as? String)
                    ?: BiomeState.getUserConfig()?.userId

                Log.d(TAG, "Message is: $msg")
                Log.d(TAG, "Resolved gameId: $gameId, userId: $userId")

                val payload = mapOf(
                    "userId" to userId,
                    "gameId" to gameId
                )

                return if (!gameId.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                    listOf(
                        RouteAction.Bubble(msg),
                        RouteAction.ApiCall(
                            endpoint = HubEndpoint.GAME_STARTED.name,
                            data = payload,
                            respondWith = BiomeMessageAction.GAME_STARTED_ACK
                        )
                    )
                } else {
                    listOf(
                        RouteAction.Bubble(msg),
                        // optionally add fallback action
                     RouteAction.SdkEvent(BiomeSdkEvent(
                         type = BiomeMessageType.CRITICAL_FAILURE,
                         action = BiomeMessageAction.ROUND_START_ERROR,
                         message = "Missing gameId or userId",
                         data = payload
                     ))
                    )
                }
            }

            BiomeMessageAction.GAME_COMPLETED -> {

                Log.i(TAG, "Game completed: ${msg.data}")

                val gemsEarned = (msg.data?.get("gems") as? Number)?.toInt() ?: 0
                val completedGameId = BiomeState.getCurrentGameId()
                val completedUserId = BiomeState.getUserConfig()?.userId
                val sessionId = BiomeState.getSessionId()
                val playTimeInSec = (msg.data?.get("playTimeInSec") as? Number)?.toInt() ?: 0
                val score = (msg.data?.get("score") as? Number)?.toInt() ?: gemsEarned

                // ✅ Optimistic update
//                if (gemsEarned > 0) {
//                    BiomeState.incrementGemsEarned(gemsEarned)
//                    Log.d(
//                        TAG,
//                        "Game completed with $gemsEarned gems, Total: ${BiomeState.getCoinsEarnedToday()}"
//                    )
//                }

                // ✅ Increment coins if max bonus not reached
//                if (!BiomeState.getCurrentGameIsMaxGameBonusEarned()) {
//                    BiomeState.incrementCoinsEarned(coinsEarned)
//                }

                val actions = mutableListOf<RouteAction>(
                    RouteAction.Bubble(msg)
                )

                // ✅ Prepare payload
                val payload = mapOf(
                    "userId" to completedUserId,
                    "gameId" to completedGameId,
                    "gameSessionId" to (sessionId ?: ""),
                    "score" to score,
                    "playTimeInSec" to playTimeInSec
                )

                Log.d(TAG, "gameend payload: $payload")
                // ✅ Sync with backend
                if (!completedUserId.isNullOrEmpty() && !completedGameId.isNullOrEmpty()) {

                    actions.add(
                        RouteAction.ApiCall(
                            endpoint = HubEndpoint.GAME_ENDED.name,
                            data = payload,
                            respondWith = BiomeMessageAction.GAME_COMPLETED_ACK
                        )
                    )

                } else {

                    actions.add(
                        RouteAction.SdkEvent(
                            BiomeSdkEvent(
                                type = BiomeMessageType.CRITICAL_FAILURE,
                                action = BiomeMessageAction.ROUND_COMPLETE_ERROR,
                                message = "Missing gameId or userId",
                                data = mapOf(
                                    "gameId" to completedGameId,
                                    "userId" to completedUserId
                                )
                            )
                        )
                    )

                    Log.w(
                        TAG,
                        "gameEnded API call NOT added: missingUserId=${completedUserId.isNullOrEmpty()}, missingGameId=${completedGameId.isNullOrEmpty()}"
                    )
                }

                actions
            }
            BiomeMessageAction.CLAIM_GULLAK -> {
                val userId = BiomeState.getUserConfig()?.userId?.takeIf { it.isNotBlank() }
                val actions = mutableListOf<RouteAction>(RouteAction.Bubble(msg))

                if (userId != null) {
                    actions.add(
                        RouteAction.ApiCall(
                            endpoint = HubEndpoint.CLAIM_GULLAK.name,
                            data = mapOf("userId" to userId),
                            respondWith = BiomeMessageAction.GULLAK_CLAIMED_ACK
                        )
                    )
                } else {
                    actions.add(
                        RouteAction.SdkEvent(
                            BiomeSdkEvent(
                                type = BiomeMessageType.CRITICAL_FAILURE,
                                action = "claimGullakError",
                                message = "Missing userId",
                                data = emptyMap()
                            )
                        )
                    )
                    Log.w(TAG, "claimGullak API call NOT added: missing userId")
                }

                actions
            }

//            BiomeMessageAction.COINS_EARNED -> {
//                listOf(
//                    RouteAction.Bubble(msg),
//                    RouteAction.SdkEvent(
//                        BiomeSdkEvent(
//                            type = BiomeMessageType.GAMEPLAY,
//                            action = BiomeMessageAction.COINS_EARNED,
//                            message = "Coins earned",
//                            data = msg.data
//                        )
//                    )
//                )
//            }
//
//            BiomeMessageAction.GEMS_EARNED -> {
//                listOf(
//                    RouteAction.Bubble(msg),
//                    RouteAction.SdkEvent(
//                        BiomeSdkEvent(
//                            type = BiomeMessageType.GAMEPLAY,
//                            action = BiomeMessageAction.GEMS_EARNED,
//                            message = "Gems earned",
//                            data = msg.data
//                        )
//                    )
//                )
//            }

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region State Sync

    private fun routeStateSync(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.REQUEST_PROFILE -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.ApiCall(
                    endpoint = HubEndpoint.GET_USER_PROFILE.name,
                    data = msg.data,
                    respondWith = BiomeMessageAction.UPDATE_PROFILE
                )
            )

            BiomeMessageAction.REQUEST_GAME_CONFIG -> {
                val gameId = msg.data?.get("gameId") as? String
                if (!gameId.isNullOrEmpty()) {
                    listOf(
                        RouteAction.Bubble(msg),
                        RouteAction.SendGameConfig(gameId)
                    )
                } else {
                    listOf(RouteAction.Bubble(msg))
                }
            }

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Load Failure

    private fun routeLoadFailure(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.HUB_LOAD_ERROR -> listOf(
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.LOAD_FAILURE,
                        action = BiomeMessageAction.HUB_LOAD_ERROR,
                        data = msg.data
                    )
                )
            )

            BiomeMessageAction.GAME_LOAD_ERROR -> listOf(
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.LOAD_FAILURE,
                        action = BiomeMessageAction.GAME_LOAD_ERROR,
                        data = msg.data
                    )
                )
            )

            BiomeMessageAction.WEBVIEW_TERMINATED -> listOf(
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.LOAD_FAILURE,
                        action = BiomeMessageAction.WEBVIEW_TERMINATED,
                        data = msg.data
                    )
                )
            )

            BiomeMessageAction.RETRY_LOAD -> {
                val target = msg.data?.get("target") as? String
                if (target == "game") {
                    val gameId = msg.data?.get("gameId") as? String
                    if (gameId != null) listOf(RouteAction.LoadGame(gameId))
                    else listOf(RouteAction.LoadHub)
                } else {
                    listOf(RouteAction.LoadHub)
                }
            }

            else -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.LOAD_FAILURE,
                        action = msg.action,
                        data = msg.data
                    )
                )
            )
        }
    }

    // endregion

    // region Network Interruption

    private fun routeNetworkInterruption(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.NETWORK_INTERRUPTED -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.NETWORK_INTERRUPTION,
                        action = BiomeMessageAction.NETWORK_INTERRUPTED,
                        data = msg.data
                    )
                )
            )

            // Sent when connectivity returns; just bubble so host app is notified
            BiomeMessageAction.NETWORK_RESTORED -> {
                Log.i(TAG, "Network restored: ${msg.data}")
                listOf(RouteAction.Bubble(msg))
            }

            // Final failure after network interruption (e.g. timeout)
            BiomeMessageAction.NETWORK_LOAD_ERROR -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.NETWORK_INTERRUPTION,
                        action = BiomeMessageAction.NETWORK_LOAD_ERROR,
                        data = msg.data
                    )
                )
            )

            BiomeMessageAction.RETRY_AFTER_NETWORK -> {
                Log.i(TAG, "Host requested retry after network restoration")
                listOf(RouteAction.LoadHub)
            }

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Activity Recreation

    private fun routeActivityRecreation(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.RELOAD_WEBVIEW -> listOf(
                RouteAction.SdkEvent(
                    BiomeSdkEvent(
                        type = BiomeMessageType.ACTIVITY_RECREATION,
                        action = BiomeMessageAction.RELOAD_WEBVIEW,
                        message = "WebView needs to be reloaded",
                        data = msg.data
                    )
                )
            )

            BiomeMessageAction.STATE_RESTORED -> listOf(RouteAction.Bubble(msg))

            BiomeMessageAction.ACTIVITY_RECREATED -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.LoadHub
            )

            BiomeMessageAction.RESTORE_STATE -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.SendProfile,
                RouteAction.SdkEvent(
                    BiomeSdkEvent(
                        type = BiomeMessageType.ACTIVITY_RECREATION,
                        action = BiomeMessageAction.RESTORE_STATE,
                        message = "State restored after recreation",
                        data = msg.data
                    )
                )
            )

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Out of Memory

    private fun routeOutOfMemory(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.LOW_MEMORY_WARNING -> {
                Log.w(TAG, "Low memory warning: ${msg.data}")
                listOf(
                    RouteAction.SdkError(
                        BiomeSdkError(
                            type = BiomeMessageType.OUT_OF_MEMORY,
                            action = BiomeMessageAction.LOW_MEMORY_WARNING,
                            data = msg.data
                        )
                    )
                )
            }

            BiomeMessageAction.RECOVER_AFTER_OOM -> {
                Log.w(TAG, "OOM recovery required: ${msg.data}")
                listOf(
                    RouteAction.SdkError(
                        BiomeSdkError(
                            type = BiomeMessageType.OUT_OF_MEMORY,
                            action = BiomeMessageAction.RECOVER_AFTER_OOM,
                            data = msg.data
                        )
                    )
                )
            }

            BiomeMessageAction.RECOVERY_COMPLETE -> {
                Log.i(TAG, "OOM recovery complete")
                listOf(RouteAction.Bubble(msg))
            }

            // Default: just bubble (matches TS behaviour)
            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region JS Error

    private fun routeJsError(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            // Recoverable JS error - logged but doesn't break UI
            BiomeMessageAction.JS_ERROR -> {
                Log.e(TAG, "JS error: ${msg.data}")
                listOf(
                    RouteAction.SdkError(
                        BiomeSdkError(
                            type = BiomeMessageType.JS_ERROR,
                            action = BiomeMessageAction.JS_ERROR,
                            data = msg.data
                        )
                    )
                )
            }

            // Unrecoverable JS crash
            BiomeMessageAction.FATAL_JS_ERROR -> {
                Log.e(TAG, "Fatal JS error: ${msg.data}")
                listOf(
                    RouteAction.Bubble(msg),
                    RouteAction.SdkError(
                        BiomeSdkError(
                            type = BiomeMessageType.JS_ERROR,
                            action = BiomeMessageAction.FATAL_JS_ERROR,
                            data = msg.data
                        )
                    )
                )
            }

            BiomeMessageAction.JS_ERROR_RECOVER_COMPLETE -> {
                Log.i(TAG, "JS error recovery confirmed")
                listOf(RouteAction.Bubble(msg))
            }

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Navigation Error

    private fun routeNavigationError(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.INVALID_NAVIGATION -> {
                Log.e(TAG, "Invalid navigation: ${msg.data}")
                listOf(
                    RouteAction.SdkError(
                        BiomeSdkError(
                            type = BiomeMessageType.NAVIGATION_ERROR,
                            action = BiomeMessageAction.INVALID_NAVIGATION,
                            data = msg.data
                        )
                    )
                )
            }

            // Web layer has handled the UI fallback - just acknowledge
            BiomeMessageAction.NAVIGATION_BLOCKED -> {
                Log.w(TAG, "Navigation blocked: ${msg.data}")
                listOf(RouteAction.Bubble(msg))
            }

            // Legacy: Android back navigation
            BiomeMessageAction.BACK_NAVIGATION -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.HostDecision(strategy = "returnToHub", data = msg.data)
            )

            // Legacy: URL was blocked
            BiomeMessageAction.URL_BLOCKED -> listOf(
                RouteAction.Bubble(msg),
                RouteAction.SdkError(
                    BiomeSdkError(
                        type = BiomeMessageType.NAVIGATION_ERROR,
                        action = BiomeMessageAction.URL_BLOCKED,
                        data = msg.data
                    )
                )
            )

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Critical Failure

    private fun routeCriticalFailure(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.HOST_DECISION -> {
                // strategy can come from data.action or data.strategy
                val strategy = (msg.data?.get("action") as? String)
                    ?: (msg.data?.get("strategy") as? String)
                    ?: "retry"
                listOf(RouteAction.HostDecision(strategy = strategy, data = msg.data))
            }

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Analytics

    private fun routeAnalytics(msg: BiomeMessage): List<RouteAction> {
        val eventName = msg.data?.get("eventName") as? String ?: "unknown_event"
        Log.i(TAG, "Analytics event: $eventName, data: ${msg.data}")
        return listOf(
            RouteAction.Bubble(msg),
            RouteAction.SdkEvent(
                BiomeSdkEvent(
                    // hardcode 'analyticsEvent' to match TS which always uses the same action
                    type = BiomeMessageType.ANALYTICS_EVENT,
                    action = BiomeMessageAction.ANALYTICS_EVENT,
                    message = "Analytics: $eventName",
                    data = msg.data
                )
            )
        )
    }

    // endregion

    // region Share

    @Suppress("UNCHECKED_CAST")
    private fun routeShare(msg: BiomeMessage): List<RouteAction> {
        return when (msg.action) {
            BiomeMessageAction.SHARE_CONTENT -> {
                val text = msg.data?.get("text") as? String
                val image = msg.data?.get("image") as? String
                // Validate required fields (text and image) just like the TS reference
                if (text.isNullOrEmpty() || image.isNullOrEmpty()) {
                    Log.w(TAG, "[MessageRouter] Share missing text/image")
                    return listOf(RouteAction.Ignore)
                }
                val payload = SharePayload(
                    image = image,
                    title = msg.data?.get("title") as? String,
                    url = msg.data?.get("url") as? String
                )
                listOf(
                    RouteAction.Bubble(msg),
                    RouteAction.ShareIntent(text, payload)
                )
            }

//            BiomeMessageAction.SHARE_REQUEST -> {
//                val payloadMap = msg.data?.get("payload") as? Map<String, Any?>
//                val text = msg.data?.get("text") as? String ?: ""
//                val payload = SharePayload(
//                    image = payloadMap?.get("image") as? String,
//                    title = payloadMap?.get("title") as? String,
//                    url = payloadMap?.get("url") as? String
//                )
//                listOf(
//                    RouteAction.Bubble(msg),
//                    RouteAction.ShareIntent(text, payload)
//                )
//            }

            BiomeMessageAction.COPY_TO_CLIPBOARD -> {
                val text = msg.data?.get("text") as? String ?: ""
                listOf(
                    RouteAction.Bubble(msg),
                    RouteAction.CopyToClipboard(text)
                )
            }

            else -> listOf(RouteAction.Bubble(msg))
        }
    }

    // endregion

    // region Player Prefs

    private fun routePlayerPrefs(msg: BiomeMessage): List<RouteAction> {
        val key = msg.data?.get("key") as? String

        return when (msg.action) {
            BiomeMessageAction.GET_PLAYER_PREF -> {
                if (key.isNullOrEmpty()) {
                    Log.w(TAG, "getPlayerPref missing key")
                    listOf(RouteAction.Ignore)
                } else {
                    listOf(
                        RouteAction.GetPlayerPref(key, msg.data?.get("defaultValue"))
                    )
                }
            }

            BiomeMessageAction.SET_PLAYER_PREF -> {
                if (key.isNullOrEmpty()) {
                    Log.w(TAG, "setPlayerPref missing key")
                    listOf(RouteAction.Ignore)
                } else {
                    listOf(
                        RouteAction.SetPlayerPref(key, msg.data?.get("value"))
                    )
                }
            }

            BiomeMessageAction.DELETE_PLAYER_PREF -> {
                if (key.isNullOrEmpty()) {
                    Log.w(TAG, "deletePlayerPref missing key")
                    listOf(RouteAction.Ignore)
                } else {
                    listOf(RouteAction.DeletePlayerPref(key))
                }
            }

            BiomeMessageAction.MIGRATE_PLAYER_PREFS -> {
                @Suppress("UNCHECKED_CAST")
                val prefs = (msg.data?.get("prefs") as? Map<String, String>)
                    ?: (msg.data as? Map<String, String>)
                if (prefs == null) {
                    Log.w(TAG, "migratePlayerPrefs missing prefs")
                    listOf(RouteAction.Ignore)
                } else {
                    listOf(RouteAction.MigratePlayerPrefs(prefs))
                }
            }

            BiomeMessageAction.USER_LOGGED_OUT -> listOf(RouteAction.UserLogout)

            else -> listOf(RouteAction.Ignore)
        }
    }

    // endregion

    // region Hub Events

    private fun routeHubEvent(msg: BiomeMessage): List<RouteAction> {
        return listOf(
            RouteAction.Bubble(msg),
            RouteAction.SdkEvent(
                BiomeSdkEvent(
                    type = BiomeMessageType.HUB_EVENT,
                    action = msg.action,
                    message = "Hub event: ${msg.action}",
                    data = msg.data
                )
            )
        )
    }

    // endregion
}

/**
 * Extension to convert a JSONObject to a Map<String, Any?>.
 */
fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = this.opt(key)
        map[key] = when (value) {
            is JSONObject -> value.toMap()
            is org.json.JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

/**
 * Extension to convert a JSONArray to a List<Any?>.
 */
fun org.json.JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        val value = this.opt(i)
        list.add(
            when (value) {
                is JSONObject -> value.toMap()
                is org.json.JSONArray -> value.toList()
                JSONObject.NULL -> null
                else -> value
            }
        )
    }
    return list
}
